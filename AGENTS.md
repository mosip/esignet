# AGENTS.md

Guidance for coding agents working in this repository: `mosip/esignet`, an
Open ID Connect identity provider. This is a **monorepo of independent
subprojects** in different stacks — pick the right one below and prefer its
own `README.md`/`AGENTS.md` over guessing conventions from a sibling.

## Layout

| Path | Stack | Purpose |
|------|-------|---------|
| `esignet-service/` | Go 1.26, `make.sh` | The OIDC/OAuth2 provider itself. Has its own [`AGENTS.md`](esignet-service/AGENTS.md) — read that before working here. |
| `oidc-ui/` | React + TypeScript + Vite (`package.json`) | Login/consent UI. |
| `postman-collection/` | Postman collection + environment (JSON) | Manual/scripted API checks against `esignet-service`. |
| `docker-compose/` | Docker Compose | Local Postgres + Redis for `esignet-service`. |
| `deploy/`, `helm/` | Kubernetes manifests, Helm charts | Deployment. |
| `db_scripts/`, `db_upgrade_script/` | SQL | Schema creation and version-named migrations. |
| `partner-onboarder/` | Shell + config | MISP partner certificate exchange. |
| `api-test/`, `ui-test/` | Java (Maven, REST Assured/Selenium + TestNG) | API and UI automation suites. |
| `performance-test/` | JMeter | Load/performance scripts. |

## Working across subprojects

- Most day-to-day feature work happens in `esignet-service/` (the provider)
  and `oidc-ui/` (the UI it redirects to for login/consent). Check both
  `.env.example`/`README.md` files for how they're wired together
  (`MOSIP_ESIGNET_HOST`, `OIDC_UI_*`).
- `postman-collection/` is the primary end-to-end/manual test path for the
  API — there is no shell-script-based smoke test in this repo (see its
  [README](postman-collection/README.md)).
- Don't assume a convention from one subproject applies to another (e.g. Go's
  `internal/` package boundaries have no equivalent in `oidc-ui/`). Each
  subproject is built, tested, and versioned independently.
- SQL changes belong in `db_scripts/` (base schema) or `db_upgrade_script/`
  (versioned migrations) — `esignet-service/internal/clientmgmt/db/schema.sql`
  is the sqlc-facing source of truth for the `client_detail` table
  specifically; keep it in sync if you change that table elsewhere.

## Conventions

- Only commit generated/build artifacts if a subproject's own docs say to;
  default to treating `out/`, `target/`, `node_modules/`, coverage files, etc.
  as build output.
- Match the license header style already present in a file's package/module
  before adding new files (e.g. the MPL 2.0 header used throughout
  `esignet-service`).
