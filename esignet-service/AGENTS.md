# AGENTS.md

Guidance for coding agents working in `esignet-service`, a Go service (module
`github.com/mosip/esignet`) that embeds the Thunder authorization engine with
PostgreSQL-backed OIDC client management, Redis-backed session/flow storage,
and pluggable authentication (mock identity system, MOSIP IDA, or SunbirdRC
KBI). See `README.md` in this directory for the full environment-variable
reference and API docs.

## Layout

```text
cmd/esignet/main.go          HTTP entrypoint
internal/clientmgmt/         OIDC client management API + sqlc-generated db/ layer
internal/config/             env-based config (app, DB, Redis)
internal/engine/             Thunder engine providers/executors
  mock/                      Mock authenticator (local/dev)
  mosip/                     MOSIP IDA authn provider + auditor
  sunbird/                   SunbirdRC KBI authn provider
  runtimestores/             Redis-backed flow/session/PAR stores
  shared/                    Code shared across engine providers
internal/security/           JWKS validation, scope middleware, request-time checks
internal/log/                Structured logging helpers
internal/common/             Shared models/utils
data/                        Declarative YAML (deployment.yaml, flows/, i18n/, layouts/, themes/)
keys/                        signing.key + signing.crt (generated locally, gitignored)
sqlc.yaml                    SQLC codegen config for internal/clientmgmt/db
make.sh                      build/run/test entry point (Linux + Git Bash)
```

## Build, test, lint

Everything goes through `./make.sh` (run from this directory):

```bash
./make.sh build            # compiles out/esignet[.exe]; generates signing keys first
./make.sh run               # go run for development (AUTHN_PROVIDER defaults to mosip)
./make.sh test               # go test -race -cover ./...
./make.sh coverage          # coverage.out + summary
./make.sh lint               # golangci-lint run ./...
```

End-to-end / client-mgmt API checks are done via the [Postman collection](../postman-collection/README.md) (a sibling directory, outside `esignet-service`), not a shell script.

- Tests use `miniredis` (no live Redis needed) and mock queriers for the
  Postgres layer, so `./make.sh test` runs standalone. Run `./make.sh keys`
  first if a test exercises JWT signing (build targets do this automatically).
- After changing `internal/clientmgmt/db/query.sql` or `schema.sql`, regenerate
  the generated Go DB layer with `./make.sh sqlc-install` (one-time) then
  `./make.sh sqlc` — never hand-edit `internal/clientmgmt/db/*.go` generated files.
- `go.mod` has a `replace` directive pinning a Thunder engine fork/branch;
  refresh it with `./make.sh update-thunder`, don't hand-edit the pinned SHA.

## Conventions

- Standard Go project layout: exported API surface stays in package roots,
  everything else in `internal/`. Follow existing package boundaries above
  rather than introducing new top-level packages for small additions.
- Env-driven configuration lives in `internal/config/`; add new settings there
  with sane defaults, and document them in `README.md`'s environment-variable
  tables when user-facing.
- Auth providers (`mosip`, `sunbird`, `mock`) are added under
  `internal/engine/<provider>/` and follow the same `authenticator.go` /
  `config.go` / `init.go` / `model.go` shape as existing providers.
- Every package's tests follow the `testify/suite` pattern — colocated as
  `_test.go` next to the code, not a separate table-driven-function style.
  Add new test cases as methods on the existing `<Name>TestSuite` struct
  (embeds `suite.Suite`); only add a new suite (`type FooTestSuite struct {
  suite.Suite }` + `func TestFooTestSuite(t *testing.T) { suite.Run(t,
  new(FooTestSuite)) }`) when testing a package that doesn't have one yet.
  Use `ts.T()` plus `testify/assert` (or `ts.Assert()`/`ts.Require()`) inside
  suite methods.
