# Authn Provider Integration

The Authn Provider is the bridge between eSignet and an identity system. It authenticates the end user, resolves a stable identifier for them, and returns the identity attributes the user consented to share — everything else (OIDC/OAuth protocol handling, consent capture, token issuance) is handled by eSignet itself.

**eSignet responsibilities**

- OAuth2 / OIDC / FAPI protocol compliance
- Consent capture and enforcement
- Token issuance (ID token, access token) and JWKS publishing

**Authn Provider responsibilities**

- Authenticate the user against the identity system
- Resolve a stable, provider-specific reference for the authenticated user
- Fetch verified user attributes (KYC) for the claims the user consented to
- Return data to eSignet in the agreed structure below

## Who should implement this interface

Any organization — public or private — that wants to connect its own identity system to eSignet implements this interface. The identity system behind it can be anything from a single database table to a full national identity registry; eSignet only depends on the Go interface, not on how the identity system itself is built.

## The interface

A provider is a Go package that implements `shared.ConsolidatedAuthnProvider` (package `github.com/mosip/esignet/internal/engine/shared`). This interface embeds the engine-level `providers.AuthnProviderInterface` (package `github.com/thunder-id/thunderid/pkg/thunderidengine/providers`) and adds two eSignet-specific methods:

```go
// ConsolidatedAuthnProvider extends providers.AuthnProviderInterface with sendOTP capability.
type ConsolidatedAuthnProvider interface {
	providers.AuthnProviderInterface

	// SendOTP sends an OTP to the user based on the provided identifiers and metadata.
	SendOTP(_ context.Context, identifiers map[string]interface{},
		metadata *providers.AuthnMetadata) (*SendOTPResult, *common.ServiceError)

	// GetSigningCertificates retrieves public keys used by the ID system to sign userinfo responses.
	GetSigningCertificates(ctx context.Context) ([]CertificateData, *common.ServiceError)
}
```

The embedded engine interface it extends:

```go
type AuthnProviderInterface interface {
	InitiateAuthentication(ctx context.Context, credentialType string, initData any,
		metadata *AuthnMetadata) (any, *common.ServiceError)
	Authenticate(ctx context.Context, identifiers, credentials map[string]interface{},
		metadata *AuthnMetadata) (*AuthnResult, *common.ServiceError)
	GetEntityReference(ctx context.Context, entityReferenceToken any) (*EntityReference,
		*common.ServiceError)
	GetAttributes(ctx context.Context, attributeToken any, consentedAttributes *RequestedAttributes,
		metadata *GetAttributesMetadata) (*AttributesResponse, *common.ServiceError)
	InitiateEnrollment(ctx context.Context, credentialType string, initData any,
		metadata *AuthnMetadata) (any, *common.ServiceError)
	Enroll(ctx context.Context, identifiers, credentials map[string]interface{},
		metadata *AuthnMetadata) (*AuthnResult, *common.ServiceError)
}
```

