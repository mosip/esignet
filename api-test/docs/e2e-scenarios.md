# e2e scenario model

The `e2e` surface reads a scenario file, registers one throwaway OIDC client, then drives each
scenario through the full relying-party journey — `authorize` (PKCE) → login → `token`
(`private_key_jwt`) → `userinfo` — and asserts the outcome.

One scenario file ships per plugin:

| Plugin | File |
|---|---|
| `mock` | `data/scenarios/e2e-scenarios.json` |
| `mosip` | `data/scenarios/e2e-scenarios-mosip.json` |
| `sunbird` | `data/scenarios/e2e-scenarios-sunbird.json` |

Selected by `e2e.spec`, or overridden for one run with `-spec`.

**Contents:** [File shape](#file-shape) · [Scenario fields](#scenario-fields) · [Filtering](#filtering) ·
[Consent](#consent-coverage) · [Captcha](#captcha-coverage)

---

## File shape

```json
{
  "redirect_uri": "https://api-e2e.example.org/callback",
  "user_claims": ["name", "email", "phone_number", "gender", "birthdate"],
  "acr": ["mosip:idp:acr:generated-code", "mosip:idp:acr:password", "mosip:idp:acr:biometrics"],
  "scenarios": [
    { "name": "otp positive: userinfo returns sub", "auth_factor": "otp",
      "scopes": ["openid"], "expect_present": ["sub"] },

    { "name": "otp negative: wrong OTP is rejected", "auth_factor": "otp",
      "expect_login_failure": true, "credentials": { "otp": "000000" },
      "scopes": ["openid"] }
  ]
}
```

The top-level `user_claims` and `acr` define the client that gets registered; `scenarios` each drive
one authentication factor against it.

---

## Scenario fields

| Field | Meaning |
|---|---|
| `name` | Shown in the report; also what `include`/`exclude` match against |
| `auth_factor` | **Required.** `otp` \| `password` \| `bio` \| `kbi` — selects the ACR at the login step |
| `credentials` | Overrides the base identity answers for this scenario only. Keys: `username`, `password`, `otp`, `fullName`, `dob`, `captchaToken` |
| `expect_login_failure` | Negative case: **passes when login is correctly rejected**, fails if a bad credential is wrongly accepted. Omitted means positive — login must succeed |
| `scopes` | Scopes requested at `authorize` |
| `userinfo_claims` | Per-claim request object, e.g. `{"name": {"essential": true}}` |
| `expect_present` / `expect_absent` | Claims that must / must not come back from `userinfo` |
| `expect_values` | Exact claim values that must match |
| `consent` | How to answer the consent step and what to assert — see below |
| `known_issue` | A reason string for an already-tracked environment gap. A **claim-assertion** failure then lands in the **Known** bucket with that reason instead of Failed, leaving the exit code alone; the failing check is still shown. It does not cover login failures, and a scenario that starts passing is still reported as passed |

> **Scenarios for unavailable factors are kept and reported failed, deliberately.** An ACR with no
> working credential on the target (`bio`, say, or `password` where the user is not seeded) stays in
> the file and is reported **FAILED** with a clear reason rather than quietly omitted. It goes green
> once real credentials exist, and stays visible until then.

---

## Filtering

```jsonc
"e2e": {
  "spec": "data/scenarios/e2e-scenarios-mosip.json",
  "auth_factors": ["otp", "password"],   // only these factors
  "include": ["^otp positive"],          // regex on scenario name, OR-ed
  "exclude": ["bio"]                     // applied last, always wins
}
```

A filter matching zero scenarios is an **error**, not an empty run.

> **The consent scenarios are order-dependent.** One client is registered per run and every scenario
> shares it, so each successful login stores a consent record. The consent-reuse case asserts "no
> prompt" precisely because the identical-request case ran immediately before it. Narrowing a run
> with `include` so an earlier case is skipped can therefore break it. A build-time test enforces
> these invariants on the shipped files.

---

## Consent coverage

Consent is covered from two directions, split by what each surface can reach:

| What | Surface | Why there |
|---|---|---|
| Consent **behaviour** — prompt, reuse, re-prompt, deny | `e2e` | Needs a driven login flow |
| Consent **configuration** — `consent_expire_in_mins`, `purpose` validation | `api` (`consent-config.feature`) | Pure API calls, no session needed |

The `consent` block on an e2e scenario:

```jsonc
"consent": {
  "expect_prompt": "yes",     // "yes" | "no" | "" (no assertion)
  "deny": ["name"],           // withhold approval from these elements
  "deny_all": false           // ...or from every element offered
}
```

- `expect_prompt: "no"` asserts the server **skipped** the prompt because a stored consent record
  still covers the request.
- `deny` on an **optional** claim: login succeeds and the claim is withheld — pair it with
  `expect_absent`.
- `deny` on an **essential** claim (`userinfo_claims: {"name": {"essential": true}}`): the login is
  rejected with `essential_consent_denied` — pair it with `expect_login_failure: true`.
- A `deny` naming an element the prompt never offered is a **scenario error**, not a silent pass:
  otherwise the case would "prove" a claim was withheld that was never consent-gated.
- Consent expectations are asserted on the rejection path too, so a deny-essential case cannot pass
  merely because login failed for an unrelated reason.

Consent-record **expiry** re-prompting is not covered: it needs control over the deployment's
consent validity period, which the harness does not have.

---

## Captcha coverage

Captcha rides on `credentials.captchaToken` rather than a field of its own. The base answers supply
a non-empty placeholder; a scenario overrides it:

```jsonc
{ "name": "captcha negative: an empty captcha token is rejected",
  "auth_factor": "otp", "expect_login_failure": true,
  "credentials": { "captchaToken": "" }, "scopes": ["openid"] }
```

An **empty** token is rejected before the service checks whether a validator is configured, so that
negative case is portable to any deployment. A **non-empty** token is accepted when no validator URL
is configured (the dev/test default) and validated for real when one is — so the positive case
asserts "accepted" only on the former.

There is deliberately **no `api`-surface captcha coverage**: captcha is a deployment-level setting
rather than per-client configuration, and it is only reachable as a flow input. A `/flow/execute`
call with a made-up `executionId` is rejected at the execution lookup before inputs are examined, so
a standalone "bad captcha token" API case would assert nothing. The same reasoning applies to
consent *behaviour*: both are wired as engine providers with no REST endpoint of their own.
