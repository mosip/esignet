# Postman — eSignet embedder flows

Manual API checks for a running [esignet-service](https://github.com/mosip/esignet) instance.

All OAuth clients are **confidential** and must use `clientAuthMethods: ["private_key_jwt"]` at registration. Public clients (`none`) are not supported.

## Files

| File | Purpose |
|------|---------|
| `embedder-local.environment.json` | Local environment variables (`baseUrl`, credentials, JWKs) |
| `embedder-positive-flow.json` | Collection of positive-path requests |

## Quick start

1. Start the server with `MOSIP_ESIGNET_HOST` matching `baseUrl` in the environment (default `http://127.0.0.1:8080`). See the parent [README](../README.md) for build and run steps.
2. In Postman, import **both** files (environment first, then collection).
3. Select the **eSignet (local)** environment.
4. Run folder **0 — Shared setup** once.
5. Run one of the flow folders below in order (top to bottom within the folder).

## Folders

### 0 — Shared setup

Health, OIDC discovery, and flow metadata. No secrets required.

### 1 — App-native flow/execute

Exercises `POST /flow/execute` directly (start → credentials). Uses `username` / `password` from the environment.

### 2 — Full OAuth (private_key_jwt)

End-to-end authorization code flow with PKCE on authorize and `private_key_jwt` at the token endpoint:

Register OAuth client → PKCE setup → Authorize → Flow resume → Credentials → Auth callback → **Token** → UserInfo

**Before running folder 2**, generate a local signing key and paste the private JWK into the environment:

```bash
cd esignet-service
./make.sh smoke-jwt-key
```

Copy the contents of `scripts/fixtures/smoke-jwt-client.jwk.json` into the `clientJwk` environment variable. The JWK must include `kid`, `n`, `e`, and `d`.

The first step **Register OAuth client** creates `clientId` (`decl-jwt-client-1` by default) via `/client-mgmt/client` with `clientAuthMethods: ["private_key_jwt"]` and the public JWK derived from `clientJwk`. If the client already exists, the step accepts `duplicate_client_id` and continues.

Authorize uses `followRedirects: false` so Postman can read `authId` and `executionId` from the `Location` header without following the UI redirect.

### 3 — MOSIP OTP (private_key_jwt)

Same OAuth sequence as folder 2 (including `private_key_jwt` at the token endpoint), but authentication uses MOSIP OTP instead of static credentials.

**Server requirements:** `AUTHN_PROVIDER=mosip` and MOSIP-related variables (see `.env.example` in the repo root).

**Environment:** set `individualId` (UIN) and `otp` before running. The OTP client (`otpClientId`) must be registered as confidential with `private_key_jwt`. Set `otpClientJwk` to the matching RSA private JWK.

### 4 — Client management

Create → Get → Update against `/client-mgmt/client`. All created clients use `clientAuthMethods: ["private_key_jwt"]`.

**Server requirements:** PostgreSQL with the `client_detail` schema.

When scope enforcement is enabled, configure the server with `CLIENT_MGMT_ISSUER_URL` and `CLIENT_MGMT_JWKS_ENDPOINT` matching `baseUrl`, then set `clientMgmtToken` in Postman to the raw JWT string (not a `Bearer` prefix — the collection adds that automatically) with the `client_mgmt` scope. When scope enforcement is disabled, leave `clientMgmtToken` empty.

Generate a token locally (same key material as the engine JWKS):

```bash
cd esignet-service
./make.sh keys   # if keys/signing.key does not exist yet

KID=$(curl -s http://127.0.0.1:8080/.well-known/jwks.json | jq -r '.keys[0].kid')
TOKEN=$(python3 scripts/sign-mgmt-token.py \
  --issuer http://127.0.0.1:8080 \
  --scope client_mgmt \
  --key keys/signing.key \
  --kid "$KID")
echo "$TOKEN"   # paste into clientMgmtToken
```

Run **Create client** → **Get client** → **Update client** in order. Create/update set `clientMgmtClientId` and `clientMgmtRequestTime` automatically. Create derives the registered public JWK from `clientJwk`.

## Environment variables

| Variable | Used by | Notes |
|----------|---------|-------|
| `baseUrl` | All | Must match `MOSIP_ESIGNET_HOST` |
| `clientId` | Folder 2 | Default `decl-jwt-client-1` |
| `clientJwk` | Folders 2, 4 | RSA private JWK JSON (secret) |
| `otpClientId` | Folder 3 | MOSIP OTP OAuth client (confidential, `private_key_jwt`) |
| `otpClientJwk` | Folder 3 Token | RSA private JWK JSON (secret) |
| `username`, `password` | Folders 1, 2 | Declarative test user |
| `individualId`, `otp` | Folder 3 | MOSIP UIN and OTP value |
| `redirectUri`, `scope` | Folders 2, 3 | OAuth parameters |
| `clientMgmtToken` | Folders 2, 4 | Bearer JWT with `client_mgmt` scope (optional when scope enforcement disabled) |
| `rpId` | Folders 2, 4 | Relying party id for client-mgmt create |

Runtime variables (`executionId`, `authCode`, `clientAssertion`, `oauthClientPublicKey`, `accessToken`, etc.) are set automatically by collection scripts and tests.

## `private_key_jwt` at the token endpoint

Folders **2** and **3** authenticate the token request with `private_key_jwt`. Each **Token** request sends:

- `client_assertion_type`: `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`
- `client_assertion`: a signed RS256 JWT

A collection-level pre-request script runs before each **Token** request. It reads the matching private JWK (`clientJwk` or `otpClientJwk`), signs an assertion with `iss`/`sub` = client id and `aud` = `{baseUrl}/oauth2/token`, and stores the result in `clientAssertion`.

No external script is needed during the Postman run; signing uses the Postman runtime (`crypto.subtle`). The shell helper `scripts/sign-client-assertion.py` is used by `oauth-smoke.sh`, not by this collection.

## Automated smoke tests

For scripted checks (no Postman UI), use:

```bash
./make.sh smoke
```

This runs `scripts/oauth-smoke.sh` (`private_key_jwt` token exchange) and `scripts/client-mgmt-smoke.sh`. Set `SKIP_CLIENT_MGMT_SMOKE=1` to skip client management.

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| Token pre-request throws “Set clientJwk…” | Run `./make.sh smoke-jwt-key` and paste the JWK into the environment |
| Token returns `invalid_client` | `clientJwk` `kid` does not match the JWKS registered for the client, or wrong `clientId` |
| Register OAuth client fails (not duplicate) | PostgreSQL not running, invalid JWK, or `clientAuthMethods` not `private_key_jwt` |
| Discovery / health fails | Server not running, or `baseUrl` does not match `MOSIP_ESIGNET_HOST` |
| Folder 4 returns 401 | Generate a management JWT (see above) or disable scope enforcement |
| Folder 3 OTP step fails | `AUTHN_PROVIDER` not set to `mosip`, or `individualId` / `otp` not set |
