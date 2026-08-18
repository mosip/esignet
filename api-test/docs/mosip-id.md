# MOSIP ID plugin (`AUTHN_PROVIDER=mosip`)

Everything specific to running the harness against a deployment using the **MOSIP ID** identity
plugin. Nothing here applies to the `mock` or `sunbird` plugins.

Two things differ from the other plugins:

1. **Client registration goes through partner-management-service (PMS)**, not eSignet's own client
   management, so that IDA gets the partner-and-policy binding it requires.
2. **OTPs can be read live** from the deployment's mock SMTP service, instead of a static test OTP.

Run it the same way as any other plugin:

```bash
./run-all.sh -c data/config/config.mosip.json --check
./run-all.sh -c data/config/config.mosip.json
```

**Contents:** [Partner registration](#partner-registration-via-pms) · [Dynamic OTP](#dynamic-otp-retrieval) ·
[Settings summary](#settings-summary) · [Troubleshooting](#troubleshooting)

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

> Note the asymmetry: the config field is `policy_id`, but its environment override is
> `AUTH_POLICY_ID`.

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

## Settings summary

| Config field | Environment | Purpose |
|---|---|---|
| `esignet.provider` | `AUTHN_PROVIDER` | Set to `mosip` |
| `esignet.identity.individual_id` | `INDIVIDUAL_ID` | The test identity — **required**; there is no fallback outside `mock` |
| `esignet.identity.id_type` | `ID_TYPE` | `uin` \| `vid` \| `phone` \| `email` — selects the matching login-id tab |
| `esignet.pms.base_url` | `PMS_BASE_URL` | partner-management-service base URL |
| `esignet.pms.auth_partner_id` | `AUTH_PARTNER_ID` | Onboarded partner id |
| `esignet.pms.policy_id` | `AUTH_POLICY_ID` | Published policy id |
| `esignet.otp.source` | `OTP_SOURCE` | `static` or `dynamic` |
| `esignet.otp.value` | `TEST_OTP` | The OTP, when `source: static` |
| `esignet.otp.ws_url` | `OTP_WS_URL` | Mock SMTP WebSocket URL, when `source: dynamic` |
| `esignet.otp.recipient_email` | `OTP_RECIPIENT_EMAIL` | Restrict to one recipient (email **or** phone) |

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
| e2e fails immediately on a missing identity | `individual_id` is required for every plugin except `mock` |

For failures not specific to this plugin, see [Troubleshooting](troubleshooting.md).
