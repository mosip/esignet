#!/usr/bin/env bash
# Bash equivalent of the Makefile. Works on Linux and Windows (Git Bash).
#
# Usage:
#   ./make.sh <target> [<target> ...] [VAR=VALUE ...]
#   ./make.sh run PORT=9090
#
# Precedence: VAR=VALUE args > .env > environment > defaults.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) WINDOWS=true ;;
  *)                    WINDOWS=false ;;
esac

# --- .env loading (equivalent of `-include .env` + `export`) ----------------
# tr strips CR so a CRLF-encoded .env does not poison values on Windows.
if [ -f .env ]; then
  set -a
  # Process substitution (source <(...)) does not export vars under macOS bash 3.2
  # with nounset; use a temp file so CRLF stripping still works on Windows.
  _env_file="$(mktemp)"
  trap 'rm -f "$_env_file"' EXIT
  tr -d '\r' < .env > "$_env_file"
  # shellcheck disable=SC1090
  source "$_env_file"
  set +a
fi

# --- VAR=VALUE command-line overrides (like `make run PORT=9090`) -----------
targets=()
for arg in "$@"; do
  if [[ "$arg" =~ ^[A-Za-z_][A-Za-z0-9_]*=.*$ ]]; then
    export "${arg?}"
  else
    targets+=("$arg")
  fi
done

# --- defaults ----------------------------------------------------------------
OUT_DIR=out
if $WINDOWS; then BINARY=$OUT_DIR/esignet.exe; else BINARY=$OUT_DIR/esignet; fi
CMD=./cmd/esignet
KEY_DIR=keys
SIGNING_KEY=$KEY_DIR/signing.key
SIGNING_CERT=$KEY_DIR/signing.crt
: "${PORT:=8080}"
# Backward compatibility: ISSUER_URL was renamed to MOSIP_ESIGNET_HOST.
if [ -z "${MOSIP_ESIGNET_HOST:-}" ] && [ -n "${ISSUER_URL:-}" ]; then
  MOSIP_ESIGNET_HOST="$ISSUER_URL"
fi
: "${MOSIP_ESIGNET_HOST:=http://127.0.0.1:$PORT}"
: "${DATA_DIR:=./data}"
: "${SIGNING_KEY_PATH:=./$SIGNING_KEY}"
: "${DOCKER_IMAGE:=esignet:latest}"
: "${GOLANGCI_LINT_VERSION:=latest}"
: "${SQLC_VERSION:=v1.29.0}"
: "${THUNDER_BRANCH:=engine}"
: "${RACE:=1}"   # set RACE=0 if no C toolchain (go test -race needs gcc on Windows)
THUNDER_MODULE=github.com/anushasunkada/thunder/backend

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "make.sh: '$1' not found on PATH (required for this target)"; exit 1; }
}

# Override GOFLAGS=-mod=readonly so go.mod/go.sum can be updated.
go_mod_write() {
  GOFLAGS="${GOFLAGS:+$GOFLAGS }-mod=mod" go "$@"
}

# --- targets -----------------------------------------------------------------

target_keys() { ## Generate local TLS signing key and certificate
  need openssl
  mkdir -p "$KEY_DIR"
  if [ -f "$SIGNING_KEY" ] && [ -f "$SIGNING_CERT" ]; then
    echo "keys: $SIGNING_KEY and $SIGNING_CERT already exist"
  else
    # MSYS_NO_PATHCONV / MSYS2_ARG_CONV_EXCL stop Git Bash from rewriting
    # "/CN=esignet" into "C:/Program Files/Git/CN=esignet".
    MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' \
      openssl req -x509 -newkey rsa:2048 \
        -keyout "$SIGNING_KEY" -out "$SIGNING_CERT" \
        -days 3650 -nodes -subj "/CN=esignet"
    echo "keys: wrote $SIGNING_KEY and $SIGNING_CERT"
  fi
}

target_build() { ## Compile production binary (out/esignet[.exe])
  need go
  target_keys
  mkdir -p "$OUT_DIR"
  CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o "$BINARY" "$CMD"
  echo "build: wrote $BINARY"
}

target_run() { ## Run with go run (development)
  target_keys
  PORT="$PORT" \
  MOSIP_ESIGNET_HOST="$MOSIP_ESIGNET_HOST" \
  DATA_DIR="$DATA_DIR" \
  AUTHN_PROVIDER="${AUTHN_PROVIDER:-mosip}" \
    go run "$CMD"
}

