# Configuration reference

Every field the harness reads, where it belongs, and how to override it. For the layering model and
the handful of values a first run needs, see the [README](../README.md#configuration).

**Contents:** [Layering](#layering) · [Config blocks](#config-blocks) · [Selecting what runs](#selecting-what-runs) ·
[Environment overrides](#environment-overrides) · [TLS verification](#tls-verification)

---

## Layering

Lowest precedence first. Each layer overrides only the keys it names, at any depth.

| Layer | In git? | Holds |
|---|---|---|
| `data/config/config.<plugin>.json` | **yes** | What the test *does*: surfaces, auth factor, modules, tags, scenario filters |
| `data/config/config.local.json` | no | What is *yours*: credentials, target URLs, test identity |
| environment | — | Always wins; how containers inject secrets |
| built-in defaults | — | Fills anything still empty |

The overlay is looked up as `config.local.json` **beside the config file you pass to `-c`**, so
`-c data/config/config.mosip.json` picks up `data/config/config.local.json`. Override the path with
`CONFIG_LOCAL`.

The split exists so you never edit a tracked file to add a secret: a dirty `config.mosip.json`
always means a real change worth reviewing, and test selection stays under review because it defines
what "passing" means.

`data/config/config.example.json` is the annotated schema for every field below.

---

## Config blocks

| Block | Used by | Notable fields |
|---|---|---|
| `esignet` | all | `base_url`, `provider`, `auth_factor`, `tls_verify`, `identity`, `credentials`, `knowledge`, `otp`, `pms` |
| `keycloak` | `api`, `e2e` | `token_url`, `client_id`, `client_secret` — a client-credentials grant |
| `conformance` | `conformance` | `base_url`, `tls_verify` |
| `plans[]` | `conformance` | One entry per plan: `name`, `variant`, `config_file`, plus optional `profile`/`modules`/`filter`/`skip`/`known_issues` — see [Conformance suite setup](conformance-suite.md#running-several-plans) |
| `api` | `api` | `tags` (Gherkin expression, comma = OR), `flow_client_id`, `tls_verify` |
| `e2e` | `e2e` | `spec`, `auth_factors`, `include`, `exclude` — see [e2e scenario model](e2e-scenarios.md) |
| `run` | all | `surfaces`, `profile`, `modules`, `filter`, `skip`, `known_issues`, `report_dir`, `debug_show_secrets`, `fail_fast`, `timeout_seconds`, `poll_interval_seconds` |

### `esignet`

| Field | Meaning |
|---|---|
| `base_url` | The deployment under test. Must serve `/.well-known/openid-configuration`. |
| `provider` | `mock` \| `sunbird` \| `mosip` — which identity plugin the deployment runs |
| `auth_factor` | Which ACR the conformance surface drives: `otp` \| `password` \| `bio` \| `kbi`. **One per run** — to cover another, change it and run again. |
| `identity.individual_id` | The test identity. Required for every provider except `mock`. |
| `identity.id_type` | `uin` \| `vid` \| `phone` \| `email` — selects the matching login-id tab |
| `credentials.username` / `.password` | For the password factor |
| `knowledge.full_name` / `.dob` | For the knowledge-based factor |
| `otp.*` | Static or dynamic OTP retrieval — see [MOSIP ID](mosip-id.md#dynamic-otp-retrieval) |
| `pms.*` | MOSIP-ID-only partner binding — see [MOSIP ID](mosip-id.md#partner-registration-via-pms) |

`esignet.base_url` may be left empty for a **conformance-only** run, in which case it is derived
from the suite's authorize URL. The `api` and `e2e` surfaces require it outright.

Requirements are **scoped to the surfaces you selected**: an e2e-only run is not rejected for a
missing `conformance.base_url`.

---

## Selecting what runs

All of it lives in the config; `--check` shows the resolved result before anything executes.

| To choose | Set |
|---|---|
| Which plugin | The config file you pass to `-c` |
| Which surfaces | `run.surfaces` (`conformance`, `api`, `e2e`), or `-s` for one run |
| Which auth factor (conformance) | `esignet.auth_factor` |
| Which conformance modules | `run.profile` (`smoke`/`full`) → `run.filter` (regex) → `run.modules` (exact list, overrides the profile) |
| Which endpoints (`api`) | `api.tags` — `@client-mgmt`, `@client-mgmt-pms`, `@flow-execute`, `@flow-authz-neg` (comma = OR) |
| Which e2e scenarios | `e2e.auth_factors`, plus `e2e.include` / `e2e.exclude` (regex on scenario name) |

`run.skip` moves modules to a **Skipped** bucket and `run.known_issues` to a **Known** bucket with a
reason; neither affects the exit code.

**Empty `api.tags` does not mean "run everything"** — it means "run what the configured credentials
can actually drive". Client-management scenarios are included once `keycloak.client_secret` is set,
authorize-validation ones once `api.flow_client_id` is, and the PMS ones only for `mosip` with
`pms.base_url` set. Anything left out is still listed in the report rather than silently omitted, so
a partial setup cannot read as a clean pass. Naming tags yourself turns that selection off entirely.

An `e2e` filter matching **zero** scenarios is an error, not an empty run: a green "0 scenarios, 0
failed" is indistinguishable from a real pass.

---

## Environment overrides

The environment always wins over both files. This is the complete set — **`run.modules`,
`run.known_issues` and every `plans[]` field except `name`/`config_file` have no environment
override**, so a container needing to change those must mount the config file itself.

| Variable | Overrides | | Variable | Overrides |
|---|---|---|---|---|
| `CONFIG` | which config file | | `SURFACES` | `run.surfaces` |
| `CONFIG_LOCAL` | overlay path | | `TEST_PROFILE` | `run.profile` |
| `CONFORMANCE_BASE_URL` | `conformance.base_url` | | `TEST_RUN` | `run.filter` (module regex) |
| `CONFORMANCE_TLS_VERIFY` | `conformance.tls_verify` | | `SKIP_MODULES` | `run.skip` |
| `CONFORMANCE_TOKEN` | `conformance.token` | | `TIMEOUT_SECONDS` / `FAIL_FAST` | `run.*` |
| `PLAN_<n>_CONFIG_PATH` | `plans[n-1].config_file` | | `POLL_INTERVAL_SECONDS` | `run.poll_interval_seconds` |
| `PLAN_<n>_NAME` | `plans[n-1].name` | | `REPORT_DIR` | `run.report_dir` |
| `DEBUG_SHOW_SECRETS` | `run.debug_show_secrets` | | | |
| `MOSIP_ESIGNET_BASE_URL` | `esignet.base_url` | | `KEYCLOAK_TOKEN_URL` | `keycloak.token_url` |
| `MOSIP_ESIGNET_AUTHN_PROVIDER` | `esignet.provider` | | `KEYCLOAK_CLIENT_ID` | `keycloak.client_id` |
| `AUTH_FACTOR` | `esignet.auth_factor` | | `KEYCLOAK_CLIENT_SECRET` | `keycloak.client_secret` |
| `ESIGNET_TLS_VERIFY` | `esignet.tls_verify` | | | |
| `INDIVIDUAL_ID` / `ID_TYPE` | `esignet.identity.*` | | `GODOG_TAGS` | `api.tags` |
| `TEST_USERNAME` / `TEST_PASSWORD` | `esignet.credentials.*` | | `FLOW_CLIENT_ID` | `api.flow_client_id` |
| `KBI_FULL_NAME` / `KBI_DOB` | `esignet.knowledge.*` | | `API_TLS_VERIFY` | `api.tls_verify` |
| `OTP_SOURCE` / `TEST_OTP` | `esignet.otp.*` | | `E2E_SPEC` | `e2e.spec` |
| `OTP_WS_URL` / `OTP_RECIPIENT_EMAIL` | `esignet.otp.*` | | `E2E_AUTH_FACTORS` | `e2e.auth_factors` |
| `PMS_BASE_URL` / `AUTH_PARTNER_ID` / `AUTH_POLICY_ID` | `esignet.pms.*` | | `E2E_INCLUDE` / `E2E_EXCLUDE` | `e2e.*` |

> **`MOSIP_ESIGNET_BASE_URL` and `MOSIP_ESIGNET_AUTHN_PROVIDER` are deliberately the same names
> eSignet itself reads** (`server.public_url` and the provider selector — see
> `esignet-service/data/deployment.yaml`), so one environment block can configure both the
> deployment and the harness pointed at it. Every other variable here is harness-only: the test
> identity, the suite connection and the run selection have no eSignet-side counterpart. Note
> `ESIGNET_TLS_VERIFY` is *not* one of the shared names — it governs how the harness verifies the
> deployment's certificate, which is a client-side concern eSignet has no setting for.

> **An environment variable that is *present but empty* still overrides.** The harness applies an
> override whenever the variable is defined, empty included, so `KEYCLOAK_CLIENT_SECRET=` blanks
> whatever your config file supplies. To fall back to the files, leave the variable **undefined** —
> in a `.env`, that means commenting the line out rather than setting it to `""`.

### Path and diagnostic variables

These are not config overrides — they point the harness at files, or turn on output.

| Variable | Effect |
|---|---|
| `BIN_DIR` | Directory of prebuilt binaries. Set by the image; unset means run from source |
| `API_FEATURES_DIR` | The Gherkin tree. Defaults to `data/features` under the harness root; `run-all.sh` exports it, and the image overrides it |
| `SUITE_WAIT_SECONDS` | How long `run-all.sh` polls the suite's readiness endpoint (default 90; 0 disables) |
| `ESIGNET_DEBUG` | Stream each `/flow/execute` request and response to stderr |
| `WSOTP_DEBUG` | Print the first few raw OTP WebSocket frames |
| `ADMIN_TOKEN` | Supplies the admin bearer directly, skipping the Keycloak client-credentials call in `api`'s `@client-mgmt` steps and in `e2e`'s pre-registration. Only meaningful against a target that does not enforce scope — `esignet-service` installs no scope middleware unless both `ISSUER_URL` and `JWKS_URL` are set, so such a target never inspects the value. Explicit opt-in, not a fallback: `keycloak.*` is still required whenever this is unset |

### Plan-config path variables

`PLAN_NAME` / `PLAN_CONFIG_PATH` without an index address a **single-plan** config. With several
plans configured they are rejected rather than guessed at — applying one mounted `config_file` to
`plans[0]` would run the FAPI plan against the OIDC client's keys. Use the indexed form there;
`<n>` is 1-based and must match a configured plan.

---

## TLS verification

Set **per target**, because the targets have very different certificates.

| Setting | Governs | Default |
|---|---|---|
| `esignet.tls_verify` / `ESIGNET_TLS_VERIFY` | The eSignet deployment: the conformance login flow, the OTP socket, and the e2e surface | **on** |
| `api.tls_verify` / `API_TLS_VERIFY` | The same eSignet, from the `api` module (a separate Go module, so it carries its own copy) | **on** |
| `conformance.tls_verify` / `CONFORMANCE_TLS_VERIFY` | The OpenID Conformance Suite only | **on**, but every shipped config sets it `false` |

The eSignet settings are on unless explicitly set to `false`, so a run against a real deployment
never sends the test identity, its OTP or its password over an unverified link. Turn them off only
for an eSignet with a self-signed certificate of its own.

`conformance.tls_verify` ships `false` for the bundled suite's self-signed
`localhost.emobix.co.uk` certificate. Override it when pointing at a remote suite. It governs
**only** the connection to the suite — it must never be the setting that decides how eSignet is
reached, which is exactly why `esignet.tls_verify` is separate.
