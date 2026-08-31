# eSignet API Test Harness

A black-box test harness for a **running eSignet (thunder-go)** deployment. You point it at a
deployment, tell it which identity plugin that deployment uses, and it produces **one
self-contained HTML report** covering every test surface you selected.

It tests a deployment from the outside, over HTTP, exactly as a relying party would. It does not
build, start or mock eSignet — the deployment under test must already be running and reachable.

**New here?** [Quick start](#quick-start) gets a first report out of a fresh clone in three
commands. [Configuration](#configuration) explains which file a given value belongs in — the one
thing worth reading before your first real run.

---

## Contents

[Test surfaces](#test-surfaces) · [Quick start](#quick-start) · [Project structure](#project-structure) ·
[Prerequisites](#prerequisites) · [Configuration](#configuration) · [Running](#running) ·
[The report](#the-report) · [Building](#building) · [Further documentation](#further-documentation)

---

## Test surfaces

A **surface** is one independent body of tests. You choose which run; the results merge into a
single report.

| Surface | What it verifies | Needs |
|---|---|---|
| **`conformance`** | Formal OpenID Connect compliance. Drives the [OpenID Conformance Suite](https://gitlab.com/openid/conformance-suite), which acts as the OAuth client and grades the result; the harness plays browser-and-user, walking `authorize → /flow/execute → callback`. | A running suite + a plan config ([setup](docs/conformance-suite.md)) |
| **`api`** | eSignet's REST endpoints directly, one endpoint at a time: client management (`/client-mgmt/client` create/get/update, consent and protocol `additionalConfig` validation) and the flow entry points (`/flow/meta`, `/flow/execute`, `/oauth2/authorize` and `/oauth2/introspect` validation). Success *and* rejection cases for each. Written in Gherkin, run by [godog](https://github.com/cucumber/godog). | A reachable eSignet; Keycloak admin credentials for the client-management endpoints |
| **`e2e`** | A complete relying-party journey. The harness registers throwaway OIDC clients, then runs `authorize` (PKCE) → login → `token` (`private_key_jwt`) → `userinfo` and asserts the claims that come back. Covers each authentication factor (OTP, password, biometrics, knowledge-based), positive and negative, plus consent, captcha, token introspection (RFC 7662), and the per-client protocol switches — PKCE, PAR and DPoP — in combination. | A reachable eSignet + Keycloak admin credentials + a test identity |

Three identity plugins are supported — **`mock`**, **`sunbird`** and **`mosip`** — selected per run
by the config file you pass. `mock` needs no identity of your own and is the right starting point.

> `mosip` (MOSIP ID) has extra setup of its own — partner registration, dynamic OTP retrieval. See
> **[MOSIP ID plugin](docs/mosip-id.md)**; nothing in this README is specific to it.

---

## Quick start

The shortest useful run is the **e2e** surface against the **mock** plugin — no conformance suite,
no plan config, no test identity: mock's seeded identity ships in the config.

```bash
cd api-test

# 1. Create your credentials overlay and fill in two values:
#    esignet.base_url  — the deployment to test
#    keycloak.client_secret — the admin client secret
cp data/config/config.local.example.json data/config/config.local.json

# 2. See what would run. Executes nothing, needs nothing running.
./run-all.sh -c data/config/config.mock.json -s e2e --check

# 3. Run it.
./run-all.sh -c data/config/config.mock.json -s e2e
```

The report lands in `out/` as a single HTML file. Open it in a browser.

Then widen one step at a time, running `--check` first each time:

| To also run | Command | What it additionally needs |
|---|---|---|
| the `api` surface | `-s e2e,api` | nothing more |
| `conformance` too | drop `-s` entirely | a running suite and a plan config — see [Conformance suite setup](docs/conformance-suite.md) |
| a different plugin | `-c data/config/config.mosip.json` | that plugin's identity and credentials in your overlay |

---

## Project structure

```text
api-test/
  run-all.sh            run selected surfaces + merge into one report
  Dockerfile            harness image
  docker-compose.yml    conformance suite + harness, one command

  cmd/
    conformance/        conformance surface runner
    e2e/                e2e surface runner
    consolidate/        merge surface results into one HTML report
    cfg/                inspect the resolved config (--check, -print-env)
  api/                  api surface: godog engine (separate Go module)
  internal/             shared implementation

  data/                 everything the run reads that is not code
    features/           Gherkin feature files (the api surface's tests)
      client-mgmt/        one file per client-management endpoint
      flow-execute/       one file per flow endpoint
    config/             per-plugin config files (see below)
    scenarios/          e2e scenario specs, one per plugin
    conformance/        conformance suite profiles + plan-config template

  out/                  reports land here (git-ignored)
```

### What each surface reads

| Surface | Tests defined in | Selected by |
|---|---|---|
| `conformance` | `data/conformance/<plan>.smoke.json` (curated module list) | `run.profile`, `run.modules`, `plans[]` |
| `api` | `data/features/**/*.feature` | `api.tags` (Gherkin tags) |
| `e2e` | `data/scenarios/e2e-scenarios[-<plugin>].json` | `e2e.auth_factors`, `e2e.include`, `e2e.exclude` |

Feature files are organised **by endpoint**: `create-client.feature` holds every create case,
success and rejection alike, so one file describes one endpoint's full behaviour.

---

## Prerequisites

Only what your selected surfaces actually need:

| # | Needed for | What |
|---|---|---|
| 1 | every surface | **A reachable eSignet.** `esignet.base_url`, e.g. `https://esignet-thunder1.esdev.mosip.net/v1/esignet`. It must serve `/.well-known/openid-configuration`. |
| 2 | `api` (client-management), `e2e` | **Keycloak admin credentials** — a client-credentials grant used to obtain a bearer token: `keycloak.token_url`, `keycloak.client_id`, `keycloak.client_secret`. The `e2e` surface registers its own throwaway client per run, so nothing needs pre-provisioning. |
| 3 | `e2e` (except `mock`) | **A test identity** on the target deployment — an identifier plus whichever credentials its authentication factors need. |
| 4 | `conformance` | **A running conformance suite and a plan config.** This is the one genuinely involved prerequisite — see **[Conformance suite setup](docs/conformance-suite.md)**. |

`./run-all.sh -c <config> --check` verifies all of the above without running anything and names
whatever is missing, so on a fresh clone its output *is* your setup checklist.

---

## Configuration

Configuration is layered, so **you never edit a tracked file to add a secret**. Lowest precedence
first:

| Layer | In git? | Holds | Example |
|---|---|---|---|
| `data/config/config.<plugin>.json` | **yes** | What the tests *do*: which surfaces, which auth factor, which modules, tags and scenarios | `data/config/config.mosip.json` |
| `data/config/config.local.json` | no | What is *yours*: credentials, target URLs, test identity | your overlay |
| environment variables | — | Always wins. How containers inject secrets and per-deployment URLs | `KEYCLOAK_CLIENT_SECRET=…` |

Each layer overrides only the keys it names, at any depth.

### The files you will touch

| File | Purpose |
|---|---|
| `data/config/config.mock.json` | Ready-to-run config for the mock plugin |
| `data/config/config.mosip.json` | …for MOSIP ID ([extra setup](docs/mosip-id.md)) |
| `data/config/config.sunbird.json` | …for Sunbird |
| `data/config/config.local.example.json` | Template for your overlay — copy it to `config.local.json` |
| `data/config/config.example.json` | Annotated reference for every available field |

Pick a plugin config with `-c`; the overlay is picked up automatically from beside it.

```bash
cp data/config/config.local.example.json data/config/config.local.json
```

A config named explicitly **must exist** — `-c data/config/config.msoip.json` is an error, not a
silent fallback to another plugin.

### The four values most runs need

```jsonc
// data/config/config.local.json
{
  "esignet":  { "base_url": "https://esignet-thunder1.esdev.mosip.net/v1/esignet",
                "identity": { "individual_id": "<your test identity>" } },
  "keycloak": { "client_secret": "<admin client secret>" }
}
```

`individual_id` is required for every plugin **except `mock`**, which falls back to its seeded
identity so a first run works out of the box.

> Full field-by-field reference, the complete environment-variable table, and the TLS verification
> knobs: **[Configuration reference](docs/configuration.md)**.

---

## Running

Five ways to run, all driven by the same config files.

### A. One command — every selected surface, one report

The normal path. Runs the surfaces listed in the config's `run.surfaces` and merges them.

```bash
./run-all.sh -c data/config/config.mosip.json
```

Works in git-bash on Windows and any POSIX shell.

| Flag | Effect |
|---|---|
| `-c, --config PATH` | Which plugin config to run |
| `-s, --surfaces LIST` | Narrow to `conformance,api,e2e` for this run only |
| `--check` | Print the resolved plan and exit without running anything |
| `-h, --help` | The script's own flag and environment reference |

Exit codes: `0` all clear · `1` a test failed · `2` a configuration or setup error.

### B. A single surface, directly

Useful while iterating on one area.

```bash
# conformance
go run ./cmd/conformance -config data/config/config.mosip.json

# api  (godog is a separate module, so hand it the resolved config as environment)
eval "$(go run ./cmd/cfg -config data/config/config.mosip.json -print-env)"
( cd api && go test ./... -run TestFeatures -count=1 )

# e2e
go run ./cmd/e2e -config data/config/config.mosip.json
```

Run directly like this, each surface writes to its own default `out/`; `run-all.sh` is what
threads a shared output directory through all of them.

To narrow the `api` surface to particular endpoints, set `api.tags` in the config (comma = OR):

| Tag | Covers |
|---|---|
| `@flow-execute` | `/flow/meta`, `/flow/execute`, `/oauth2/introspect` request and client-authentication validation |
| `@flow-authz-neg` | `/oauth2/authorize` request validation |
| `@client-mgmt` | client create / get / update, consent configuration, protocol `additionalConfig` validation (PKCE, PAR, DPoP) |
| `@client-mgmt-pms` | client registration via PMS ([MOSIP ID only](docs/mosip-id.md)) |

Leaving `api.tags` empty runs whatever your configured credentials can actually drive; anything
skipped for missing configuration is still listed in the report rather than silently omitted.

### C. Merge results you have already produced

```bash
go run ./cmd/consolidate \
  -conformance out/conformance_mock_<timestamp>.json \
  -api         out/api-envelope.json \
  -e2e         out/e2e-envelope.json \
  -plugin mock -out out
```

Any of the three inputs may be omitted; whatever you pass is merged.

### D. Docker Compose — conformance suite and harness together

Brings up the OpenID Conformance Suite (MongoDB + server + nginx) alongside the harness and runs
everything. It starts the suite *process* for you, but it does **not** create the plan config the
`conformance` surface needs — that stays a one-time manual step per environment, because the file
holds a private JWKS ([Conformance suite setup](docs/conformance-suite.md)).

```bash
cp data/config/config.local.example.json data/config/config.local.json   # required: it is mounted
cp .env.example .env
# In .env: set MOSIP_ESIGNET_BASE_URL, and SURFACES=api,e2e for a first run (see below).

docker compose run --rm --no-deps harness -c /app/config.json --check   # dry run
docker compose up --build --abort-on-container-exit --exit-code-from harness
```

> **Start with `SURFACES=api,e2e`.** Every shipped plugin config selects all three surfaces, so an
> out-of-the-box compose run includes `conformance` and stops at `NOT RUNNABLE YET` until a plan
> config exists in `conformance-suite-private/`. `api` and `e2e` need only `MOSIP_ESIGNET_BASE_URL` plus
> the Keycloak credentials, so get those green first and widen once the conformance setup is done.
> For those two, `docker compose run --rm --no-deps --build harness -c /app/config.json` runs them
> and writes the same report to `./out` without starting the suite at all — `up` would boot mongodb,
> server and nginx regardless, since `harness` declares them as dependencies.

> **`--no-deps` on the dry run.** `--check` resolves configuration and executes nothing, so it does
> not need the suite. Without the flag Compose pulls and starts mongodb, server and nginx just to
> print the plan.

> **On Git Bash (Windows)**, prefix these with `MSYS_NO_PATHCONV=1`. MSYS rewrites the
> container-side `/app/config.json` into a Windows path before Docker sees it, and the run fails
> with `config file not found: C:/Program Files/Git/app/config.json`.

The report appears in `./out` on the host. `docker compose down -v` tears everything down.

Compose does **not** start eSignet itself — point `MOSIP_ESIGNET_BASE_URL` at a deployed environment, or
at `http://host.docker.internal:8080` for one running on your own machine.

| `.env` knob | Effect |
|---|---|
| `CONFIG_FILE` | Which plugin config is mounted (default `data/config/config.mock.json`) |
| `MOSIP_ESIGNET_BASE_URL` | The deployment under test |
| `KEYCLOAK_TOKEN_URL`, `KEYCLOAK_CLIENT_SECRET` | Admin credentials |
| `ADMIN_TOKEN` | Skips the Keycloak round-trip above, for a target that does not enforce scope (no `ISSUER_URL`/`JWKS_URL`) — a locally started `esignet-service`, typically. Explicit opt-in, not a fallback |
| `INDIVIDUAL_ID`, `FLOW_CLIENT_ID` | Test identity and the pre-registered client for authorize validation |
| `SURFACES`, `TEST_PROFILE` | Narrow the run without editing a config |
| `ESIGNET_TLS_VERIFY`, `API_TLS_VERIFY` | Certificate verification for the deployment under test — on unless set `false` |
| `CONFORMANCE_BASE_URL`, `CONFORMANCE_TLS_VERIFY` | The suite; compose defaults these to the bundled one |
| `SUITE_IMAGE_TAG` | Conformance suite image version |
| `SUITE_WAIT_SECONDS` | How long to wait for the suite to boot (default 150s under compose) |

`CONFIG_FILE` and `SUITE_IMAGE_TAG` are read by Compose itself, to pick the bind-mount source and
the image tag. The rest are passed into the container, and **only** variables listed in the
`harness` service's `environment:` block are — so anything not above has to be added there before
`.env` can reach the harness.

> **An empty value in `.env` is not "unset".** `KEYCLOAK_CLIENT_SECRET=` reaches the container as
> an empty string and *overrides* your config file, blanking the secret. To fall back to the config
> files, leave the line commented out rather than setting it empty. `.env.example` ships every
> optional override commented for this reason.

### E. `docker run` — scheduled and remote runs

```bash
docker run --rm \
  -v /opt/esignet-harness/config.mosip.json:/app/config.json:ro \
  -v /opt/esignet-harness/out:/app/out \
  -e CONFIG=/app/config.json \
  -e MOSIP_ESIGNET_BASE_URL=https://esignet-thunder1.esdev.mosip.net/v1/esignet \
  -e KEYCLOAK_CLIENT_SECRET=*** \
  apitest-esignet:<tag> -c /app/config.json
```

For the conformance surface you also need the plan config mounted — as a Kubernetes Secret or
Docker secret, never baked into the image. See
**[Providing the plan config securely](docs/conformance-suite.md#providing-the-plan-config-securely)**.

**What needs a rebuild and what does not.** Anything *mounted* is environment data and changes
without rebuilding: the plugin, surfaces, auth factor, identity, scenario filters, tags, and the
plan config. Only image *contents* need a merge and an image build: Go code, `run-all.sh`, and the
`data/` tree.

---

## The report

One self-contained HTML file per run, in `out/`. The filename records which surfaces ran, the
plugin, the timestamp and the result counts, so a directory of runs reads at a glance:

```text
out/conformance_api_e2e_mock_20260818-195317_t-57_p-42_f-12_sk-3_ki-0.html
```

Inside: overall result tiles, then a section per surface, each case expandable to show

- a **Validation** table of every expected-vs-actual check performed, passes included — not just
  the final status code,
- the full eSignet request/response trace for that case,
- the conformance suite's own condition log, for conformance rows.

Credentials are **redacted by default** — OTPs, passwords, tokens, `Authorization` headers, cookies
and identity inputs are masked before anything is written, since reports are archived as CI
artifacts. The `userinfo` response claims stay readable: proving the right claims came back is what
the e2e surface exists to evidence. Treat a report as containing subject claims and handle it
accordingly.

To debug a failure a redacted trace cannot explain, set `run.debug_show_secrets: true` (or
`DEBUG_SHOW_SECRETS=true`) for a local run. The report then carries a red banner saying so. Never
leave it on for anything CI archives.

---

## Building

```bash
go build ./... && go vet ./... && go test ./...     # main module
( cd api && go vet ./... && go test ./... )         # api surface module
```

Two Go modules: the main one is **stdlib-only**; `api/` is separate because it pins godog and
gjson. `.github/workflows/push-trigger.yml` builds and publishes the `apitest-esignet` image — it
does not execute the tests, which run against deployed environments on their own schedule.

---

## Further documentation

| Document | Covers |
|---|---|
| **[Configuration reference](docs/configuration.md)** | Every config field, the full environment-variable table, TLS verification, selecting exactly what runs |
| **[Conformance suite setup](docs/conformance-suite.md)** | Standing up the suite, creating the plan config, and providing it securely in a deployment |
| **[MOSIP ID plugin](docs/mosip-id.md)** | Partner registration via PMS, dynamic OTP retrieval, and the settings only this plugin needs |
| **[e2e scenario model](docs/e2e-scenarios.md)** | Writing and filtering e2e scenarios; consent and captcha coverage |
| **[Troubleshooting](docs/troubleshooting.md)** | Symptom-to-cause table for common failures |

Design and background: [mosip/esignet#2120](https://github.com/mosip/esignet/issues/2120).
