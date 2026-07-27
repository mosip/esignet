# eSignet API Test Harness (four surfaces, one report)

A black-box API test harness for a **running eSignet (thunder-go)** deployment. It runs **four
independent test surfaces** against a target and consolidates them into **one self-contained HTML
report per plugin**:

| Surface | What it does | How it runs |
|---|---|---|
| **conformance** | Drives the [OpenID Conformance Suite](https://gitlab.com/openid/conformance-suite)'s `oidcc-test-plan`. The suite is the OAuth client and does the grading; the harness plays "browser + user", walking `authorize → /flow/execute → auth/callback` and handing the code back. | `cmd/conformance` (Go, stdlib-only) |
| **client-mgmt** | Coverage of `/client-mgmt/client` (create/get/update): a positive create→get→update lifecycle plus negative/edge cases, admin-token authenticated. | `bdd/` godog module, `@client-mgmt` |
| **flow-execute** | Negative/edge cases the conformance suite can't reach (bad `executionId`, unknown flow type, `/flow/meta`). | `bdd/` godog module, `@flow-execute` |
| **e2e** | The harness acts as a **real, self-contained OAuth client**: register a throwaway client → `authorize` (PKCE) → login → `token` (`private_key_jwt`) → `userinfo`, asserting claims. Full ACR matrix (otp/password/bio/kbi), positive + negative. | `cmd/e2e` (Go, stdlib crypto) |

Design/background: [mosip/esignet#2120](https://github.com/mosip/esignet/issues/2120).
Plugin rollout order is **mock → sunbird → mosip** (by deployment-dependency weight).

> **All three plugins run on every surface.** `mock`/`mosip`/`sunbird` are accepted across
> conformance, bdd, and e2e. sunbird's environment prerequisites (a reachable KBI flow, a seeded
> registry/policy) surface at **runtime** as `ENV_NOT_READY` / failures, not as a hard config
> rejection.
>
> **mosipid specifics.** For `AUTHN_PROVIDER=mosip` the test OIDC client is registered through
> partner-management-service (`{PMS_BASE_URL}/oauth/client`) rather than eSignet client-mgmt, so IDA
> gets the partner+policy binding — set `PMS_BASE_URL`, `AUTH_PARTNER_ID`, `AUTH_POLICY_ID` (the
> partner + published policy must already be onboarded; PMS reuses the `KEYCLOAK_*` creds). And
> `OTP_SOURCE=dynamic` reads the live OTP from the mock-SMTP WebSocket (`OTP_WS_URL`, e.g.
> `https://smtp.<env>.mosip.net/`) instead of a static `TEST_OTP`. The 6-digit code is pulled from
> the message body (`\b\d{6}\b`); `OTP_RECIPIENT_EMAIL` filters to one recipient — an email **or a
> phone** (UIN OTPs arrive as SMS) — or leave it empty to take the newest fresh code (reliable for a
> single-identity run).
>
> **sunbird validated end-to-end, 2026-07-22** — all four surfaces green against a live sunbird
> deployment: conformance smoke (2/2), client-mgmt (8/8, incl. the positive lifecycle), flow/execute
> (4/4), e2e KBI positive+negative (2/2). The full 37-module conformance plan surfaces one open
> finding — see the troubleshooting table below (`userinfo` returns a JWT, not JSON).

---

## Layout

```
api-test/
  cmd/conformance/     conformance orchestrator  -> out/<...>.html + .json
  cmd/e2e/             e2e surface runner        -> out/e2e-envelope.json
  cmd/consolidate/     merge surface envelopes   -> out/<...>.html (one report)
  internal/            orchestrator, esignet driver, e2e client+crypto, report renderer, config, result
  bdd/                 SEPARATE nested module (godog + gjson): client-mgmt + flow-execute
    features/          PO-authored .feature files
  e2e-scenarios.json          e2e scenarios for mock   (default spec)
  e2e-scenarios-mosip.json    e2e scenarios for mosip
  e2e-scenarios-sunbird.json  e2e scenarios for sunbird
  run-all.sh           one command: run selected surfaces + consolidate
  config.json          conformance config (copy from config.example.json)
  Dockerfile           harness image (multi-stage: builds run-all.sh's binaries)
  docker-compose.yml   suite (mongodb+server+nginx) + harness, one command locally/CI
```

---

## Prerequisites (one-time, out of band)

Depending on which surfaces you run:

1. **eSignet reachable** (all surfaces) — `ESIGNET_BASE_URL`, e.g.
   `https://esignet-thunder1.esdev.mosip.net/v1/esignet`; must expose `/.well-known/openid-configuration`.
2. **A running conformance suite** (conformance surface only). Locally:
   ```bash
   cd /path/to/conformance-suite
   docker compose -f docker-compose-prebuilt.yml up -d
   curl -sk -o /dev/null -w "%{http_code}\n" https://localhost.emobix.co.uk:8443/api/runner/available  # 200 when ready
   ```
   Plus the two pre-registered static clients and the **suite plan config file** (private JWKS) —
   see the [Conformance config](#conformance-config-configjson) section.
3. **Keycloak admin credentials** (client-mgmt + e2e surfaces) — a client-credentials grant used
   to get a bearer token for `/client-mgmt/client`:
   `KEYCLOAK_TOKEN_URL`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`.
   The **e2e** surface registers its own throwaway client per run, so it needs no pre-provisioned client.
4. **A test identity** for the plugin under test (`AUTHN_PROVIDER` + `INDIVIDUAL_ID`/credentials).

---

## Running locally

### A. One command — all surfaces, one report (`run-all.sh`)

```bash
cd api-test
ESIGNET_BASE_URL=https://esignet-thunder1.esdev.mosip.net/v1/esignet \
PLUGIN=mock \
KEYCLOAK_TOKEN_URL=https://iam.esdev.mosip.net/auth/realms/mosip/protocol/openid-connect/token \
KEYCLOAK_CLIENT_ID=mosip-pms-client \
KEYCLOAK_CLIENT_SECRET=***your-secret*** \
./run-all.sh
```

Knobs (env): `PLUGIN` (mock|mosip|sunbird, default mock), `SURFACES` (comma list of
`conformance,bdd,e2e`, default all three), `CONFIG` (conformance config path, default `config.json`).
Runs in git-bash on Windows or any POSIX shell. It runs the chosen surfaces then calls
`cmd/consolidate` → one HTML report under `out/`.

> `run-all.sh` uses `e2e-scenarios.json` (mock) by default. For mosip/sunbird e2e, run `cmd/e2e`
> directly with `-spec e2e-scenarios-mosip.json` / `-sunbird.json` (see D), or set `E2E_SPEC`.

### B. Conformance surface only

```bash
cp config.example.json config.json   # first time; then edit it
go run ./cmd/conformance -config config.json
```
Exit: `0` all-clear · `1` a FAILED/errored module · `2` config/run error.

### C. bdd surfaces only (client-mgmt + flow-execute)

```bash
cd bdd
ESIGNET_BASE_URL=https://esignet-thunder1.esdev.mosip.net/v1/esignet AUTHN_PROVIDER=mock \
KEYCLOAK_TOKEN_URL=... KEYCLOAK_CLIENT_ID=mosip-pms-client KEYCLOAK_CLIENT_SECRET=*** \
go test ./... -run TestFeatures        # writes ../out/bdd-envelope.json
```
Without the `KEYCLOAK_*` vars, `@client-mgmt` is skipped and reported `ENV_NOT_READY`;
`@flow-execute` still runs. Narrow with `GODOG_TAGS=@flow-execute` (comma = OR).

### D. e2e surface only (per plugin)

```bash
# mock (default spec)
ESIGNET_BASE_URL=... AUTHN_PROVIDER=mock KEYCLOAK_TOKEN_URL=... KEYCLOAK_CLIENT_ID=mosip-pms-client KEYCLOAK_CLIENT_SECRET=*** \
go run ./cmd/e2e -spec e2e-scenarios.json -out out/e2e-envelope.json

# sunbird (KBI answers)
ESIGNET_BASE_URL=... AUTHN_PROVIDER=sunbird INDIVIDUAL_ID=<policy#> KBI_FULL_NAME="..." KBI_DOB=YYYY-MM-DD KEYCLOAK_...=... \
go run ./cmd/e2e -spec e2e-scenarios-sunbird.json -out out/e2e-envelope-sunbird.json
```

### E. Consolidate surfaces you've already run

```bash
go run ./cmd/consolidate \
  -conformance out/oidcc-test-plan_mock_<ts>.json \
  -bdd out/bdd-envelope.json \
  -e2e out/e2e-envelope.json \
  -plugin mock -out out
```
Any of `-conformance`/`-bdd`/`-e2e` may be omitted; whatever is passed is merged into one report.

### F. Through an IDE

**VS Code** — add `.vscode/launch.json` (a starter is committed with empty secrets; fill
`KEYCLOAK_CLIENT_SECRET` locally, don't commit real secrets):

```jsonc
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "conformance",
      "type": "go", "request": "launch", "mode": "auto",
      "program": "${workspaceFolder}/api-test/cmd/conformance",
      "cwd": "${workspaceFolder}/api-test",
      "args": ["-config", "config.json"]
    },
    {
      "name": "e2e (mock)",
      "type": "go", "request": "launch", "mode": "auto",
      "program": "${workspaceFolder}/api-test/cmd/e2e",
      "cwd": "${workspaceFolder}/api-test",
      "args": ["-spec", "e2e-scenarios.json", "-out", "out/e2e-envelope.json"],
      "env": {
        "ESIGNET_BASE_URL": "https://esignet-thunder1.esdev.mosip.net/v1/esignet",
        "AUTHN_PROVIDER": "mock",
        "KEYCLOAK_TOKEN_URL": "https://iam.esdev.mosip.net/auth/realms/mosip/protocol/openid-connect/token",
        "KEYCLOAK_CLIENT_ID": "mosip-pms-client",
        "KEYCLOAK_CLIENT_SECRET": ""
      }
    },
    {
      "name": "bdd (client-mgmt + flow-execute)",
      "type": "go", "request": "launch", "mode": "test",
      "program": "${workspaceFolder}/api-test/bdd",
      "cwd": "${workspaceFolder}/api-test/bdd",
      "args": ["-test.run", "TestFeatures", "-test.v"],
      "env": {
        "ESIGNET_BASE_URL": "https://esignet-thunder1.esdev.mosip.net/v1/esignet",
        "AUTHN_PROVIDER": "mock",
        "KEYCLOAK_TOKEN_URL": "https://iam.esdev.mosip.net/auth/realms/mosip/protocol/openid-connect/token",
        "KEYCLOAK_CLIENT_ID": "mosip-pms-client",
        "KEYCLOAK_CLIENT_SECRET": ""
      }
    }
  ]
}
```
Pick the config in the Run and Debug panel and hit ▶. (For mosip/sunbird e2e, copy the `e2e (mock)`
config, swap `-spec` and the `AUTHN_PROVIDER`/identity env.)

**GoLand / IntelliJ** — two run configs:
- **Go Build**: Run kind *Package*, Package path `.../api-test/cmd/e2e` (or `cmd/conformance`),
  Working directory `.../api-test`, Program arguments `-spec e2e-scenarios.json -out out/e2e-envelope.json`,
  and add the same `ESIGNET_BASE_URL` / `AUTHN_PROVIDER` / `KEYCLOAK_*` **Environment** variables.
- **Go Test** (for bdd): Test kind *Package*, Directory `.../api-test/bdd`, Pattern `TestFeatures`,
  Working directory `.../api-test/bdd`, same env vars.

### G. Docker Compose — suite + harness, one command

`docker-compose.yml` brings up the OpenID Conformance Suite (mongodb + server + nginx, service
definitions mirror the suite's own `docker-compose-prebuilt.yml`) alongside the harness image built
from the `Dockerfile` in this directory, and runs `run-all.sh` inside it. The harness image is a
multi-stage build: `cmd/conformance`/`cmd/e2e`/`cmd/consolidate` are built as static Go binaries, and
the `bdd/` module is compiled ahead of time to a standalone test binary (`go test -c`) — the runtime
image needs no Go toolchain. `run-all.sh` picks up the prebuilt binaries automatically via `BIN_DIR`
(unset locally, so non-container runs still use `go run`/`go test` from source).

Prerequisites are the same as local runs: the suite's two pre-registered static clients, and
`conformance-suite-private/esignet-config.json` (the private-jwks plan config, §2 above) must already
exist on the host — compose only brings the suite *process* up, it doesn't provision the plan.

**This compose file does not bring up eSignet itself** — that's a separate concern (`../esignet-service`
has its own run path: `make.sh run`, `go run ./cmd/esignet`, or its own compose). Point `ESIGNET_BASE_URL`
at whichever eSignet you want to test — a deployed environment, or one you're running yourself:

```bash
cd api-test
cp .env.example .env
# .env: ESIGNET_BASE_URL=https://esignet-thunder1.esdev.mosip.net/v1/esignet     (deployed), or
# .env: ESIGNET_BASE_URL=http://host.docker.internal:8080                       (self-run on this host)
docker compose up --build --abort-on-container-exit --exit-code-from harness
```

The consolidated report lands in `./out` on the host (bind-mounted into the container). Exit code of
the `up` command matches the harness's (`0` all-clear, `1` a failure — including a surface that died
before writing its envelope, `2` a config/run error — same as running `run-all.sh` directly). Knobs:
`PLUGIN`, `SURFACES`, `TEST_PROFILE`, `SUITE_WAIT_SECONDS` (how long the harness polls the suite's
`/api/runner/available` before giving up and running anyway — default 150s, covers the suite's
Mongo+Java cold start), `SUITE_IMAGE_TAG` (the suite image tag — keep it in step with `suite_version`
in `profiles/oidcc-test-plan.smoke.json`, which the smoke allow-list is curated against),
`CONFORMANCE_TLS_VERIFY` / `BDD_TLS_VERIFY`. `httpd` carries the network alias
`localhost.emobix.co.uk` (the suite's own `BASE_URL`) so the harness container resolves it to the
suite instead of its own loopback.

> **Bind-mount ownership (Linux).** The image runs as non-root uid 1001, while `./out` is
> bind-mounted from the host with the host's ownership. If the host directory isn't writable by
> uid 1001 the run fails at the very end, when the report is written. Either `mkdir -p out &&
> chown 1001:1001 out` beforehand, or add `user: "$(id -u):$(id -g)"` to the `harness` service.
> Docker Desktop (macOS/Windows) handles this for you.

To rerun without rebuilding: `docker compose up` (drop `--build`). To tear down (including the suite's
Mongo volume): `docker compose down -v`.

---

## Conformance config (`config.json`)

The conformance surface reads `config.json` (env vars override the file). The suite is the OAuth
client, so eSignet's discovery URL + client ids + **private** JWKS live in the *suite plan config
file* referenced by `plan.config_file`, not here.

```json
{
  "conformance": { "base_url": "https://localhost.emobix.co.uk:8443", "tls_verify": false, "token": "" },
  "plan": {
    "name": "oidcc-test-plan",
    "variant": { "client_auth_type": "private_key_jwt", "response_type": "code",
                 "response_mode": "default", "client_registration": "static_client" },
    "config_file": "../conformance-suite-private/esignet-config.json"
  },
  "esignet": {
    "base_url": "", "provider": "mock", "auth_factor": "otp",
    "identity":    { "individual_id": "+912532509749", "id_type": "phone" },
    "credentials": { "username": "", "password": "" },
    "knowledge":   { "full_name": "", "dob": "" },
    "otp": { "source": "static", "value": "111111", "ws_url": "", "recipient_email": "" }
  },
  "run": {
    "modules": [], "profile": "full", "filter": "",
    "skip": [], "known_issues": [],
    "poll_interval_seconds": 2, "timeout_seconds": 60, "fail_fast": false, "report_dir": "out"
  }
}
```
- `plan.variant` must **not** include `server_metadata` (the plan sets it; passing it → HTTP 400).
- `esignet.base_url` empty ⇒ derived from the suite's authorize URL; if set, it's validated
  against it (mismatch → `ESIGNET_BASE_URL_MISMATCH`).
- `run.profile` `smoke` (curated allow-list) or `full` (all modules; undrivable ones →
  `SKIPPED_BY_HARNESS`). `tls_verify:false` needed for the suite's self-signed local cert.

### Env vars (conformance surface — override the file)

| Env var | Overrides | | Env var | Overrides |
|---|---|---|---|---|
| `CONFORMANCE_BASE_URL` | `conformance.base_url` | | `AUTH_FACTOR` | `esignet.auth_factor` |
| `CONFORMANCE_TLS_VERIFY` | `conformance.tls_verify` | | `INDIVIDUAL_ID` | `esignet.identity.individual_id` |
| `PLAN_CONFIG_PATH` | `plan.config_file` (secret) | | `TEST_OTP` | `esignet.otp.value` |
| `ESIGNET_BASE_URL` | `esignet.base_url` | | `TEST_PROFILE` | `run.profile` |
| `AUTHN_PROVIDER` | `esignet.provider` (`mock`\|`mosip`\|`sunbird`) | | `TEST_RUN` | `run.filter` (module regex) |
| `TEST_USERNAME`/`TEST_PASSWORD` | `esignet.credentials.*` | | `TIMEOUT_SECONDS`/`FAIL_FAST` | `run.*` |

---

## e2e scenario model (per-ACR, positive + negative)

`cmd/e2e` reads a scenario JSON (`-spec`). One throwaway client is registered with `user_claims` +
`acr`, then **each scenario drives its own ACR** through the full chain and asserts the outcome.

```json
{
  "redirect_uri": "https://bdd-e2e.example.org/callback",
  "user_claims": ["name", "email", "phone_number", "gender", "birthdate"],
  "acr": ["mosip:idp:acr:generated-code", "mosip:idp:acr:password", "mosip:idp:acr:biometrics"],
  "scenarios": [
    { "name": "otp positive: userinfo returns sub", "auth_factor": "otp",
      "scopes": ["openid"], "expect_present": ["sub"] },
    { "name": "otp negative: wrong OTP is rejected", "auth_factor": "otp",
      "expect_login_failure": true, "credentials": { "otp": "000000" }, "scopes": ["openid"] },
    { "name": "password positive", "auth_factor": "password",
      "credentials": { "username": "decl-user-1", "password": "..." },
      "scopes": ["openid"], "expect_present": ["sub"] }
  ]
}
```

Per-scenario fields:
- **`auth_factor`** (required) — `otp | password | bio | kbi`; selects the ACR at the login step.
- **`credentials`** — overrides the base identity answers for just this scenario (e.g. a wrong OTP
  for a negative case, or a real password for a positive one). Keys: `username`, `password`,
  `otp`, `fullName`, `dob`.
- **`expect_login_failure`** — negative case: **passes when login is correctly rejected**, fails if
  a bad credential is wrongly accepted. Omitted ⇒ positive: login **must** succeed (a failure —
  including "no credential configured for this ACR yet" — is reported **FAILED**, not skipped, so
  the case stays visible until real credentials exist).
- `scopes`, `userinfo_claims`, `expect_present`, `expect_values`, `expect_absent` — claim checks.

> **Keep-in-place-and-fail is intentional.** ACRs with no working credential on the target (e.g.
> `bio`, or `password` where the user isn't seeded) are kept as scenarios and reported FAILED with
> a clear reason, rather than being omitted. They go green once real credentials/config exist.

e2e env: `ESIGNET_BASE_URL`, `AUTHN_PROVIDER`, `INDIVIDUAL_ID`, `ID_TYPE`, `TEST_OTP`,
`TEST_USERNAME`/`TEST_PASSWORD`, `KBI_FULL_NAME`/`KBI_DOB`, `KEYCLOAK_*`, `E2E_SPEC`, `BDD_TLS_VERIFY`.

> **`INDIVIDUAL_ID` is required for every plugin except `mock`.** The mock plugin falls back to its
> seeded synthetic identity so a first run works out of the box; against any real deployment the e2e
> surface fails fast rather than silently authenticating as — and reporting claims for — whoever owns
> a baked-in identifier.

TLS verification for the bdd and e2e surfaces (`BDD_TLS_VERIFY`) and for the conformance
surface (`CONFORMANCE_TLS_VERIFY` / `conformance.tls_verify`) is **on unless explicitly set to
`false`**, so a run against a real deployment never sends credentials over an unverified link.
Set it to `false` only for self-signed dev certs. Compose defaults `CONFORMANCE_TLS_VERIFY=false`
for the bundled suite's self-signed cert — override it when pointing `CONFORMANCE_BASE_URL` at a
remote suite.

Reports are archived as CI artifacts, so the harness redacts before writing: credential and session
headers (`Authorization`, `Cookie`/`Set-Cookie`, `X-API-KEY`), cookie values (names and `Set-Cookie`
attributes stay readable), JSON *and* form-encoded body fields matching `password`/`otp`/`assertion`/
`secret`/`token`, and `code`/`code_verifier`/`id_token`/`access_token`/`refresh_token` in both URL
query strings and fragments. The conformance suite's own log entries get the same treatment — they
carry whole HTTP exchanges, so a detail keyed by a credential name is masked outright and the rest
is run through the body/URL scrubbers. Values are masked whether they are strings or bare numbers.
`Config.Redacted()` additionally masks the OTP value, individual id, recipient, full name and DOB.

> **Identity redaction is request-only, by design.** The login inputs the driver posts to
> `/flow/execute` (`individualId`, `username`, `fullName`, `dob`) are authenticators and are masked
> in *request* bodies, matched on exact key so the client-mgmt trace keeps `clientName`. The
> userinfo *response* claims are left readable — proving the right claims came back is precisely
> what the e2e surface exists to evidence. Treat the report as containing subject claims and handle
> the artifact accordingly.

---

## Selecting what runs

- **Which surfaces** — `run-all.sh`'s `SURFACES` (`conformance,bdd,e2e`).
- **Conformance modules** — `run.profile` (`smoke`/`full`) → `run.filter` (regex) → `run.modules`
  (exact list, overrides profile). `run.skip` (→ **Skipped** bucket) and `run.known_issues`
  (→ **Known** bucket, with reason) carve modules out of execution without touching the exit code.
- **Gherkin scenarios** — `GODOG_TAGS` (e.g. `@client-mgmt`, `@flow-execute`, `@sunbird`; comma = OR).

---

## Report

One self-contained, light/dark HTML file under `out/`, filename encoding plan/provider/timestamp/counts:
```
out/oidcc-test-plan_mock_20260721-195317_p-30_f-10_sk-1_ki-0.html   (+ .json sidecar)
```
Contents:
- **Overall tiles** + a **section per surface** (Conformance · Client-mgmt · flow/execute · E2E),
  each with its own tiles.
- **Validation tab** on each client-mgmt/flow-execute/e2e case — a table of **every** expected-vs-actual
  check performed (HTTP status, JSON field, claim, login accepted/rejected), pass **and** fail, not
  just the final status code. Auto-opens when a check fails. (Conformance rows keep the suite's own
  structured log instead.)
- **Expand / collapse controls** — whole-report and per-section "Collapse all sections", plus
  per-section "Expand/Collapse rows", and each section heading is itself a collapsible toggle.
- **Drill-downs**: failure findings, the suite condition log (conformance), the eSignet flow trace,
  and the full eSignet request/response API-call trace (bearer token redacted, repeats collapsed `×N`).

Set `ESIGNET_DEBUG=1` to stream each `/flow/execute` request/response to stderr live.

---

## Build / test

```bash
go build ./... && go vet ./...          # parent module (conformance + e2e + consolidate)
( cd bdd && go vet ./... )               # nested bdd module
```
The parent module is **stdlib-only**. The `bdd/` module pins godog + gjson, kept isolated in its
own `go.mod`. Local/CI containerized runs are covered below; Rancher/CronJob in-cluster scheduling
is tracked separately (plan doc §8g, mosip/esignet#2120 Phase 5+).

---

## CI (GitHub Actions)

`.github/workflows/push-trigger.yml` builds and publishes the harness image — it does **not** run the
tests. Two jobs, alongside the existing `esignet`/`oidc-ui` ones:
- **`build_go_apitest`** — `go build`/`go vet` both modules (the Go equivalent of the retired Java rig's
  `build_maven_apitest_esignet`).
- **`build_dockers_apitest`** — builds/pushes the `apitest-esignet` image via the same
  `mosip/kattu/.github/workflows/docker-build.yml` reusable workflow (`ONLY_DOCKER`) every other service
  image in this repo uses, on the same triggers (PR/push/release/workflow_dispatch).

Scheduled/on-demand test *execution* against that image happens outside GitHub Actions — on Rancher,
the same way the `apitestrig` Helm chart already schedules the legacy image (`docker run
apitest-esignet:<tag> ...`, env-configured per `docker-compose.yml`'s `harness` service above, or a
CronJob once that lands — plan doc §8g / mosip/esignet#2120 Phase 5+).

Report storage (S3-compatible upload vs. mounted volume) is not implemented in the Go harness yet.
The legacy Java rig's equivalent (`apitest-commons`' `S3Adapter`, config keys `s3-host`/`s3-region`/
`s3-user-key`/`s3-user-secret`/`s3-account`/`push-reports-to-s3`) calls the AWS S3 SDK directly from
the test process after a run — not a wrapper script — so a faithful port means the same from
`cmd/consolidate`, which would need either hand-rolled SigV4 signing (keeps the parent module
stdlib-only) or an AWS SDK dependency (breaks that invariant). Deferred until the Rancher run path
itself is being built; `out/` (bind-mounted locally, a workflow artifact in CI) is the fallback today.

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `ENV_NOT_READY` / suite not available | conformance suite not up (nginx 502 until the Java server boots) |
| `create plan HTTP 400 … server_metadata` | remove `server_metadata` from `plan.variant` |
| sunbird conformance run `ENV_NOT_READY` / login failures | sunbird prerequisites not met on the target (KBI flow / seeded registry) — a runtime setup gap, not a config rejection |
| module stuck → `timeout … WAITING` | implicit-submit didn't fire, or the login didn't complete — check the flow trace |
| `no configured answer for flow input(s): …` | the flow asked for an input with no config value (add `credentials`/identity) — expected for uncredentialed ACRs |
| e2e login loops `INCOMPLETE` then fails | the submitted credential isn't authenticating (user not seeded / wrong password) — not a "clean rejection" |
| client-mgmt reports `ENV_NOT_READY` | `KEYCLOAK_*` env not set (admin token unavailable) |
| sunbird conformance: `scope-*`/`userinfo-*` modules FAILED (`UserInfo endpoint response is not a JSON object`) | that plugin's `pm-client` (or the deployment) returns userinfo as a **signed JWT**, not plain JSON — mock returns JSON so these pass there. Open finding as of 2026-07-22; check the client's `userinfo_response_type` registration / deployment default, not the harness. |
