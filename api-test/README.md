# eSignet API Test Harness (four surfaces, one report)

A black-box API test harness for a **running eSignet (thunder-go)** deployment. It runs **four
independent test surfaces** against a target and consolidates them into **one self-contained HTML
report per plugin**:

| Surface | What it does | How it runs |
|---|---|---|
| **conformance** | Drives the [OpenID Conformance Suite](https://gitlab.com/openid/conformance-suite)'s `oidcc-test-plan`. The suite is the OAuth client and does the grading; the harness plays "browser + user", walking `authorize → /flow/execute → auth/callback` and handing the code back. | `cmd/conformance` (Go, stdlib-only) |
| **client-mgmt** | Coverage of `/client-mgmt/client` (create/get/update): a positive create→get→update lifecycle plus negative/edge cases, admin-token authenticated. Includes per-client **consent configuration** validation (`consent_expire_in_mins`, `purpose`), and mosipid client registration through PMS. | `bdd/` godog module, `@client-mgmt`, `@client-mgmt-pms` |
| **flow-execute** | Negative/edge cases the conformance suite can't reach (bad `executionId`, unknown flow type, `/flow/meta`, authorize negatives). | `bdd/` godog module, `@flow-execute`, `@flow-authz-neg` |
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
> (4/4), e2e KBI positive+negative (2/2). Those counts are the suite **as it stood that day**; it has
> grown since (consent + captcha coverage), so a rerun reports more cases. The full 37-module
> conformance plan surfaces one open finding — see the troubleshooting table below (`userinfo`
> returns a JWT, not JSON).

---

## Quick start

The shortest useful run is the **e2e** surface against the **mock** plugin: no conformance suite, no
plan config, no test identity of your own — mock's seeded identity and login ship in
`config.mock.json`.

```bash
cd api-test
cp config.local.example.json config.local.json    # set esignet.base_url + keycloak.client_secret
./run-all.sh -c config.mock.json -s e2e --check   # what WOULD run; nothing executes
./run-all.sh -c config.mock.json -s e2e           # run it -> out/<...>.html
```

Widen one step at a time, `--check` first every time:

| to run | use | what it additionally needs |
|---|---|---|
| e2e | `-s e2e` | Keycloak client-credentials (it registers a throwaway client per run) |
| \+ client-mgmt / flow-execute | `-s e2e,bdd` | nothing more |
| \+ conformance | drop `-s` (runs `run.surfaces`) | a running suite **and** [`conformance-suite-private/*.json`](#conformance-suite-plan-config-conformance-suite-private) — the one genuinely fiddly prerequisite |
| another plugin | `-c config.mosip.json` | that plugin's identity/credentials in `config.local.json` |

If you read one section before your first run, make it
[Configuration model](#configuration-model) — it explains which file a given value belongs in.

**Contents** — [Layout](#layout) · [Configuration model](#configuration-model) ·
[Prerequisites](#prerequisites-one-time-out-of-band) · [Running locally](#running-locally) ·
[Config reference](#config-reference) · [Env overrides](#env-overrides) ·
[Rancher / `docker run`](#rancher--plain-docker-run) ·
[e2e scenario model](#e2e-scenario-model-per-acr-positive--negative) ·
[Selecting what runs](#selecting-what-runs) · [Report](#report) · [CI](#ci-github-actions) ·
[Troubleshooting](#troubleshooting)

---

## Layout

```
api-test/
  cmd/conformance/     conformance orchestrator  -> out/<...>.html + .json
  cmd/e2e/             e2e surface runner        -> out/e2e-envelope.json
  cmd/consolidate/     merge surface envelopes   -> out/<...>.html (one report)
  internal/            orchestrator, esignet driver, e2e client+crypto, report renderer,
                       config, result, httpx, wsotp (live OTP off the mock-SMTP WebSocket)
  bdd/                 SEPARATE nested module (godog + gjson): client-mgmt + flow-execute
    features/          PO-authored .feature files (tags below)
  e2e-scenarios.json          e2e scenarios for mock   (default spec)
  e2e-scenarios-mosip.json    e2e scenarios for mosip
  e2e-scenarios-sunbird.json  e2e scenarios for sunbird
  cmd/cfg/             resolve the config: -check, -print-env, -get
  run-all.sh           one command: run selected surfaces + consolidate

  config.mock.json          TRACKED per-plugin config: what the run does
  config.mosip.json         (surfaces, modules, tags, scenarios, auth factor)
  config.sunbird.json
  config.example.json       annotated schema reference for the three above
  config.local.example.json template for the overlay below
  config.local.json         GITIGNORED: your credentials + machine URLs
  plan-config.example.json  placeholder template for the suite plan config
  conformance-suite-private/ GITIGNORED: filled-in plan configs (private jwks)

  Dockerfile           harness image (multi-stage: builds run-all.sh's binaries)
  docker-compose.yml   suite (mongodb+server+nginx) + harness, one command locally/CI
```

`out/` above is the default output directory; `run.report_dir` (`REPORT_DIR`) moves it. Only
`cmd/conformance` reads the config directly, so `run-all.sh` threads the resolved value to the
other surfaces as `-out` / `BDD_ENVELOPE_OUT` — that way **every** surface writes into the one
directory compose bind-mounts. Running a surface by hand (§ below) bypasses that plumbing and uses
each binary's own `out/` default.

### Configuration model

One file per plugin, layered with a gitignored overlay. Lowest precedence first:

| layer | tracked? | holds |
|---|---|---|
| `config.<plugin>.json` | **yes** | what the test *does*: surfaces, auth factor, modules, bdd tags, e2e scenario filters |
| `config.local.json` | no | what is *yours*: credentials, target URLs, test identity |
| environment | — | always wins; how compose/Rancher inject secrets |
| `defaults()` | — | fills anything still empty |

Each layer overrides only the keys it names, at any depth. The split exists so
you **never edit a tracked file to add a secret** — a dirty `config.mosip.json`
always means a real change worth reviewing, and test selection stays under review
because it defines what "passing" means.

First run:

```bash
cd api-test
cp config.local.example.json config.local.json     # fill in ~4 fields
./run-all.sh -c config.mosip.json --check          # confirm what would run
./run-all.sh -c config.mosip.json                  # run it
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
   see [Conformance suite plan config](#conformance-suite-plan-config-conformance-suite-private)
   below.
3. **Keycloak admin credentials** (client-mgmt + e2e surfaces) — a client-credentials grant used
   to get a bearer token for `/client-mgmt/client`:
   `KEYCLOAK_TOKEN_URL`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`.
   The **e2e** surface registers its own throwaway client per run, so it needs no pre-provisioned client.
4. **A test identity** for the plugin under test (`AUTHN_PROVIDER` + `INDIVIDUAL_ID`/credentials).

### Conformance suite plan config (`conformance-suite-private/`)

The conformance surface needs one file per plan under the **gitignored**
`conformance-suite-private/` directory. The files themselves are never in git —
they hold **private** key material — so `run-all.sh`/`docker compose` cannot
create them: a fresh clone (or a fresh target environment) has to fill them in
once, out of band, before the conformance surface can run.

The *shape* is tracked, as a placeholder-only template:

```bash
cd api-test
mkdir -p conformance-suite-private
cp plan-config.example.json conformance-suite-private/esignet-config.json
cp plan-config.example.json conformance-suite-private/esignet-fapi2-config.json
# then replace every REPLACE-WITH-* value in each copy
```

**Both** files are needed as shipped: `config.mock/sunbird/mosip.json` each declare
two plans (`oidcc-test-plan` + `fapi2-security-profile-final-test-plan`). To run
only the first, drop the second `plans[]` entry — or point both `config_file`
values at the same file when the same clients are registered for both.

`plan-config.example.json` is tracked precisely because it contains **no** key
material — every field is a `REPLACE-WITH-*` placeholder. It is named so it
matches neither `.gitignore` pattern (`*esignet-config*.json`,
`conformance-suite-private/`); keep it that way, or the template starts being
ignored and the next person is back to hand-writing it.

What the file is: the exact JSON the harness reads from `plans[].config_file` and
POSTs as-is to the suite's create-plan API (`internal/orchestrator/run.go`) — the
suite's own "static client" plan-registration format. Fields to fill:

| field | value |
|---|---|
| `alias` | the suite's callback path segment: `<CONFORMANCE_BASE_URL>/test/a/<alias>/callback`. Keep the template's `esignet-test` unless you have a reason to change it — the registered redirect URI must match. |
| `server.discoveryUrl` | `<ESIGNET_BASE_URL>/.well-known/openid-configuration` |
| `client.client_id` / `client2.client_id` | the client ids registered in eSignet (below) |
| `client.jwks` / `client2.jwks` | the **full private** RSA JWK — `n`,`e`,`d`,`p`,`q`,`dp`,`dq`,`qi` — `alg: PS256`, one per client |

- `oidcc-test-plan` only needs `client`; `fapi2-security-profile-final-test-plan`
  uses two clients, hence `client2`. The template carries both, so one template
  serves both files — leave `client2` filled in (harmless) or drop it from the
  oidcc copy.
- Only `profiles/oidcc-test-plan.smoke.json` exists, so a plan with no curated
  smoke list of its own must pin `"profile": "full"` in its `plans[]` entry (all
  three plugin configs already do this for the FAPI plan). Left on
  `run.profile: "smoke"` it looks for a `profiles/<plan name>.smoke.json` that
  isn't there and becomes one errored `(plan setup)` row.
- The **private** JWKS lives here because the suite (not the harness) signs the
  `private_key_jwt` client assertion itself when it drives the flow — this file
  is the credential that lets it act as that OAuth client.

To produce the values, once per target eSignet environment:

1. Generate an RSA keypair per client as a JWK (PS256, matching the `alg` eSignet
   expects for `private_key_jwt`), keeping the **full private** JWK for this file
   and the **public** half for step 2. Two keypairs for the FAPI plan
   (`client` + `client2`), one for an oidcc-only run. Any JWK generator works —
   the private key is test-only, but it is still a key: prefer generating locally
   over pasting it into a web service.
2. Register each `client_id` in eSignet (client-mgmt) as `private_key_jwt`, with
   the **public** JWK from step 1 and a redirect URI matching the `alias`
   callback path above.
3. Fill the copies from the `cp` step with those values.

The exact client-registration steps are eSignet/client-mgmt specifics, and the
plan-config shape is the suite's own — see the
[OpenID Conformance Suite](https://gitlab.com/openid/conformance-suite) docs for
the authoritative reference if either changes. Treat the resulting files as
long-lived infrastructure credentials, same as `KEYCLOAK_CLIENT_SECRET`: never
commit them, and see [Rancher / plain `docker run`](#rancher--plain-docker-run)
for mounting them as a Kubernetes Secret instead of a bind mount in a shared
cluster.

---

## Running locally

### A. One command — selected surfaces, one report (`run-all.sh`)

```bash
cd api-test
./run-all.sh -c config.mosip.json
```

Runs in git-bash on Windows or any POSIX shell. It runs the surfaces listed in
the config's `run.surfaces`, then calls `cmd/consolidate` → one HTML report under
`out/`.

| flag | effect |
|---|---|
| `-c, --config PATH` | which plugin config to run (or `CONFIG=...`) |
| `-s, --surfaces LIST` | narrow to `conformance,bdd,e2e` for this run only |
| `--check` | print the resolved plan and exit without running |
| `-h, --help` | the script's own header block — flags plus the `BIN_DIR`/`SUITE_WAIT_SECONDS` env it reads |

A config named explicitly **must exist** — `-c config.msoip.json` is an error,
not a silent fallback, because falling back would run mock while you believe
mosip ran and the green report would be read as mosip passing.

`--check` shows exactly what will happen before a long run starts. It needs nothing running and
never fails on a missing file: anything it cannot resolve is reported in place as an
`ENV_NOT_READY` line, so on a fresh clone the output *is* the setup checklist. It still exits
non-zero when the run could not start, with a closing `NOT RUNNABLE YET` line naming the blocker.

```
config    config.mosip.json + config.local.json + 2 env override(s)
plugin    mosip
surfaces  conformance, bdd, e2e
esignet   https://esignet-thunder1.esdev.mosip.net/v1/esignet (tls_verify=true)

conformance
  suite       https://localhost.emobix.co.uk:8443 (tls_verify=false)
  auth factor otp
  plans       2, run in this order

  1. plan      oidcc-test-plan
     config    conformance-suite-private/esignet-config.json
     variant   client_auth_type=private_key_jwt, client_registration=static_client, ...
     modules   profile=smoke

  2. plan      fapi2-security-profile-final-test-plan
     config    conformance-suite-private/esignet-fapi2-config.json
     variant   authorization_request_type=simple, client_auth_type=private_key_jwt, ...
     modules   profile=full
bdd
  tags        (auto — chosen from configured credentials)
  client-mgmt will run
  authz-neg   ENV_NOT_READY: no bdd.flow_client_id
e2e
  spec        e2e-scenarios-mosip.json
  scenarios   4 of 6
                otp positive: MOSIP send-OTP -> verify -> userinfo returns sub
                otp negative: wrong OTP is rejected
                ...
```

### B. Conformance surface only

```bash
go run ./cmd/conformance -config config.mosip.json
```
Exit: `0` all-clear · `1` a FAILED/errored module · `2` config/run error.

Conformance drives **one auth factor per run** (`esignet.auth_factor`). To cover
another, change that field and run again.

### C. bdd surfaces only (client-mgmt + flow-execute)

`bdd/` is a separate Go module and cannot import `internal/config`, so it reads
environment variables. `cmd/cfg -print-env` renders the config into exactly those:

```bash
eval "$(go run ./cmd/cfg -config config.mosip.json -print-env)"
cd bdd && go test ./... -run TestFeatures        # writes ../out/bdd-envelope.json
```

Narrow with `bdd.tags` in the config (comma = OR). The four tags in use, and
which report section each lands in:

| tag | features | report surface | auto-selected when `bdd.tags` is empty |
|---|---|---|---|
| `@flow-execute` | [`/flow/meta`](bdd/features/flow-execute/meta.feature), [`/flow/execute` negatives](bdd/features/flow-execute/negative.feature) | flow/execute | **always** — needs nothing but a reachable eSignet |
| `@flow-authz-neg` | [authorize negatives](bdd/features/flow-execute/authorize-negative.feature) | flow/execute | `bdd.flow_client_id` set (they need a pre-registered client to drive) |
| `@client-mgmt` | create / get / update lifecycle, validation, [consent-config](bdd/features/client-mgmt/consent-config.feature) | Client-mgmt | `keycloak.client_secret` set |
| `@client-mgmt-pms` | [mosipid registration via PMS](bdd/features/client-mgmt/create-update-client-pms.feature) | Client-mgmt | the above **and** `provider=mosip` **and** `esignet.pms.base_url` — never runs for mock/sunbird |

Empty `bdd.tags` does **not** mean "run everything": it means "run what the configured
credentials can actually drive", per the last column. Whatever gets gated out is still
reported, as an explicit `ENV_NOT_READY` row — an omitted section would read as a false
green during sign-off. Naming tags yourself turns the auto-selection off entirely, so
`bdd.tags: "@client-mgmt-pms"` runs that feature (and lets it fail) regardless of provider.

### D. e2e surface only (per plugin)

```bash
go run ./cmd/e2e -config config.mosip.json
```

The spec comes from `e2e.spec`; override with `-spec` for a one-off. Every
scenario in the spec runs by default, each driving the ACR it declares. Narrow it
with the `e2e` block:

```jsonc
"e2e": {
  "spec": "e2e-scenarios-mosip.json",
  "auth_factors": ["otp", "password"],   // only these ACRs
  "include": ["^otp positive"],          // regex on scenario name, OR-ed
  "exclude": ["bio"]                     // applied last, always wins
}
```

A filter matching zero scenarios is an **error**, not an empty run — a green
"0 scenarios, 0 failed" is indistinguishable from a clean pass.

### E. Consolidate surfaces you've already run

```bash
go run ./cmd/consolidate \
  -conformance out/conformance_mock_<ts>.json \
  -bdd out/bdd-envelope.json \
  -e2e out/e2e-envelope.json \
  -plugin mock -out out
```
Any of `-conformance`/`-bdd`/`-e2e` may be omitted; whatever is passed is merged into one report.

### F. Through an IDE

**VS Code** — the repo's committed [`.vscode/launch.json`](../.vscode/launch.json) has only a
`Debug esignet` entry for the service; add these three alongside it (keep `KEYCLOAK_CLIENT_SECRET`
empty in anything you commit):

```jsonc
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "conformance",
      "type": "go", "request": "launch", "mode": "auto",
      "program": "${workspaceFolder}/api-test/cmd/conformance",
      "cwd": "${workspaceFolder}/api-test",
      "args": ["-config", "config.mock.json"]
    },
    {
      "name": "e2e (mock)",
      "type": "go", "request": "launch", "mode": "auto",
      "program": "${workspaceFolder}/api-test/cmd/e2e",
      "cwd": "${workspaceFolder}/api-test",
      "args": ["-config", "config.mock.json"]
    },
    {
      "name": "bdd (client-mgmt + flow-execute)",
      "type": "go", "request": "launch", "mode": "test",
      "program": "${workspaceFolder}/api-test/bdd",
      "cwd": "${workspaceFolder}/api-test/bdd",
      "args": ["-test.run", "TestFeatures", "-test.v"],
      "env": {
        "_comment": "bdd is a separate module and cannot read the config; render it with",
        "_comment2": "`go run ./cmd/cfg -config config.mock.json -print-env` and paste the values here",
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
Pick the config in the Run and Debug panel and hit ▶. For mosip/sunbird, copy the
`conformance` / `e2e (mock)` entries and swap the `-config` argument — nothing
else changes, since the config file carries the identity and the scenario spec.

**GoLand / IntelliJ** — two run configs:
- **Go Build**: Run kind *Package*, Package path `.../api-test/cmd/e2e` (or `cmd/conformance`),
  Working directory `.../api-test`, Program arguments `-config config.mock.json`. No env needed.
- **Go Test** (for bdd): Test kind *Package*, Directory `.../api-test/bdd`, Pattern `TestFeatures`,
  Working directory `.../api-test/bdd`, plus the env from `cmd/cfg -print-env` as above.

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
cp config.local.example.json config.local.json   # required — it is bind-mounted
cp .env.example .env
# in .env, set CONFIG_FILE=config.mosip.json and UNCOMMENT one ESIGNET_BASE_URL:
#   ESIGNET_BASE_URL=https://esignet-thunder1.esdev.mosip.net/v1/esignet   (deployed), or
#   ESIGNET_BASE_URL=http://host.docker.internal:8080                     (self-run on this host)
docker compose run --rm harness -c /app/config.json --check          # dry run, no suite needed
docker compose up --build --abort-on-container-exit --exit-code-from harness
```

> **An empty value in `.env` is not "unset".** `FOO=` reaches the container as `FOO=""`, and the
> harness overrides a config field whenever the variable is *present* — empty included
> (`envStr` in [internal/config/config.go](internal/config/config.go) uses `LookupEnv`). So
> `KEYCLOAK_CLIENT_SECRET=` in
> `.env` blanks the secret your mounted `config.local.json` supplies. `.env.example` ships every
> optional override **commented out** for this reason: to fall back to the config files, leave the
> line commented — don't set it to `""`. `docker compose config` shows what is actually passed.

> `SUITE_IMAGE_TAG`'s default in `.env.example`/`docker-compose.yml` can go stale — GitLab prunes old
> `conformance-suite` container tags, so a pull can 404 on a tag that built fine last month. If
> `docker compose up` fails to pull `server`/`httpd`, check currently published tags
> ([releases](https://gitlab.com/openid/conformance-suite/-/releases)) and set `SUITE_IMAGE_TAG` in
> your `.env` to one that still exists.

Compose mounts `$CONFIG_FILE` as `/app/config.json` and your `config.local.json`
beside it, so **the same two files drive both the native and containerised run**.
`conformance-suite-private/` is mounted at the *same relative path* it has on the
host, which is why `plans[].config_file` needs no per-environment override.

> `config.local.json` must exist before `docker compose up`. A bind mount whose
> source is missing makes Docker create a **directory** at the target; the
> harness detects that and says so, but the fix is the `cp` above.

The consolidated report lands in `./out` on the host (bind-mounted into the container). Exit code of
the `up` command matches the harness's (`0` all-clear, `1` a failure — including a surface that died
before writing its envelope, `2` a config/run error — same as running `run-all.sh` directly).

Knobs compose passes through (the full set — anything else has to be added to the `harness`
service's `environment:` list before `.env` can reach it):

| knob | effect |
|---|---|
| `CONFIG_FILE` | which plugin config is mounted as `/app/config.json` (default `config.mock.json`) |
| `SURFACES`, `TEST_PROFILE` | narrow the run without editing the config |
| `ESIGNET_BASE_URL`, `KEYCLOAK_CLIENT_SECRET`, `INDIVIDUAL_ID` | the per-deployment/secret escape hatches |
| `SUITE_WAIT_SECONDS` | how long the harness polls the suite's `/api/runner/available` before giving up and running anyway — compose defaults it to 150s to cover the suite's Mongo+Java cold start (`run-all.sh`'s own default outside compose is 90s) |
| `SUITE_IMAGE_TAG` | the suite image tag — keep it in step with `suite_version` in `profiles/oidcc-test-plan.smoke.json`, which the smoke allow-list is curated against |
| `CONFORMANCE_BASE_URL`, `CONFORMANCE_TLS_VERIFY` | compose defaults these to the bundled suite and `false` (self-signed cert); override when pointing at a remote suite |
| `ESIGNET_TLS_VERIFY`, `BDD_TLS_VERIFY` | no compose-side default — on unless you set them `false`, since the eSignet target is usually a real deployment |

`httpd` carries the network alias
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

## Config reference

`config.example.json` is the annotated schema. All three surfaces read the same
file; see [Configuration model](#configuration-model) for the layering.

The suite is the OAuth client for the conformance surface, so eSignet's discovery
URL + client ids + **private** JWKS live in the *suite plan config file*
referenced by each `plans[].config_file` — never in these files, and never in git.

| block | used by | notable fields |
|---|---|---|
| `conformance` | conformance | `base_url`, `tls_verify` (false for the suite's self-signed local cert) |
| `plans[]` | conformance | one entry per plan: `name`, `variant`, `config_file` (the private-JWKS plan config), plus optional `profile`/`modules`/`filter`/`skip`/`known_issues` |
| `keycloak` | bdd, e2e | `token_url`, `client_id`, `client_secret` — client-credentials grant |
| `esignet` | all | `base_url`, `provider`, `auth_factor`, `tls_verify`, `identity`, `credentials`, `knowledge`, `otp`, `pms` |
| `bdd` | bdd | `tags` (godog expression, comma = OR), `flow_client_id`, `tls_verify` |
| `e2e` | e2e | `spec`, `auth_factors`, `include`, `exclude` |
| `run` | all | `surfaces`, `modules`, `profile`, `filter`, `skip`, `known_issues`, timeouts |

#### Running several plans in one run

`plans` is a list, and one run executes every entry in order — e.g.
`oidcc-test-plan` followed by `fapi2-security-profile-final-test-plan`. Each
entry brings its own variant and its own `config_file`, so two plans mean two
client/key sets (and a FAPI plan config carries `client` **and** `client2`). The
bdd and e2e surfaces still run once, and every plan's modules land in the same
consolidated report under a section of their own:

```jsonc
"plans": [
  { "name": "oidcc-test-plan",
    "variant": { "client_auth_type": "private_key_jwt", "response_type": "code",
                 "response_mode": "default", "client_registration": "static_client" },
    "config_file": "conformance-suite-private/esignet-config.json" },

  { "name": "fapi2-security-profile-final-test-plan",
    "variant": { "client_auth_type": "private_key_jwt", "sender_constrain": "dpop",
                 "authorization_request_type": "simple", "openid": "openid_connect",
                 "fapi_profile": "plain_fapi" },
    "config_file": "conformance-suite-private/esignet-fapi2-config.json",
    "profile": "full" }
]
```

- `profile`/`modules`/`filter`/`skip`/`known_issues` are per plan and fall back to
  `run.*`. `profile: "smoke"` reads `profiles/<plan name>.smoke.json`, so a plan
  with no curated allow-list of its own needs `"profile": "full"`.
- Plan names must be unique — they key the report sections and the smoke files.
- `run.fail_fast` stops the **whole** run, remaining plans included.
- A plan the suite refuses to create (a variant it rejects, an unreadable config
  file) becomes one errored `(plan setup)` row and the next plan still runs, so a
  broken FAPI variant doesn't throw away the oidcc results.
- `./run-all.sh -c <config> --check` prints the plans in run order with their
  resolved variants — worth a look before a two-plan run, which takes twice as long.
- The legacy singular `"plan": { … }` block still works and is read as a
  one-entry list; setting both `plan` and `plans` is an error.

Notes:
- `plans[].variant` must **not** include `server_metadata` (the plan sets it; passing it → HTTP 400).
- `esignet.base_url` empty ⇒ derived from the suite's authorize URL for the conformance
  surface; if set, it's validated against it (mismatch → `ESIGNET_BASE_URL_MISMATCH`).
  bdd and e2e require it outright.
- `run.profile` `smoke` (curated allow-list) or `full` (all modules; undrivable ones →
  `SKIPPED_BY_HARNESS`). `run.modules` overrides the profile entirely.
- Requirements are **scoped to the selected surfaces**: an e2e-only run is not
  rejected for missing `conformance.base_url`. A single-surface binary invoked
  directly still enforces its own surface's requirements.

### Env overrides

The environment always wins over both files. The table below is the complete set —
**`run.modules`, `run.known_issues` and every `plans[]` field except `name`/`config_file`
have no env override**, so a container that needs to change those must mount the config
file itself.

| Env var | Overrides | | Env var | Overrides |
|---|---|---|---|---|
| `CONFIG` | which config file | | `SURFACES` | `run.surfaces` |
| `CONFIG_LOCAL` | overlay path | | `TEST_PROFILE` | `run.profile` |
| `CONFORMANCE_BASE_URL` | `conformance.base_url` | | `TEST_RUN` | `run.filter` (module regex) |
| `CONFORMANCE_TLS_VERIFY` | `conformance.tls_verify` | | `SKIP_MODULES` | `run.skip` |
| `CONFORMANCE_TOKEN` | `conformance.token` (suite bearer) | | `TIMEOUT_SECONDS`/`FAIL_FAST` | `run.*` |
| `PLAN_<n>_CONFIG_PATH` | `plans[n-1].config_file` (secret) | | `POLL_INTERVAL_SECONDS` | `run.poll_interval_seconds` |
| `PLAN_<n>_NAME` | `plans[n-1].name` | | `REPORT_DIR` | `run.report_dir` |
| `DEBUG_SHOW_SECRETS` | `run.debug_show_secrets` | | | |
| `ESIGNET_BASE_URL` | `esignet.base_url` | | `KEYCLOAK_TOKEN_URL` | `keycloak.token_url` |
| `AUTHN_PROVIDER` | `esignet.provider` | | `KEYCLOAK_CLIENT_ID` | `keycloak.client_id` |
| `AUTH_FACTOR` | `esignet.auth_factor` | | `KEYCLOAK_CLIENT_SECRET` | `keycloak.client_secret` |
| `ESIGNET_TLS_VERIFY` | `esignet.tls_verify` | | | |
| `INDIVIDUAL_ID`/`ID_TYPE` | `esignet.identity.*` | | `GODOG_TAGS` | `bdd.tags` |
| `TEST_USERNAME`/`TEST_PASSWORD` | `esignet.credentials.*` | | `FLOW_CLIENT_ID` | `bdd.flow_client_id` |
| `KBI_FULL_NAME`/`KBI_DOB` | `esignet.knowledge.*` | | `BDD_TLS_VERIFY` | `bdd.tls_verify` |
| `OTP_SOURCE`/`TEST_OTP` | `esignet.otp.*` | | `E2E_SPEC` | `e2e.spec` |
| `OTP_WS_URL`/`OTP_RECIPIENT_EMAIL` | `esignet.otp.*` | | `E2E_AUTH_FACTORS` | `e2e.auth_factors` |
| `PMS_BASE_URL`/`AUTH_PARTNER_ID`/`AUTH_POLICY_ID` | `esignet.pms.*` | | `E2E_INCLUDE`/`E2E_EXCLUDE` | `e2e.*` |

`PLAN_NAME`/`PLAN_CONFIG_PATH` (no index) still address a **single-plan** config.
With several plans configured they are rejected rather than guessed at — applying
a mounted `config_file` to `plans[0]` would run the FAPI plan against the OIDC
client's keys. Use the indexed form there; `<n>` is 1-based and must match a
configured plan.

---

## Rancher / plain `docker run`

Scheduled runs execute this image outside GitHub Actions. Mount the same two
files you use locally; inject only secrets as env.

```bash
docker run --rm \
  -v /opt/esignet-harness/config.mosip.json:/app/config.json:ro \
  -v /opt/esignet-harness/secrets:/app/conformance-suite-private:ro \
  -v /opt/esignet-harness/out:/app/out \
  -e CONFIG=/app/config.json \
  -e ESIGNET_BASE_URL=https://esignet-thunder1.esdev.mosip.net/v1/esignet \
  -e KEYCLOAK_CLIENT_SECRET=*** \
  apitest-esignet:<branch>
```

On Kubernetes/Rancher the plan config belongs in a **Secret volume**, not an env
var — it holds private JWKS, and secret volumes are tmpfs and never appear in
`kubectl describe pod` or the Rancher UI's env tab:

```bash
kubectl create secret generic esignet-conformance-config \
  --from-file=esignet-config.json=./conformance-suite-private/esignet-config.json -n <ns>
```
```yaml
spec:
  volumes:
    - name: plan-config
      secret: { secretName: esignet-conformance-config }
    - name: harness-config
      configMap: { name: esignet-harness-config }   # holds config.mosip.json
  containers:
    - name: harness
      args: ["-c", "/app/config.json"]
      env:
        - name: KEYCLOAK_CLIENT_SECRET
          valueFrom:
            secretKeyRef: { name: esignet-harness-secrets, key: keycloak-client-secret }
      volumeMounts:
        - { name: plan-config,    mountPath: /app/conformance-suite-private, readOnly: true }
        - { name: harness-config, mountPath: /app/config.json, subPath: config.mosip.json, readOnly: true }
```

**What needs a PR, and what doesn't.** Anything mounted is environment data and
changes without a rebuild: plugin, surfaces, auth factor, profile, identity,
scenario filters, `run.modules`, `bdd.tags`, and the plan config itself. Only
*image contents* need commit → merge → image build: Go code, `run-all.sh`, BDD
feature files, and the baked `e2e-scenarios*.json` / `profiles/*.json`. (Both of
those last two can be shadowed by mounting your own and pointing `E2E_SPEC` /
`run.modules` at it, for iterating before you PR.)

The all-env path still works unchanged — a missing config file is allowed when
the environment supplies everything, so pre-existing deployments need no edits.

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
  `otp`, `fullName`, `dob`, `captchaToken`.
- **`consent`** — how to answer the consent step and what to assert about it (see
  [Captcha and consent coverage](#captcha-and-consent-coverage)).
- **`expect_login_failure`** — negative case: **passes when login is correctly rejected**, fails if
  a bad credential is wrongly accepted. Omitted ⇒ positive: login **must** succeed (a failure —
  including "no credential configured for this ACR yet" — is reported **FAILED**, not skipped, so
  the case stays visible until real credentials exist).
- `scopes`, `userinfo_claims`, `expect_present`, `expect_values`, `expect_absent` — claim checks.
- **`known_issue`** — a reason string for an already-tracked environment gap. A **claim-assertion**
  failure then lands in the **Known** bucket with that reason instead of Failed, leaving the exit
  code alone; the failing check is still shown in the drill-down. It does not cover login failures,
  and a scenario that starts passing is still reported PASSED rather than silently downgraded.

> **Keep-in-place-and-fail is intentional.** ACRs with no working credential on the target (e.g.
> `bio`, or `password` where the user isn't seeded) are kept as scenarios and reported FAILED with
> a clear reason, rather than being omitted. They go green once real credentials/config exist.

### Captcha and consent coverage

Split across two surfaces by what each can reach:

| | surface | covers |
|---|---|---|
| consent **behaviour** — prompt, reuse, re-prompt, deny | **e2e** (below) | needs a driven login flow |
| consent **configuration** — `consent_expire_in_mins`, `purpose` validation | **bdd** `@client-mgmt` ([consent-config.feature](bdd/features/client-mgmt/consent-config.feature)) | pure API, no session |
| captcha accept/reject | **e2e** (below) | needs a driven login flow |

`consent-config.feature` is plugin-independent (admin-authenticated client-mgmt, no identity, no
captcha) so it runs unchanged under any `AUTHN_PROVIDER`: a valid `consent_expire_in_mins` + `purpose`
is accepted, and each way of getting it wrong is rejected with `invalid_additional_config` —
expiry below the 10-minute minimum, a non-numeric expiry, a `purpose` with no/empty `type`, and a
`title` lang map missing the `@none` default. Update is covered too, since `validate.go` re-validates
`additionalConfig` on the create, update **and** merged-update paths.

> **Captcha has no bdd coverage, deliberately.** It is a deployment-level setting
> (`MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL`), not per-client config, and it is only reachable as a
> flow input — and any `/flow/execute` call with a bogus `executionId` is rejected on the execution
> lookup before inputs are examined, so a "bad captcha token" case posted without a live flow would
> assert nothing. Same reason there is no consent-*behaviour* bdd case: consent and captcha are
> wired as engine providers with no standalone REST endpoint of their own.

The three e2e spec files carry the same seven appended cases, positive and negative, mirroring the
Postman collection's `captcha_token` input and `flow/execute — consent` request.

**Captcha** rides on `credentials.captchaToken` — no separate field. The base answers supply a
non-empty placeholder for every step; a scenario overrides it:

```jsonc
{ "name": "captcha negative: an empty captcha token is rejected",
  "auth_factor": "otp", "expect_login_failure": true,
  "credentials": { "captchaToken": "" }, "scopes": ["openid"] }
```

An **empty** token is rejected by the service *before* it checks whether a validator is configured
(`captcha_provider.go` `Verify`), so this negative is portable to any deployment. A **non-empty**
token is accepted when `MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL` is unset (the dev/test default) and
validated for real when it is set — so the positive case asserts "accepted" only in the former.

**Consent** uses the `consent` block:

```jsonc
"consent": {
  "expect_prompt": "yes",     // "yes" | "no" | "" (no assertion)
  "deny": ["name"],           // withhold approval from these elements
  "deny_all": false           // ...or from every element offered
}
```

- `expect_prompt: "no"` asserts the server **skipped** the prompt because a stored consent record
  still covers the request — the observable for `ResolveConsent`'s hash-match path.
- `deny` on an **optional** claim ⇒ login succeeds and the claim is withheld (`expect_absent`).
  On an **essential** claim (marked via `userinfo_claims: {"name": {"essential": true}}`) ⇒
  `essential_consent_denied` and the login is rejected (`expect_login_failure: true`).
- A `deny` naming an element the prompt never offered is a **scenario error**, not a silent pass —
  otherwise the case would "prove" a claim was withheld that was never consent-gated.
- Consent expectations are asserted on the rejection path too, so a deny-essential case cannot pass
  merely because the login failed for an unrelated reason (a wrong OTP, say).

> **The consent cases are order-dependent and stateful.** One client is registered per run and
> *every* scenario shares it, so each successful login stores a consent record for
> (client, user, scopes+claims hash). The reuse case asserts "no prompt" purely because the
> identical-request case ran immediately before it; every other consent case deliberately uses a
> **scope set no earlier succeeding scenario used**, so its hash differs and the server re-prompts.
> Reordering the scenarios, or narrowing a run with `e2e.include` so the approve-all case is
> skipped, breaks the reuse case. `TestShippedSpecsParseAndAreConsistent` enforces both invariants
> at build time — including against plain non-consent scenarios earlier in the file, which store
> consent just the same.

Consent-record **expiry** re-prompting is not covered: it needs control over the deployment's
consent validity period, which the harness doesn't have.

e2e env: `ESIGNET_BASE_URL`, `AUTHN_PROVIDER`, `INDIVIDUAL_ID`, `ID_TYPE`, `TEST_OTP`,
`TEST_USERNAME`/`TEST_PASSWORD`, `KBI_FULL_NAME`/`KBI_DOB`, `KEYCLOAK_*`, `E2E_SPEC`, `BDD_TLS_VERIFY`.

> **`INDIVIDUAL_ID` is required for every plugin except `mock`.** The mock plugin falls back to its
> seeded synthetic identity so a first run works out of the box; against any real deployment the e2e
> surface fails fast rather than silently authenticating as — and reporting claims for — whoever owns
> a baked-in identifier.

TLS verification is set **per target**, not per run, because the two targets have very different
certificates:

| knob | target | default |
|---|---|---|
| `esignet.tls_verify` / `ESIGNET_TLS_VERIFY` | the eSignet deployment: the conformance surface's login flow, the mock-SMTP OTP socket, and the e2e surface | **on** |
| `bdd.tls_verify` / `BDD_TLS_VERIFY` | the same eSignet, from the godog module (a separate Go module, so it gets its own copy) | **on** |
| `conformance.tls_verify` / `CONFORMANCE_TLS_VERIFY` | the OpenID Conformance Suite | **on**, but every shipped config and compose set it `false` |

The eSignet knobs are **on unless explicitly set to `false`**, so a run against a real deployment
never sends the test identity, its OTP or its password over an unverified link. Set them `false`
only for an eSignet with a self-signed cert of its own.

`conformance.tls_verify` is the one that ships `false`, for the bundled suite's self-signed
`localhost.emobix.co.uk` cert — override it when pointing `CONFORMANCE_BASE_URL` at a remote suite.
It governs **only** the connection to the suite: it must never be the flag that decides how eSignet
is reached, which is exactly why `esignet.tls_verify` exists.

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

All of it lives in the per-plugin config; `--check` shows the resolved result.

- **Which plugin** — one config file per plugin; pick with `-c config.mosip.json`.
- **Which surfaces** — `run.surfaces` (`conformance`, `bdd`, `e2e`), or `-s` for a one-off.
- **Which auth factor (conformance)** — `esignet.auth_factor`. One factor per run;
  to cover another, change it and run again.
- **Conformance modules** — `run.profile` (`smoke`/`full`) → `run.filter` (regex) → `run.modules`
  (exact list, overrides profile). `run.skip` (→ **Skipped** bucket) and `run.known_issues`
  (→ **Known** bucket, with reason) carve modules out of execution without touching the exit code.
- **Gherkin scenarios** — `bdd.tags`: `@client-mgmt`, `@client-mgmt-pms`, `@flow-execute`,
  `@flow-authz-neg` (comma = OR). Empty auto-selects by configured credentials —
  [table above](#c-bdd-surfaces-only-client-mgmt--flow-execute).
- **e2e scenarios** — `e2e.auth_factors` (which ACRs) plus `e2e.include` / `e2e.exclude`
  (regex on scenario name). Every scenario runs by default, each driving its own ACR.

---

## Report

One self-contained, light/dark HTML file under `out/`, filename encoding
surfaces/provider/timestamp/counts — the surfaces the report actually covers, so
a directory of runs reads at a glance:
```
out/conformance_bdd_e2e_mock_20260721-195317_t-57_p-42_f-12_sk-3_ki-0.html   (+ .json sidecar)
out/conformance_mosip_20260721-201044_t-41_p-30_f-10_sk-1_ki-0.html          (conformance only)
```
`t-<n>` is every module in the run — `p`+`f`+`sk`+`ki` plus any `Errored`/`Warning`/`Review` bucket
that has no letter of its own in the name, so a mismatch between `t` and the other four tells you
one of those buckets is non-empty without opening the report.
The client-mgmt and flow-execute surfaces share the `bdd` part. Plan names are
**not** in the filename — there can be several and they are long; the report
header and its per-plan sections name them instead.
Contents:
- **Overall tiles** + a **section per surface** (Conformance · Client-mgmt · flow/execute · E2E),
  each with its own tiles. A run covering several plans gets one **conformance
  section per plan**, and one plan-config panel per plan (each has its own
  client + keys).
- **Validation tab** on each client-mgmt/flow-execute/e2e case — a table of **every** expected-vs-actual
  check performed (HTTP status, JSON field, claim, login accepted/rejected), pass **and** fail, not
  just the final status code. Auto-opens when a check fails. (Conformance rows keep the suite's own
  structured log instead.)
- **Expand / collapse controls** — whole-report and per-section "Collapse all sections", plus
  per-section "Expand/Collapse rows", and each section heading is itself a collapsible toggle.
- **Drill-downs**: failure findings, the suite condition log (conformance), the eSignet flow trace,
  and the full eSignet request/response API-call trace (bearer token redacted, repeats collapsed `×N`).

Set `ESIGNET_DEBUG=1` to stream each `/flow/execute` request/response to stderr live.

### Redaction

By default the captured wire trace is scrubbed before it reaches either the HTML
or the `.json` sidecar: OTPs, passwords, the signed client assertion, identity
inputs (`individualId`, `username`, `fullName`, `dob`), `Authorization` headers,
cookies, and credential-bearing URL/fragment params. The userinfo **response**
claims stay readable — they're the artifact the harness exists to evidence.

When a redacted trace can't explain a failure, opt out for a local run:

```jsonc
"run": { "debug_show_secrets": true }     // or DEBUG_SHOW_SECRETS=true
```

The whole wire trace then appears verbatim and the report carries a red warning
banner saying so. `--check` warns before the run starts too. Two things it does
**not** unmask, in either mode: the configuration panel (Keycloak client secret)
and the suite plan config (private JWKS) — those are long-lived infrastructure
credentials rather than per-run test data.

> The default is safe by construction: the zero value redacts, so an omitted
> field, a hand-written config, or a config predating this option all redact.
> Don't leave it on for anything CI archives.

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
| `create plan HTTP 400 … server_metadata` | remove `server_metadata` from that plan's `variant` |
| sunbird conformance run `ENV_NOT_READY` / login failures | sunbird prerequisites not met on the target (KBI flow / seeded registry) — a runtime setup gap, not a config rejection |
| module stuck → `timeout … WAITING` | implicit-submit didn't fire, or the login didn't complete — check the flow trace |
| `no configured answer for flow input(s): …` | the flow asked for an input with no config value (add `credentials`/identity) — expected for uncredentialed ACRs |
| e2e login loops `INCOMPLETE` then fails | the submitted credential isn't authenticating (user not seeded / wrong password) — not a "clean rejection" |
| client-mgmt reports `ENV_NOT_READY` | `KEYCLOAK_*` env not set (admin token unavailable) |
| sunbird conformance: `scope-*`/`userinfo-*` modules FAILED (`UserInfo endpoint response is not a JSON object`) | that plugin's `pm-client` (or the deployment) returns userinfo as a **signed JWT**, not plain JSON — mock returns JSON so these pass there. Open finding as of 2026-07-22; check the client's `userinfo_response_type` registration / deployment default, not the harness. |
