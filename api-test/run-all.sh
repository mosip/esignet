#!/usr/bin/env bash
#
# run-all.sh — run the selected test surfaces for ONE plugin and produce a single
# consolidated HTML report (plan doc §6). Runs in git-bash on Windows or any
# POSIX shell.
#
# Everything is configured by a per-plugin JSON file; no exports needed locally.
# Environment variables still override any value in it, which is how containers
# (compose / Rancher) inject secrets and per-deployment URLs.
#
#   ./run-all.sh -c config.mosip.json --check    # what would run
#   ./run-all.sh -c config.mosip.json            # run it
#
#   Surfaces (run.surfaces in the config):
#     conformance  — cmd/conformance drives the OpenID suite (suite must be up)
#     bdd          — godog client-mgmt + flow/execute (eSignet must be reachable)
#     e2e          — cmd/e2e drives register -> authorize -> token -> userinfo
#                    (eSignet must be reachable)
#
#   Flags:
#     -c, --config PATH   config file (default: $CONFIG, else config.json).
#                         An explicitly named file that does not exist is an
#                         error — silently falling back would run the wrong
#                         plugin and report it as the one you asked for.
#     -s, --surfaces LIST comma list, overrides run.surfaces for this run only
#         --check         print the resolved plan and exit without running
#
#   Env (all optional; each overrides the same-named config field):
#     CONFIG                config path, same as -c
#     CONFIG_LOCAL          overlay path (default: config.local.json beside -c)
#     ESIGNET_BASE_URL, KEYCLOAK_*, INDIVIDUAL_ID, SURFACES, TEST_PROFILE, ...
#     BIN_DIR               dir with prebuilt conformance/e2e/consolidate/cfg/
#                           bdd.test binaries (set by the container image);
#                           unset -> `go run`/`go test` from source.
#     SUITE_WAIT_SECONDS    seconds to poll the conformance suite's
#                           /api/runner/available before the conformance surface
#                           runs (default: 90; 0 disables the wait)
#
set -uo pipefail
cd "$(dirname "$0")" || exit 1

# Whether CONFIG was chosen (env or -c) rather than falling back to the default
# must be decided BEFORE the default is applied.
CONFIG_EXPLICIT=0
[[ -n "${CONFIG:-}" ]] && CONFIG_EXPLICIT=1
CONFIG="${CONFIG:-config.json}"
SURFACES_OVERRIDE=""
CHECK_ONLY=0
BIN_DIR="${BIN_DIR:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -c|--config)   CONFIG="$2"; CONFIG_EXPLICIT=1; shift 2 ;;
    -s|--surfaces) SURFACES_OVERRIDE="$2"; shift 2 ;;
    --check)       CHECK_ONLY=1; shift ;;
    # Print the header comment block, however long it grows, and stop at the
    # first line of actual script.
    -h|--help)     awk 'NR>1{ if (/^#/) print; else exit }' "$0"; exit 0 ;;
    *) echo "unknown argument: $1 (try --help)" >&2; exit 2 ;;
  esac
done

# A config named explicitly must exist. Falling through to defaults on a typo
# would run mock while the operator believes mosip ran, and the green report
# would be read as mosip passing.
if (( CONFIG_EXPLICIT )) && [[ ! -f "$CONFIG" ]]; then
  echo "config file not found: $CONFIG" >&2
  exit 2
fi
export CONFIG

# -s narrows the run without editing the file; it goes through the same env
# override path every other field uses.
[[ -n "$SURFACES_OVERRIDE" ]] && export SURFACES="$SURFACES_OVERRIDE"

if [[ -n "$BIN_DIR" ]]; then
  run_conformance() { "$BIN_DIR/conformance" "$@"; }
  run_e2e()         { "$BIN_DIR/e2e" "$@"; }
  run_consolidate() { "$BIN_DIR/consolidate" "$@"; }
  run_cfg()         { "$BIN_DIR/cfg" "$@"; }
  run_bdd()         { ( cd bdd && "$BIN_DIR/bdd.test" -test.run TestFeatures ); }
else
  run_conformance() { go run ./cmd/conformance "$@"; }
  run_e2e()         { go run ./cmd/e2e "$@"; }
  run_consolidate() { go run ./cmd/consolidate "$@"; }
  run_cfg()         { go run ./cmd/cfg "$@"; }
  run_bdd()         { ( cd bdd && go test ./... -run TestFeatures ); }
fi

if (( CHECK_ONLY )); then
  run_cfg -config "$CONFIG" -check
  exit $?
fi

# Resolve the config once and hand it to every surface. The bdd tree is a
# separate Go module that cannot import internal/config, so it receives the
# resolved values as environment variables — this eval is the only bridge.
# Values are already fully resolved (file + overlay + env), so an operator's own
# override was folded in before this ran and survives it.
CFG_ENV="$(run_cfg -config "$CONFIG" -print-env)" || {
  echo "config error (see above)" >&2
  exit 2
}
eval "$CFG_ENV"
unset CFG_ENV

# PLUGIN and SURFACES come from that same eval, so there is no second config load
# that could fail on its own and leave SURFACES empty — which would run nothing
# and still consolidate to a green report.
if [[ -z "${SURFACES:-}" ]]; then
  echo "no surfaces selected (run.surfaces resolved empty)" >&2
  exit 2
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

echo "== api-test: plugin=$PLUGIN surfaces=$SURFACES config=$CONFIG =="

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
    echo "esignet.base_url (ESIGNET_BASE_URL) is required for the bdd surfaces" >&2
    exit 2
  fi
  run_bdd || { echo "(bdd reported scenario failures)"; surface_failed=1; }
fi

if [[ ",$SURFACES," == *",e2e,"* ]]; then
  echo "-- e2e surface (create client -> login -> token -> userinfo claims) --"
  run_e2e -config "$CONFIG" || { echo "(e2e reported scenario failures)"; surface_failed=1; }
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
