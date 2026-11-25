/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ldp

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/url"
	"slices"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/vc/ldp/jsonval"
	"github.com/mosip/esignet/internal/vc/ldp/proof"
)

const (
	contextV1              = "https://www.w3.org/2018/credentials/v1"
	contextV2              = "https://www.w3.org/ns/credentials/v2"
	typeCredential         = "VerifiableCredential"
	typePresentation       = "VerifiablePresentation"
	typeEnvelopedVC        = "EnvelopedVerifiableCredential"
	fieldContext           = "@context"
	fieldCredentialSubject = "credentialSubject"
	fieldProof             = "proof"
	fieldIssuanceDate      = "issuanceDate"
	fieldExpirationDate    = "expirationDate"
	fieldValidFrom         = "validFrom"
	fieldValidUntil        = "validUntil"
	fieldCredentialSchema  = "credentialSchema"
)

// commonMandatory are required by both data models.
var commonMandatory = []string{fieldContext, "type", fieldCredentialSubject, "issuer", fieldProof}

// typedFields must each carry a non-blank type when present; some also an id.
var typedFields = []string{fieldProof, "credentialStatus", "evidence", fieldCredentialSchema, "refreshService", "termsOfUse"}

var (
	v1IDMandatory = []string{"credentialStatus", "refreshService", fieldCredentialSchema}
	v2IDMandatory = []string{fieldCredentialSchema}
)

// validity is a credential's validity window (VCDM 1.1 issuanceDate /
// expirationDate, or VCDM 2.0 validFrom / validUntil).
type validity struct {
	from, until *time.Time
}

// credential is a structurally valid credential, inside its validity
// window. raw is the document as received, for canonicalization.
type credential struct {
	raw      map[string]any
	issuer   string
	types    []string
	subjects []map[string]any // at least one
	proof    map[string]any
	validity
}

// dataModel is the VCDM version a credential's first context selects.
type dataModel struct {
	mandatory          []string
	fromField          string
	untilField         string
	idMandatory        []string
	languageValues     bool
	notYetValidMessage string
}

var (
	vcdm1 = dataModel{
		mandatory:          append(slices.Clone(commonMandatory), fieldIssuanceDate),
		fromField:          fieldIssuanceDate,
		untilField:         fieldExpirationDate,
		idMandatory:        v1IDMandatory,
		notYetValidMessage: "the current time is before issuanceDate",
	}
	vcdm2 = dataModel{
		mandatory:          commonMandatory,
		fromField:          fieldValidFrom,
		untilField:         fieldValidUntil,
		idMandatory:        v2IDMandatory,
		languageValues:     true,
		notYetValidMessage: "the current time is before validFrom",
	}
)

// parseCredential checks the VCDM 1.1 or 2.0 structure and validity window
// of vc. An expired credential is a failure.
func parseCredential(vc map[string]any, now time.Time, skew time.Duration) (*credential, error) {
	ctx, ok := vc[fieldContext]
	if !ok {
		return nil, missingField(fieldContext)
	}
	var m dataModel
	switch firstContext(ctx) {
	case contextV1:
		m = vcdm1
	case contextV2:
		m = vcdm2
	default:
		return nil, invalidField(fieldContext, contextV1+" or "+contextV2+" must be the first context")
	}
	if err := checkMandatory(vc, m.mandatory); err != nil {
		return nil, err
	}
	w, err := dateWindow(vc, m.fromField, m.untilField)
	if err != nil {
		return nil, err
	}
	if w.from != nil && isFuture(*w.from, now, skew) {
		return nil, fieldError(ErrNotYetValid, m.fromField, m.notYetValidMessage)
	}
	if err := checkTypedFields(vc, m.idMandatory); err != nil {
		return nil, err
	}
	if m.languageValues {
		if err := checkLanguageValues(vc); err != nil {
			return nil, err
		}
	}
	c, err := checkCommon(vc)
	if err != nil {
		return nil, err
	}
	if w.until != nil && isExpired(*w.until, now, skew) {
		return nil, newError(ErrExpired, m.untilField+" has passed")
	}
	c.validity = w
	return c, nil
}

func firstContext(ctx any) string {
	l := jsonval.List(ctx)
	if len(l) == 0 {
		return ""
	}
	return jsonval.String(l[0])
}

func checkMandatory(vc map[string]any, fields []string) error {
	for _, f := range fields {
		if vc[f] == nil {
			return missingField(f)
		}
	}
	return nil
}

// dateWindow parses the from/until fields, failing on unparseable values.
func dateWindow(vc map[string]any, fromField, untilField string) (validity, error) {
	var w validity
	for _, f := range []struct {
		name string
		dst  **time.Time
	}{{fromField, &w.from}, {untilField, &w.until}} {
		v, present := vc[f.name]
		if !present {
			continue
		}
		t, ok := jsonval.Time(v)
		if !ok {
			return validity{}, invalidField(f.name, "not a valid date-time")
		}
		*f.dst = &t
	}
	return w, nil
}

// checkCommon runs the checks shared by both models and returns the
// credential's typed view.
func checkCommon(vc map[string]any) (*credential, error) {
	subjects, err := checkCredentialSubject(vc[fieldCredentialSubject])
	if err != nil {
		return nil, err
	}
	pr, err := checkProofShape(vc[fieldProof])
	if err != nil {
		return nil, err
	}
	if id, present := vc["id"]; present && !isValidURI(jsonval.String(id)) {
		return nil, invalidField("id", "not a valid URI")
	}
	types := jsonval.Strings(vc["type"])
	if !slices.Contains(types, typeCredential) {
		return nil, invalidField("type", "must include "+typeCredential)
	}
	issuer := jsonval.ID(vc["issuer"])
	if !isValidURI(issuer) {
		return nil, invalidField("issuer", "not a valid URI")
	}
	return &credential{raw: vc, issuer: issuer, types: types, subjects: subjects, proof: pr}, nil
}

