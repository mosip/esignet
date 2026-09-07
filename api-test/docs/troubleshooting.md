# Troubleshooting

Start with `--check`. It resolves the whole configuration, needs nothing running, and names every
blocker it finds:

```bash
./run-all.sh -c data/config/config.mosip.json --check
```

It exits non-zero with a closing `NOT RUNNABLE YET` line when the run could not have started.

**Contents:** [Reading the result buckets](#reading-the-result-buckets) · [Setup and configuration](#setup-and-configuration) ·
[Conformance surface](#conformance-surface) · [api surface](#api-surface) · [e2e surface](#e2e-surface) ·
[Containers](#containers)

---

## Reading the result buckets

A case lands in one of these. Only **Failed** affects the exit code.

| Bucket | Meaning |
|---|---|
| **Passed** | The assertion held |
| **Failed** | The assertion did not hold — a real finding |
| **Skipped** | Excluded on purpose via `run.skip` |
| **Known** | A tracked environment gap, via `run.known_issues` or a scenario's `known_issue` |
| **Not run** | The surface could not run for want of configuration — reported rather than omitted, so a partial setup never reads as a clean pass |

A **Not run** entry is the harness telling you something is unconfigured, not that a test failed.
Its detail line names the setting to supply.

---

## Setup and configuration

| Symptom | Likely cause |
|---|---|
| `… uses the old "bdd" block — rename it to "api"` | The surface was renamed. Rename the block in your config; the harness rejects the old name rather than dropping it in silence, which would also reset `tls_verify` to `true` |
| `features directory … not found` | The Gherkin tree could not be located. Run from the harness root, or set `API_FEATURES_DIR` |
| `config file not found: …` | A config named with `-c` must exist. There is no silent fallback — otherwise a typo would run `mock` while you believed `mosip` ran, and the green report would be read as `mosip` passing. |
| Client-management cases report as not run | `keycloak.token_url` / `client_id` / `client_secret` unset, so no admin token can be obtained |
| Authorize-validation cases report as not run | `api.flow_client_id` unset — those cases need a pre-registered client to drive |
| A secret from your config file appears blank in the run | An environment variable of the same name is defined but empty. Present-and-empty still overrides; leave it undefined instead — see [Configuration](configuration.md#environment-overrides) |
| e2e fails immediately for a missing identity | `esignet.identity.individual_id` is required for every plugin except `mock` |

---

## Conformance surface

| Symptom | Likely cause |
|---|---|
| `NOT RUNNABLE YET` … `config_file "conformance-suite-private/…" not readable` | The plan config has not been created yet. It is git-ignored and holds a private JWKS, so no script generates it — make it once per environment ([setup](conformance-suite.md#creating-the-plan-config)), or run `-s api,e2e` until you have. Under Compose a missing `conformance-suite-private/` is recreated by the bind mount as an *empty directory*, so the symptom is this message rather than a mount error |
| Suite reported unavailable | The suite is not up. Its nginx front end returns 502 until the Java server finishes booting — allow ~30s, or raise `SUITE_WAIT_SECONDS` |
| `create plan HTTP 400 … server_metadata` | Remove `server_metadata` from that plan's `variant` — the plan sets it itself |
| One errored `(plan setup)` row | The suite refused to create that plan: an unreadable `config_file`, a rejected variant, or `profile: "smoke"` with no curated list for that plan. Remaining plans still run |
| Module stuck, then `timeout … WAITING` | The login did not complete, or the implicit submit never fired — check the flow trace in the report |
| `The user was authenticated on the initial visit to login page`, or a module waiting for `error=access_denied` | A few modules need driving that is not the happy path, and the harness carries a per-module table for them (`moduleBehaviors` in `internal/conformance/run.go`): `user-rejects-authentication` denies consent and follows the resulting error redirect back to the client, and `par-ensure-reused-request-uri…` stops at the login page on its first visit so the same `request_uri` can be driven a second time. A new module needing either has to be added there |
| `ESIGNET_BASE_URL_MISMATCH` | `esignet.base_url` disagrees with the authorize URL the suite discovered. Either correct it or leave it empty for a conformance-only run |
| Image pull fails for `server` / `httpd` | The pinned suite tag was pruned. Pick a current one from the [releases page](https://gitlab.com/openid/conformance-suite/-/releases) and set `SUITE_IMAGE_TAG` |

Full setup: [Conformance suite setup](conformance-suite.md).

---

## api surface

| Symptom | Likely cause |
|---|---|
| Every scenario fails on a connection error | `esignet.base_url` wrong or unreachable from where the harness runs |
| A validation case expected 200 but got 4xx | eSignet returns **HTTP 200 with a populated `errors` array** for client-management validation failures (the MOSIP API convention), not a 4xx. The features assert accordingly |
| PMS scenarios not run | MOSIP ID only, and needs `pms.base_url` — see [MOSIP ID](mosip-id.md) |
| TLS errors against a self-signed eSignet | Set `api.tls_verify: false` **as well as** `esignet.tls_verify: false` — the `api` surface is a separate Go module with its own setting |

---

## e2e surface

| Symptom | Likely cause |
|---|---|
| `no configured answer for flow input(s): …` | The flow asked for an input with no configured value. Expected for factors you have not credentialed; add them under `esignet.credentials` / `.knowledge`, or the scenario's `credentials` |
| `flow step rejected: FET-… (…)` | The deployment refused the submitted credential and left the flow `INCOMPLETE`. The driver reports the code rather than re-submitting the same step, which on the OTP path would spend a real attempt per retry and can trip a max-attempts lockout. Usually an unseeded user or a wrong password — distinct from a clean, asserted rejection |
| `authorize returned eSignet's error page: …` | eSignet refused the authorize request and sent the browser to its own `/error` route instead of redirecting to the client. The named `errorCode` is the server's reason — a required `code_challenge`, a PAR-only client approached directly, an unregistered `redirect_uri` |
| `unexpected alg "…" (want RS256 or PS256)` | A signed `userinfo` or ID token was issued under an algorithm the harness does not verify. Both RSA families are accepted; anything else is a target-side registration question |
| A negative case fails | Read it the right way round: `expect_login_failure` cases **pass when login is rejected**. A failure means a bad credential was wrongly *accepted* |
| A filter runs nothing | A filter matching zero scenarios is an error, not an empty run — a green "0 of 0" is indistinguishable from a real pass |
| The consent-reuse case fails after narrowing a run | Those cases are order-dependent; see [e2e scenario model](e2e-scenarios.md#filtering) |
| `userinfo` modules fail with "response is not a JSON object" | The deployment returns userinfo as a **signed JWT** rather than plain JSON. Check the client's `userinfo_response_type` registration and the deployment default — this is a target-side finding, not a harness one |

---

## Containers

| Symptom | Likely cause |
|---|---|
| The harness reports a config path is a directory | A bind mount whose source does not exist: Docker creates a **directory** at the target. Create the file first — `cp data/config/config.local.example.json data/config/config.local.json` |
| The run fails at the very end, writing the report | `./out` is not writable by the image's non-root uid 1001. Either `mkdir -p out && chown 1001:1001 out`, or put `user: "${HOST_UID}:${HOST_GID}"` on the `harness` service and start Compose with `HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose up` — Compose interpolates variables but does not run command substitution, so `$(id -u)` written into the YAML reaches Docker literally. Docker Desktop handles this for you |
| A value set in `.env` seems to be ignored | Only variables listed in the `harness` service's `environment:` block are passed through. Add it there first |
| A config file change has no effect in the container | Mounted files change without a rebuild; anything baked into the image (Go code, `run-all.sh`, the `data/` tree) needs a rebuild |

---

## Getting more detail

- `--check` — resolve and print the whole plan without running it.
- `ESIGNET_DEBUG=1` — stream each `/flow/execute` request and response to stderr live.
- `WSOTP_DEBUG=1` — print the first few raw OTP WebSocket frames ([MOSIP ID](mosip-id.md#dynamic-otp-retrieval)).
- `run.debug_show_secrets: true` — show the wire trace unredacted for a **local** run. The report
  then carries a red banner saying so. Never leave it on for anything CI archives.
