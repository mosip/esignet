# FAQs | eSignet

Below are frequently asked questions about eSignet.

---

## About eSignet

**What is eSignet?**

eSignet is a digital identity verification tool that simplifies access to online services. It allows users to identify themselves using various authentication methods and supports multiple forms of IDs as handles (e.g. National ID, Phone Number, Email ID, etc.).

In today's era of digital transformation, there has been a global shift towards moving most services online. To facilitate personalized access to these online services, a secure and trusted digital identity is crucial. eSignet provides a user-friendly and effective method for individuals to authenticate themselves and utilize online services while also having the option to share their profile information. eSignet supports multiple modes of identity verification to ensure inclusivity and broaden access, thereby reducing potential digital barriers.

---

**How can I use eSignet?**

You can integrate with eSignet based on the type of entity, such as an ID system, a relying party, or a digital wallet. For more details, please go through the [integration guide](https://docs.esignet.io).

If you are interested in trying out eSignet right away, you can use the sandbox for testing. Please go through the ["Try it out"](https://docs.esignet.io) section for more details.

---

**What are the various modes of authentication that eSignet supports?**

eSignet provides multiple authentication methods, as listed below:

- OTP Authentication
- Biometric Authentication
- Wallet-based Authentication
- Password-based Authentication
- Knowledge Based Identification (KBI)

For a full list of supported authentication methods, refer to the [eSignet documentation](https://docs.esignet.io).

---

**Who are the intended users of eSignet?**

The intended users of eSignet include:

- Government ID Agencies that need secure verification mechanisms to deliver services to their residents.
- Individuals or residents accessing online services.
- Businesses and/or Service Providers that require streamlined methods to authenticate beneficiaries and provide services.

---

**How scalable is eSignet? Can it handle a significant increase in user volume?**

eSignet is simple, lightweight, and powerful. The Go-based implementation compiles to a single binary with a minimal memory footprint, making horizontal scaling straightforward. It uses [Redis](https://redis.io/) as a shared session/flow state store, enabling stateless multi-instance deployments behind a load balancer. It can scale effortlessly to handle large user volumes while acting as a middle layer for identity verification.

---

**How does eSignet ensure the security and privacy of user data?**

eSignet minimizes data storage by using access tokens linked to user IDs for login, ensuring identity verification without capturing personal information. The login process occurs exclusively on the eSignet platform, with mandatory user consent through a built-in consent flow that allows users to grant or withhold explicit access to their personal information. The Go implementation adds further protections including JWE-encrypted ID tokens and userinfo responses, DPoP-bound access tokens, JTI replay prevention, and an embedded HSM-capable key manager.

---

**What technologies are used in the development of eSignet?**

The current eSignet implementation is written in **Go 1.26** and embeds the **[ThunderID](https://github.com/thunder-id/thunderid)** engine for OAuth 2.1 / OpenID Connect protocol handling. Key technologies include:

| Layer | Technology |
|---|---|
| Backend | Go 1.26, single binary |
| Authorization engine | [ThunderID](https://github.com/thunder-id/thunderid) (Go module) |
| Database | PostgreSQL 14+ |
| Session / flow store | Redis 6.0+ |
| Key storage | PKCS#11 (HSM / [SoftHSM2](https://www.opendnssec.org/softhsm/)) or PKCS#12 (file-based) |
| Frontend | React 19, TypeScript, Vite 8, Tailwind CSS v4 |
| Observability | [Prometheus](https://prometheus.io/) metrics endpoint |

---

**Why should an entity adopt eSignet?**

eSignet is an open-source, flexible solution that follows standard protocols ([OAuth 2.1](https://oauth.net/2.1/), [OpenID Connect](https://openid.net/specs/openid-connect-core-1_0.html), [FAPI 2.0](https://openid.net/specs/fapi-security-profile-2_0.html)) for easy integration and high security, ensuring no vendor lock-in. As a MOSIP product, it integrates with any trusted ID system and offers a secure, adaptable identity verification solution.

---

## Features and Functionality

**What are the core features of eSignet?**

eSignet offers user-friendly identity verification, flexible login options, and multiple secure authentication methods including OTP, biometrics, wallet-based, password, and knowledge-based authentication. It integrates with existing identity databases for eKYC compliance, supports multiple languages, and enforces explicit user consent before sharing personal information. The Go implementation adds FAPI 2.0 compliance, encrypted token responses, Prometheus metrics, and a declarative YAML-based flow configuration.

---

**What unique features does eSignet offer?**

- **Standards-based security:** [OAuth 2.1](https://oauth.net/2.1/), [OpenID Connect](https://openid.net/specs/openid-connect-core-1_0.html), [FAPI 2.0](https://openid.net/specs/fapi-security-profile-2_0.html) (PAR + DPoP + `private_key_jwt`), PKCE, JWE-encrypted responses.
- **Declarative authentication flows:** Authentication logic is defined as YAML flow graphs (`data/flows/*.yaml`) and interpreted at runtime — no code changes required to modify the login flow.
- **Multiple pluggable identity backends:** MOSIP IDA (OTP + KYC), [SunbirdRC](https://github.com/Sunbird-RC/sunbird-rc-core) KBI, and a mock backend for development/testing.
- **Embedded key manager:** Automatic key hierarchy provisioning (`ROOT`, `OIDC_SERVICE`, `OIDC_PARTNER`) with support for PKCS#11 HSMs and PKCS#12 file keystores.
- **User centricity:** Single identity credential access across services, mandatory user consent, and multiple authentication methods. Sensitive inputs are cleared between retries via the `ClearInputs` executor.
- **Flexible CAPTCHA support:** [Google reCAPTCHA](https://www.google.com/recaptcha/), [Cloudflare Turnstile](https://www.cloudflare.com/products/turnstile/), and [hCaptcha](https://www.hcaptcha.com/) are all supported.

---

**What standards does eSignet follow?**

eSignet implements the following standards:

- **[OAuth 2.1](https://oauth.net/2.1/)** and **[OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)**
- **[FAPI 2.0](https://openid.net/specs/fapi-security-profile-2_0.html)** (Financial-grade API Security Profile)
- **[RFC 9126](https://www.rfc-editor.org/rfc/rfc9126)** — Pushed Authorization Requests (PAR)
- **[RFC 9449](https://www.rfc-editor.org/rfc/rfc9449)** — DPoP (Demonstrating Proof of Possession)
- **[RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)** — Token Revocation
- **Secure Biometric Interface (SBI)** for biometric device compatibility
- **[JWE](https://www.rfc-editor.org/rfc/rfc7516) / [JWS](https://www.rfc-editor.org/rfc/rfc7515)** (RFC 7516, 7515) for encrypted and signed token responses

---

**What is ThunderID and how does it relate to eSignet?**

[ThunderID](https://github.com/thunder-id/thunderid) is an open-source Go-based OAuth 2.1 / OpenID Connect engine that eSignet embeds as a Go module dependency. It handles all protocol endpoints (authorize, token, JWKS, discovery, introspect, userinfo, revocation, PAR, DPoP, etc.) and the flow execution engine.

eSignet acts as a MOSIP-specific embedder: it injects MOSIP-aware providers (identity authentication, key management, consent storage, client registry) into the ThunderID engine via functional options, and registers its own client management API on top. This means eSignet benefits from ThunderID's protocol correctness and standards coverage while retaining full control over identity verification logic.

---

**Does eSignet support FAPI 2.0?**

Yes. The Go implementation supports the [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-security-profile-2_0.html). Clients can be configured to require:

1. **PAR** (Pushed Authorization Requests) — the client POSTs authorization parameters to `/oauth2/par` and receives a `request_uri` before redirecting the user.
2. **DPoP** — access tokens are bound to the client's private key, preventing token theft.
3. **`private_key_jwt`** client authentication — the client proves identity with a signed JWT rather than a shared secret.

Refer to the [Postman collection](https://github.com/mosip/esignet/tree/main/postman-collection) (folder "FAPI 2.0") in the repository for a working example.

---

**What is a Pushed Authorization Request (PAR)?**

PAR ([RFC 9126](https://www.rfc-editor.org/rfc/rfc9126)) is a mechanism that improves the security of the authorization code flow. Instead of passing authorization parameters as query strings in the browser redirect (which are visible in browser history and server logs), the client first POSTs them directly to the `/oauth2/par` endpoint. The server returns a short-lived `request_uri` that the client then uses in the standard `/oauth2/authorize` redirect. This prevents parameter tampering and is required for FAPI 2.0 compliance.

---

**What is DPoP?**

DPoP (Demonstrating Proof of Possession, [RFC 9449](https://www.rfc-editor.org/rfc/rfc9449)) is a mechanism that binds an access token to a specific client key pair. The client attaches a signed DPoP proof header to each request. The server verifies that the proof was signed by the same key that was used when the token was issued, making stolen tokens unusable by a third party. DPoP support is built into the [ThunderID](https://github.com/thunder-id/thunderid) engine embedded in eSignet.

---

**Does eSignet support JWE-encrypted token responses?**

Yes. Per-client JWE ([RFC 7516](https://www.rfc-editor.org/rfc/rfc7516)) encryption of the `id_token` and userinfo response can be enabled via the `additionalConfig` field in the client registration payload. When configured, the RP must possess the corresponding private key to decrypt the response.

---

## Architecture

**How is the Go-based eSignet structured?**

```
esignet-service/
  cmd/esignet/main.go    # Entrypoint: wires all providers and starts the HTTP server
  internal/
    clientmgmt/          # OAuth/OIDC client registry (PostgreSQL)
    consentmgmt/         # Consent records and audit history (PostgreSQL)
    engine/
      mosip/             # MOSIP IDA authenticator (OTP + KYC)
      sunbird/           # SunbirdRC KBI authenticator
      mock/              # Mock authenticator for development
      executors/         # Custom flow executors: OTP, AuthorizationCheck, ClearInputs
      runtimestores/     # Redis-backed or in-memory flow/session stores
    keymanager/          # Key lifecycle, PKCS#11/PKCS#12, certificate management
    security/            # Bearer-token scope enforcement, JWKS cache
    metrics/             # Prometheus metrics
  data/
    deployment.yaml      # All runtime configuration (env-var expanded)
    flows/               # Declarative YAML authentication flow definitions
    i18n/, themes/       # UI internationalization and theming assets
oidc-ui/                 # React 19 login UI
```

The [ThunderID](https://github.com/thunder-id/thunderid) engine is embedded as a Go module; `main.go` calls `thunderidengine.New(mux, ...options)` and all standard protocol endpoints are registered automatically.

---

**What changed from the Java version to the Go version?**

| Dimension | Java eSignet | Go eSignet |
|---|---|---|
| Language / runtime | Java 11 / Spring Boot | Go 1.26, single binary |
| Protocol logic | Internal Java services | Delegated to [ThunderID](https://github.com/thunder-id/thunderid) engine |
| Key management | External MOSIP keymanager microservice | Embedded Go keymanager (PKCS#11 / PKCS#12) |
| Configuration | `application.properties` / Spring Config | `data/deployment.yaml` + environment variables |
| Authentication flow | Hard-coded Java controllers | Declarative YAML flow graphs |
| Database access | Spring Data JPA / Hibernate | Raw SQL via `pgx/v5` + `sqlc` |
| FAPI 2.0 / PAR / DPoP | Partial / experimental | Built into ThunderID engine |
| CAPTCHA providers | Single provider | Google reCAPTCHA, Cloudflare Turnstile, hCaptcha |
| Metrics | Spring Actuator / Micrometer | [Prometheus](https://prometheus.io/) endpoint |
| Session store | Spring Session / Redis | Redis via `go-redis/v9` or in-memory |

---

**How does key management work in the Go version?**

eSignet embeds a Go key manager (`internal/keymanager`) that automatically provisions a three-level key hierarchy on first startup:

- `ROOT` — the root CA key
- `OIDC_SERVICE` — the signing key for ID tokens and JWKS
- `OIDC_PARTNER` — per-partner signing/encryption keys

Two backends are supported, selected at build time:

- **PKCS#11** (default production build): uses a hardware HSM or [SoftHSM2](https://www.opendnssec.org/softhsm/). Requires CGO and the PKCS#11 shared library.
- **PKCS#12** (default dev build): keys are stored in an encrypted `.p12` file on disk. No native dependencies required.

Certificate upload/download is available at `/system-info/certificate` and `/system-info/uploadCertificate`.

---

**How are authentication flows defined in the Go version?**

Authentication logic is expressed as declarative YAML flow graphs stored in `data/flows/`. The main flow file is `flow-esignet.yaml`. Each flow is a directed graph of named nodes; each node calls a registered executor.

eSignet registers three custom executors in addition to the 30+ built-in [ThunderID](https://github.com/thunder-id/thunderid) executors:

| Executor | Purpose |
|---|---|
| `eSignetOtpExecutor` | Dispatches OTP via the selected IDA backend (MOSIP, SunbirdRC, mock) |
| `AuthorizationExecutor` | Validates the OIDC client and requested scopes |
| `ClearInputsExecutor` | Clears sensitive user inputs between authentication retries |

The YAML flow supports branching (OTP, password, biometric, KBI sub-flows), looping (re-prompting on failed consent), and convergence before the final authorization assertion.

---

## Partner Integrations

**Can you provide examples of successful integrations with potential partners?**

eSignet has been integrated or is in active integration across several domains:

- **Health Management:** Integration complete with OTP and biometric-based authentication for seamless access to health services.
- **SuperApp Integration:** Multi-service SuperApp integration for registration, login, and enhanced eKYC.
- **Insurance Portal:** Integration using secure authentication with quick access to insurance services; uses [SunbirdRC](https://github.com/Sunbird-RC/sunbird-rc-core) KBI for knowledge-based identification.
- **University Authentication:** Face authentication of students and staff for access to exams, hostel assignments, and meal identification.
- **Government and Private Services:** MOSIP brownfield implementation with eSignet authenticating users across government and private services.
- **Self-Service Portal for Benefits Delivery:** Integration with [OpenG2P](https://www.openg2p.org/) for resident authentication via National ID and benefits registration.

---

## Configuration and Setup

**Which version of eSignet should I use?**

The current Go-based implementation is pinned against [ThunderID](https://github.com/thunder-id/thunderid) `v0.0.0-20260822180739`. Please refer to the `go.mod` file and the [GitHub releases page](https://github.com/mosip/esignet/releases) for the latest versioned release.

---

**Where can I access the source code?**

You can access the source code from the [eSignet GitHub repository](https://github.com/mosip/esignet). The `esignet-service/` directory contains the Go backend; `oidc-ui/` contains the React frontend.

---

**Is there documentation available for setting up eSignet locally?**

Yes. A `docker-compose/` directory is provided with a `docker-compose.yaml` that spins up PostgreSQL and Redis. Refer to the [README](https://github.com/mosip/esignet) at the repository root for step-by-step local setup instructions including building the PKCS#12 (dev) binary.

---

**How is eSignet configured in the Go version?**

All runtime configuration is in `esignet-service/data/deployment.yaml`. Environment variables are expanded inline using `${ENV_VAR_NAME}` syntax. Key configuration sections include:

```yaml
server:
  port: 8088

issuer: "https://esignet.example.org"

oauth:
  token:
    accessTokenExpiry: 3600
    idTokenExpiry: 3600
    refreshTokenExpiry: 86400

db:
  host: "${DB_HOST}"
  port: "${DB_PORT}"
  name: "${DB_NAME}"
  username: "${DB_USERNAME}"
  password: "${DB_PASSWORD}"

redis:
  host: "${REDIS_HOST}"
  port: "${REDIS_PORT}"
```

For local development you can override any value by setting the corresponding environment variable before running the binary.

---

**How to configure password authentication in the Go version?**

Password authentication is enabled by including the ACR value `mosip:idp:acr:password` in the `authContextRefs` array when creating or updating a client via the `/client-mgmt/oidc-client` API.

In `deployment.yaml`, ensure the `password` authentication mode is listed in the flow definition and that the MOSIP IDA (or mock) backend is configured to accept password credentials. No separate ACR-AMR mapping file is required — the mapping is handled within the YAML flow graph (`flow-esignet.yaml`). Refer to the [eSignet API documentation](https://docs.esignet.io) for the full client registration payload schema.

---

**How to add a new language in eSignet?**

The React-based `oidc-ui` uses [ISO 639-1](https://www.iso.org/iso-639-language-codes.html) language codes for localization. To add a new language:

1. Go to `oidc-ui/public/locales/`.
2. Copy `en.json` and rename it with the ISO 639-1 language code (e.g. `fr.json` for French).
3. Translate the values in the new file.
4. Update `default.json` to register the new language:

```json
{
  "languages_2Letters": {
    "en": "English",
    "fr": "Français"
  },
  "rtlLanguages": ["ar"],
  "langCodeMapping": {
    "eng": "en",
    "fra": "fr"
  }
}
```

If the language is RTL, add its ISO 639-1 code to the `rtlLanguages` array.

For production, update the same files in your i18n bundle artifact and redeploy the `oidc-ui` container.

---

**How to remove a language from the eSignet default setup?**

1. Delete the language's JSON file (e.g. `fr.json`) from `oidc-ui/public/locales/`.
2. Remove its entry from `default.json`.

Rebuild and redeploy the `oidc-ui` container for the change to take effect in production.

---

**How to integrate wallets with eSignet?**

Wallet configuration in the Go version is provided as environment variables consumed by the React `oidc-ui` at build time. Set the following in the `oidc-ui` `.env` file or as container environment variables:

```
VITE_WALLET_NAME=Inji
VITE_WALLET_LOGO_URL=inji_logo.png
VITE_WALLET_DOWNLOAD_URI=https://example.org/inji
VITE_WALLET_DEEP_LINK_URI=inji://landing-page?linkCode=LINK_CODE&linkExpireDateTime=LINK_EXPIRE_DT
```

Multiple wallets can be configured by adding additional numbered environment variable sets. Rebuild the `oidc-ui` container after making changes. For wallet integration using the Inji wallet, refer to the [Inji documentation](https://docs.inji.io) for the deep-link URI format.

---

**How to configure the expected quality score, timeouts, and number of biometric attributes?**

These are React `oidc-ui` build-time environment variables. Set them in `oidc-ui/.env` or pass them as container environment variables:

```
# Quality score thresholds (0–100)
VITE_SBI_FACE_CAPTURE_SCORE=70
VITE_SBI_FINGER_CAPTURE_SCORE=70
VITE_SBI_IRIS_CAPTURE_SCORE=70

# Number of biometric subtypes to capture
VITE_SBI_FACE_CAPTURE_COUNT=1
VITE_SBI_FINGER_CAPTURE_COUNT=1
VITE_SBI_IRIS_CAPTURE_COUNT=1

# Timeouts in seconds
VITE_SBI_CAPTURE_TIMEOUT=30
VITE_SBI_DINFO_TIMEOUT=30
VITE_SBI_DISC_TIMEOUT=30
```

Note: Variable names use the `VITE_` prefix ([Vite](https://vite.dev/)) instead of the `REACT_APP_` prefix used in the previous CRA-based UI.

---

**How to enable or disable CAPTCHA in eSignet UI?**

CAPTCHA configuration in the Go version is set in `deployment.yaml`. Three providers are supported:

```yaml
captcha:
  required: true
  provider: "recaptcha"   # Options: recaptcha | turnstile | hcaptcha
  siteKey: "${CAPTCHA_SITE_KEY}"
  secretKey: "${CAPTCHA_SECRET_KEY}"
```

Set `required: false` to disable CAPTCHA entirely. The supported providers are:

- **`recaptcha`** — [Google reCAPTCHA](https://www.google.com/recaptcha/)
- **`turnstile`** — [Cloudflare Turnstile](https://www.cloudflare.com/products/turnstile/)
- **`hcaptcha`** — [hCaptcha](https://www.hcaptcha.com/)

---

**How to configure Redis for session storage?**

[Redis](https://redis.io/) is used as the shared flow/session state store and is required for multi-instance deployments. Configure it in `deployment.yaml`:

```yaml
redis:
  host: "${REDIS_HOST}"
  port: "${REDIS_PORT}"
  password: "${REDIS_PASSWORD}"
  db: 0
  tlsEnabled: false
```

For single-instance development setups, Redis can be replaced with the in-memory runtime store by setting `runtimeStore.type: memory` in `deployment.yaml`. This setting is not suitable for production as state is lost on restart.

---

**How to configure PKCS#11 / HSM key storage?**

To use a hardware HSM or [SoftHSM2](https://www.opendnssec.org/softhsm/) in production:

1. Build the binary with the `pkcs11` build tag: `go build -tags pkcs11 ./cmd/esignet`.
2. Configure the PKCS#11 provider in `deployment.yaml`:

```yaml
keymanager:
  backend: pkcs11
  pkcs11:
    library: "/usr/lib/softhsm/libsofthsm2.so"
    tokenLabel: "esignet"
    pin: "${HSM_PIN}"
```

For development without HSM, use the default PKCS#12 (file-based) backend:

```yaml
keymanager:
  backend: pkcs12
  pkcs12:
    path: "/etc/esignet/keystore.p12"
    password: "${KEYSTORE_PASSWORD}"
```

On first startup, the key hierarchy (`ROOT`, `OIDC_SERVICE`, `OIDC_PARTNER`) is provisioned automatically.

---

**How to register or create a client ID in eSignet?**

In order to utilize eSignet for authenticating users and obtaining their information, relying parties are required to:

1. Register as a client in the eSignet system via the `/client-mgmt/oidc-client` API. The API is secured with a bearer token scoped to `client_management`. A client registration payload specifies the client name, redirect URIs, allowed scopes, grant types, token endpoint authentication method (`private_key_jwt` recommended), and any ACR values required. Refer to the [eSignet documentation](https://docs.esignet.io) for full payload details.
2. Integrate with eSignet APIs, following the guidelines provided by [OpenID Connect](https://openid.net/specs/openid-connect-core-1_0.html), on their web or mobile applications.

For MOSIP-integrated environments, relying parties are Auth partners and must complete partner onboarding on the [MOSIP PMS portal](https://docs.mosip.io) before calling the client management API.

---

**How is a relying party onboarded to eSignet integrated with MOSIP?**

Relying parties are considered Auth partners in MOSIP and must complete [authentication partner onboarding](https://docs.mosip.io) before registering a client:

- **Self Onboarding:** Partners register directly on the [MOSIP PMS portal](https://docs.mosip.io).
- **Assisted Onboarding:** Partners fill out the onboarding form; credentials are sent via email.

Once onboarded, partners call the `/client-mgmt/oidc-client` API with their partner certificate to register an OIDC client.

---

**How to configure Knowledge Based Identification (KBI) with SunbirdRC?**

The [SunbirdRC](https://github.com/Sunbird-RC/sunbird-rc-core) KBI authenticator (`internal/engine/sunbird/`) identifies users by matching fields from the KBI form against records in a SunbirdRC registry. The fields displayed in the KBI form are driven by the registry schema. If more than one registry entry matches the provided details, authentication is denied.

Configure the SunbirdRC backend in `deployment.yaml`:

```yaml
authnProvider:
  type: sunbird
  sunbird:
    registryUrl: "${SUNBIRD_REGISTRY_URL}"
    entity: "InsuranceMember"
    kbiFields: ["fullName", "dob", "policyNumber"]
```

The current compatible SunbirdRC version is [v2.0.0-rc3](https://github.com/Sunbird-RC/sunbird-rc-core/releases).

---

**How to configure a VC issuer in eSignet?**

Verifiable Credential issuance ([OpenID4VCI](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)) is implemented in the [ThunderID](https://github.com/thunder-id/thunderid) engine that eSignet embeds, but is not wired into eSignet's current provider configuration by default. For production VC issuance, refer to **[Inji Certify](https://docs.inji.io)**, which provides a dedicated OpenID4VCI-compliant issuer service.

To enable VC issuance directly through the ThunderID engine in a custom deployment, configure a credential definition under `vc.credentials` in `deployment.yaml` and implement the `VCIssuerProvider` interface.

Note: Verifiable Credentials Issuance (VCI) in production environments is now recommended via [Inji Certify](https://docs.inji.io).

---

**Where can I find Prometheus metrics for eSignet?**

The Go binary exposes a [Prometheus](https://prometheus.io/)-compatible metrics endpoint. By default it is available at:

```
GET /metrics
```

Key metrics include active flow sessions, token issuance counts, authentication attempt counts by method and outcome, and key manager operation latencies. Configure scraping in your Prometheus `prometheus.yml` or via a Kubernetes `ServiceMonitor`. For a reference Kubernetes setup, see the [Helm charts](https://github.com/mosip/esignet/tree/main/helm) in the repository.

---

*Last updated August 2026*

*Copyright © 2021 MOSIP. This work is licensed under a [Creative Commons Attribution (CC-BY-4.0) International License](https://creativecommons.org/licenses/by/4.0/) unless otherwise noted.*
