# e2e scenario model

The `e2e` surface reads a scenario file, registers a throwaway OIDC client for each distinct client
configuration the scenarios ask for, then drives each scenario through the full relying-party
journey — optional `/oauth2/par` → `authorize` (PKCE) → login → `token` (`private_key_jwt`, with a
DPoP proof when the client is bound) → `userinfo` — and asserts the outcome.

One scenario file ships per plugin:

| Plugin | File |
|---|---|
| `mock` | `data/scenarios/e2e-scenarios.json` |
| `mosip` | `data/scenarios/e2e-scenarios-mosip.json` |
| `sunbird` | `data/scenarios/e2e-scenarios-sunbird.json` |

Selected by `e2e.spec`, or overridden for one run with `-spec`.

**Contents:** [File shape](#file-shape) · [Scenario fields](#scenario-fields) · [Filtering](#filtering) ·
[Protocol combinations](#protocol-combinations) · [Introspection](#introspection-coverage) ·
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
| `expect_login_failure` | Negative case: **passes when the flow is correctly rejected**, fails if a bad credential or an unhardened request is wrongly accepted. Rejection anywhere in the chain counts, not only at login. Omitted means positive — the flow must complete |
| `expect_error_contains` | On a negative case, additionally requires the rejection message to contain this substring, so the case cannot pass on an unrelated failure. Ignored without `expect_login_failure` |
| `client_config` | The `additionalConfig` protocol switches the scenario's client is registered with — see [Protocol combinations](#protocol-combinations) |
| `flow` | Overrides what the relying party actually sends, independently of what the client requires — see [Protocol combinations](#protocol-combinations) |
| `scopes` | Scopes requested at `authorize` |
| `userinfo_claims` | Per-claim request object, e.g. `{"name": {"essential": true}}` |
| `expect_present` / `expect_absent` | Claims that must / must not come back from `userinfo` |
| `expect_values` | Exact claim values that must match |
| `introspect` | Introspection cases to run once the flow has completed — see [Introspection](#introspection-coverage) |
| `consent` | How to answer the consent step and what to assert — see below |
| `client_lifecycle` | Deactivates the scenario's client partway through the flow — see [Client status](#client-status-coverage) |
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

> **The consent scenarios are order-dependent.** Scenarios that name no `client_config` all share
> one registered client, so each successful login stores a consent record. The consent-reuse case
> asserts "no prompt" precisely because the identical-request case ran immediately before it.
> Narrowing a run with `include` so an earlier case is skipped can therefore break it. A build-time
> test enforces these invariants on the shipped files.

---

## Protocol combinations

eSignet exposes three protocol switches in a client's `additionalConfig`, enforced by the engine at
`authorize`, `/oauth2/par`, `token` and `userinfo` respectively:

| Switch | What the client then requires |
|---|---|
| `require_pkce` | A `code_challenge` on the authorization request, and `S256` — the `plain` method is refused |
| `require_pushed_authorization_requests` | The request pushed to `/oauth2/par` first; a direct `authorize` is refused |
| `dpop_bound_access_tokens` | A DPoP proof (RFC 9449) at `token`, and the resulting token presented with the `DPoP` scheme plus a fresh proof at `userinfo` |

A scenario declares the client it wants; the harness registers **one client per distinct
`client_config`**, on first use, and reuses it for every later scenario asking for the same one. A
scenario with no `client_config` gets the plain unhardened client — the same one it always got.

```jsonc
{ "name": "protocol positive: PKCE + PAR + DPoP completes to userinfo",
  "auth_factor": "otp",
  "client_config": {
    "require_pkce": true,
    "require_pushed_authorization_requests": true,
    "dpop_bound_access_tokens": true
  },
  "scopes": ["openid"], "expect_present": ["sub"] }
```

By default the relying party does whatever the client requires: it pushes when PAR is required,
proves possession when the client is DPoP-bound, and always sends `S256` PKCE. That is all a
**positive** combination case needs.

A **negative** case is written by making the RP disagree with the client, through `flow`:

| `flow` field | Effect |
|---|---|
| `use_par` | Force the push on or off, against what the client requires |
| `use_dpop` | Force proofs on or off |
| `pkce` | `S256` (default), `plain`, or `none` |
| `dpop_key_mismatch` | Redeem the code with a proof from a *different* key than the one bound at PAR |
| `bearer_at_userinfo` | Present a DPoP-bound token with the `Bearer` scheme |

```jsonc
{ "name": "protocol negative: DPoP-bound client rejects a token call with no proof",
  "auth_factor": "otp",
  "client_config": { "dpop_bound_access_tokens": true },
  "flow": { "use_dpop": false },
  "expect_login_failure": true,
  "expect_error_contains": "token exchange failed",
  "scopes": ["openid"] }
```

Alongside the claim checks, these scenarios add protocol assertions to the report: the PAR
`request_uri` is in the RFC 9126 URN namespace, `token_type` comes back as `DPoP`, and the access
token's `cnf.jkt` is the thumbprint of the key that proved possession.

**What they need from the deployment.** PAR cases need discovery to advertise
`pushed_authorization_request_endpoint`; DPoP cases read `dpop_signing_alg_values_supported` and
sign with `PS256` or `RS256`, whichever it lists. A deployment advertising neither still runs every
other scenario — the affected ones fail with an explicit message rather than being skipped.

> **mosipid caveat.** With the `mosip` plugin, clients are registered through PMS `/oauth/client`,
> not eSignet client-mgmt. The harness sends `additionalConfig` on that request, but whether PMS
> forwards the switches to eSignet is PMS's call. If it drops them, the positives still pass (an
> unhardened client happily accepts a hardened flow) and the **negatives** are what expose it, by
> not being rejected.

Registration-side validation of these same keys — allowlisting, type checking, the update path — is
covered by the `api` surface in `additional-config.feature`, where no login is needed.

---

## Introspection coverage

`/oauth2/introspect` (RFC 7662) answers *is this token still good, and what is it for*. It takes the
token as a form parameter, authenticates the caller with `private_key_jwt` exactly like `token`, and
returns `active` either way: a token it never issued is reported **inactive rather than as an
error**, and that answer carries no other metadata — otherwise introspection becomes an oracle for
guessed tokens (RFC 7662 §2.2, §4).

Reaching it needs a token the deployment actually issued, so the scenarios add `introspect` to a
flow that has already completed through `userinfo`. Each entry is one POST, and prefixes its own
assertions with its `name`, so a row says *which* case failed:

```json
{ "name": "introspection positive: an issued access token is reported active …",
  "auth_factor": "otp",
  "scopes": ["openid", "profile"],
  "expect_present": ["sub"],
  "introspect": [
    { "name": "access token with hint",
      "token": "access_token", "hint": "access_token",
      "expect_active": true,
      "expect_present": ["client_id", "sub", "scope", "iss", "exp", "iat"],
      "expect_values": { "client_id": "{{client_id}}", "sub": "{{sub}}" } }
  ]
}
```

| Field | Meaning |
|---|---|
| `name` | Labels the case in the report; defaults to `<token>/<client_auth>` |
| `token` | What is submitted: `access_token` (default) \| `id_token` \| `unissued` (a value this deployment never minted) \| `none` (the parameter is left out) |
| `hint` | `token_type_hint`. Omitted when empty — RFC 7662 §2.1 makes it an optimization the server may ignore, so a *mismatched* hint must still resolve the token |
| `client_auth` | `private_key_jwt` (default) \| `no_assertion` (a `client_id` alone) \| `wrong_key` (an assertion signed with a key the client never registered) \| `wrong_audience` (a correctly signed assertion made out to somebody else) |
| `expect_status` | HTTP status the call must answer with. Defaults to `200` |
| `expect_active` | Asserts the `active` member. Left unset, nothing is asserted about it — which is what an error case wants, since a 400/401 body has none |
| `expect_present` / `expect_absent` | Response members that must / must not be there |
| `expect_values` | Exact member values. Dotted paths work (`cnf.jkt`), and an array member matches when any element does (RFC 7662 lets `aud` be either) |
| `expect_error` | The OAuth `error` member a rejection must carry |

Four values are substituted from what the run obtained, since a spec file cannot know them ahead of
time: `{{client_id}}`, `{{issuer}}`, `{{sub}}` (the `id_token` subject) and `{{dpop_jkt}}` (the
thumbprint a DPoP-bound access token is bound to). One check is added rather than declared: an
`active` token whose `exp` is already in the past fails, because a resource server would otherwise
accept a token the authorization server considers dead.

The negatives are written by pointing one axis away from the positive — an unissued token behind
good client authentication, or a good token behind an assertion that does not verify. Client
authentication is checked **first**, so `wrong_key` and `wrong_audience` are refused before the
token is ever looked at.

**What it needs from the deployment.** Discovery must advertise `introspection_endpoint`; without it
the introspection scenarios fail with an explicit message and everything else still runs.

Endpoint-shape validation that needs no token — a request naming no client, a `client_id` with no
assertion, an unverifiable assertion, and the endpoint not being exposed over `GET` — is covered by
the `api` surface in `introspect.feature`, where no login is needed.

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

## Client status coverage

A client's status is the operator's kill switch: `INACTIVE` is how a relying party that has been
retired, compromised or offboarded is taken out of service without deleting its record. That the
switch can be *thrown* is the api surface's business (`client-mgmt/client-status.feature`); whether
eSignet then *acts* on it is this surface's, because it takes a real client through real endpoints.

```jsonc
"client_lifecycle": {
  "deactivate": "before_authorize",  // before_authorize | after_authorize | after_token
  "reactivate": false                // set back to ACTIVE right after, before the flow continues
}
```

The stage names the door the client is standing at when the switch is flipped, and each asks a
different question:

| Stage | Question | Rejection expected at |
|---|---|---|
| `before_authorize` | May a deactivated client start an authorization at all? | `authorize`, or `par` for a PAR-required client |
| `after_authorize` | May a code issued while the client was active still be redeemed? | `token` |
| `after_token` | May a token issued while the client was active still be spent? | `userinfo` |

They are separate scenarios rather than one switch because they fail differently — the first is a
check on a request, the last two are checks on credentials already issued — and a deployment could
plausibly have one without the others. Pair each with `expect_error_contains` (`"login flow
failed"`, `"par failed"`, `"token exchange failed"`, `"userinfo request failed"`) so a case meant to
prove one endpoint enforces the status cannot pass by being rejected at an earlier one.

`reactivate` is the **positive control**: the same status writes run, the client ends up `ACTIVE`
again, and the flow must complete. A negative is only meaningful next to it — without the control,
a rejection could be the deactivation plumbing or the client itself rather than enforcement.

Two mechanics are worth knowing:

- **The client is dedicated, never pooled.** A scenario carrying `client_lifecycle` registers a
  client of its own. Deactivating a shared client would break every later scenario using that
  config, and would do so silently — they would simply stop being able to log in, for a reason none
  of them names.
- **Each status write is read back** from `GET /client-mgmt/client/{id}` and asserted, for every
  plugin including `mosip` (whose clients are registered through PMS, but whose eSignet record is
  what the engine actually resolves). A patch response that echoed `INACTIVE` over a record that
  stayed `ACTIVE` would otherwise leave every assertion below it testing an active client.

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
