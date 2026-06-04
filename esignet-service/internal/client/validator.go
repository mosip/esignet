package client

import (
	"context"
	_ "embed"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"time"

	"github.com/santhosh-tekuri/jsonschema/v6"
	"github.com/santhosh-tekuri/jsonschema/v6/kind"
	"golang.org/x/text/language"
)

//go:embed schemas/create_request.schema.json
var embeddedCreateRequestSchemaBytes []byte

//go:embed schemas/additional_config.schema.json
var embeddedAdditionalConfigSchemaBytes []byte

const (
	createRequestSchemaID    = "embedded://create_request.schema.json"
	additionalConfigSchemaID = "embedded://additional_config_request_schema.json"
)

// schemaFetchTimeout bounds the one-shot http(s) fetch on the override
// path. file:// reads are unbounded.
const schemaFetchTimeout = 10 * time.Second

// maxSchemaBytes caps an override schema fetch at 10 MiB. Real schemas
// are ~2 KB; the ceiling guards against unbounded downloads.
const maxSchemaBytes = 10 << 20

// Placeholder sentinels in the schema's enum constraints, substituted
// with operator-configured lists at compile time.
const (
	placeholderUserClaims        = "__PLACEHOLDER_USER_CLAIMS__"
	placeholderACRValues         = "__PLACEHOLDER_ACR_VALUES__"
	placeholderGrantTypes        = "__PLACEHOLDER_GRANT_TYPES__"
	placeholderClientAuthMethods = "__PLACEHOLDER_CLIENT_AUTH_METHODS__"
)

// ValidatorConfig carries the operator-controlled allowed-value sets
// for grant types, claims, ACR values, client auth methods, and the regex
// that bounds clientId / relyingPartyId.
type ValidatorConfig struct {
	SupportedGrantTypes        []string
	SupportedClientAuthMethods []string
	SupportedUserClaims        []string
	SupportedACRValues         []string
	SupportedIDRegex           string
}

// Validator holds the compiled schemas for each HTTP verb.
type Validator struct {
	create                 *jsonschema.Schema
	additionalConfigSchema *jsonschema.Schema
}

// NewValidator builds a Validator using the embedded additionalConfig
// schema. For an override URL, use NewValidatorWithSchema.
func NewValidator(cfg ValidatorConfig) (*Validator, error) {
	return buildValidator(cfg, embeddedAdditionalConfigSchemaBytes)
}

// NewValidatorWithSchema is the override-URL variant of NewValidator. It
// fetches the additionalConfig schema from schemaURL (http / https / file).
func NewValidatorWithSchema(ctx context.Context, cfg ValidatorConfig, schemaURL string) (*Validator, error) {
	if schemaURL == "" {
		return NewValidator(cfg)
	}
	raw, err := fetchSchemaBytes(ctx, schemaURL)
	if err != nil {
		return nil, fmt.Errorf("fetch additionalConfig schema: %w", err)
	}
	return buildValidator(cfg, raw)
}

func buildValidator(cfg ValidatorConfig, additionalConfigRaw []byte) (*Validator, error) {
	// 1. Parse the create-request schema and substitute enum placeholders.
	var createDoc any
	if err := json.Unmarshal(embeddedCreateRequestSchemaBytes, &createDoc); err != nil {
		return nil, fmt.Errorf("parse create_request schema: %w", err)
	}
	substituteEnumPlaceholders(createDoc, map[string][]string{
		placeholderUserClaims:        cfg.SupportedUserClaims,
		placeholderACRValues:         cfg.SupportedACRValues,
		placeholderGrantTypes:        cfg.SupportedGrantTypes,
		placeholderClientAuthMethods: cfg.SupportedClientAuthMethods,
	})

	// 2. Parse the additionalConfig schema (it carries no placeholders).
	var addCfgDoc any
	if err := json.Unmarshal(additionalConfigRaw, &addCfgDoc); err != nil {
		return nil, fmt.Errorf("parse additionalConfig schema: %w", err)
	}

	// 3. Compile both schemas with custom formats registered.
	compiler := jsonschema.NewCompiler()
	// Draft 2020-12 makes `format` annotation-only by default; we want it
	// to be assertion (i.e. validation actually fails on a bad format).
	compiler.AssertFormat()
	if err := registerCustomFormats(compiler, cfg); err != nil {
		return nil, fmt.Errorf("register custom formats: %w", err)
	}

	if err := compiler.AddResource(additionalConfigSchemaID, addCfgDoc); err != nil {
		return nil, fmt.Errorf("register additionalConfig schema: %w", err)
	}
	if err := compiler.AddResource(createRequestSchemaID, createDoc); err != nil {
		return nil, fmt.Errorf("register create_request schema: %w", err)
	}

	addCfgSchema, err := compiler.Compile(additionalConfigSchemaID)
	if err != nil {
		return nil, fmt.Errorf("compile additionalConfig schema: %w", err)
	}
	createSchema, err := compiler.Compile(createRequestSchemaID)
	if err != nil {
		return nil, fmt.Errorf("compile create_request schema: %w", err)
	}

	return &Validator{
		create:                 createSchema,
		additionalConfigSchema: addCfgSchema,
	}, nil
}

