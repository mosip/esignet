# Postman — eSignet embedder flows

Manual API checks for a running [esignet-service](../esignet-service/README.md) instance.

All OAuth clients are **confidential** and must use `clientAuthMethods: ["private_key_jwt"]` at registration. Public clients (`none`) are not supported.

Variable names follow the standard eSignet Postman convention (snake_case: `baseUrl`, `client_id`, `client_private_key`, `redirect_uri`, `code`, `access_token`, `dpop_*`, …).

## Files

| File | Purpose |
|------|---------|
| `Go-eSignet.postman_environment.json` | Environment variables (`baseUrl`, audiences, credentials, runtime tokens/keys) |
| `Go-eSignet.postman_collection.json` | The collection: client management + three OAuth flows |

## Quick start

1. Start the server with `MOSIP_ESIGNET_HOST` matching `baseUrl` in the environment (default `http://127.0.0.1:8080`). See [esignet-service/README.md](../esignet-service/README.md) for build and run steps.
2. In Postman, import **both** files (environment first, then collection).
3. Select the **Go-eSignet (local)** environment.
4. Run **Client Management → Create client** once per flow you want to exercise (see below — `additionalConfig` differs per flow).
5. Run one of the numbered flow folders top to bottom.

No external script or manually-generated key is needed: **Create client**'s pre-request script generates a fresh RSA key pair and `client_id` entirely inside Postman (`crypto.subtle`) and stores them in `client_private_key` / `client_public_key` / `client_id`. Every later request in the flow folders signs with that same private key.

## Folders

### Client Management

`POST/GET/PUT /client-mgmt/client`. Run **Create client** first — its pre-request script generates the RSA key and `client_id` used by every flow folder. Toggle `additionalConfig` in the **Create client** body per client type:

- Normal OAuth (folders 1, 2): `pkce_required: true` only.
- FAPI2 (folder 3): also `require_pushed_authorization_requests: true` and `dpop_bound_access_tokens: true`.

