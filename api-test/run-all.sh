#!/usr/bin/env bash
#
# run-all.sh — run all three test surfaces for ONE plugin and produce a single
# consolidated HTML report (plan doc §6). Runs in git-bash on Windows or any
# POSIX shell. Surfaces are selectable; secrets come from the environment only.
#
#   Surfaces:
#     conformance  — cmd/conformance drives the OpenID suite (suite must be up)
#     bdd          — godog client-mgmt + flow/execute (eSignet must be reachable)
#     e2e          — cmd/e2e drives register -> authorize -> token -> userinfo
#                    (eSignet must be reachable)
#
#   Required env:
#     ESIGNET_BASE_URL      e.g. https://esignet-thunder1.esdev.mosip.net/v1/esignet
#   Optional env:
#     PLUGIN                mock | sunbird                   (default: mock)
#     SURFACES              comma list of conformance,bdd,e2e (default: all three)
#     CONFIG                conformance config path          (default: config.json)
#     KEYCLOAK_TOKEN_URL / KEYCLOAK_CLIENT_ID / KEYCLOAK_CLIENT_SECRET
#                           enable the client-mgmt surface (else it is ENV_NOT_READY)
#     AUTHN_PROVIDER        overrides the bdd plugin tag     (default: $PLUGIN)
#     BIN_DIR               dir with prebuilt conformance/e2e/consolidate/bdd.test
#                           binaries (set by the container image); unset -> `go
#                           run`/`go test` from source, for local dev.
#     SUITE_WAIT_SECONDS    seconds to poll the conformance suite's
#                           /api/runner/available before the conformance surface
#                           runs (default: 90; 0 disables the wait)
#
# Example:
#   ESIGNET_BASE_URL=https://esignet-thunder1.esdev.mosip.net/v1/esignet \
#   KEYCLOAK_TOKEN_URL=https://iam.esdev.mosip.net/auth/realms/mosip/protocol/openid-connect/token \
#   KEYCLOAK_CLIENT_ID=mosip-pms-client KEYCLOAK_CLIENT_SECRET=*** \
#   ./run-all.sh
#
set -uo pipefail
cd "$(dirname "$0")" || exit 1

PLUGIN="${PLUGIN:-mock}"
SURFACES="${SURFACES:-conformance,bdd,e2e}"
CONFIG="${CONFIG:-config.json}"
BIN_DIR="${BIN_DIR:-}"
export AUTHN_PROVIDER="${AUTHN_PROVIDER:-$PLUGIN}"

if [[ -n "$BIN_DIR" ]]; then
  run_conformance() { "$BIN_DIR/conformance" "$@"; }
  run_e2e()         { "$BIN_DIR/e2e" "$@"; }
  run_consolidate() { "$BIN_DIR/consolidate" "$@"; }
  run_bdd()         { ( cd bdd && "$BIN_DIR/bdd.test" -test.run TestFeatures ); }
else
  run_conformance() { go run ./cmd/conformance "$@"; }
  run_e2e()         { go run ./cmd/e2e "$@"; }
  run_consolidate() { go run ./cmd/consolidate "$@"; }
  run_bdd()         { ( cd bdd && go test ./... -run TestFeatures ); }
fi

# wait_for_suite polls the conformance suite's readiness endpoint so a
# container-started harness doesn't race the suite's ~30s Mongo/Java boot.
# Local dev (suite already up) succeeds on the first try.
wait_for_suite() {
  local wait_s="${SUITE_WAIT_SECONDS:-90}"
  # Guard the arithmetic below: a non-integer would otherwise error out here and,
  # with no `set -e`, drop the poll loop onto a garbage deadline.
  if ! [[ "$wait_s" =~ ^[0-9]+$ ]]; then
    echo "(SUITE_WAIT_SECONDS=$wait_s is not an integer — using 90)" >&2
    wait_s=90
  fi
  [[ "$wait_s" -eq 0 ]] && return 0
  local base="${CONFORMANCE_BASE_URL:-https://localhost.emobix.co.uk:8443}"
  local deadline=$((SECONDS + wait_s))
  until curl -skf -o /dev/null --max-time 3 "$base/api/runner/available" 2>/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "(conformance suite not reachable at $base after ${wait_s}s — proceeding anyway)" >&2
      return 0
    fi
    sleep 2
  done
}

echo "== api-test: plugin=$PLUGIN surfaces=$SURFACES =="

# A surface that dies before writing its envelope (build error, panic, missing
# env) is silently dropped from the consolidate args below, so track it here and
# fail the run rather than reporting green on a missing surface.
surface_failed=0
conf_json=""
if [[ ",$SURFACES," == *",conformance,"* ]]; then
  echo "-- conformance surface --"
  wait_for_suite
  # Capture stdout to learn the report path; the orchestrator exits non-zero on
  # failures, which is fine here — we still want to consolidate what it produced.
  conf_out="$(run_conformance -config "$CONFIG")" || echo "(conformance run reported failures)"
  printf '%s\n' "$conf_out"
  conf_html="$(printf '%s\n' "$conf_out" | sed -n 's/^report: //p' | tail -1)"
  [[ -n "$conf_html" ]] && conf_json="${conf_html%.html}.json"
  # No report at all means it died before writing one (config error, or a run
  # error with zero modules) — the same silent-green hole guarded for bdd/e2e.
  if [[ -z "$conf_json" ]]; then
    echo "conformance surface produced no report (crashed before writing one)" >&2
    surface_failed=1
  fi
fi

if [[ ",$SURFACES," == *",bdd,"* ]]; then
  echo "-- bdd surfaces (client-mgmt + flow/execute) --"
  if [[ -z "${ESIGNET_BASE_URL:-}" ]]; then
    echo "ESIGNET_BASE_URL is required for the bdd surfaces" >&2
    exit 2
  fi
  run_bdd || { echo "(bdd reported scenario failures)"; surface_failed=1; }
fi

if [[ ",$SURFACES," == *",e2e,"* ]]; then
  echo "-- e2e surface (create client -> login -> token -> userinfo claims) --"
  if [[ -z "${ESIGNET_BASE_URL:-}" ]]; then
    echo "ESIGNET_BASE_URL is required for the e2e surface" >&2
    exit 2
  fi
  run_e2e || { echo "(e2e reported scenario failures)"; surface_failed=1; }
fi

echo "-- consolidate --"
args=(-plugin "$PLUGIN" -out out)
[[ -n "$conf_json" ]] && args+=(-conformance "$conf_json")
[[ ",$SURFACES," == *",bdd,"* && -f out/bdd-envelope.json ]] && args+=(-bdd out/bdd-envelope.json)
[[ ",$SURFACES," == *",e2e,"* && -f out/e2e-envelope.json ]] && args+=(-e2e out/e2e-envelope.json)

# Missing envelopes for a requested surface mean it never produced results.
if [[ ",$SURFACES," == *",bdd,"* && ! -f out/bdd-envelope.json ]]; then
  echo "bdd surface produced no envelope (out/bdd-envelope.json missing)" >&2
  surface_failed=1
fi
if [[ ",$SURFACES," == *",e2e,"* && ! -f out/e2e-envelope.json ]]; then
  echo "e2e surface produced no envelope (out/e2e-envelope.json missing)" >&2
  surface_failed=1
fi

run_consolidate "${args[@]}"
consolidate_rc=$?

if (( surface_failed )); then
  echo "one or more surfaces failed to run to completion — see above" >&2
  exit 1
fi
exit "$consolidate_rc"