// ValidateCreate validates a raw decoded create-request body. Returns nil
// if valid; otherwise a non-empty list of wire error codes.
func (v *Validator) ValidateCreate(raw any) []string {
	if err := v.create.Validate(raw); err != nil {
		return mapSchemaError(err)
	}
	return nil
}

// substituteEnumPlaceholders walks the schema in place and replaces
// "enum": ["__PLACEHOLDER_*__"] sentinels with the matching list from
// replacements. Unknown sentinels are left intact and surface at Compile.
func substituteEnumPlaceholders(node any, replacements map[string][]string) {
	switch n := node.(type) {
	case map[string]any:
		if rawEnum, ok := n["enum"]; ok {
			if arr, ok := rawEnum.([]any); ok && len(arr) == 1 {
				if s, ok := arr[0].(string); ok {
					if vals, ok := replacements[s]; ok {
						newArr := make([]any, len(vals))
						for i, v := range vals {
							newArr[i] = v
						}
						n["enum"] = newArr
					}
				}
			}
		}
		for _, v := range n {
			substituteEnumPlaceholders(v, replacements)
		}
	case []any:
		for _, item := range n {
			substituteEnumPlaceholders(item, replacements)
		}
	}
}

// registerCustomFormats binds the four schema-referenced format names
// (id-format, iso-639-2-t, redirect-url, not-blank) to Go validator
// functions. Returns an error when cfg.SupportedIDRegex doesn't compile;
// an empty pattern falls back to DefaultIDRegex.
func registerCustomFormats(c *jsonschema.Compiler, cfg ValidatorConfig) error {
	idPattern := cfg.SupportedIDRegex
	if idPattern == "" {
		idPattern = DefaultIDRegex
	}
	idRegex, err := regexp.Compile(idPattern)
	if err != nil {
		return fmt.Errorf("invalid MOSIP_ESIGNET_SUPPORTED_ID_REGEX %q: %w", idPattern, err)
	}

	c.RegisterFormat(&jsonschema.Format{
		Name: "id-format",
		Validate: func(v any) error {
			s, ok := v.(string)
			if !ok {
				return nil // type errors are reported by the "type" keyword
			}
			if strings.TrimSpace(s) == "" {
				return errors.New("blank")
			}
			if !idRegex.MatchString(s) {
				return errors.New("does not match id regex")
			}
			return nil
		},
	})

	c.RegisterFormat(&jsonschema.Format{
		Name: "iso-639-2-t",
		Validate: func(v any) error {
			s, ok := v.(string)
			if !ok {
				return nil
			}
			if len(s) != 3 {
				return errors.New("not a 3-letter code")
			}
			base, err := language.ParseBase(s)
			if err != nil {
				return fmt.Errorf("not a valid language code: %w", err)
			}
			if base.ISO3() != strings.ToLower(s) {
				return errors.New("not an ISO 639-2/T code")
			}
			return nil
		},
	})

	c.RegisterFormat(&jsonschema.Format{
		Name: "redirect-url",
		Validate: func(v any) error {
			raw, ok := v.(string)
			if !ok {
				return nil
			}
			if raw == "" {
				return errors.New("empty")
			}
			u, err := url.Parse(raw)
			if err != nil {
				return fmt.Errorf("parse: %w", err)
			}
			if u.Scheme == "" {
				return errors.New("missing scheme")
			}
			if u.Fragment != "" {
				return errors.New("fragment not allowed")
			}
			switch u.Scheme {
			case "http", "https":
				if u.Host == "" {
					return errors.New("http(s) requires authority")
				}
			default:
				// Custom schemes (mobile deep-links) may omit authority.
				// Only reject when the body is fully empty.
				if u.Host == "" && u.Opaque == "" && u.Path == "" {
					return errors.New("empty body")
				}
			}
			return nil
		},
	})

	c.RegisterFormat(&jsonschema.Format{
		Name: "not-blank",
		Validate: func(v any) error {
			s, ok := v.(string)
			if !ok {
				return nil
			}
			if strings.TrimSpace(s) == "" {
				return errors.New("blank or whitespace-only")
			}
			return nil
		},
	})

	return nil
}

