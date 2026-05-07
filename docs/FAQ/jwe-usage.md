# JWE (JSON Web Encryption) in eSignet OIDC

Encrypt userinfo responses in eSignet using JWE ([RFC 7516](https://datatracker.ietf.org/doc/html/rfc7516)), ensuring sensitive user data is only readable by the intended relying party.

---

## Table of Contents

- [Background](#background)
- [How It Works](#how-it-works)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
  - [1. Set Encryption Public Key](#1-set-encryption-public-key)
  - [2. Enable JWE Response Type](#2-enable-jwe-response-type)
  - [3. Configure the Relying Party](#3-configure-the-relying-party)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

---

## Background

By default, eSignet's `/userinfo` endpoint returns a **JWS** (JSON Web Signature) response. While JWS ensures integrity, it can be decoded by anyone — the payload is base64-encoded, not encrypted.

With **JWE** enabled, the userinfo payload is encrypted using the relying party's public key before being returned. Only the relying party, which holds the corresponding private key, can decrypt it.

**Default behavior (JWS):**

```
Client --> /userinfo --> JWS (signed, readable by anyone)
```

**With JWE enabled:**

```
Client --> /userinfo --> JWE (signed + encrypted, only relying party can decrypt)
```

---

## How It Works

1. The relying party generates an **encryption key pair** and shares the **public key** with eSignet.
2. eSignet stores the public key in the `enc_public_key` column of the `client_detail` table.
3. When the relying party requests userinfo, eSignet encrypts the response using this public key.
4. The relying party decrypts the response using its private key.

> **Important:** The encryption key pair (`enc_public_key`) is **separate** from the client signing key (`public_key`). Do not reuse the same key pair for both signing and encryption — this is discouraged by the JWE/JWS specifications.

---

## Prerequisites

- eSignet is deployed and operational
- A relying party (client) is registered in the `client_detail` table
- The relying party has generated a dedicated encryption key pair (RSA recommended)

---

## Configuration

### 1. Set Encryption Public Key and Enable JWE Response Type

Update the client configuration via the client management API:

```
PUT /v1/esignet/client-mgmt/client/{{client_id}}
```

```json
{
  "requestTime": "{{currentTime}}",
  "request": {
    "additionalConfig": {
      ...,
      "userinfo_response_type": "JWE",
      ...
    },
    "enc_public_key": "{{your_enc_public_key}}"
  }
}
```

> Do **not** confuse `enc_public_key` (for encryption) with `public_key` (for signature verification). They serve different purposes and should be different keys.

### 2. (Optional) Enable JWE via Server Profile

Alternatively, instead of setting `userinfo_response_type` in the API request above, you can enable it globally via a server profile:

1. Set the server profile in `deployment.yaml` or `application-default.properties`:

   ```properties
   MOSIP_ESIGNET_SERVER_PROFILE=enc
   ```

2. Add a record in the `server_profile` table:

   | Column                  | Value                    |
   |-------------------------|--------------------------|
   | `profile_name`          | `enc`                    |
   | `feature`               | `JWE`                    |
   | `additional_config_key` | `userinfo_response_type` |

### 3. Configure the Relying Party

For `mock-relying-party-service`, set these environment variables or properties:

| Property                   | Description                                               | Example                          |
|----------------------------|-----------------------------------------------------------|----------------------------------|
| `JWE_USERINFO_PRIVATE_KEY` | Base64-encoded encryption private key (pair of the public key in Step 1) | `MIIEvQIBADANBgkqh...` |
| `USERINFO_RESPONSE_TYPE`   | Response type flag                                        | `JWE`                            |

> **Note:** This configuration is specific to eSignet's mock relying party. Production relying parties should implement decryption according to their own architecture, using the private key that corresponds to the public key registered with eSignet.

---

## Verification

After completing the configuration:

1. Authenticate a user through the OIDC flow.
2. Exchange the authorization code for tokens.
3. Call the `/userinfo` endpoint with the access token.
4. Confirm the response is a JWE token (five base64url-encoded segments separated by dots):
   ```
   eyJhbGciOi...eyJlbmMiOi...IV...ciphertext...tag
   ```
5. Decrypt the JWE using the relying party's private key and verify the userinfo payload.

---

## Troubleshooting

| Problem                          | Possible Cause              | Solution                                                                          |
|----------------------------------|-----------------------------|-----------------------------------------------------------------------------------|
| Userinfo still returns JWS       | `userinfo_response_type` not set to `JWE` | Verify `additional_config` in `client_detail` or the `server_profile` record |
| Decryption fails on relying party | Key mismatch               | Ensure `JWE_USERINFO_PRIVATE_KEY` is the private key paired with `enc_public_key`  |
| `enc_public_key` column is empty | Key not configured          | Set the relying party's encryption public key in `client_detail`                   |
| Server profile not applied       | Profile name mismatch       | Verify `MOSIP_ESIGNET_SERVER_PROFILE` matches `profile_name` in `server_profile`   |