target_test() { ## Run unit tests (race + coverage; RACE=0 to disable race)
  target_keys
  if [ "$RACE" = "1" ]; then
    go test -race -cover ./...
  else
    go test -cover ./...
  fi
}

target_coverage() { ## Run tests and write coverage profile (coverage.out)
  target_keys
  if [ "$RACE" = "1" ]; then
    go test -race -coverprofile=coverage.out -covermode=atomic ./...
  else
    go test -coverprofile=coverage.out -covermode=atomic ./...
  fi
  go tool cover -func=coverage.out | tail -1
}

target_coverage_html() { ## Generate HTML coverage report (coverage.html)
  target_coverage
  go tool cover -html=coverage.out -o coverage.html
  echo "report: coverage.html"
}

target_lint() { ## Run golangci-lint
  local gobin
  gobin="$(go env GOPATH)/bin"
  # go env prints a Windows path (C:\...) under Git Bash; PATH needs /c/... form.
  if command -v cygpath >/dev/null 2>&1; then gobin="$(cygpath -u "$gobin")"; fi
  export PATH="$gobin:$PATH"
  export GOLANGCI_LINT_CACHE="$PWD/.golangci-lint-cache"
  if command -v golangci-lint >/dev/null 2>&1; then
    golangci-lint run ./...
  else
    echo "golangci-lint not on PATH; running via go run (use ./make.sh lint-install to install)"
    go run "github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$GOLANGCI_LINT_VERSION" run ./...
  fi
}

target_lint_install() { ## Install golangci-lint
  go install "github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$GOLANGCI_LINT_VERSION"
  echo "installed: $(go env GOPATH)/bin/golangci-lint"
}

target_smoke_jwt_key() { ## Generate local private_key_jwt smoke client key (gitignored)
  bash ./scripts/generate-smoke-jwt-client-key.sh
}

target_smoke() { ## End-to-end OAuth smoke test (server must be running)
  if [ ! -f ./scripts/fixtures/smoke-jwt-client.key ]; then
    echo "smoke-jwt-key: generating local JWT client key..."
    target_smoke_jwt_key
  fi
  # Invoked via bash so it works even when the exec bit is lost on Windows.
  BASE_URL="${BASE_URL:-$MOSIP_ESIGNET_HOST}" bash ./scripts/oauth-smoke.sh
}

target_docker_build() { ## Build container image (esignet:latest by default)
  need docker
  docker build -f Dockerfile -t "$DOCKER_IMAGE" .
}

target_docker_run() { ## Run container mapped to PORT (default 8080)
  target_docker_build
  docker run --rm -p "$PORT:8088" \
    -e MOSIP_ESIGNET_HOST="$MOSIP_ESIGNET_HOST" \
    -e AUTHN_PROVIDER="${AUTHN_PROVIDER:-mosip}" \
    -e CRYPTO_ENCRYPTION_KEY="${CRYPTO_ENCRYPTION_KEY:-}" \
    "$DOCKER_IMAGE"
}

target_sqlc() { ## Regenerate DB layer from SQL (requires sqlc; run sqlc-install first)
  local gobin
  gobin="$(go env GOPATH)/bin"
  if command -v cygpath >/dev/null 2>&1; then gobin="$(cygpath -u "$gobin")"; fi
  export PATH="$gobin:$PATH"
  if command -v sqlc >/dev/null 2>&1; then
    sqlc generate
  else
    echo "sqlc not on PATH; running via go run (use ./make.sh sqlc-install to install)"
    go run "github.com/sqlc-dev/sqlc/cmd/sqlc@$SQLC_VERSION" generate
  fi
}

target_sqlc_install() { ## Install sqlc
  go install "github.com/sqlc-dev/sqlc/cmd/sqlc@$SQLC_VERSION"
  echo "installed: $(go env GOPATH)/bin/sqlc"
}