// mapSchemaError returns ONE wire error code from a validation-error tree,
// in priority order. Shape violations beat content errors so the client
// sees one user-visible issue at a time.
//  1. AdditionalProperties (unknown key)                    → invalid_input
//     or invalid_additional_config inside additionalConfig
//  2. PropertyNames        (langmap key fails iso-639-2-t)  → invalid_language_code
//  3. Required             (missing required field)         → field-specific code
//  4. Anything else        (format / type / enum / length)  → instance-path lookup
func mapSchemaError(err error) []string {
	var verr *jsonschema.ValidationError
	if !errors.As(err, &verr) || verr == nil {
		return []string{errInvalidInput}
	}
	if code := firstAdditionalPropertiesCode(verr); code != "" {
		return []string{code}
	}
	if hasErrorKind[*kind.PropertyNames](verr) {
		return []string{errInvalidLanguageCode}
	}
	if code := firstRequiredCode(verr); code != "" {
		return []string{code}
	}
	if code := walkInstanceLocations(verr); code != "" {
		return []string{code}
	}
	return []string{errInvalidInput}
}

// hasErrorKind reports whether any node in the tree has an ErrorKind of
// the given concrete type T.
func hasErrorKind[T jsonschema.ErrorKind](verr *jsonschema.ValidationError) bool {
	if verr == nil {
		return false
	}
	if _, ok := verr.ErrorKind.(T); ok {
		return true
	}
	for _, cause := range verr.Causes {
		if hasErrorKind[T](cause) {
			return true
		}
	}
	return false
}

// firstAdditionalPropertiesCode locates an AdditionalProperties violation
// anywhere in the tree and returns the appropriate wire code. Violations
// inside /request/additionalConfig map to invalid_additional_config; all
// others (envelope-level, request-level) map to invalid_input.
func firstAdditionalPropertiesCode(verr *jsonschema.ValidationError) string {
	if verr == nil {
		return ""
	}
	if _, ok := verr.ErrorKind.(*kind.AdditionalProperties); ok {
		instance := "/" + strings.Join(verr.InstanceLocation, "/")
		if strings.HasPrefix(instance, "/request/additionalConfig") {
			return errInvalidAdditionalConfig
		}
		return errInvalidInput
	}
	for _, cause := range verr.Causes {
		if code := firstAdditionalPropertiesCode(cause); code != "" {
			return code
		}
	}
	return ""
}

// firstRequiredCode returns the wire code for the first missing required
// field found anywhere in the tree, or "" if no Required violation exists.
func firstRequiredCode(verr *jsonschema.ValidationError) string {
	if verr == nil {
		return ""
	}
	if req, ok := verr.ErrorKind.(*kind.Required); ok && len(req.Missing) > 0 {
		if code := wireCodeForField(req.Missing[0]); code != "" {
			return code
		}
	}
	for _, cause := range verr.Causes {
		if code := firstRequiredCode(cause); code != "" {
			return code
		}
	}
	return ""
}

// walkInstanceLocations descends into Causes first, then maps the deepest
// leaf's location to a wire code. Returns "" when nothing matches.
func walkInstanceLocations(verr *jsonschema.ValidationError) string {
	for _, cause := range verr.Causes {
		if code := walkInstanceLocations(cause); code != "" {
			return code
		}
	}
	instance := "/" + strings.Join(verr.InstanceLocation, "/")
	if len(verr.InstanceLocation) == 0 {
		instance = ""
	}
	var keyword string
	if verr.ErrorKind != nil {
		keyword = strings.Join(verr.ErrorKind.KeywordPath(), "/")
	}
	return mapInstanceLocation(instance, keyword)
}

// wireCodeForField maps a request-field name (as it appears in the JSON
// document) to its wire error code.
func wireCodeForField(field string) string {
	switch field {
	case "clientId":
		return errInvalidClientID
	case "clientName":
		return errInvalidClientName
	case "clientNameLangMap":
		return errInvalidLanguageCode
	case "relyingPartyId":
		return errInvalidRPID
	case "logoUri":
		return errInvalidURI
	case "redirectUris":
		return errInvalidRedirectURI
	case "authContextRefs":
		return errInvalidACR
	case "publicKey":
		return errInvalidPublicKey
	case "userClaims":
		return errInvalidClaim
	case "grantTypes":
		return errUnsupportedGrantType
	case "clientAuthMethods":
		return errInvalidClientAuth
	}
	return ""
}