func checkCredentialSubject(v any) ([]map[string]any, error) {
	subjects, ok := jsonval.Objects(v)
	if !ok || len(subjects) == 0 {
		return nil, invalidField(fieldCredentialSubject, "must be an object or a non-empty array of objects")
	}
	for _, s := range subjects {
		if id, present := s["id"]; present && !isValidURI(jsonval.String(id)) {
			return nil, invalidField(fieldCredentialSubject+".id", "not a valid URI")
		}
	}
	return subjects, nil
}

// checkProofShape requires a single proof object of a known VC suite.
// Suite policy (allowed suites, JWS algorithm) is applied by proof.Parse.
func checkProofShape(v any) (map[string]any, error) {
	p, ok := v.(map[string]any)
	if !ok {
		return nil, invalidField(fieldProof, "must be a single object; multiple proofs are not supported")
	}
	if id, err := proof.SuiteID(p); err == nil && proof.Supported(id) {
		return p, nil
	}
	return nil, fieldError(ErrUnsupportedProof, fieldProof+".type", "")
}

// checkTypedFields requires a type on each typedFields object, and a valid
// id where present or mandatory.
func checkTypedFields(vc map[string]any, idMandatory []string) error {
	for _, field := range typedFields {
		v, present := vc[field]
		if !present {
			continue
		}
		objects, ok := jsonval.Objects(v)
		if !ok {
			return invalidField(field, "must be an object or an array of objects")
		}
		for _, o := range objects {
			if err := checkTypedObject(field, o, slices.Contains(idMandatory, field)); err != nil {
				return err
			}
		}
	}
	return nil
}

func checkTypedObject(field string, o map[string]any, idMandatory bool) error {
	t, present := o["type"]
	if !present {
		return missingField(field + ".type")
	}
	types := jsonval.Strings(t)
	if len(types) == 0 || slices.ContainsFunc(types, isBlank) {
		return invalidField(field+".type", "must not be empty")
	}
	id, hasID := o["id"]
	if idMandatory && !hasID {
		return missingField(field + ".id")
	}
	if !hasID {
		return nil
	}
	s := jsonval.String(id)
	if idMandatory && isBlank(s) {
		return invalidField(field+".id", "must not be empty")
	}
	if s != "" && !isValidURI(s) {
		return invalidField(field+".id", "not a valid URI")
	}
	return nil
}

// checkLanguageValues requires VCDM 2.0 name and description to be a string
// or an array of language value objects, keyed "language" or "@language".
func checkLanguageValues(vc map[string]any) error {
	for _, field := range []string{"name", "description"} {
		v, present := vc[field]
		if !present {
			continue
		}
		if _, ok := v.(string); ok {
			continue
		}
		bad := invalidField(field, "must be a string, a language value object or an array of them")
		var values []any
		switch t := v.(type) {
		case []any:
			values = t
		case map[string]any:
			values = []any{t}
		default:
			return bad
		}
		for _, e := range values {
			o, ok := e.(map[string]any)
			if !ok {
				return bad
			}
			if _, hasLang := o["language"]; !hasLang {
				if _, hasLang = o["@language"]; !hasLang {
					return bad
				}
			}
		}
	}
	return nil
}

func isBlank(s string) bool { return strings.TrimSpace(s) == "" }

// isFuture reports whether t is after now + skew.
func isFuture(t, now time.Time, skew time.Duration) bool {
	return t.After(now.Add(skew))
}

// isExpired reports whether expiry is not after now - skew.
func isExpired(expiry, now time.Time, skew time.Duration) bool {
	return !expiry.After(now.Add(-skew))
}

var errNotObject = errors.New("not a JSON object")

// decodeObject decodes a JSON object, keeping numbers as json.Number, and
// rejects trailing data and nesting deeper than maxDepth.
func decodeObject(raw []byte, maxDepth int) (map[string]any, error) {
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	var v any
	if err := dec.Decode(&v); err != nil {
		return nil, err
	}
	if dec.More() {
		return nil, errors.New("trailing data after JSON value")
	}
	obj, ok := v.(map[string]any)
	if !ok {
		return nil, errNotObject
	}
	if d := depth(obj); maxDepth > 0 && d > maxDepth {
		return nil, fmt.Errorf("JSON nesting depth %d exceeds %d", d, maxDepth)
	}
	return obj, nil
}

func depth(v any) int {
	deepest := 0
	switch t := v.(type) {
	case map[string]any:
		for _, e := range t {
			deepest = max(deepest, depth(e))
		}
	case []any:
		for _, e := range t {
			deepest = max(deepest, depth(e))
		}
	default:
		return 0
	}
	return deepest + 1
}

// isValidURI accepts an absolute URI with a scheme; http(s) URIs also need a
// host. Characters RFC 3986 does not allow unescaped are rejected.
func isValidURI(s string) bool {
	if s == "" || strings.ContainsAny(s, " \t\r\n\"<>\\^`{|}") {
		return false
	}
	u, err := url.Parse(s)
	if err != nil || u.Scheme == "" {
		return false
	}
	if (u.Scheme == "http" || u.Scheme == "https") && u.Host == "" {
		return false
	}
	return true
}
