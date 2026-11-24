/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package ldp verifies W3C JSON-LD Verifiable Credentials and Presentations
// secured with Linked Data proofs (the OpenID4VP ldp_vc
// format), with the checks a server-side verifier needs: mandatory
// challenge/domain binding, required holder binding, issuer trust and
// issuer/key control, expired credentials as failures, pinned JSON-LD
// contexts and safe mode.
//
// The package knows nothing about OpenID4VP: the caller issues the nonce,
// picks the expected domain and matches the result against its request.
// See docs/openid4vp-ldp-vc-verification-go.md.
package ldp

import (
	"errors"
	"fmt"
	"net/http"
	"slices"
	"time"

	"github.com/mosip/esignet/internal/vc/ldp/didkeys"
	"github.com/mosip/esignet/internal/vc/ldp/fetch"
	"github.com/mosip/esignet/internal/vc/ldp/jsonld"
	"github.com/mosip/esignet/internal/vc/ldp/proof"
	"github.com/mosip/esignet/internal/vc/ldp/status"
)

// Defaults applied to zero-valued Config fields.
const (
	DefaultDIDDocCacheTTL        = 5 * time.Minute
	DefaultStatusListCacheTTL    = 5 * time.Minute
	DefaultContextCacheTTL       = 24 * time.Hour
	DefaultFetchFailureTTL       = 30 * time.Second
	DefaultCacheSize             = 256
	DefaultMaxPresentationBytes  = 1 << 20
	DefaultMaxCredentials        = 16
	DefaultMaxJSONDepth          = 64
	DefaultMaxFetchBytes         = 2 << 20
	DefaultMaxBlankNodes         = 1024
	DefaultFetchTimeout          = 10 * time.Second
	DefaultClockSkew             = 3 * time.Second
	DefaultCredentialConcurrency = 4
)

// Config is fixed for the lifetime of a Verifier. Zero values take the
// defaults above.
type Config struct {
	// IssuerTrusted decides whether a credential issuer is trusted. Required:
	// use TrustIssuers for a fixed list, or AnyIssuer to accept every issuer
	// that controls its signing key.
	IssuerTrusted func(issuer string) bool
	// StatusPurposes lists the credentialStatus purposes to check, e.g.
	// ["revocation"]. Empty skips status checks.
	StatusPurposes []string
	// VPSuites and VCSuites restrict the accepted proof suites (proof
	// types). Empty means proof.DefaultVPSuites / proof.DefaultVCSuites.
	VPSuites, VCSuites []string
	// ClockSkew is tolerated on every date comparison (default 3s).
	ClockSkew time.Duration
	// MaxVPAge bounds the age of a presentation proof's created. Zero means
	// created is not checked.
	MaxVPAge time.Duration
	// Now is the clock (default time.Now).
	Now func() time.Time

	// ContextsDir holds extra pinned JSON-LD contexts: an index.json mapping
	// context URLs to file names, plus those files. The embedded set (W3C
	// VCDM 1.1/2.0, Ed25519Signature2020, JWS 2020, ODRL, MOSIP identity) is
	// always loaded.
	ContextsDir string
	// RemoteContextAllowlist lists URL prefixes of contexts that may be
	// fetched at verification time. Empty means no remote contexts.
	RemoteContextAllowlist []string
	// HTTPClient is used for did:web, HTTPS key and controller documents,
	// status lists and allow-listed contexts. Nil gets a client with
	// FetchTimeout, a redirect cap and an SSRF guard, and no proxy.
	HTTPClient *http.Client
	// AllowInsecureHTTP permits http:// fetches (local development only).
	AllowInsecureHTTP bool
	// AllowPrivateNetworks lets the default client reach loopback and private
	// addresses. Ignored when HTTPClient is set.
	AllowPrivateNetworks bool
	FetchTimeout         time.Duration
	// FetchFailureTTL is how long a failed fetch is remembered, so a dead or
	// slow host is not retried on every verification.
	FetchFailureTTL time.Duration

	DIDDocCacheTTL     time.Duration
	StatusListCacheTTL time.Duration
	ContextCacheTTL    time.Duration
	CacheSize          int // entries per cache

	MaxPresentationBytes int
	MaxCredentials       int
	MaxJSONDepth         int
	MaxFetchBytes        int64
	MaxBlankNodes        int
	// CredentialConcurrency is how many credentials of one presentation are
	// verified in parallel.
	CredentialConcurrency int
}

// TrustIssuers returns an IssuerTrusted function accepting exactly ids.
func TrustIssuers(ids ...string) func(issuer string) bool {
	ids = slices.Clone(ids)
	return func(issuer string) bool { return slices.Contains(ids, issuer) }
}

// AnyIssuer is an IssuerTrusted function accepting every issuer. Issuers
// must still control the keys that sign their credentials.
func AnyIssuer(string) bool { return true }

