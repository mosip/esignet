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

```go
cfg := keymanager.LoadConfig()
ks, _ := keystore.New(cfg.KeystoreType, cfg.KeystoreParams)
conn, _ := sqlx.Connect("postgres", cfg.DSN())
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

All env vars are prefixed `KEYMANAGER_`. Nothing has a built-in default for DB credentials or keystore path/password — both must be set explicitly.

**Database**
| Var | Default |
|---|---|
| `DATABASE_URL` | — (full DSN, overrides the fields below) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `mosip_keymgr` / `postgres` / — |
| `DB_SCHEMA` | `keymgr` |

**Keystore**
| Var | Default |
|---|---|
| `KEYSTORE_TYPE` | `PKCS11` (or `PKCS12`) |
| `PKCS11_MODULE_PATH`, `PKCS11_TOKEN_LABEL` or `PKCS11_SLOT_ID`, `PKCS11_PIN` | — |
| `PKCS12_FILE_PATH`, `PKCS12_PASSWORD` | — |

SoftHSM2 slot IDs are large opaque numbers, not `0`/`1` — prefer `PKCS11_TOKEN_LABEL` over `PKCS11_SLOT_ID`.

**Key policy**
| Var | Default |
|---|---|
| `SYMMETRIC_KEY_ALLOWED_REF_IDS` | — (comma-separated; unset rejects all symmetric-key generation) |
| `SYMMETRIC_KEY_VALIDITY_DAYS` | `1825` (5 years) |
| `FOREIGN_DOMAIN_ALLOWED_APP_IDS` | `PARTNER,IDA` |
| `CERT_CN` / `CERT_OU` / `CERT_O` / `CERT_L` / `CERT_ST` / `CERT_C` | `www.mosip.io` / `thunder-tech-team` / `IIITB` / `Bangalore` / `KA` / `IN` |
| `ASYMMETRIC_KEY_LENGTH` | `2048` |

A ref id listed in `SYMMETRIC_KEY_ALLOWED_REF_IDS` can never be used for an asymmetric key, and vice versa — the two namespaces are disjoint.

## Known limitations

- SECP256K1 certificates are unsupported (Go's `crypto/x509` doesn't recognize the curve OID).
- PKCS#12 output is a JSON container of individually-valid PFX blobs per alias, not a single standards-compliant multi-entry PKCS#12 file.
