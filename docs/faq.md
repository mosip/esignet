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
- Password-based Authentication
- Knowledge-Based Identification (KBI)

For a full list of supported authentication methods, refer to the [eSignet documentation](https://docs.esignet.io).

---

**Who are the intended users of eSignet?**

The intended users of eSignet include:

- Government ID Agencies that need secure verification mechanisms to deliver services to their residents.
- Individuals or residents accessing online services.
- Businesses and/or Service Providers that require streamlined methods to authenticate beneficiaries and provide services.

---

**How scalable is eSignet? Can it handle a significant increase in user volume?**

eSignet is simple, lightweight, and powerful. The Go-based implementation compiles to a single binary with a minimal memory footprint, making horizontal scaling straightforward. It uses [Redis](https://redis.io/) as a shared OIDC transaction and flow state store, enabling stateless multi-instance deployments behind a load balancer. It can scale effortlessly to handle large user volumes while acting as a middle layer for identity verification.

For capacity planning, refer to the [performance test module](https://github.com/mosip/esignet/tree/develop-go/performance-test) in the repository. It ships JMeter scripts and a TPS thread-setting calculator (`MOSIP_TPS_Thread_setting_calculator-ESignet.xlsx`, based on Little's law) to estimate the required threads and sustainable throughput for a target TPS. Published benchmark reports are available in the [MOSIP documentation](https://docs.mosip.io).

---

**How does eSignet ensure the security and privacy of user data?**

eSignet applies several data-minimization and data-protection controls to limit exposure of personal information:

- **Data minimization:** eSignet issues access tokens tied to user identifiers and releases only the claims explicitly requested and consented to by the user. Authentication inputs (OTP, biometric, KBI fields) are processed in-flight and are not persisted by eSignet.
- **Consent:** The login process occurs exclusively on the eSignet platform. A built-in consent flow requires users to explicitly grant or withhold access to each requested claim before any information is shared with a relying party. Consent decisions are recorded with an expiry and can be withdrawn.
- **Protected data flow:** The Go implementation enforces JWE-encrypted ID tokens and userinfo responses (configured per client), DPoP-bound access tokens when enabled per client via `additionalConfig.dpop_bound_access_tokens` (preventing token replay by a different client), and JTI replay prevention on incoming signed assertions. All signing and encryption keys are managed by the embedded Go keymanager, configured via `KEYMANAGER_*` environment variables, with optional HSM (PKCS#11) backing.

---

**What technologies are used in the development of eSignet?**

For a complete breakdown of the technology stack used in the Go-based eSignet implementation, refer to the [Technology Stack](technology-stack-go.md) document.

---

**Why should an entity adopt eSignet?**

eSignet is an open-source, flexible solution that follows standard protocols ([OAuth 2.1](https://oauth.net/2.1/), [OpenID Connect](https://openid.net/specs/openid-connect-core-1_0.html), [FAPI 2.0](https://openid.net/specs/fapi-security-profile-2_0.html)) for easy integration and high security, ensuring no vendor lock-in. As a MOSIP product, it integrates with any trusted ID system and offers a secure, adaptable identity verification solution.

---

## Features and Functionality

**What unique features does eSignet offer?**

- **Standards-based security:** [OAuth 2.1](https://oauth.net/2.1/), [OpenID Connect](https://openid.net/specs/openid-connect-core-1_0.html), [FAPI 2.0](https://openid.net/specs/fapi-security-profile-2_0.html) (PAR + DPoP + `private_key_jwt`), PKCE, JWE-encrypted responses.
- **Declarative authentication flows:** Authentication logic is defined as YAML flow graphs (`data/flows/*.yaml`) and interpreted at runtime — no code changes required to modify the login flow.
- **Multiple pluggable identity backends:** MOSIP IDA (OTP + KYC), [SunbirdRC](https://github.com/Sunbird-RC/sunbird-rc-core) KBI, and a mock backend for development/testing.
- **Embedded key manager:** Automatic two-level key hierarchy provisioning (`OIDC_SERVICE`, `OIDC_PARTNER`) with support for PKCS#11 HSMs and PKCS#12 file keystores.
- **User centricity:** Single identity credential access across services, mandatory user consent, and multiple authentication methods.
- **Flexible CAPTCHA support:** [Google reCAPTCHA](https://www.google.com/recaptcha/), [Cloudflare Turnstile](https://www.cloudflare.com/products/turnstile/), and [hCaptcha](https://www.hcaptcha.com/) are all supported.

---

**What standards does eSignet follow?**

eSignet implements the following standards:

- **[OAuth 2.1](https://oauth.net/2.1/)** and **[OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)**
- **[FAPI 2.0](https://openid.net/specs/fapi-security-profile-2_0.html)** (Financial-grade API Security Profile)
- **[RFC 9126](https://www.rfc-editor.org/rfc/rfc9126)** — Pushed Authorization Requests (PAR)
- **[RFC 9449](https://www.rfc-editor.org/rfc/rfc9449)** — DPoP (Demonstrating Proof of Possession)
- **Secure Biometric Interface (SBI)** for biometric device compatibility
- **[JWE](https://www.rfc-editor.org/rfc/rfc7516) / [JWS](https://www.rfc-editor.org/rfc/rfc7515)** (RFC 7516, 7515) for encrypted and signed token responses

---

**What is ThunderID and how does it relate to eSignet?**

[ThunderID](https://github.com/thunder-id/thunderid) is an open-source Go-based OAuth 2.1 / OpenID Connect engine that eSignet embeds as a Go module dependency. It handles all protocol endpoints (authorize, token, JWKS, discovery, introspect, userinfo, PAR, DPoP, etc.) and the flow execution engine.

eSignet acts as a MOSIP-specific embedder: it injects MOSIP-aware providers (identity authentication, key management, consent storage, client registry) into the ThunderID engine via functional options, and registers its own client management API on top. This means eSignet benefits from ThunderID's protocol correctness and standards coverage while retaining full control over identity verification logic.

---

**Does eSignet support FAPI 2.0?**

Yes. The Go implementation supports the [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-security-profile-2_0.html). FAPI 2.0 requirements are enforced per-client via the `additionalConfig` object in the `POST /client-mgmt/client` registration payload:

```json
{
  "additionalConfig": {
    "require_pushed_authorization_requests": true,
    "dpop_bound_access_tokens": true,
    "require_pkce": true
  }
}
```

- `require_pushed_authorization_requests: true` — forces the client to POST authorization parameters to `POST /oauth2/par` first and use the returned `request_uri` in the subsequent `GET /oauth2/authorize` redirect.
- `dpop_bound_access_tokens: true` — rejects any token request from this client that does not include a valid `DPoP` proof header.
- `clientAuthMethods: ["private_key_jwt"]` — the client authenticates at the token endpoint using a signed JWT rather than a shared secret.

Refer to the [Postman collection](https://github.com/mosip/esignet/tree/master/postman-collection) (folder "FAPI 2.0") in the repository for a working example.

---

**Does eSignet support JWE-encrypted token responses?**

Yes. Per-client JWE ([RFC 7516](https://www.rfc-editor.org/rfc/rfc7516)) encryption is configured in two steps:

1. **Set the response type** in the `additionalConfig` object when registering via `POST /client-mgmt/client`:

   ```json
   {
     "additionalConfig": {
       "userinfo_response_type": "JWE",
       "id_token_response_type": "JWE"
     }
   }
   ```

2. **Register the encryption public key** via `PATCH /client-mgmt/client/{client_id}` using the `encPublicKey` field. Both RSA (`RSA-OAEP-256`, `RSA-OAEP`) and EC (`ECDH-ES`, `ECDH-ES+A128KW`, etc.) keys are supported. Setting `encPublicKey` to `null` clears the key. The signing public key (`publicKey`) set at registration cannot be changed; if it is compromised, create a new client.

When JWE is active, the RP must possess the corresponding private key to decrypt the `id_token` and userinfo response.

---

## Architecture

**How is the Go-based eSignet structured?**

For a detailed breakdown of the project structure, refer to the [esignet-service README](../esignet-service/README.md).

The [ThunderID](https://github.com/thunder-id/thunderid) engine is embedded as a Go module; `main.go` calls `thunderidengine.New(mux, ...options)` and all standard protocol endpoints are registered automatically.

---

**What changed from the Java version to the Go version?**

| Dimension | Java eSignet | Go eSignet |
|---|---|---|
| Language / runtime | Java 11 / Spring Boot | Go 1.26, single binary |
| Protocol logic | Internal Java services | Delegated to [ThunderID](https://github.com/thunder-id/thunderid) engine |
| Key management | MOSIP keymanager (Java microservice) | Embedded Go keymanager (`KEYMANAGER_*` env vars) |
| Configuration | `application.properties` / Spring Config | `data/deployment.yaml` + environment variables |
| Authentication flow | Hard-coded Java controllers | Declarative YAML flow graphs |
| Database access | Spring Data JPA / Hibernate | Raw SQL via `pgx/v5` + `sqlc` |
| Metrics | Spring Actuator / Micrometer | [Prometheus](https://prometheus.io/) endpoint |
| Transaction store | Redis or in-memory | Redis or in-memory |

---

**How does key management work in the Go version?**

The Go version ships an **embedded Go keymanager** — no separate Java microservice is required. It is configured exclusively via `KEYMANAGER_*` environment variables and automatically provisions a two-level key hierarchy on first startup:

- `OIDC_SERVICE` — the signing key for ID tokens and JWKS
- `OIDC_PARTNER` — per-partner signing/encryption keys

Two backends are supported, selected at runtime via `KEYMANAGER_KEYSTORE_TYPE`:

- **PKCS#11** (production builds with CGO enabled): uses a hardware HSM or [SoftHSM2](https://www.opendnssec.org/softhsm/). Requires a CGO-enabled binary and the PKCS#11 shared library.
- **PKCS#12** (default dev builds, CGO disabled): keys are stored in an encrypted `.p12` file on disk. No native dependencies required.

Certificate upload/download is available at `/system-info/certificate` and `/system-info/uploadCertificate`.

---

**How are authentication flows defined in the Go version?**

Authentication logic is expressed as declarative YAML flow graphs stored in `data/flows/`. The main flow file is `flow-esignet.yaml`. Each flow is a directed graph of named nodes; each node calls a registered executor.

eSignet registers two custom executors in addition to the 30+ built-in [ThunderID](https://github.com/thunder-id/thunderid) executors:

| Executor | Purpose |
|---|---|
| `eSignetOtpExecutor` | Dispatches OTP via the selected IDA backend (MOSIP, SunbirdRC, mock) |
| `ClearInputsExecutor` | Clears sensitive user inputs between authentication retries |

The YAML flow supports branching (OTP, password, biometric, KBI sub-flows), looping (re-prompting on failed consent), and convergence before the final authorization assertion.

---

## Configuration and Setup

**Which version of eSignet should I use?**

Always use the latest GA (Generally Available) release of eSignet for the best security, features, and performance. Refer to the [GitHub releases page](https://github.com/mosip/esignet/releases) for the latest versioned release. If you are an existing eSignet user, use the upgrade scripts provided in the repository to migrate to a newer version.

---

**Where can I access the source code?**

You can access the source code from the [eSignet GitHub repository](https://github.com/mosip/esignet). The `esignet-service/` directory contains the Go backend; `oidc-ui/` contains the React frontend.

---

**Is there documentation available for setting up eSignet locally?**

Yes. A `docker-compose/` directory is provided with a `docker-compose.yaml` that spins up PostgreSQL and Redis. Refer to the [README](https://github.com/mosip/esignet) at the repository root for step-by-step local setup instructions.

---

**How is eSignet configured in the Go version?**

Runtime configuration spans several sources:

- **`esignet-service/data/deployment.yaml`** — core server, database, Redis, OAuth, and issuer settings. Environment variables are expanded inline using `${ENV_VAR_NAME}` syntax.
- **`data/flows/*.yaml`** — declarative authentication flow graphs (e.g. `flow-esignet.yaml`), which define login logic and executor sequences.
- **`KEYMANAGER_*` environment variables** — keystore backend selection (`KEYMANAGER_KEYSTORE_TYPE`), PKCS#11 module path/PIN, or PKCS#12 file path/password.
- **CAPTCHA variables** (e.g. `MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL`) — endpoint and credentials for server-side CAPTCHA token validation.
- **`oidc-ui` configuration** — frontend environment variables with the `VITE_` prefix, set during the `oidc-ui` build or via its deployment configuration.

Key sections in `deployment.yaml` include:

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

Environment variable overrides apply only to values declared with `${ENV_VAR_NAME}` placeholders in the YAML (e.g. `host: "${REDIS_HOST}"`). Literal values such as `server.port`, `issuer`, and token expiries must be changed directly in the YAML file or via a Helm values override.

---

**How to configure password authentication in the Go version?**

Two conditions must be satisfied:

1. **Register the password ACR on the client:** include the ACR value `mosip:idp:acr:password` in the `authContextRefs` array when creating or updating a client via the `/client-mgmt/client` API.
2. **The integrated ID system must support password-based authentication:** the configured identity backend (MOSIP IDA, SunbirdRC, or mock) must be able to verify the resident's password credential.

No separate ACR-AMR mapping file is required — the mapping is handled within the YAML flow graph (`flow-esignet.yaml`). Refer to the [eSignet API documentation](https://docs.esignet.io) for the full client registration payload schema.

---

**How to add a new language in eSignet?**

Localization strings live in the eSignet service data directory at [`esignet-service/data/i18n/`](https://github.com/mosip/esignet/tree/develop-go/esignet-service/data/i18n), with one YAML file per language named using its [ISO 639-1](https://www.iso.org/iso-639-language-codes.html) code (e.g. `en.yaml`, `fr.yaml`). The service auto-discovers the available languages by scanning this folder, so there is no separate registration file. To add a new language:

1. Go to `esignet-service/data/i18n/` (the folder resolved from `DATA_DIR`).
2. Copy `en.yaml` and rename it with the ISO 639-1 language code (e.g. `fr.yaml` for French).
3. Translate the values in the new file, keeping the top-level namespace keys unchanged.
4. Restart or redeploy the eSignet service so the new file is picked up. Requests then resolve via BCP47 matching (for example, `fr-FR` falls back to `fr`), with `en` as the final fallback.

---

**How to remove a language from the eSignet default setup?**

1. Delete the language's YAML file (e.g. `fr.yaml`) from `esignet-service/data/i18n/`.
2. Restart or redeploy the eSignet service so the language is no longer listed.

---

**How to configure the expected quality score, timeouts, and number of biometric attributes?**

These SBI capture parameters are passed to the [`@mosip/secure-biometric-interface-integrator`](https://www.npmjs.com/package/@mosip/secure-biometric-interface-integrator) widget by the `oidc-ui` React app. They are currently defined as the `DEFAULT_SBI_ENV` defaults in [`oidc-ui/src/components/SbiComponent/SbiComponent.tsx`](https://github.com/mosip/esignet/blob/develop-go/oidc-ui/src/components/SbiComponent/SbiComponent.tsx) and are **not** overridable via environment variables:

```ts
const DEFAULT_SBI_ENV = {
  env: "Staging",
  captureTimeout: 30,
  faceCaptureCount: 1,
  faceCaptureScore: 80,
  fingerCaptureCount: 1,
  fingerCaptureScore: 80,
  irisCaptureCount: 1,
  irisCaptureScore: 80,
  portRange: "4501-4600",
  discTimeout: 15,
  dinfoTimeout: 30,
  // ...
};
```

To change the quality-score thresholds (0–100), capture counts, or timeouts (in seconds), edit this object and rebuild/redeploy the `oidc-ui` container.

---

**How to enable or disable CAPTCHA in eSignet UI?**

CAPTCHA is wired into the authentication flow, not `deployment.yaml`. The `captcha` block lives in the flow definition [`esignet-service/data/flows/flow-esignet.yaml`](https://github.com/mosip/esignet/blob/develop-go/esignet-service/data/flows/flow-esignet.yaml), and its values are supplied through environment variables (see [`.env.example`](https://github.com/mosip/esignet/blob/develop-go/esignet-service/.env.example)):

```dotenv
# Provider shown by the UI and its public site key
MOSIP_ESIGNET_CAPTCHA_SITE_PROVIDER=hcaptcha   # e.g. recaptcha | turnstile | hcaptcha
MOSIP_ESIGNET_CAPTCHA_SITE_KEY=<public-site-key>

# Server-side token validation (skipped when the URL is unset)
MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL=https://<captcha-service-host>/v1/captcha/validatecaptcha
# Use http:// only for isolated local development (no outbound HTTPS available)
MOSIP_ESIGNET_CAPTCHA_MODULE_NAME=esignet
MOSIP_ESIGNET_CAPTCHA_TIMEOUT_SECS=10
```

To disable CAPTCHA entirely, remove the `CAPTCHA_BOX` node reference from the relevant steps in `flow-esignet.yaml`. Do **not** leave `MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL` unset in production — omitting it causes CAPTCHA tokens to be accepted without server-side verification, which defeats bot protection. Leaving the URL unset is only acceptable for isolated local development where no CAPTCHA service is reachable. The providers selectable via `MOSIP_ESIGNET_CAPTCHA_SITE_PROVIDER` are:

- **`recaptcha`** — [Google reCAPTCHA](https://www.google.com/recaptcha/)
- **`turnstile`** — [Cloudflare Turnstile](https://www.cloudflare.com/products/turnstile/)
- **`hcaptcha`** — [hCaptcha](https://www.hcaptcha.com/)

---

**How to configure Redis for OIDC transaction storage?**

[Redis](https://redis.io/) is used as the shared OIDC transaction and flow state store and is required for multi-instance deployments. Configure it in `deployment.yaml`:

```yaml
redis:
  host: "${REDIS_HOST}"
  port: "${REDIS_PORT}"
  password: "${REDIS_PASSWORD}"
  db: 0
  tls: true   # set to false only for isolated local development; always true for production
```

Redis is selected by setting `MOSIP_ESIGNET_CACHE_TYPE=redis`. For single-instance development setups, use the in-memory runtime store instead by setting `MOSIP_ESIGNET_CACHE_TYPE=inmemory` (any value other than `redis` selects the in-memory store). This is not suitable for production as state is lost on restart.

---

**How to configure PKCS#11 / HSM key storage?**

Keystore selection is a **runtime** setting driven by `KEYMANAGER_*` environment variables (read by the keymanager at startup), not a `deployment.yaml` block. The PKCS#11 backend additionally requires a CGO-enabled binary, since it links a native PKCS#11 module.

To use a hardware HSM or [SoftHSM2](https://www.opendnssec.org/softhsm/) in production, build with CGO enabled (see `make.sh`) and set:

```dotenv
# PKCS#11 requires a CGO_ENABLED=1 build
KEYMANAGER_KEYSTORE_TYPE=PKCS11
KEYMANAGER_PKCS11_MODULE_PATH=/usr/lib/softhsm/libsofthsm2.so
KEYMANAGER_PKCS11_TOKEN_LABEL=esignet
KEYMANAGER_PKCS11_SLOT_ID=<slot-id>
KEYMANAGER_PKCS11_PIN=${HSM_PIN}
```

For development without an HSM, use the file-based PKCS#12 backend (the only backend available in the default `CGO_ENABLED=0` build):

```dotenv
KEYMANAGER_KEYSTORE_TYPE=PKCS12
KEYMANAGER_PKCS12_FILE_PATH=/opt/mosip/keystore.p12
KEYMANAGER_PKCS12_PASSWORD=${KEYSTORE_PASSWORD}
KEYMANAGER_PKCS12_ALLOW_INSECURE_SOFTWARE_KEYSTORE=true
```

On first startup, the two-level key hierarchy (`OIDC_SERVICE`, `OIDC_PARTNER`) is provisioned automatically.

---

**How to register or create a client ID in eSignet?**

In order to utilize eSignet for authenticating users and obtaining their information, relying parties are required to:

1. Register as a client in the eSignet system using one of the client management endpoints below. All endpoints require a bearer token (`Authorization: Bearer <token>`) carrying the appropriate scope.
2. Integrate with eSignet APIs, following the guidelines provided by [OpenID Connect](https://openid.net/specs/openid-connect-core-1_0.html), on their web or mobile applications.

The Go implementation exposes three registration profiles:

| Method | Endpoint | Profile | Scope required |
|---|---|---|---|
| `POST` | `/client-mgmt/client` | Generic — **recommended for new integrations** | `client_mgmt_write` |
| `PUT` | `/client-mgmt/client/{client_id}` | Generic — full update | `client_mgmt_write` |
| `PATCH` | `/client-mgmt/client/{client_id}` | Generic — partial update | `client_mgmt_write` |
| `GET` | `/client-mgmt/client/{client_id}` | Generic — fetch | `client_mgmt_read` |
| `POST` | `/client-mgmt/oidc-client` | OIDC profile (legacy compat) | `client_mgmt_write` |
| `PUT` | `/client-mgmt/oidc-client/{client_id}` | OIDC profile — full update | `client_mgmt_write` |
| `POST` | `/client-mgmt/oauth-client` | OAuth profile | `client_mgmt_write` |
| `PUT` | `/client-mgmt/oauth-client/{client_id}` | OAuth profile — full update | `client_mgmt_write` |

Use `/client-mgmt/client` for all new integrations. The `/client-mgmt/oidc-client` endpoint is retained for backward compatibility with existing Java-era integrations. Refer to the [eSignet documentation](https://docs.esignet.io) for the full registration payload schema.

For MOSIP-integrated environments, relying parties are Auth partners and must complete partner onboarding on the [MOSIP PMS portal](https://docs.mosip.io) before calling the client management API.

---

**How is a relying party onboarded to eSignet integrated with MOSIP?**

Relying parties are considered Auth partners in MOSIP and must complete [authentication partner onboarding](https://docs.mosip.io) before registering a client:

- **Self-service onboarding:** Partners self-register on the [MOSIP PMS portal](https://docs.mosip.io).
- **Onboarder script:** Partners are provisioned using the [partner-onboarder](https://github.com/mosip/esignet/tree/develop-go/partner-onboarder) script bundled in the repository.

When onboarding through MOSIP PMS, PMS invokes the `/client-mgmt/client` endpoint directly as part of the partner and policy configuration — partners do not call it themselves. In standalone (non-MOSIP) deployments, the client is registered by calling the `/client-mgmt/client` API (or the profile-specific `/client-mgmt/oidc-client` for backward compatibility) with a bearer token scoped to `client_mgmt_write`.

---

**How to configure Knowledge-Based Identification (KBI) with SunbirdRC?**

The [SunbirdRC](https://github.com/Sunbird-RC/sunbird-rc-core) KBI authenticator (`internal/engine/sunbird/`) identifies users by matching fields from the KBI form against records in a SunbirdRC registry. The fields displayed in the KBI form are driven by the registry schema. If more than one registry entry matches the provided details, authentication is denied.

Configure the SunbirdRC backend through environment variables (see [`.env.example`](https://github.com/mosip/esignet/blob/develop-go/esignet-service/.env.example)):

```dotenv
MOSIP_ESIGNET_AUTHN_PROVIDER=sunbird
MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL=https://registry.example.net/api/v1/Insurance/search
MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL=https://registry.example.net/api/v1/Insurance/
MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD=policyNumber
MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_KBI_ENTITY_ID_FIELD=osid
MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_FIELD_DETAILS=[{"id":"policyNumber","type":"text","format":""},{"id":"fullName","type":"text","format":""},{"id":"dob","type":"date","format":"dd/mm/yyyy"}]
```

The `KBI_FIELD_DETAILS` entries define the fields shown on the KBI form; the `individual-id-field` entry is the identifier and every other entry is a required credential.

eSignet has been tested with SunbirdRC [v2.0.0-rc3](https://github.com/Sunbird-RC/sunbird-rc-core/releases/tag/v2.0.0-rc3). Verify compatibility before upgrading to a newer SunbirdRC release.

---

**Where can I find Prometheus metrics for eSignet?**

The Go binary exposes a [Prometheus](https://prometheus.io/)-compatible metrics endpoint on a **separate private listener** — not the main application port and not routed through the public gateway/ingress. It is only reachable within the cluster (for example, by Prometheus). The listener defaults to port `9090` and is configurable via the `METRICS_PORT` environment variable (or `metrics_port` in `deployment.yaml`):

```http
GET http://<host>:9090/metrics
```

Key metrics include active OIDC transactions, token issuance counts, authentication attempt counts by method and outcome, and key manager operation latencies. Configure scraping in your Prometheus `prometheus.yml` or via a Kubernetes `ServiceMonitor`. For a reference Kubernetes setup, see the [Helm charts](https://github.com/mosip/esignet/tree/develop-go/helm) in the repository.

---

*Last updated August 2026*

*Copyright © 2021 MOSIP. This work is licensed under a [Creative Commons Attribution (CC-BY-4.0) International License](https://creativecommons.org/licenses/by/4.0/) unless otherwise noted.*
