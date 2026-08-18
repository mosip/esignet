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
#   ./run-all.sh -c data/config/config.mosip.json --check    # what would run
#   ./run-all.sh -c data/config/config.mosip.json            # run it
#
#   Surfaces (run.surfaces in the config):
#     conformance  — cmd/conformance drives the OpenID suite (suite must be up)
#     api          — godog endpoint scenarios: client-mgmt + flow/execute
#                    (eSignet must be reachable)
#     e2e          — cmd/e2e drives register -> authorize -> token -> userinfo
#                    (eSignet must be reachable)
#
#   Flags:
#     -c, --config PATH   config file (default: $CONFIG, else
#                         data/config/config.json).
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
#                           api.test binaries (set by the container image);
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
CONFIG="${CONFIG:-data/config/config.json}"
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
  # Runs from the harness root, not from api/: the prebuilt binary carries no
  # module directory with it, so the image points API_FEATURES_DIR at the
  # features tree instead.
  run_api()         { "$BIN_DIR/api.test" -test.run TestFeatures; }
else
  run_conformance() { go run ./cmd/conformance "$@"; }
  run_e2e()         { go run ./cmd/e2e "$@"; }
  run_consolidate() { go run ./cmd/consolidate "$@"; }
  run_cfg()         { go run ./cmd/cfg "$@"; }
  # -count=1 defeats Go's test cache. These are integration tests against a live
  # eSignet, so their outcome depends on that server, not on the Go sources the
  # cache keys off. Without it a second run with unchanged sources prints
  # "ok (cached)" without executing a single scenario, writes no envelope, and
  # fails the run with "api surface produced no envelope" — a message that points
  # at a missing file rather than at the cache. The BIN_DIR branch above needs no
  # such flag: it runs the prebuilt api.test binary, which is not cached.
  run_api()         { ( cd api && go test ./... -run TestFeatures -count=1 ); }
fi

if (( CHECK_ONLY )); then
  run_cfg -config "$CONFIG" -check
  exit $?
fi

# Resolve the config once and hand it to every surface. The api tree is a
# separate Go module that cannot import internal/config, so it receives the
# resolved values as environment variables — this eval is the only bridge.
# Values are already fully resolved (file + overlay + env), so an operator's own
# override was folded in before this ran and survives it.
# Tracing off across the substitution and the eval: with `set -x` in the caller's
# environment, bash would echo every resolved export — TEST_PASSWORD, TEST_OTP,
# KEYCLOAK_CLIENT_SECRET — into the CI log. Restored afterwards only if it was on.
__xtrace_was_on=0
case "$-" in *x*) __xtrace_was_on=1 ;; esac
set +x
CFG_ENV="$(run_cfg -config "$CONFIG" -print-env)" || {
  echo "config error (see above)" >&2
  exit 2
}
eval "$CFG_ENV"
unset CFG_ENV
[[ "$__xtrace_was_on" == "1" ]] && set -x
unset __xtrace_was_on

# PLUGIN and SURFACES come from that same eval, so there is no second config load
# that could fail on its own and leave SURFACES empty — which would run nothing
# and still consolidate to a green report.
if [[ -z "${SURFACES:-}" ]]; then
  echo "no surfaces selected (run.surfaces resolved empty)" >&2
  exit 2
fi

# One output directory for every surface. REPORT_DIR comes from the same eval
# (run.report_dir), and each surface has to be told about it explicitly: only
# cmd/conformance reads the config, while the api module and cmd/e2e default to
# a hardcoded out/. Under compose only $REPORT_DIR is bind-mounted, so a surface
# left on the default wrote its results somewhere the host never sees.
OUT_DIR="${REPORT_DIR:-out}"
mkdir -p "$OUT_DIR" || { echo "cannot create report dir $OUT_DIR" >&2; exit 2; }
# The api module runs with cwd=api/, so a relative path would land in api/out.
OUT_ABS="$(cd "$OUT_DIR" && pwd)"
export API_ENVELOPE_OUT="$OUT_ABS/api-envelope.json"