If scope enforcement is enabled server-side (see `esignet-service`'s `security_config`), set `client_mgmt_token` to a valid Bearer JWT first; otherwise leave it empty.

### 1 — MOSIP OTP (private_key_jwt)

Authorization code flow with PKCE, resumed via `/flow/execute` using an individual ID + OTP, then `private_key_jwt` at the token endpoint:

Authorize (no redirect) → Flow meta → Flow execute (resume → select acr → individual ID → OTP) → Auth callback → Token → UserInfo

**Server requirements:** `AUTHN_PROVIDER=mosip` and MOSIP variables (see `esignet-service/.env.example`). Set `individual_id` and `otp` in the environment before running.

### 2 — MOSIP Credentials (private_key_jwt)

Same shape as folder 1, but resumes the flow with static `username`/`password` credentials instead of OTP.

### 3 — MOSIP FAPI2 flow

Pushed Authorization Requests + DPoP-bound tokens, with a PS256 `private_key_jwt` client assertion:

Initiate PAR → Initiate Authorization → Flow execute (resume → select acr → individual ID → OTP) → Auth callback → Exchange Code for Token → Exchange Token for Userinfo

The PAR request generates a fresh DPoP key pair (`dpop_*` variables) and reuses it for the token and userinfo DPoP proofs. Requires the server's Redis to be **6.2+** (PAR uses `GETDEL`).

**Server-side:** register the client with `require_pushed_authorization_requests: true` and `dpop_bound_access_tokens: true` (see Client Management above).

## Audiences (editable from the environment)

The `aud` of each `private_key_jwt` client assertion **and** the `htu` of each DPoP proof are read from environment variables — change them here to test, instead of editing pre-request scripts. Each falls back to a `{{baseUrl}}`-derived default if left empty.

| Variable | Default | Drives |
|----------|---------|--------|
| `audience` | `{{baseUrl}}/oauth2/token` | token `client_assertion` `aud` (all folders) **and** the token-request DPoP `htu` (folder 3) |
| `par_audience` | `{{baseUrl}}/oauth2/par` | PAR `par_client_assertion` `aud` **and** the PAR DPoP `htu` (folder 3) |
| `userinfo_audience` | `{{baseUrl}}/oauth2/userinfo` | the userinfo DPoP `htu` (folder 3) |

> A DPoP `htu` must equal the URL the request is actually sent to. If you change `baseUrl`, update these audiences to match (or clear them to use the `{{baseUrl}}`-derived fallback).

## The two client assertions (collection variables)

The `private_key_jwt` signed at the token endpoint and the one signed for PAR are **separate** values, so they can never be confused:

| Collection variable | Signed by | Sent as `client_assertion` at |
|---------------------|-----------|-------------------------------|
| `client_assertion` | collection pre-request (RS256, folders 1–2) / **Exchange Code for Token** pre-request (PS256, folder 3) | `/oauth2/token` |
| `par_client_assertion` | **Initiate PAR** pre-request (PS256, folder 3) | `/oauth2/par` |

Both are stored as **collection variables** (not environment) and regenerated on every run — they are transient signing artifacts. Do not confuse either with `assertion`, which is the **KBA flow assertion** returned by `/flow/execute` on `COMPLETE` and submitted to `/oauth2/auth/callback`.

## Environment variables

| Variable | Used by | Notes |
|----------|---------|-------|
| `baseUrl` | All | Base URL; must match `MOSIP_ESIGNET_HOST` |
| `audience`, `par_audience`, `userinfo_audience` | Token / PAR / UserInfo | Client-assertion `aud` + DPoP `htu` — see [Audiences](#audiences-editable-from-the-environment) |
| `application_id` | — | Reserved; not sent by the current requests |
| `otp_application_id` | Folders 1, 2, 3 | App id used for `/flow/meta` |
| `scope`, `redirect_uri` | Folders 1, 2, 3 | OAuth parameters |
| `relying_party_id` | Client Management | Relying party id for client-mgmt create |
| `client_mgmt_token` | Client Management | Bearer JWT (only needed if scope enforcement is enabled; leave empty otherwise) |
| `client_id`, `client_private_key`, `client_public_key` | All flows | Generated automatically by **Create client**; every flow signs with these |
| `username`, `password` | Folder 2 | Test user credentials |
| `individual_id`, `otp` | Folders 1, 3 (`individual_id` also folder 2) | MOSIP UIN and OTP value |
| `code_verifier`, `code_challenge` | Folders 1, 2, 3 | PKCE, generated per-run |
| `request_uri` | Folder 3 | Set from the PAR response |
| `dpop_jkt`, `dpop_private_key`, `dpop_public_key` | Folder 3 | DPoP key, generated by Initiate PAR |
| `execution_id`, `auth_id`, `challenge_token`, `action_ref`, `assertion` | Flow execute steps | Set automatically as the flow progresses |
| `code`, `access_token`, `id_token` | Auth callback / Token | Set automatically as the flow progresses |

`client_assertion` and `par_client_assertion` are **collection variables**, not environment variables — see [the section above](#the-two-client-assertions-collection-variables).

## `private_key_jwt` at the token endpoint

Every **Token** / **Exchange Code for Token** request signs a `client_assertion` with `client_private_key` in a pre-request script:

- `client_assertion_type`: `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`
- `client_assertion`: a signed JWT (RS256 for folders 1–2, PS256 for folder 3), `iss`/`sub` = `client_id`, `aud` = `audience` (the token endpoint)

All signing uses the Postman runtime (`crypto.subtle`) — no external script is needed during a run.

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| **Create client** fails with a `crypto.subtle` error | Update Postman (needs `crypto.subtle.generateKey` support), or set `client_private_key` + `client_public_key` + `client_id` manually |
| Token returns `invalid_client` | Run **Create client** again for this flow, or check that `client_id`/`client_private_key` weren't overwritten by a different flow's Create client run |
| Token/PAR returns `invalid_client` after editing an audience | The `audience` / `par_audience` must equal the server's expected token/PAR endpoint |
| Folder 3 DPoP proof rejected (`invalid_dpop_proof`) | A DPoP `htu` (from `audience` / `par_audience` / `userinfo_audience`) does not match the URL the request was sent to — clear it to use the `{{baseUrl}}`-derived fallback |
| Client Management returns 401 | `security_config.issuer_url`/`jwks_url` are set in `esignet-service/data/deployment.yaml` (enforcement on) — supply a valid `client_mgmt_token`, or clear those settings to disable enforcement |
| Discovery / health fails | Server not running, or `baseUrl` does not match `MOSIP_ESIGNET_HOST` |
| Folder 1/2/3 OTP or credentials step fails | `AUTHN_PROVIDER` not set to `mosip`, or `individual_id`/`otp`/`username`/`password` not set |
| Folder 3 fails at PAR or token | Server's Redis is older than 6.2, or the client wasn't created with `require_pushed_authorization_requests`/`dpop_bound_access_tokens` |