// FormatLDPVC is the OpenID4VP credential format of the presentations
// VerifyPresentation accepts.
const FormatLDPVC = "ldp_vc"

// PresentationResult is returned only when every check passed.
type PresentationResult struct {
	Format string // FormatLDPVC
	// Claims merges the credentialSubject claims of every credential in
	// document order; a later value overrides an earlier one.
	Claims map[string]any
	// HolderBindingThumbprint is the RFC 7638 SHA-256 JWK thumbprint of the
	// key that signed the presentation.
	HolderBindingThumbprint string
	Created                 *time.Time
}

// credentialResult describes one verified credential.
type credentialResult struct {
	Issuer  string
	Types   []string
	Subject any                      // credentialSubject (object or array), safe-mode checked
	Status  map[string]status.Result // purpose -> result
}

// Verifier verifies ldp_vc presentations and credentials. It is safe for
// concurrent use.
type Verifier struct {
	cfg    Config
	loader *jsonld.Loader
	keys   didkeys.Resolver
	status *status.Checker
}

// New validates cfg and builds a Verifier.
func New(cfg Config) (*Verifier, error) {
	cfg = withConfigDefaults(cfg)
	if cfg.IssuerTrusted == nil {
		return nil, errors.New("ldp: Config.IssuerTrusted is required (use TrustIssuers or AnyIssuer)")
	}
	for _, s := range slices.Concat(cfg.VPSuites, cfg.VCSuites) {
		if !proof.Supported(s) {
			return nil, fmt.Errorf("ldp: unknown proof suite %q", s)
		}
	}
	client := cfg.HTTPClient
	if client == nil {
		client = fetch.DefaultClient(cfg.FetchTimeout, cfg.AllowPrivateNetworks, cfg.AllowInsecureHTTP)
	}
	fetcher := fetch.New(fetch.Options{
		Client:     client,
		Timeout:    cfg.FetchTimeout,
		MaxBytes:   cfg.MaxFetchBytes,
		AllowHTTP:  cfg.AllowInsecureHTTP,
		FailureTTL: cfg.FetchFailureTTL,
		CacheSize:  cfg.CacheSize,
	})
	loader, err := jsonld.NewLoader(jsonld.Options{
		Dir:             cfg.ContextsDir,
		RemoteAllowlist: cfg.RemoteContextAllowlist,
		Fetcher:         fetcher,
		CacheTTL:        cfg.ContextCacheTTL,
		CacheSize:       cfg.CacheSize,
		MaxBlankNodes:   cfg.MaxBlankNodes,
	})
	if err != nil {
		return nil, fmt.Errorf("ldp: load JSON-LD contexts: %w", err)
	}
	return &Verifier{
		cfg:    cfg,
		loader: loader,
		keys:   didkeys.NewResolver(fetcher, cfg.DIDDocCacheTTL, cfg.CacheSize),
		status: status.NewChecker(fetcher, cfg.StatusListCacheTTL, cfg.CacheSize),
	}, nil
}

func withConfigDefaults(c Config) Config {
	if len(c.VPSuites) == 0 {
		c.VPSuites = proof.DefaultVPSuites()
	}
	if len(c.VCSuites) == 0 {
		c.VCSuites = proof.DefaultVCSuites()
	}
	if c.Now == nil {
		c.Now = time.Now
	}
	setDefault(&c.ClockSkew, DefaultClockSkew)
	setDefault(&c.FetchTimeout, DefaultFetchTimeout)
	setDefault(&c.FetchFailureTTL, DefaultFetchFailureTTL)
	setDefault(&c.DIDDocCacheTTL, DefaultDIDDocCacheTTL)
	setDefault(&c.StatusListCacheTTL, DefaultStatusListCacheTTL)
	setDefault(&c.ContextCacheTTL, DefaultContextCacheTTL)
	setDefault(&c.CacheSize, DefaultCacheSize)
	setDefault(&c.MaxPresentationBytes, DefaultMaxPresentationBytes)
	setDefault(&c.MaxCredentials, DefaultMaxCredentials)
	setDefault(&c.MaxJSONDepth, DefaultMaxJSONDepth)
	setDefault(&c.MaxFetchBytes, DefaultMaxFetchBytes)
	setDefault(&c.MaxBlankNodes, DefaultMaxBlankNodes)
	setDefault(&c.CredentialConcurrency, DefaultCredentialConcurrency)
	return c
}

func setDefault[T int | int64 | time.Duration](field *T, def T) {
	if *field <= 0 {
		*field = def
	}
}

// withIndex attributes a credential failure to its position in the VP.
func withIndex(err error, index int) error {
	var ve *VerificationError
	if errors.As(err, &ve) {
		c := *ve
		c.Index = index
		return &c
	}
	return err
}
