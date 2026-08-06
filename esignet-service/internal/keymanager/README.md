# keymanager

Cryptographic key lifecycle management for MOSIP: key generation, certificate/CSR issuance, upload, revocation, and lazy expiry-driven rotation, backed by Postgres and a PKCS#11 (HSM/SoftHSM2) or PKCS#12 keystore. A Go port of the Java `KeymanagerService`.

## Key hierarchy

```
ROOT (self-signed)
 ├── Component Master Key (RefID=RSA_2048)        — signed by ROOT
 ├── EC sign key (RefID=EC_*_SIGN / ED25519_SIGN)  — signed by ROOT
 └── Component Encryption Key (any other RefID)    — signed by the Component Master Key
```

Each application (`ApplicationID`) needs a `key_policy_def` row before any key can be generated for it.

## Usage

keymanager has no database connection or DSN handling of its own — it shares the caller's `*sqlx.DB` (typically the same connection the rest of the service already opened against `mosip_esignet`), scoped to its own schema (`Config.DBSchema`):

```go
cfg := keymanager.LoadConfig()
ks, err := keystore.New(cfg.KeystoreType, cfg.KeystoreParams)
if err != nil {
    log.Fatal(err)
}
// conn is the service's existing *sqlx.DB (or sqlx.NewDb(pgConn, "postgres")
// wrapping an existing *sql.DB) — see cmd/esignet/main.go's initializeKeyManager.
svc := keymanager.NewService(conn, ks, cfg)
```

Main methods:

| Method | Purpose |
|---|---|
| `GenerateMasterKey` | Admin-only, one-time-per-app: provisions ROOT / a Component Master Key / an EC sign key |
| `GetCertificate`, `GenerateCSR` | Fetch a certificate/CSR; generates a Component Encryption Key on first request and auto-rotates any tier on expiry |
| `UploadCertificate` | Replace the current cert (must match the existing key pair, must not be a byte-identical re-upload) |
| `UploadOtherDomainCertificate` | Store a foreign-domain, cert-only entry (no private key) — app id restricted, see below |
| `GenerateSymmetricKey`, `RevokeKey`, `GetAllCertificates`, `GetCertificateChain`, `GetSigningCertificate` | As named |

Manual end-to-end testing: `cmd/keymanagertest` (`go run ./cmd/keymanagertest <command> -h`).

## Configuration

All env vars are prefixed `KEYMANAGER_`. There is no DB connection config here — keymanager reuses whatever `*sqlx.DB` its caller passes to `NewService` (see [Usage](#usage) above); only the schema is configurable. Nothing has a built-in default for keystore path/password — both must be set explicitly.

**Database**
| Var | Default |
|---|---|
| `DB_SCHEMA` | `keymgr` (`esignet` in docker-compose — see `docker-compose/init.sql`) |

**Keystore**
| Var | Default |
|---|---|
| `KEYSTORE_TYPE` | `PKCS11` (or `PKCS12`) |
| `PKCS11_MODULE_PATH`, `PKCS11_TOKEN_LABEL` or `PKCS11_SLOT_ID`, `PKCS11_PIN` | — |
| `PKCS12_FILE_PATH`, `PKCS12_PASSWORD` | — |

SoftHSM2 slot IDs are large opaque numbers, not `0`/`1` — prefer `PKCS11_TOKEN_LABEL` over `PKCS11_SLOT_ID`. The PKCS#11 backend (`keystore/pkcs11/store.go`) is built only under `//go:build cgo`; a `CGO_ENABLED=0` build (the local `make.sh build`/`run` default) links `keystore/pkcs11/stub.go` instead, which errors at startup on any use — set `KEYSTORE_TYPE=PKCS12` for CGO-free local development, or build with `CGO_ENABLED=1` (a C toolchain is required; `make.sh docker-build`'s image has one and doesn't override `CGO_ENABLED`, so PKCS#11 works there by default) for real PKCS#11/HSM support.

**Key policy**
| Var | Default |
|---|---|
| `SYMMETRIC_KEY_ALLOWED_REF_IDS` | `CACHE_ENCRYPT` (comma-separated; covers esignet's own cache/session encryption key — see `internal/engine/runtime_crypto_provider.go`'s `defaultSymmetricEncryptReferenceID`) |
| `SYMMETRIC_KEY_VALIDITY_DAYS` | `1825` (5 years) |
| `FOREIGN_DOMAIN_ALLOWED_APP_IDS` | `PARTNER,IDA` |
| `CERT_CN` / `CERT_OU` / `CERT_O` / `CERT_L` / `CERT_ST` / `CERT_C` | `www.mosip.io` / `mosip-esignet` / `IIITB` / `Bangalore` / `KA` / `IN` |
| `ASYMMETRIC_KEY_LENGTH` | `2048` |

A ref id listed in `SYMMETRIC_KEY_ALLOWED_REF_IDS` can never be used for an asymmetric key, and vice versa — the two namespaces are disjoint.

## HTTP endpoints

`Handler` (`handler.go`), mounted by `cmd/esignet/main.go`, mirrors the Java service's `SystemInfoController`:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/system-info/certificate` | Fetch the current certificate/CSR for `applicationId` (+ optional `referenceId`) |
| POST | `/system-info/uploadCertificate` | Replace the certificate for `applicationId`/`referenceId` |

## Startup provisioning

`cmd/esignet/main.go`'s `provisionKeyHierarchy` calls `GenerateMasterKey`/`GenerateSymmetricKey` on every service startup to idempotently provision:

- `ROOT` — self-signed root CA
- `OIDC_SERVICE` (esignet itself) — RSA_2048 Component Master Key, `EC_SECP256R1_SIGN` sign key (JWT signing), `CACHE_ENCRYPT` symmetric key (cache/session encryption)
- `OIDC_PARTNER` — RSA_2048 Component Master Key, used to sign outbound MOSIP IDA requests

Each of those `ApplicationID`s needs a `key_policy_def` row first (see `docker-compose/init.sql`'s `KEY_POLICY_DEF` inserts, or the [main README's environment reference](../../README.md#key-management-keymanager)).

## Known limitations

- SECP256K1 certificates are unsupported (Go's `crypto/x509` doesn't recognize the curve OID).
- PKCS#12 output is a JSON container of individually-valid PFX blobs per alias, not a single standards-compliant multi-entry PKCS#12 file.
