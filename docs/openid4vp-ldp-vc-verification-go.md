# `ldp_vc` (JSON-LD) Presentation Verification in Go

Design notes for a Go package (`ldp`) that verifies W3C JSON-LD Verifiable Presentations and Credentials secured with Linked Data proofs. This is the format OpenID4VP calls `ldp_vc`.

**Status:** implemented in `esignet-service/internal/vc/ldp`.

This document covers only the verification package. The OpenID4VP layer that calls it is out of scope (see [§1.2](#12-out-of-scope-caller-responsibilities)).

The document has these parts:

1. Scope and the boundary with the caller.
2. The verification rules: structure, signing input, proof suites.
3. Known gaps, in the imported libraries and in the current code.
4. The Go design: inputs, outputs, package layout, libraries, algorithms.
5. Testing, security checklist, status and open questions.

The package runs server-side and its result can authenticate a user, so the bar is higher than for a wallet-side or offline verifier.

---

## 1. Scope

### 1.1 In scope

- Parsing a JSON-LD Verifiable Presentation (VP) supplied by the caller as raw JSON.
- Verifying the **VP proof** (holder signature), including its binding to a caller-supplied `challenge` and `domain`.
- **Holder binding**: the VP signer is the holder, and every VC's `credentialSubject.id` is the holder.
- For each embedded **VC**:
  - structural validation (VCDM 1.1 and 2.0)
  - issuer trust
  - issuer proof verification, including issuer control of the signing key
  - validity window checks
  - optional BitstringStatusList revocation/suspension check
- The proof suites in [§2.3](#23-proof-suites-and-signature-algorithms).
- Key resolution for `did:key`, `did:jwk`, `did:web`, and HTTPS key documents, with the key's controller document checked for the proof purpose.
- JSON-LD context loading and canonicalization (URDNA2015).

### 1.2 Out of scope (caller responsibilities)

The `ldp` package doesn't know about OpenID4VP. The caller, an OpenID4VP layer or any other consumer, handles:

- Decrypting and parsing the response (`direct_post.jwt`, the `vp_token` object, `state`).
- Issuing, storing and expiring the `nonce`, and making sure it is used only once.
- Choosing the `client_id` to pass as the expected domain.
- Matching the result against the request (DCQL credential ids, `format`, `meta.type_values`, `claims`, `claim_sets`, `credential_sets`, `multiple`, `trusted_authorities`).
- Mapping verified claims to a user identity or OIDC claims.
- Other credential formats (`dc+sd-jwt`, `mso_mdoc`, `jwt_vc_json`, `cwt_vc`).

The result ([§4.2](#42-outputs)) holds the claims of every VC merged into one map. It has no per-credential types, issuers or claim boundaries, so it is not enough for request matching on its own. The caller keeps the presentation it passed in and parses it for the matching, keeping each credential's claims apart, and uses the result only once verification has passed.

---

## 2. Verification rules

### 2.1 VC structural validation (`validate.go`)

The data model is picked from the first `@context` entry:

| `@context[0]` | Model | Mandatory fields | Validity window |
|---|---|---|---|
| `https://www.w3.org/2018/credentials/v1` | VCDM 1.1 | `@context, type, credentialSubject, issuer, proof, issuanceDate` | `issuanceDate`, `expirationDate` |
| `https://www.w3.org/ns/credentials/v2` | VCDM 2.0 | `@context, type, credentialSubject, issuer, proof` | `validFrom`, `validUntil` |
| anything else | — | rejected: `ErrInvalidField` on `@context` | — |

Checks common to both models:

- `credentialSubject` is an object or a non-empty array of objects. Any `id` in it is a valid URI.
- `proof` is a single object of a known suite (its `type`). The allowed suites and JWS algorithms are applied later, by `proof.Parse`.
- `id`, if present, is a valid URI.
- `type` contains `VerifiableCredential`.
- `issuer` (a string, or an object with `id`) is a valid URI.
- Each of `proof, credentialStatus, evidence, credentialSchema, refreshService, termsOfUse` that is present is an object or an array of objects, each with a non-blank `type` and, where present, a valid `id`.
  - VCDM 1.1 also requires `id` on `credentialStatus`, `refreshService` and `credentialSchema`.
  - VCDM 2.0 also requires `id` on `credentialSchema`.
- VCDM 2.0 only: `name` and `description` are either strings or arrays of language value objects, keyed `language` or `@language`.

"Valid URI" means it parses with a scheme, has no characters RFC 3986 disallows unescaped, and, for `http` and `https`, has a host.

Dates are parsed as ISO-8601 with an offset, falling back to local date-time treated as UTC. An unparseable date is `ErrInvalidField`. `ClockSkew` widens the window on both sides:

- a start date (`issuanceDate`/`validFrom`) later than now + skew is `ErrNotYetValid`;
- an end date (`expirationDate`/`validUntil`) not later than now − skew is `ErrExpired`. There is no "verified but expired" result.

### 2.2 Canonicalization and the "hash data"

Every supported suite signs the same construction. Only the encoding of the signature differs:

```
proofConfig  = proof minus {proofValue, jws, signatureValue}, with "@context" = <proof context>
unsecuredDoc = document minus "proof"

hashData = SHA-256( URDNA2015(proofConfig) ) || SHA-256( URDNA2015(unsecuredDoc) )   // 64 bytes
```

The `<proof context>` differs between implementations:

- jsonld-signatures (JavaScript) uses the document's `@context`.
- ld-signatures-java, which MOSIP and Inji issuers sign with, uses the suite's own context:
  - `https://w3id.org/security/v2` for the 2018/2019 suites (also `https://w3id.org/security/suites/secp256k1-2019/v1` for `EcdsaSecp256k1Signature2019`)
  - `https://w3id.org/security/suites/ed25519-2020/v1` for `Ed25519Signature2020`
  - `https://w3id.org/security/suites/jws-2020/v1` for `JsonWebSignature2020`
- The package accepts either (see [§4.8](#48-hash-data-and-suites)).

`URDNA2015` and W3C `RDFC-1.0` produce the same output for these inputs. The output format is N-Quads.

What gets signed, per proof encoding:

| Proof field | Bytes passed to the signature verifier |
|---|---|
| `proofValue` (multibase: `z` = base58btc; legacy suites also `u` = base64url) | `hashData` |
| `jws` (detached, `b64:false`) | `ASCII(base64url(protectedHeader)) \|\| '.' \|\| hashData` |

The verifier applies the algorithm's own digest on top. For example, ES256 is ECDSA over `SHA-256(hashData)`. Ed25519 signs `hashData` directly.

### 2.3 Proof suites and signature algorithms

| Suite id | Encoding | Key | Algorithm |
|---|---|---|---|
| `Ed25519Signature2018` | `jws` (EdDSA) | Ed25519 | Ed25519 |
| `Ed25519Signature2020` | `proofValue` | Ed25519 | Ed25519 |
| `EcdsaSecp256k1Signature2019` | `jws` (ES256K) or `proofValue` | secp256k1 | ECDSA-SHA256, raw `r\|\|s` 64 B |
| `EcdsaSecp256r1Signature2019` | `jws` (ES256) or `proofValue` | P-256 | ECDSA-SHA256, raw `r\|\|s` 64 B |
| `RsaSignature2018` | `jws` (RS256/PS256) or `proofValue` (RS256) | RSA ≥ 2048 bits | PKCS#1 v1.5 / PSS (SHA-256, MGF1-SHA256, salt 32) |
| `JsonWebSignature2020` | `jws` (EdDSA/ES256/ES256K) | per alg | per alg |

These are the suites Inji supports. `DataIntegrityProof` (`eddsa-rdfc-2022`, `ecdsa-rdfc-2019`) is not supported and fails as an unsupported proof type.

A suite id is the proof `type`. By default VPs accept every suite except `RsaSignature2018`, and VCs every suite except `JsonWebSignature2020` (`Config.VPSuites`, `Config.VCSuites`).

For every suite:

- `proofPurpose` must be `authentication` for VPs and `assertionMethod` for VCs.
- A `proof` array (multiple proofs) is rejected.
- On a VP proof, `challenge` and `domain` are mandatory ([§4.7](#47-vp-verification-algorithm)).

---

## 3. Known gaps

### 3.1 In the imported libraries

These are gaps in libraries the package imports. Each one is closed or worked around in the package.

| Library | Gap | Risk if used as is | Handling in `ldp` |
|---|---|---|---|
| `piprate/json-gold` | Expansion silently drops terms the context doesn't define. Its `SafeMode` option catches dropped properties, but not a `@type`/`@id` left as a relative IRI. | Attributes outside the signature that are still visible in the JSON, including to callers reading `PresentationResult.Claims`. | `SafeMode` plus a walk over the expanded form that rejects any property, `@type` or `@id` RDF conversion would drop (`ErrUndefinedTerm`), for documents and proof options ([§4.10](#410-json-ld-safe-mode-and-canonicalization)). |
| `piprate/json-gold` | Container mappings are resolved against the term's scoped context instead of the active context. VCDM 2.0 declares `verifiableCredential` with `"@context": null`, so its `@graph` container is lost. | Embedded VCs land in the VP's own graph. The VP canonical form differs from other processors, and valid VPs fail. | `fixGraphContainers` wraps them back. |
| `piprate/json-gold` | RDF conversion emits triples for relative IRIs, which JSON-LD to RDF must drop. | Hash data differs from other processors (jsonld.js, Titanium). | `dropRelativeIRIs` removes them. |
| `piprate/json-gold` | The default document loader fetches any HTTP/HTTPS context URL at processing time, with no allow-list and no cache. | SSRF, latency/DoS, and semantics that change if a remote context changes. | Own loader: pinned contexts, optional allow-list of remote prefixes, guarded fetcher, size-limited cache ([§4.11](#411-document-loader)). |
| `piprate/json-gold` | No limit on blank nodes before URDNA2015 normalization. | CPU exhaustion from a crafted document. | `MaxBlankNodes` checked before normalizing. |
| `net/http` (default client) | No timeout, no body size limit, up to 10 redirects, proxy from the environment, any destination address. | DoS and SSRF through did:web, HTTPS key, controller, status list or context URLs. | `fetch.Fetcher`: timeouts, body cap, 3 redirects, no proxy, dialer that refuses private and special-purpose addresses ([§4.12](#412-key-resolution-and-fetching)). |

### 3.2 In the current code

These are open. [§7](#7-status) tracks the work.

| Gap | Effect |
|---|---|
| Official `security/v1`, `security/v2` and `secp256k1-2019` contexts are not pinned. | Legacy `RsaSignature2018`/`Ed25519Signature2018` proofs signed by ld-signatures-java don't verify ([§4.11](#411-document-loader)). |
| Multiple proofs (a `proof` array) are not supported. | Credentials or presentations with proof sets or chains are rejected. |
| Enveloped VCs (`EnvelopedVerifiableCredential`) are not supported. | They are rejected explicitly. |
| JWKS key resolution is not supported. | A `verificationMethod` pointing at a JWKS fails key resolution. |
| Cache lifetimes are fixed TTLs. `Cache-Control` is ignored. | A DID document, status list or context can be served stale up to its TTL, or refetched sooner than needed. |
| High-S ECDSA signatures are accepted (stdlib `crypto/ecdsa` and `btcec` both accept them). | Signatures are malleable. Verification outcome is unaffected, and replay is controlled by `challenge`. |
| No metrics hooks. | No visibility into fetch latency, cache hits or failure kinds beyond logs. |

---

## 4. Go design

### 4.1 Inputs

**Per call, supplied by the caller**

| Input | Required | Use |
|---|---|---|
| VP (raw JSON bytes) | yes | The presentation to verify. One VP per call. If a response carries several, the caller loops. |
| Challenge | yes | Must equal `proof.challenge` on the VP. In OpenID4VP this is the request `nonce`. |
| Domain | yes | Must be in `proof.domain` on the VP. In OpenID4VP this is the full `client_id`. |

Everything else is policy that doesn't change between requests, so it lives in `Config` and is validated once, in `New`. An unknown proof suite or a missing issuer-trust function fails at startup, not on every request. A caller that needs different policies, e.g. per requested credential, builds one `Verifier` per policy.

**Configuration, set once at construction (`Config`)**

| Field | Default | Use |
|---|---|---|
| `IssuerTrusted func(string) bool` | **required** | Issuer trust. `TrustIssuers(ids...)` for a fixed list, `AnyIssuer` to accept every issuer that controls its key, or a custom function (e.g. a trust registry). |
| `StatusPurposes` | none | e.g. `["revocation"]`. Empty skips status checks. |
| `VPSuites`, `VCSuites` | `proof.DefaultVPSuites()` / `DefaultVCSuites()` | Accepted proof suites, so the verifier stays in line with what it advertised. |
| `ClockSkew` | 3 s | Tolerance on every date comparison. |
| `MaxVPAge` | off | Rejects a VP proof whose `created` is older than this. |
| `Now` | `time.Now` | Injected in tests. |
| `ContextsDir`, `RemoteContextAllowlist` | none | Extra pinned contexts; URL prefixes the loader may fetch. |
| `HTTPClient` | guarded default client | did:web, HTTPS key and controller documents, status lists, allowed remote contexts. |
| `AllowInsecureHTTP`, `AllowPrivateNetworks` | false | Local development only. |
| `FetchTimeout`, `FetchFailureTTL` | 10 s, 30 s | Per-request timeout; how long a failed fetch is remembered. |
| `DIDDocCacheTTL`, `StatusListCacheTTL`, `ContextCacheTTL`, `CacheSize` | 5 min, 5 min, 24 h, 256 | Caches. |
| `MaxPresentationBytes`, `MaxCredentials`, `MaxJSONDepth`, `MaxFetchBytes`, `MaxBlankNodes` | 1 MiB, 16, 64, 2 MiB, 1024 | Input and complexity limits. |
| `CredentialConcurrency` | 4 | Credentials of one VP verified in parallel. |

**Not inputs.** The request query (DCQL), the response `state`, decryption keys and the request transaction aren't passed in. The caller handles them ([§1.2](#12-out-of-scope-caller-responsibilities)).

### 4.2 Outputs

On success, the package returns:

| Output | Use for the caller |
|---|---|
| Format (`ldp_vc`) | Dispatch alongside other OpenID4VP credential formats. |
| Claims: the `credentialSubject` claims of every VC, merged in document order (after safe mode passed) | Claim mapping. A later value overrides an earlier one. |
| Holder binding thumbprint: RFC 7638 SHA-256 JWK thumbprint of the VP proof key | Binding the session to the wallet key. |
| VP proof `created` | Audit. |

On failure it returns a single `*VerificationError`. The package never returns a "verified but expired" state.

- **`Kind`:** an enum, so callers can `switch` on it or use `errors.Is(err, ldp.ErrSignature)`.
- **`Index`:** the position of the failing VC, or `-1` for the VP itself.
- **`Field` / `StatusPurpose`:** where a kind needs detail (e.g. `ErrMissingField` with `Field: "issuanceDate"`).
- **`Message` and the wrapped `Err`:** for logs.

### 4.3 Placement and dependencies

The package has no Thunder and no eSignet imports. It depends only on the standard library, `json-gold`, `mr-tron/base58`, `btcec` and `golang.org/x/sync`. That lets it be tested in isolation, and lets it live in `esignet-service/internal/vc/ldp` or move to another module unchanged.

### 4.4 Package layout

```text
internal/vc/ldp/
  verifier.go           package doc; Verifier, Config, New, TrustIssuers / AnyIssuer, results
  presentation.go       VerifyPresentation: VP parse, challenge/domain, VP proof, holder binding, parallel VCs
  credential.go         per-VC checks: issuer trust, VC proof, issuer/key control, status; error mapping
  validate.go           VCDM 1.1 / 2.0 structural validation into a typed credential view;
                        tolerance helpers, bounded JSON decoding, URI check
  errors.go             Kind, VerificationError
  jsonval/
    jsonval.go          List, String, Strings, ID, Objects, Time (shared with status)
  proof/
    proof.go            Canonicalizer interface, HashData, RFC 7797 b64:false detached JWS
    suites.go           suite registry, Parse, Verify (proof-context candidates)
  jsonld/
    loader.go           pinned (go:embed + ContextsDir) and allow-listed caching loader
    canon.go            safe mode, URDNA2015, json-gold workarounds
    contexts/           pinned contexts + index.json
  didkeys/
    resolver.go         Resolver, Method, router, verification relationships;
                        HTTPS key documents + controller documents
    did.go              DID URL parsing, did:key, did:jwk, did:web document fetch + VM lookup
    keys.go             publicKeyJwk/Multibase/Base58/Hex/Pem -> crypto.PublicKey, RFC 7638 thumbprint
  sig/
    verify.go           Ed25519, ECDSA P-256, ECDSA secp256k1, RS256, PS256
  status/
    bitstring.go        BitstringStatusList fetch/verify/decode/read
  fetch/
    fetch.go            guarded HTTP client, singleflight, failure cache, generic TTL cache
  testdata/             sample credentials, presentations, DID/key/controller documents, contexts
```

Every file carries the MPL 2.0 header used across `esignet-service`.

### 4.5 Library choices

| Need | Choice | Notes |
|---|---|---|
| JSON-LD expand + URDNA2015 | `github.com/piprate/json-gold` v0.9.1 | Has a `SafeMode` expansion option. Its gaps and the workarounds are in [§3.1](#31-in-the-imported-libraries). |
| base58btc multibase | `github.com/mr-tron/base58` | Only `z` (base58btc) and, for legacy proof values, `u` (base64url) are needed. |
| Ed25519, P-256, RSA | `crypto/ed25519`, `crypto/ecdsa`, `crypto/rsa` | stdlib. PS256 uses a fixed salt length of 32 (the SHA-256 digest size). |
| secp256k1 (ES256K) | `github.com/btcsuite/btcd/btcec/v2` (already a direct dependency) | The signature is built from raw `r`,`s` scalars rather than DER. |
| JWK parsing | hand-written (`didkeys/keys.go`) | Small, covers secp256k1, and rejects private members. go-jose is not used. |
| Detached JWS `b64:false` | hand-written (`proof/proof.go`) | Controls the `crit`/`b64` checks and the empty payload. |
| Request de-duplication, bounded parallelism | `golang.org/x/sync` (`singleflight`, `errgroup`), already a direct dependency | |
| HTTP | `net/http` with a guarded client | [§4.12](#412-key-resolution-and-fetching). |

New dependencies in `esignet-service/go.mod`: `github.com/piprate/json-gold` and `github.com/mr-tron/base58`. `json-gold` brings `github.com/pquerna/cachecontrol` and `github.com/cayleygraph/quad`. All four are recorded in `THIRD-PARTY-NOTICES.txt`.

### 4.6 Public API

```go
// Config is fixed for the lifetime of a Verifier; see §4.1 for every field.
type Config struct {
	IssuerTrusted      func(issuer string) bool // required
	StatusPurposes     []string
	VPSuites, VCSuites []string
	ClockSkew, MaxVPAge time.Duration
	Now                func() time.Time
	// contexts, HTTP, caches, limits, CredentialConcurrency ...
}

func New(cfg Config) (*Verifier, error)

func TrustIssuers(ids ...string) func(issuer string) bool
func AnyIssuer(string) bool

// VerifyPresentation verifies the VP proof, its binding to challenge and
// domain, holder binding, and every embedded VC.
func (v *Verifier) VerifyPresentation(ctx context.Context, vp []byte, challenge, domain string) (*PresentationResult, error)

const FormatLDPVC = "ldp_vc"

type PresentationResult struct {
	Format                  string         // FormatLDPVC
	Claims                  map[string]any // credentialSubject claims of every VC, merged; later values override
	HolderBindingThumbprint string         // RFC 7638 SHA-256 JWK thumbprint of the VP proof key
	Created                 *time.Time
}

// Kind classifies a failure. Kinds are errors: errors.Is(err, ldp.ErrSignature).
type Kind int

const (
	ErrInvalidRequest Kind = iota + 1 // empty challenge or domain
	ErrMalformed                      // not a JSON object, or JSON-LD processing failed
	ErrLimitExceeded
	ErrMissingField     // Field names it
	ErrInvalidField     // Field names it
	ErrUnsupportedProof // suite or algorithm not accepted
	ErrUndefinedTerm    // safe mode: a term would be dropped or left relative
	ErrContext          // context not pinned/allowed, or not loadable
	ErrChallenge
	ErrDomain
	ErrProofPurpose
	ErrProofExpired
	ErrKeyResolution
	ErrKeyNotAuthorized // controller document doesn't list the key for the proof purpose
	ErrSignature
	ErrUntrustedIssuer
	ErrIssuerKeyMismatch // VC key not controlled by the issuer
	ErrHolderBinding
	ErrNotYetValid // Field: validFrom / issuanceDate
	ErrExpired
	ErrStatusUnavailable
	ErrStatusSet // StatusPurpose names it
)

type VerificationError struct {
	Kind          Kind
	Index         int    // VC index inside the VP, -1 for the VP itself
	Field         string // e.g. "credentialSubject.id"
	StatusPurpose string // e.g. "revocation"
	Message       string
	Err           error
}
```

`proof.DefaultVPSuites()` returns every suite except `RsaSignature2018`. `proof.DefaultVCSuites()` returns every suite except `JsonWebSignature2020`. Both return copies.

### 4.7 VP verification algorithm

```go
func (v *Verifier) VerifyPresentation(ctx context.Context, raw []byte, challenge, domain string) (*PresentationResult, error) {
	// 0. challenge and domain are required (ErrInvalidRequest)
	// 1. size cap, then decode (UseNumber, no trailing data, depth cap)
	// 2. parsePresentation: @context[0] in {v1, v2}; type contains VerifiablePresentation;
	//    exactly one proof object; verifiableCredential an object or array of
	//    objects, none enveloped, at most MaxCredentials
	// 3. checkProofBinding: proofPurpose == authentication, challenge (constant time),
	//    domain (string or array), expires / MaxVPAge
	// 4. proof.Parse against Config.VPSuites
	// 5. safe mode on the VP with every proof removed; the VC proofs are checked
	//    later under their own contexts
	// 6. canonicalize the VP minus its proof (VC proofs included: the holder signed them)
	// 7. resolve the verificationMethod; its controller document must list it under
	//    authentication
	// 8. verify the signature (proof options canonicalized in safe mode)
	// 9. bindHolder: the proof method belongs to the holder,
	//    then the holder key's RFC 7638 thumbprint
	// 10. verify the VCs in parallel (CredentialConcurrency); bind each one's subjects
	//     to the holder; return the error of the first failing VC in document order
	// 11. merge the subjects' claims in document order; a later value overrides
}
```

The VC path (`verifyCredential`), cheapest and local checks first:

1. `parseCredential` checks structure ([§2.1](#21-vc-structural-validation-validatego)) and the validity window, and returns a typed view (issuer, types, subjects, proof, window). Expired or not-yet-valid fails.
2. `Config.IssuerTrusted(issuer)`. An untrusted issuer causes no network fetch.
3. `proofPurpose == assertionMethod`, the proof's own `expires` (if present, with `ClockSkew`; `MaxVPAge` does not apply), then `proof.Parse` against `Config.VCSuites`.
4. Safe-mode canonicalization of the VC minus its proof: one expansion does both.
5. Resolve the verificationMethod. Its controller document must list it under `assertionMethod`. Its controller must equal the issuer.
6. Verify the signature.
7. If `StatusPurposes` is set, `status.Check` ([§4.14](#414-credential-status)).

### 4.8 Hash data and suites

```go
// proof/proof.go
type Canonicalizer interface {
	Canonicalize(ctx context.Context, doc map[string]any) (string, error)     // drops undefined terms
	CanonicalizeSafe(ctx context.Context, doc map[string]any) (string, error) // fails on them
	Pinned(contextURL string) bool
}

// proof/suites.go
func (p *Proof) Verify(ctx context.Context, c Canonicalizer, docContext any, canonicalDoc string, pub crypto.PublicKey) error
```

The caller canonicalizes the document and passes the N-Quads in: VCs in safe mode, VPs not ([§4.10](#410-json-ld-safe-mode-and-canonicalization)). `Verify` then:

1. checks that the key type fits the algorithm (`sig.CheckKey`) before any hashing;
2. canonicalizes the proof options in safe mode, under each candidate context in turn:
   - the document's `@context` (what jsonld-signatures does)
   - then each **pinned** suite context from [§2.2](#22-canonicalization-and-the-hash-data);
3. accepts under the first candidate that defines every option and yields a valid signature.

If some candidate canonicalized but none verified, the result is `ErrSignature`. If no candidate defines every option, the result is `ErrUndefinedTerm`.

The suite registry is table-driven:

```go
type suiteSpec struct {
	jwsAlgs       []string // algorithms accepted in a detached JWS; nil => no jws
	proofValueAlg string   // algorithm for proofValue; "" => no proofValue
}

var suites = map[string]suiteSpec{
	"Ed25519Signature2018":        {jwsAlgs: []string{"EdDSA"}},
	"Ed25519Signature2020":        {proofValueAlg: "EdDSA"},
	"EcdsaSecp256k1Signature2019": {jwsAlgs: []string{"ES256K"}, proofValueAlg: "ES256K"},
	"EcdsaSecp256r1Signature2019": {jwsAlgs: []string{"ES256"}, proofValueAlg: "ES256"},
	"RsaSignature2018":            {jwsAlgs: []string{"RS256", "PS256"}, proofValueAlg: "RS256"},
	"JsonWebSignature2020":        {jwsAlgs: []string{"EdDSA", "ES256", "ES256K"}},
}
```

`proofValue` uses `z` (base58btc) or `u` (base64url).

The detached JWS (RFC 7797) is strict:

- three segments, **payload segment empty**;
- `b64` present and `false`;
- `crit` exactly `["b64"]`.

The signing input is `protectedHeader || '.' || hashData`. A non-empty payload (some wallets put `hello` there) is rejected as a malformed proof.

### 4.9 Signature verification

`sig.Verify(alg, pub, msg, sig)` covers EdDSA, ES256, ES256K, RS256 and PS256. `CheckKey` rejects:

- a key whose type or curve doesn't fit the algorithm;
- RSA keys under 2048 bits.

ECDSA signatures are raw 64-byte `r||s`. ES256 converts them to ASN.1 for `ecdsa.VerifyASN1`. ES256K builds `btcec` `ModNScalar` values, and an overflowing scalar is rejected.

**Low-S:** high-S signatures are accepted ([§3.2](#32-in-the-current-code)). A unit test covers it for secp256k1.

### 4.10 JSON-LD safe mode and canonicalization

Every property, `@type` and `@id` must expand to an absolute IRI. Otherwise the proof ignores it, but it is still visible to anyone reading the JSON, including callers that read `PresentationResult.Claims`.

- json-gold's `SafeMode` expansion fails on dropped properties (`InvalidProperty`, mapped to `ErrUndefinedTerm`).
- A walk over the expanded form then rejects what expansion keeps but RDF conversion drops: a property that is not an absolute IRI (including a blank node property such as `_:role`, or `./x:y`), and a `@type`/`@id` that is neither an absolute IRI (by json-gold's own `IsAbsoluteIri`) nor a keyword/blank node.
- `CanonicalizeSafe` does both on the same expansion it canonicalizes, so a VC is expanded once for safe mode and signing input together.
- The VP is checked separately: safe mode runs on the VP with every proof (its own and the VCs') removed. The VP signing input is then canonicalized normally, because the embedded VC proofs may use terms only their suite context defines.
- Proof options are always canonicalized in safe mode ([§4.8](#48-hash-data-and-suites)).

`jsonld/canon.go` also works around the two json-gold v0.9.1 canonicalization bugs in [§3.1](#31-in-the-imported-libraries):

1. **Lost `@graph` container:** `fixGraphContainers` wraps the embedded VCs back into graph objects. It does not descend into `@value`, so a `@json` literal that happens to contain the `verifiableCredential` IRI stays as signed.
2. **Relative IRIs:** `dropRelativeIRIs` removes the triples, so the hash data matches other processors.

The W3C Data Integrity test vectors (VCs and VPs, produced by digitalbazaar's implementation) cross-check both workarounds. The verifier does not accept Data Integrity proofs, so the test recomputes their hash data and checks the signatures directly.

MOSIP issuer contexts often use `@vocab`, which makes every term defined. Safe mode still catches relative IRIs and contexts without `@vocab`. In the samples, `PS256SignedMosipVC.json` fails safe mode: its type `MOSIPVerifiableCredential` is undefined.

### 4.11 Document loader

- **Pinned contexts:** embedded with `go:embed` under `jsonld/contexts/` and listed in `index.json`, plus any `Config.ContextsDir` with the same layout.
- **Remote contexts:** fetched only when the URL matches a `RemoteContextAllowlist` prefix, through the guarded fetcher, and cached for `ContextCacheTTL`. Every redirect target must match the allow-list too.
- **Anything else** fails with `ErrContext`.

Embedded set:

- `https://www.w3.org/2018/credentials/v1`
- `https://www.w3.org/ns/credentials/v2`: the older draft MOSIP issuers sign with (no `@vocab`). MOSIP samples verify only with it.
- `https://w3id.org/security/suites/ed25519-2020/v1`
- `https://w3id.org/security/suites/jws-2020/v1`
- `https://www.w3.org/ns/odrl.jsonld`
- `https://mosip.github.io/inji-config/contexts/mock-identity-context.json`
- `https://inji.github.io/inji-config/contexts/mosip-identity-context.json`

**Not yet pinned:** `https://w3id.org/security/v1` and `v2`, and `https://w3id.org/security/suites/secp256k1-2019/v1`. Without `security/v2`, legacy `RsaSignature2018`/`Ed25519Signature2018` proofs signed by ld-signatures-java don't verify ([§2.2](#22-canonicalization-and-the-hash-data)). Tests use a labelled stand-in (`testdata/contexts/security-v2-standin.jsonld`), which is **not** the published context.

### 4.12 Key resolution and fetching

```go
type Method struct {
	ID            string           // the verificationMethod URL as given
	Controller    string           // the DID, or the controller of an HTTPS key document
	Key           crypto.PublicKey
	Relationships []string         // assertionMethod / authentication the controller authorizes
}

func (m *Method) Authorizes(purpose string) bool

type Resolver interface {
	Resolve(ctx context.Context, verificationMethod string) (*Method, error)
}
```

The verifier requires `m.Authorizes(proofPurpose)` (`ErrKeyNotAuthorized`) before comparing `m.Controller` with the issuer (`ErrIssuerKeyMismatch`) or holder. Without the relationship check, anyone could host a key document naming a trusted issuer as its controller.

| Method | Behaviour |
|---|---|
| `did:key` | `z` multibase, varint multicodec: Ed25519 (`0xed`), P-256 (`0x1200`, compressed or not), secp256k1 (`0xe7`), RSA (`0x1205`). Authorized for both relationships. |
| `did:jwk` | base64url with `=` padding tolerated. Private JWK members are rejected. Authorized for both relationships, unless the JWK has `"use": "enc"`: its DID document lists it under `keyAgreement` only, so it is authorized for neither. |
| `did:web` | `did:web:host` maps to `https://host/.well-known/did.json`, `did:web:host:a:b` to `https://host/a/b/did.json`, with `%3A` in the host for a port and unsafe path segments rejected. The document `id` must equal the DID. The VM is found in `verificationMethod` or embedded in a relationship, by absolute id or by `#fragment` relative to the DID. Relationships are read from `assertionMethod`/`authentication` entries (reference strings, absolute or relative, or embedded objects with the same key) that name the VM. Cached for `DIDDocCacheTTL`. |
| `https://…` | Fetches the key document. Its `id`, if present, must match. It must name a `controller`. The **controller document** is then fetched: HTTPS (its `id` must equal the controller) or `did:web`. Relationships come from the controller document's entries that reference the key. An embedded entry counts only if its own key equals the fetched one. A key document naming a controller that doesn't list it is authorized for nothing. |

Key encodings (`didkeys/keys.go`): `publicKeyJwk` (OKP Ed25519, EC P-256/secp256k1, RSA), `publicKeyHex`, `publicKeyPem` (SPKI), `publicKeyMultibase` (multicodec), `publicKeyBase58` (raw). The key must agree with the VM `type`. `JsonWebKey2020`, `JsonWebKey` and `Multikey` accept any supported key, and unknown VM types are rejected. Keys are compared with each type's `Equal` (btcec `IsEqual` for secp256k1).

All outbound HTTP goes through `fetch.Fetcher`:

- HTTPS only (HTTP with `AllowInsecureHTTP`), no userinfo, at most 3 redirects (each re-checked, also on a caller-supplied `HTTPClient`, whose own `CheckRedirect` then still applies), body capped at `MaxFetchBytes`, JSON decoded with `UseNumber`.
- The default client's dialer refuses:
  - loopback, private, link-local, multicast and unspecified addresses
  - `0.0.0.0/8`, `100.64.0.0/10`, `192.0.0.0/24`, `198.18.0.0/15` and `240.0.0.0/4`
  - NAT64 and 6to4 ranges, which embed IPv4 addresses
  - the IPv6 discard prefix (`100::/64`)
- The default client **never uses a proxy**: the guard checks the dialed address, which with a proxy would be the proxy's. A deployment that needs a proxy supplies its own `HTTPClient` and its own egress controls.
- Concurrent requests for one URL share a single fetch (`singleflight`). The shared request runs on its own timeout, and each caller still honours its own context.
- A failed fetch is remembered for `FetchFailureTTL`, so a dead or slow host isn't retried on every verification.

### 4.13 Holder binding

Holder binding is always required. There is no lenient mode:

```go
// bindHolder: the VP proof method belongs to the holder.
//   holder missing                     => ErrHolderBinding
//   holder is did:key / did:jwk        => its key must equal the method's key
//   holder is did:web / HTTPS / other  => the method's controller must equal the holder
//                                         (the method is already authorized for authentication)
//   holder DID malformed               => ErrHolderBinding ("Fail to decode ...")
//
// bindSubjects: every credentialSubject.id is the holder.
//   id missing                         => ErrHolderBinding
//   id == holder                       => ok
//   id is did:key / did:jwk            => its key must equal the holder key (cross-encoding allowed)
//   any other id                       => ErrHolderBinding
```

A `did:jwk` holder with a `did:key` subject for the same key passes (`vp/crossMethodBindingVP.json`). A `did:web` holder passes when its DID document lists the VP key under `authentication` and the subjects name the holder DID.

### 4.14 Credential status

BitstringStatusList (W3C), in `status/bitstring.go`:

1. `credentialStatus` is an object or array. Entries are filtered by `statusPurpose` against `Config.StatusPurposes` (case-insensitive). A purpose with several entries is set if any of them is, and every entry is still checked.
2. Each matching entry must have:
   - `type == BitstringStatusListEntry`
   - a `statusListCredential` URL
   - a non-negative integer `statusListIndex`
3. Fetch `statusListCredential` through the guarded fetcher, then verify it with the VC path minus issuer trust and status (no recursion), using the verifier's `VCSuites`.
4. The status list issuer must **equal** the credential issuer. Otherwise an attacker-hosted list could report "not revoked".
5. The status list's `credentialSubject` must have:
   - its own `validFrom`/`validUntil` window satisfied
   - `type == BitstringStatusList`
   - `statusPurpose` (a string or an array) containing the entry's purpose
6. Decode `encodedList`:
   - multibase base64url (the `u` prefix is optional)
   - GZIP, decompressed size capped at 32 MiB
7. `statusSize` is read from the entry first, then from the list subject, and defaults to 1 (maximum 16). If it is greater than 1, `statusMessage` must have `2^statusSize` entries (an array, as in the W3C spec, or an object).
8. Bit position = `statusListIndex * statusSize`. Bits are read **MSB-first within each byte**. A value of `0` means valid.

Results:

- Verified lists are cached per URL for `StatusListCacheTTL`, or until the list's `validUntil` (VCDM 2.0), `expirationDate` (VCDM 1.1) or subject `validUntil` if that is sooner.
- If the list can't be fetched, verified or decoded, the result is `ErrStatusUnavailable`. An unreachable list is never treated as "not revoked".
- A set status is `ErrStatusSet` with `StatusPurpose`.

---

## 5. Testing

### 5.1 Fixtures

`internal/vc/ldp/testdata/` holds:

- **Samples:** MOSIP, Inji and Sunbird credentials and presentations (`ldp_vc/`, `vp/`).
- **DID and key documents** their proofs reference (`did/`).
- **W3C vectors:** the W3C Data Integrity test vectors (`w3c/`, with their own README), used only to cross-check canonicalization.
- **Contexts:** test contexts (`contexts/`).

The tests serve the documents from an in-memory `http.RoundTripper`, so they never hit the network.

- The controller documents for the HTTPS key samples (`did/idaController.json`, `did/httpsMockController*.json`) were **written for the tests**. The published MOSIP ones aren't in the samples.
- `did/didMockKey.json` (the Inji mock issuer) and `did/didEcr1Key.json` list the DID itself, not a VM, under `assertionMethod`. Their credentials and status lists fail with `ErrKeyNotAuthorized`. A test shows they verify once the documents list the keys, and the status-list end-to-end test serves such a corrected copy.
- Many samples have dates that have since passed. The clock is pinned through `Config.Now`.
- The sample VPs predate challenge/domain binding, or carry no `proofPurpose`. The tests:
  - assert that they fail on the public path;
  - verify their signatures through a test helper;
  - verify their embedded VCs on their own.
- Several sample VPs carry `hello` in the detached JWS payload. The three Ed25519Signature2018 ones used in signature tests now fail as malformed proofs.

Results on the samples (signature checks all pass; full path):

| Outcome | Samples |
|---|---|
| Pass | `ES256KSignedMockVC`, `Ed25519Signature2018SignedVC-did`, `Ed25519Signature2020SignedSunbirdVC` |
| `ErrKeyNotAuthorized` (issuer DID document authorizes no key) | `ES256SignedVC`, `mosipUnrevokedVC`, `mosipRevokedVC`, `mosipUnrevokedStatusList` |
| `ErrUndefinedTerm` | `PS256SignedMosipVC`, `Ed25519Signature2018SignedVC-https` |
| `ErrMissingField` (`issuanceDate`) | `RS256SignedMosipVC` |
| `ErrUnsupportedProof` (`proof.type`) | W3C Data Integrity vectors |

### 5.2 Go-generated tests

Policy cases use credentials and presentations signed in the test with `did:key` or `did:web` Ed25519 keys (`Ed25519Signature2020`):

- Challenge and domain:
  - mismatches; `domain` as an array; a non-string entry in `domain`.
  - wrong `proofPurpose` on the VP and on a VC.
  - empty challenge or domain (`ErrInvalidRequest`).
- Proof time window: an expired proof; a stale proof and a future `created` under `MaxVPAge`.
- Credential validity: expired and not-yet-valid VCs; bad dates; empty subject; bad issuer; wrong type; wrong context.
- Holder binding: subject without id, with an unsupported method, non-DID, or someone else's; a holder that doesn't control the proof key; a missing holder; `did:web` holders.
- Key authorization:
  - a `did:web` issuer key under `assertionMethod`, under `authentication` only, or under neither;
  - the forged HTTPS key document that names a trusted issuer as its controller.
- Issuer trust:
  - exact list, prefix function and `AnyIssuer`;
  - an untrusted issuer triggers no fetch;
  - a `did:key` issuer whose VC is signed by another key.
- `New` rejects a missing `IssuerTrusted` and unknown suites.
- Safe mode: an undefined term in the subject, in the type, and in the proof options.
- Contexts: a non-allow-listed remote context fails; an allow-listed one is fetched once and cached.
- Proof structure: suites outside `VPSuites`/`VCSuites`; a key/algorithm mismatch; a proof array; an unsupported multibase prefix on a proof value.
- Presentation shape: enveloped and string VCs; no VCs; not a VP; wrong context; no proof; non-object input.
- Limits: size, depth, credential count, blank nodes.
- Parallel verification: multiple credentials; the first failing VC in document order is reported; concurrent `VerifyPresentation` calls.
- Status: valid, revoked, list from another issuer, unreachable list, caching, a purpose with no entry.
- Sub-packages:
  - **fetch:** SSRF ranges, no proxy, singleflight, caller cancellation, failure cache, TTL cache.
  - **keys:** relationships, embedded methods, controller documents, `did:web` controllers.
  - **proof:** detached JWS rules, proof values, suite parsing, default suites.
  - **jsonld:** safe canonicalization, the graph-container and relative-IRI workarounds.
  - **status:** bit reading, decoding, `statusSize`/`statusMessage`.
  - **jsonval**.

### 5.3 Conventions

- `testify/suite` per package, as `esignet-service/AGENTS.md` requires.
- `go test -race` needs CGO. Locally it runs in the `golang:1.27.2` container, as does golangci-lint (`.golangci.yml`, 0 issues).
- Cross-implementation check: json-gold reproduces the hash data of the W3C Data Integrity vectors (digitalbazaar).

---

## 6. Security checklist

- [x] Challenge and domain are mandatory, checked for every VP suite.
- [x] `proofPurpose`: `authentication` for VPs, `assertionMethod` for VCs.
- [x] The controller document authorizes the proof key for that purpose, including the controller document of an HTTPS key.
- [x] The VC key is controlled by the issuer. The VP key is controlled by the holder.
- [x] Issuer trust required. "Any issuer" is an explicit choice (`AnyIssuer`). Checked before any fetch for the credential.
- [x] Holder binding always required.
- [x] Expired or not-yet-valid VCs fail.
- [x] JSON-LD safe mode on (document and proof options). Contexts pinned. No arbitrary remote fetch.
- [x] All outbound HTTP:
  - HTTPS only
  - timeouts set
  - body size capped
  - redirects limited
  - no private or special-purpose targets unless configured (SSRF)
  - no proxy in the default client
  - repeated failures not retried within `FetchFailureTTL`
- [x] GZIP status list decompression capped at 32 MiB. Status failures fail closed. Status list issuer must equal the credential issuer.
- [x] Inputs bounded:
  - VP size limit before parsing
  - maximum number of VCs per VP
  - JSON nesting depth
- [x] Blank-node count capped before URDNA2015 normalization.
- [x] Detached JWS payload must be empty.
- [x] `VerificationError` messages are suitable for logs. The caller decides what, if anything, goes back to the wallet.
- [ ] Official `security/v1`, `security/v2` and `secp256k1-2019` contexts pinned ([§4.11](#411-document-loader)).

---

## 7. Status

| Done | Notes |
|---|---|
| Loader, canonicalization, json-gold workarounds | §4.10–4.11 |
| Suite registry, detached JWS, signatures | §4.8–4.9 |
| Key resolution: `did:key`, `did:jwk`, `did:web`, HTTPS + controller documents | §4.12 |
| VCDM 1.1/2.0 validation | §2.1 |
| Challenge/domain, holder binding, issuer trust, key authorization, issuer/key control, safe mode | §4.7, §4.13 |
| BitstringStatusList | §4.14 |
| Guarded fetcher, caches, limits, parallel VC verification | §4.12 |

| Not done | Notes |
|---|---|
| Pin the official security contexts | Blocks legacy suites signed by ld-signatures-java. |
| JWKS key resolution | Only if a target issuer needs it. |
| `Cache-Control`-based cache lifetimes | Fixed TTLs today. |
| Metrics hooks | |
| Wiring into `internal/config` and the OpenID4VP layer | Config through `.env`, as for the rest of `esignet-service`. |

---

## 8. Open questions

1. **Package location**: `esignet-service/internal/vc/ldp`, or a separate module so another repo (e.g. Thunder) can import it? `internal/` blocks imports from outside `esignet-service`.
2. **Wallet proof suites and `domain`**: which VP proof suites do the target wallets (Inji, others) emit, do they set `domain`, and do any still put a placeholder in the detached JWS payload? This decides the default `VPSuites` and whether the mandatory checks cause interop problems.
3. **Inji/MOSIP DID documents**: the Inji mock issuer's DID document lists the DID itself, not the key, under `assertionMethod`, so its credentials are rejected (`ErrKeyNotAuthorized`). Do production Inji Certify DID documents do the same? If so, the issuer documents need fixing, or the package needs an explicit, opt-in tolerance.
4. **MOSIP IDA controller documents**: do the HTTPS key documents' controllers (e.g. `https://api.collab.mosip.net/.well-known/ida-controller.json`) publish a document that lists the key under `assertionMethod`? Without one, IDA-signed credentials are rejected.
5. **VCDM 2.0 context**: the pinned `credentials/v2` is the older draft MOSIP issuers sign with. Should the published W3C context be pinned as well, and which does Inji Certify use today?
6. **Safe mode and MOSIP types**: real MOSIP credentials use types their contexts don't define (`MOSIPVerifiableCredential`). Keep safe mode strict, or ask issuers to fix their contexts?
7. **Multiple proofs**: does any target issuer attach more than one proof?