target_update_thunder() { ## Update thunder replace directive to latest commit on THUNDER_BRANCH
  need go
  need git
  echo "Fetching latest commit on branch '$THUNDER_BRANCH'..."
  local sha version
  sha="$(git ls-remote https://github.com/anushasunkada/thunder.git "refs/heads/$THUNDER_BRANCH" | awk '{print $1}')"
  if [ -z "$sha" ]; then echo "error: branch '$THUNDER_BRANCH' not found"; exit 1; fi
  # Resolve the exact SHA we just fetched (the Makefile resolved the branch
  # name again, which could race with new pushes).
  version="$(GOPROXY=direct go_mod_write list -m -f '{{.Version}}' "$THUNDER_MODULE@$sha")"
  if [ -z "$version" ]; then echo "error: could not resolve version"; exit 1; fi
  echo "Resolved version: $version"
  sed -i.bak "s|replace github.com/thunder-id/thunderid => $THUNDER_MODULE .*|replace github.com/thunder-id/thunderid => $THUNDER_MODULE $version|" go.mod
  rm -f go.mod.bak
  echo "go.mod updated"
  go_mod_write mod tidy
  echo "go.sum updated"
}

target_tidy() { ## Run go mod tidy
  go_mod_write mod tidy
}

target_clean() { ## Remove build artefacts and coverage output
  rm -f "$BINARY"
  rm -rf bin/ "${OUT_DIR:?}/"
  rm -f coverage.out coverage.html
}

target_distclean() { ## Also remove generated signing keys under keys/
  target_clean
  rm -f "$SIGNING_KEY" "$SIGNING_CERT"
  rmdir "$KEY_DIR" 2>/dev/null || true
}

target_help() { ## Show this help
  cat <<EOF

Usage: ./make.sh <target> [<target> ...] [VAR=VALUE ...]

Build
  all                Alias for build
  keys               Generate local TLS signing key and certificate
  build              Compile production binary ($BINARY)

Run
  run                Run with go run (development)
  dev                Alias for run

Test
  test               Run unit tests (race + coverage; RACE=0 to disable race)
  coverage           Run tests and write coverage profile (coverage.out)
  coverage-html      Generate HTML coverage report (coverage.html)
  lint               Run golangci-lint
  lint-install       Install golangci-lint (GOLANGCI_LINT_VERSION=$GOLANGCI_LINT_VERSION)
  smoke              End-to-end OAuth smoke test (server must be running)
  smoke-jwt-key      Generate local private_key_jwt smoke client key (gitignored)

Docker
  docker-build       Build container image ($DOCKER_IMAGE)
  docker-run         Run container mapped to PORT (default 8080)

Maintenance
  sqlc               Regenerate DB layer from SQL (requires sqlc; run sqlc-install first)
  sqlc-install       Install sqlc (SQLC_VERSION=$SQLC_VERSION)
  update-thunder     Update thunder replace directive and refresh go.sum (THUNDER_BRANCH=$THUNDER_BRANCH)
  tidy               Run go mod tidy
  clean              Remove build artefacts and coverage output
  distclean          Also remove generated signing keys under keys/

Environment (override on the command line or in .env):
  PORT=$PORT
  MOSIP_ESIGNET_HOST=$MOSIP_ESIGNET_HOST
  DATA_DIR=$DATA_DIR
  AUTHN_PROVIDER=${AUTHN_PROVIDER:-} (mosip|sunbird, default mosip)
  MOSIP_API_INTERNAL_HOST (optional, used to derive IDA endpoint URLs)
  MOSIP_ESIGNET_MISP_KEY (optional, used in IDA endpoint paths)
  MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL (optional override)
  MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL (optional override)
  MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL (optional override)
  MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL (optional override)
  MOSIP_P12_PATH (required for MOSIP auth)
  MOSIP_P12_PASSWORD (required for MOSIP auth)

EOF
}

# --- dispatch ----------------------------------------------------------------
if [ ${#targets[@]} -eq 0 ]; then targets=(help); fi

for t in "${targets[@]}"; do
  case "$t" in
    help)            target_help ;;
    all|build)       target_build ;;
    keys)            target_keys ;;
    run|dev)         target_run ;;
    test)            target_test ;;
    coverage)        target_coverage ;;
    coverage-html)   target_coverage_html ;;
    lint)            target_lint ;;
    lint-install)    target_lint_install ;;
    smoke)           target_smoke ;;
    smoke-jwt-key)   target_smoke_jwt_key ;;
    docker-build)    target_docker_build ;;
    docker-run)      target_docker_run ;;
    sqlc)            target_sqlc ;;
    sqlc-install)    target_sqlc_install ;;
    update-thunder)  target_update_thunder ;;
    tidy)            target_tidy ;;
    clean)           target_clean ;;
    distclean)       target_distclean ;;
    *) echo "make.sh: unknown target '$t' (run ./make.sh help)"; exit 1 ;;
  esac
done
