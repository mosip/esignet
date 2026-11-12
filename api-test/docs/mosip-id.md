# MOSIP ID plugin (`MOSIP_ESIGNET_AUTHN_PROVIDER=mosip`)

Everything specific to running the harness against a deployment using the **MOSIP ID** identity
plugin. Nothing here applies to the `mock` or `sunbird` plugins.

Three things differ from the other plugins:

1. **Client registration goes through partner-management-service (PMS)**, not eSignet's own client
   management, so that IDA gets the partner-and-policy binding it requires.
2. **OTPs can be read live** from the deployment's mock SMTP service, instead of a static test OTP.
3. **Biometric logins use real captures** from a mock MDS the harness image runs, which IDA
   verifies like any device's.

Run it the same way as any other plugin:

```bash
./run-all.sh -c data/config/config.mosip.json --check
./run-all.sh -c data/config/config.mosip.json
```

**Contents:** [Partner registration](#partner-registration-via-pms) · [Dynamic OTP](#dynamic-otp-retrieval) ·
[Biometric captures](#biometric-captures) · [Settings summary](#settings-summary) · [Troubleshooting](#troubleshooting)

---

## Partner registration via PMS

For `mosip`, the harness registers its test OIDC client at `{pms.base_url}/oauth/client` rather than
at eSignet's `/client-mgmt/client`. Registering through PMS is what binds the client to an onboarded
**partner** and a published **policy**; a client registered directly with eSignet has no such
binding, and IDA rejects authentication requests from it.

This affects both the `e2e` surface (which registers a throwaway client per run) and the `api`
surface's `@client-mgmt-pms` scenarios.

### What must already exist

The partner and policy are **provisioned out of band** — the harness does not create them.

| Prerequisite | Where its id goes |
|---|---|
| An onboarded **auth partner** | `pms.auth_partner_id` |
| A **published policy** bound to that partner | `pms.policy_id` |
| A reachable **PMS** | `pms.base_url` |

PMS authenticates with the same `keycloak.*` client-credentials grant the rest of the harness uses,
so no additional credentials are needed.

### Configuration

```jsonc
// data/config/config.local.json
{
  "esignet": {
    "provider": "mosip",
    "pms": {
      "base_url":        "https://api-internal.esdev.mosip.net/v1/partnermanager",
      "auth_partner_id": "<onboarded partner id>",
      "policy_id":       "<published policy id>"
    }
  }
}
```

Or as environment variables: `PMS_BASE_URL`, `AUTH_PARTNER_ID`, `AUTH_POLICY_ID`.

### Which PMS registration endpoint is used

PMS deployments differ in where OIDC clients are registered. Builds from **1.2.2.x serve only
`{base}/oauth/client`** and answer **404** for `{base}/oidc-clients`; current builds serve both.
`client_api` selects which one the harness posts to — `oidc-clients` (the default) or
`oauth-client`.

**Both endpoints take the identical request body**, so the setting changes the path and nothing
else. That was verified live against both a 1.2.2.3 and a current PMS: `/oidc-clients` validates the
wrapper `id` and rejects a wrong one with `PMS_REQUEST_ERROR_002`, while `/oauth/client` registers
the client regardless — it ignores the `id`/`version`/`metadata` members entirely.

Leave it at the default on any deployment that serves `/oidc-clients`. A client registered there is
the one IDA is known to authenticate, whereas `/oauth/client` registrations have been seen refused
at login — which only a full login reveals, so no create-and-read-back test catches it. Set
`oauth-client` when the deployment is old enough not to serve `/oidc-clients` at all; registration
then fails with an HTTP 404 that names this setting.

> Note this is **not** auto-detected. A deployment whose PMS is upgraded or replaced needs this
> setting revisited, or e2e registration fails with that 404.

Leave `pms.base_url` unset and the PMS-backed scenarios report as not-run rather than failing, so a
partial setup is visible in the report instead of looking like a test failure.

---

## Dynamic OTP retrieval

For factors that send a one-time code, the harness can either use a **static** OTP from the config,
or read the **real** OTP the deployment just sent.

| `otp.source` | Behaviour |
|---|---|
| `static` (default) | Uses `otp.value` — works where the deployment accepts a fixed test OTP |
| `dynamic` | Connects to the deployment's mock SMTP service over a WebSocket and reads the OTP out of the message it observes |

Dynamic retrieval is what most real MOSIP environments need, since they issue genuine codes.

```jsonc
{
  "esignet": {
    "otp": {
      "source":          "dynamic",
      "ws_url":          "https://smtp.<env>.mosip.net/",
      "recipient_email": "<optional filter>"
    }
  }
}
```

Environment equivalents: `OTP_SOURCE`, `OTP_WS_URL`, `OTP_RECIPIENT_EMAIL`.

**How the code is extracted.** The harness watches messages arriving on the socket and takes the
first six-digit sequence (`\b\d{6}\b`) from the message body.

**`recipient_email` filters to a single recipient**, and despite the name it matches an email
address **or a phone number** — OTPs for a UIN arrive as SMS. Leave it empty to take the newest
fresh code, which is reliable when only one identity is being tested at a time; set it when several
runs or identities share one mock SMTP instance.

`WSOTP_DEBUG=1` prints the first few raw frames, which is the way to diagnose a payload-format
change when extraction stops finding a code. It is off by default and never set in CI.

---

## Biometric captures

The `bio TC_BIO_*` scenarios are [scripted](e2e-scenarios.md#scripted-scenarios): they drive
`/flow/execute` step by step with `acr_values=mosip:idp:acr:biometrics` and submit captures taken from
the mock MDS the harness image starts on `127.0.0.1:4501` (`docker-entrypoint.sh`, which sets
`MOCK_MDS_BASE_URL`). Each capture is encrypted with the IDA partner certificate the MDS fetches with
its `MOCK_MDS_AUTH_*` / `MOCK_MDS_IDA_SERVER_URL` settings (see `.env.example`), and bound to the
transaction id of the login it was taken in.

What the target needs for them to pass:

- **The identity enrolled from the mock MDS's `Default` profile**, which returns the same
  biometrics whatever the login id. Captures from its `Automatic` profile stand in for someone else.
- **`MOCK_MDS_ENV` and `MOCK_MDS_DOMAIN_URI` equal to the provider's `IDA_AUTHENTICATOR_ENV` and
  `MOSIP_ESIGNET_DOMAIN_URL`.** Both are signed into the capture and compared by IDA. Defaults:
  `Staging`, and the origin of `MOSIP_ESIGNET_BASE_URL`.

Run outside the image (no MDS), every scenario that takes a capture is reported ENV_NOT_READY. To
run only these: `E2E_INCLUDE='^bio TC_BIO_'`.

---

## Settings summary

| Config field | Environment | Purpose |
|---|---|---|
| `esignet.provider` | `MOSIP_ESIGNET_AUTHN_PROVIDER` | Set to `mosip` |
| `esignet.identity.individual_id` | `INDIVIDUAL_ID` | The test identity — **required** on every plugin |
| `esignet.identity.id_type` | `ID_TYPE` | `uin` \| `vid` \| `phone` \| `email` — selects the matching login-id tab |
| `esignet.pms.base_url` | `PMS_BASE_URL` | partner-management-service base URL |
| `esignet.pms.auth_partner_id` | `AUTH_PARTNER_ID` | Onboarded partner id |
| `esignet.pms.policy_id` | `AUTH_POLICY_ID` | Published policy id |
| `esignet.pms.client_api` | `PMS_CLIENT_API` | `oidc-clients` (default) \| `oauth-client` — which registration endpoint PMS serves |
| `esignet.otp.source` | `OTP_SOURCE` | `static` or `dynamic` |
| `esignet.otp.value` | `TEST_OTP` | The OTP, when `source: static` |
| `esignet.otp.ws_url` | `OTP_WS_URL` | Mock SMTP WebSocket URL, when `source: dynamic` |
| `esignet.otp.recipient_email` | `OTP_RECIPIENT_EMAIL` | Restrict to one recipient (email **or** phone) |
| `e2e.mock_mds.base_url` | `MOCK_MDS_BASE_URL` | Mock MDS for biometric captures; the harness image sets it |
| `e2e.mock_mds.env` / `.domain_uri` | `MOCK_MDS_ENV` / `MOCK_MDS_DOMAIN_URI` | Signed into each capture; must match the provider's IDA env and domain URI |

The e2e scenario set for this plugin is `data/scenarios/e2e-scenarios-mosip.json`.

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| PMS scenarios reported as not run | `pms.base_url` unset — the harness skips rather than failing an unconfigured surface |
| Client registration rejected by PMS | Partner or policy not onboarded/published, or `auth_partner_id`/`policy_id` wrong |
| Authentication rejected by IDA despite a registered client | Client registered directly with eSignet rather than through PMS, so it has no partner/policy binding |
| OTP step times out with `source: dynamic` | `ws_url` unreachable, or `recipient_email` filtering out the message that arrived — clear it, or set `WSOTP_DEBUG=1` to see the frames |
| OTP found but rejected | A stale code was matched: another run's message arrived first. Set `recipient_email` to disambiguate. |
| e2e fails immediately on a missing identity | `individual_id` is required for every plugin |
| Biometric scenarios reported ENV_NOT_READY | No mock MDS: running outside the harness image, `MOCK_MDS_ENABLED=false`, or the MDS did not come up (see `out/mock-mds.log`) |
| Every valid capture refused as invalid credentials | Capture env/domainUri differ from the provider's (`MOCK_MDS_ENV`, `MOCK_MDS_DOMAIN_URI`), the MDS fetched the wrong IDA certificate, or the identity was not enrolled from the `Default` profile |

For failures not specific to this plugin, see [Troubleshooting](troubleshooting.md).