// mapInstanceLocation translates a JSON-pointer instance path + keyword path
// to a wire error code. Order matters: more-specific paths first.
func mapInstanceLocation(instance, keyword string) string {
	// clientNameLangMap has three distinct codes:
	//   - key fails propertyNames           → invalid_language_code
	//   - value exceeds maxLength           → invalid_client_name_length
	//   - value otherwise (blank, type, …)  → invalid_client_name_value
	if strings.HasPrefix(instance, "/request/clientNameLangMap") {
		if strings.Contains(keyword, "propertyNames") {
			return errInvalidLanguageCode
		}
		if instance == "/request/clientNameLangMap" {
			// Map-level violations (required, minProperties) collapse to
			// invalid_language_code.
			return errInvalidLanguageCode
		}
		if strings.Contains(keyword, "maxLength") {
			return errInvalidClientNameLength
		}
		return errInvalidClientNameValue
	}

	// Strip array indices: "/request/redirectUris/0" → "/request/redirectUris".
	parent := stripArrayIndex(instance)

	switch parent {
	case "/request/clientId":
		return errInvalidClientID
	case "/request/clientName":
		return errInvalidClientName
	case "/request/relyingPartyId":
		return errInvalidRPID
	case "/request/logoUri":
		return errInvalidURI
	case "/request/redirectUris":
		return errInvalidRedirectURI
	case "/request/authContextRefs":
		return errInvalidACR
	case "/request/userClaims":
		return errInvalidClaim
	case "/request/grantTypes":
		return errUnsupportedGrantType
	case "/request/clientAuthMethods":
		return errInvalidClientAuth
	case "/request/publicKey":
		return errInvalidPublicKey
	}

	// additionalConfig violations are anywhere under /request/additionalConfig.
	if strings.HasPrefix(instance, "/request/additionalConfig") {
		return errInvalidAdditionalConfig
	}

	// A required-field violation reports the parent path; check whether the
	// missing field name matches a top-level field of the request.
	if instance == "/request" || instance == "" || instance == "/" {
		return errInvalidInput
	}
	return ""
}

// stripArrayIndex removes the trailing numeric index from a JSON pointer:
// "/request/redirectUris/0" → "/request/redirectUris". Returns the input
// unchanged when no trailing numeric segment is present.
func stripArrayIndex(p string) string {
	idx := strings.LastIndex(p, "/")
	if idx < 0 {
		return p
	}
	tail := p[idx+1:]
	if tail == "" {
		return p
	}
	for _, r := range tail {
		if r < '0' || r > '9' {
			return p
		}
	}
	return p[:idx]
}

// fetchSchemaBytes pulls an override schema document. http/https URLs are
// timeout-bounded and size-capped; file:// URLs are unbounded.
func fetchSchemaBytes(ctx context.Context, schemaURL string) ([]byte, error) {
	u, err := url.Parse(schemaURL)
	if err != nil {
		return nil, fmt.Errorf("invalid url: %w", err)
	}
	switch u.Scheme {
	case "file":
		path := u.Path
		// Windows file:// URLs carry a leading slash before the drive letter.
		if strings.HasPrefix(path, "/") && len(path) > 2 && path[2] == ':' {
			path = path[1:]
		}
		return os.ReadFile(path)
	case "http", "https":
		c, cancel := context.WithTimeout(ctx, schemaFetchTimeout)
		defer cancel()
		req, err := http.NewRequestWithContext(c, http.MethodGet, schemaURL, nil)
		if err != nil {
			return nil, err
		}
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			return nil, err
		}
		defer func() { _ = resp.Body.Close() }()
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			return nil, fmt.Errorf("unexpected status %d", resp.StatusCode)
		}
		body, err := io.ReadAll(io.LimitReader(resp.Body, maxSchemaBytes+1))
		if err != nil {
			return nil, err
		}
		if int64(len(body)) > maxSchemaBytes {
			return nil, fmt.Errorf("schema body exceeds %d bytes", maxSchemaBytes)
		}
		return body, nil
	default:
		return nil, fmt.Errorf("unsupported scheme %q (want http, https, or file)", u.Scheme)
	}
}