So a full implementation has eight methods: `SendOTP`, `Authenticate`, `GetEntityReference`, `GetAttributes`, `GetSigningCertificates`, plus `InitiateAuthentication`, `InitiateEnrollment`, and `Enroll` (see [Passkey/WebAuthn-only methods](#passkeywebauthn-only-methods) — these last three can be safely stubbed out for OTP/password/biometric/KBI-style identity systems).

### Supporting types

```go
// AuthnMetadata carries request-scoped metadata into every provider call.
type AuthnMetadata struct {
	RuntimeMetadata map[string][]string `json:"runtimeMetadata,omitempty"`
}

// AuthnResult is returned by Authenticate (and Enroll).
type AuthnResult struct {
	AuthenticatedClaims AuthenticatedClaims `json:"authenticatedClaims,omitempty"`
	EntityReferenceToken any             `json:"entityReferenceToken"` // opaque token, or nil if EntityReference is set directly
	EntityReference      *EntityReference `json:"entityReference,omitempty"`
	AttributeToken any                 `json:"attributeToken"` // opaque token, or nil if Attributes is set directly
	Attributes     *AttributesResponse `json:"attributes,omitempty"`
}

// EntityReference identifies the authenticated user to the rest of the engine.
type EntityReference struct {
	EntityID       string `json:"entityId"` // the stable, pairwise identifier returned as the OIDC "sub" claim
	EntityCategory string `json:"entityCategory"`
	EntityType     string `json:"entityType"`
	OUID           string `json:"ouId"`
}

// GetAttributesMetadata carries locale and runtime metadata into GetAttributes.
type GetAttributesMetadata struct {
	Locale          string              `json:"locale"`
	RuntimeMetadata map[string][]string `json:"runtimeMetadata,omitempty"`
}

// RequestedAttributes lists the claims the user has consented to release.
type RequestedAttributes struct {
	Attributes    map[string]*AttributeMetadataRequest `json:"attributes,omitempty"`
	Verifications map[string]*VerificationRequest      `json:"verifications,omitempty"`
}

// AttributesResponse carries the resolved claim values back to the engine.
type AttributesResponse struct {
	Attributes    map[string]*AttributeResponse    `json:"attributes,omitempty"`
	Verifications map[string]*VerificationResponse `json:"verifications,omitempty"`
}
type AttributeResponse struct {
	Value                     interface{}                `json:"value,omitempty"`
	AssuranceMetadataResponse *AssuranceMetadataResponse `json:"assuranceMetadataResponse,omitempty"`
}

// SendOTPResult and CertificateData are eSignet-specific (package shared).
type SendOTPResult struct {
	TransactionID string
	MaskedEmail   string `json:"maskedEmail,omitempty"`
	MaskedMobile  string `json:"maskedMobile,omitempty"`
}
type CertificateData struct {
	KeyID       string
	Certificate string
}
```

`identifiers` and `credentials` (the two `map[string]interface{}` arguments to `Authenticate`/`SendOTP`) split the login form's inputs, but not strictly by "sensitive vs. not" — the actual split, per the flow executor that builds these maps, is: a UIN/username lands in `identifiers`; OTP and password land in `credentials` (they arrive as sensitive `OTP_INPUT`/`PASSWORD_INPUT` flow inputs); PIN and biometric payloads land in `identifiers`, not `credentials`; and an arbitrary KBI challenge (no fixed field set) is read from whatever remains in `credentials`. See [`internal/engine/mock/authenticator.go`](../esignet-service/internal/engine/mock/authenticator.go)'s `setChallenge` function for the exact per-factor mapping.

### Errors

Every method returns `*common.ServiceError` (package `github.com/thunder-id/thunderid/pkg/thunderidengine/common`) instead of a plain Go `error`:

```go
type ServiceError struct {
	Code             string           `json:"code"`
	Type             ServiceErrorType `json:"type"` // common.ClientErrorType or common.ServerErrorType
	Error            I18nMessage      `json:"error"`
	ErrorDescription I18nMessage      `json:"error_description,omitempty"`
}
```

`ClientErrorType` surfaces as a user-facing flow error (e.g. "invalid OTP"); `ServerErrorType` surfaces as an HTTP 500. eSignet's own provider package (`internal/engine/shared/errors.go`) defines a shared vocabulary of `*common.ServiceError` values — `ClientNotFoundError`, `InvalidIndividualIDError`, `InvalidRequestError`, `AuthenticationFailedError`, `SendOTPFailedError`, `MaxOTPAttemptsReachedError`, `CertificateFetchFailed`, `AuthTokenFetchFailed`, `InternalServerError`, and others. Reuse these where they fit; define new ones in your own package's file when they don't.

## Lifecycle — call order during a login

There is no dynamic plugin loading (no `.so` files, no registry). A provider is a compile-time Go package, and the engine invokes its methods directly during flow execution. For eSignet's shipped OTP/password/biometric/KBI flow (`esignet-service/data/flows/flow-esignet.yaml`), the calls happen in this order:

```text
SendOTP           →  Authenticate        →  GetEntityReference   →  GetAttributes
(send-otp screen)    (kyc-auth: verify      (resolve/cache the      (kyc-exchange: fetch
                      the entered OTP/       "sub" claim for         only the consented
                      password/biometric)    consent + token         claims, after consent)
                                              issuance)
```

1. **`SendOTP`** — called only for OTP-based factors, when the user requests a one-time code. Generate the OTP, dispatch it (SMS/email/push), and return masked contact info for display. Not called for password/biometric/KBI factors.
2. **`Authenticate`** — called once the user submits their credential. Verify it against the identity system and return an `AuthnResult`. For each of the entity reference and the attributes, `AuthnResult` accepts *either* an opaque token (`EntityReferenceToken`/`AttributeToken` — a session ID, a KYC token, anything only your provider needs to understand) *or* the resolved value directly (`EntityReference`/`Attributes`) — the two are mutually exclusive per field.
3. **`GetEntityReference`** — the engine calls this **only if** you returned `EntityReferenceToken` (non-nil) from `Authenticate`, passing that token back to resolve the stable identifier used as the OIDC `sub` claim. If you returned `EntityReference` directly instead, the engine skips calling this method entirely and passes your value through as-is.
4. **`GetAttributes`** — likewise, the engine calls this **only if** you returned `AttributeToken` (non-nil); it's called after the user has given consent, with that token and the `RequestedAttributes` the user actually consented to (a subset of what the relying party asked for). Fetch and return those claims via `AttributesResponse.Attributes`; if your identity system also supports verified/attested claims, honor `RequestedAttributes.Verifications` and populate `AttributesResponse.Verifications` to match. If you returned `Attributes` directly from `Authenticate` instead of a token, the engine skips this call and passes that value through unchanged.

### Passkey/WebAuthn-only methods

`InitiateAuthentication`, `InitiateEnrollment`, and `Enroll` exist for the engine's built-in passkey/WebAuthn executor. None of eSignet's shipped flows use them — all three built-in providers implement them as three-line no-ops returning `nil, nil` (see [`internal/engine/mock/authenticator.go`](../esignet-service/internal/engine/mock/authenticator.go), lines 176-189, for the exact shape). Implement them for real only if you add a flow that exercises the engine's passkey/WebAuthn path.

## Configuration

Each provider owns its own configuration — there is no shared plugin-config schema. The convention is a `Config` struct plus a `LoadConfig()` function that reads environment variables directly via `os.Getenv`, with sane defaults or fail-fast validation for required values. See [`internal/engine/mock/config.go`](../esignet-service/internal/engine/mock/config.go) for the all-optional-defaults shape, or [`internal/engine/mosip/config.go`](../esignet-service/internal/engine/mosip/config.go) for one with required, fail-fast variables.

Name environment variables with a provider-specific prefix (e.g. `MOSIP_ESIGNET_MOCK_...`, `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_...`) to avoid colliding with the generic `MOSIP_ESIGNET_*` settings and other providers' variables.

## How to register a new provider

All available providers are registered and compiled into the binary at build time, in [`internal/engine/idsystem_factory.go`](../esignet-service/internal/engine/idsystem_factory.go)'s `switch`. Which one actually runs is chosen at process startup: the `MOSIP_ESIGNET_AUTHN_PROVIDER` environment variable (default `mock`) is read into `appConfig.Provider`, and that value selects the matching `case` below:

```go
func NewIDSystemProviders(appConfig *config.AppConfig, clientSvc *clientmgmt.Service,
	keyMgrSvc *keymanager.Service, sigSvc *signature.Service) (
	shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error)
```

It `switch`es on `appConfig.Provider` (`"mosip"`, `"sunbird"`, `"mock"`), calling that package's `Init(...)` and returning an error for any other value — see the file above for the exact case list.

To add a new provider:

1. Create a new package under `esignet-service/internal/engine/<yourprovider>/`, following the existing `authenticator.go` / `config.go` / `init.go` / `model.go` file layout.
2. Implement `shared.ConsolidatedAuthnProvider` on a type in `authenticator.go`.
3. Add an `Init(...) (shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error)` function in `init.go` — see [Audit Plugin Integration](audit-plugin-integration.md) for what to return as the second value if you don't have your own audit sink (return `shared.NewNoopAuditor()`).
4. Add a `case "<yourprovider>":` branch to the switch above, calling your package's `Init`.
5. Rebuild — there is no dynamic loading step.

## Reference implementations

Three providers ship in this repository, each a complete worked example:

| Provider | Package | Auth factors | Notes |
|---|---|---|---|
| Mock | [`esignet-service/internal/engine/mock`](../esignet-service/internal/engine/mock) | OTP, password, PIN, biometrics, arbitrary KBI | Talks to a mock identity system over HTTP; defaults to a plain-HTTP local address (`MOSIP_ESIGNET_MOCK_DOMAIN_URL`, configurable to HTTPS) for local development and testing, with no cryptographic envelope on the payload itself. |
| MOSIP IDA | [`esignet-service/internal/engine/mosip`](../esignet-service/internal/engine/mosip) | OTP, password, biometrics | Requests are AES-256-GCM encrypted, key-wrapped with RSA-OAEP against the IDA partner certificate, and signed as a JWT using a key from the embedded keymanager. Also ships the audit plugin — see [Audit Plugin Integration](audit-plugin-integration.md). |
| SunbirdRC | [`esignet-service/internal/engine/sunbird`](../esignet-service/internal/engine/sunbird) | Knowledge-based identity (KBI) only | Exact-match search against a Sunbird Registered Claims registry; authentication succeeds only when exactly one entity matches; claims are released only via an explicit field-mapping allow-list. |