# The feature files live in the shared data/ tree rather than inside the api
# module, so the surface has to be told where they are. Absolute, so it resolves
# the same whether the module runs from source (cwd=api/) or as the prebuilt
# binary (cwd=harness root). The image exports its own value; honour it.
export API_FEATURES_DIR="${API_FEATURES_DIR:-$PWD/data/features}"

# Clear the envelopes this run is about to produce. The consolidate step below
# decides a surface ran by testing -f on its envelope, so a surface that dies
# before writing one (build error, panic, missing env) would otherwise leave the
# PREVIOUS run's file in place and have it folded into this report as current —
# stale results presented as fresh, which is worse than the missing-surface
# error the check is there to raise.
rm -f "$OUT_DIR/api-envelope.json" "$OUT_DIR/e2e-envelope.json"

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
  # Follow the configured TLS policy instead of always passing -k: the harness
  # centralizes that decision in httpx.NewClient, and CONFORMANCE_TLS_VERIFY
  # comes from the same eval above. (The suite's default self-signed localhost
  # cert is why every shipped config sets it false.)
  local curl_opts=(-sf -o /dev/null --max-time 3)
  [[ "${CONFORMANCE_TLS_VERIFY:-true}" == "false" ]] && curl_opts+=(-k)
  local deadline=$((SECONDS + wait_s))
  until curl "${curl_opts[@]}" "$base/api/runner/available" 2>/dev/null; do
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
  # error with zero modules) — the same silent-green hole guarded for api/e2e.
  if [[ -z "$conf_json" ]]; then
    echo "conformance surface produced no report (crashed before writing one)" >&2
    surface_failed=1
  fi
fi

if [[ ",$SURFACES," == *",api,"* ]]; then
  echo "-- api surface (client-mgmt + flow/execute endpoints) --"
  if [[ -z "${ESIGNET_BASE_URL:-}" ]]; then
    echo "esignet.base_url (ESIGNET_BASE_URL) is required for the api surfaces" >&2
    exit 2
  fi
  run_api || { echo "(api reported scenario failures)"; surface_failed=1; }
fi

if [[ ",$SURFACES," == *",e2e,"* ]]; then
  echo "-- e2e surface (create client -> login -> token -> userinfo claims) --"
  run_e2e -config "$CONFIG" -out "$OUT_DIR/e2e-envelope.json" || { echo "(e2e reported scenario failures)"; surface_failed=1; }
fi

echo "-- consolidate --"
args=(-plugin "$PLUGIN" -out "$OUT_DIR")
# consolidate has no config of its own; forward the resolved debug flag so the
# consolidated report redacts exactly like the per-surface ones.
[[ "${DEBUG_SHOW_SECRETS:-false}" == "true" ]] && args+=(-show-secrets)
[[ -n "$conf_json" ]] && args+=(-conformance "$conf_json")
[[ ",$SURFACES," == *",api,"* && -f "$OUT_DIR/api-envelope.json" ]] && args+=(-api "$OUT_DIR/api-envelope.json")
[[ ",$SURFACES," == *",e2e,"* && -f "$OUT_DIR/e2e-envelope.json" ]] && args+=(-e2e "$OUT_DIR/e2e-envelope.json")

# Missing envelopes for a requested surface mean it never produced results.
if [[ ",$SURFACES," == *",api,"* && ! -f "$OUT_DIR/api-envelope.json" ]]; then
  echo "api surface produced no envelope ($OUT_DIR/api-envelope.json missing)" >&2
  surface_failed=1
fi
if [[ ",$SURFACES," == *",e2e,"* && ! -f "$OUT_DIR/e2e-envelope.json" ]]; then
  echo "e2e surface produced no envelope ($OUT_DIR/e2e-envelope.json missing)" >&2
  surface_failed=1
fi

run_consolidate "${args[@]}"
consolidate_rc=$?

if (( surface_failed )); then
  echo "one or more surfaces failed to run to completion — see above" >&2
  exit 1
fi
exit "$consolidate_rc"
