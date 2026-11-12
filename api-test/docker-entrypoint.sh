#!/usr/bin/env bash
#
# docker-entrypoint.sh — container entrypoint for the harness image. Starts the
# bundled mock MDS (MOSIP SBI device service) on loopback, waits until it answers,
# then hands every argument to run-all.sh unchanged.
#
# The biometric tests reach the MDS over the SBI loopback API exactly as a
# browser on a citizen's machine would, so it has to run inside this container,
# not as a compose sibling: it binds 127.0.0.1 only.
#
#   Env (all optional):
#     MOCK_MDS_ENABLED        false skips the MDS entirely (default: true)
#     MOCK_MDS_PORT           SBI port; pinned so tests get a fixed URL (default: 4501)
#     MOCK_MDS_PURPOSE        Auth | Registration (default: Auth)
#     MOCK_MDS_BIOMETRIC_TYPE Biometric Device | Finger | Face | Iris
#                             (default: Biometric Device — all three modalities)
#     MOCK_MDS_IMAGE_TYPE     WSQ | JP2000 (default: WSQ; Registration needs JP2000)
#     MOCK_MDS_WAIT_SECONDS   seconds to wait for the MDS to answer (default: 60)
#     MOCK_MDS_LOG            MDS stdout/stderr (default: <REPORT_DIR|out>/mock-mds.log)
#     MOCK_MDS_JAVA_OPTS      extra JVM flags
#     MOCK_MDS_AUTH_SERVER_URL, MOCK_MDS_AUTH_APP_ID, MOCK_MDS_AUTH_CLIENT_ID,
#     MOCK_MDS_AUTH_SECRET_KEY, MOCK_MDS_IDA_SERVER_URL
#                             Auth-purpose capture encrypts the biometric with the
#                             IDA partner certificate, fetched with these. Each
#                             overrides its mosip.auth.* / mosip.ida.* property.
#
#   Exports for the surfaces:
#     MOCK_MDS_BASE_URL       http://127.0.0.1:<port>, once the MDS is answering
#
set -uo pipefail
cd "$(dirname "$0")" || exit 1

MOCK_MDS_HOME="${MOCK_MDS_HOME:-/opt/mock-mds}"

# set_prop rewrites one key of the MDS's application.properties. Delete-then-append
# rather than sed substitution: the values are URLs and secrets, and any of `/`,
# `&` or `|` in them would corrupt a sed replacement.
set_prop() {
  local file="$1" key="$2" value="$3"
  grep -v "^${key//./\\.}=" "$file" > "$file.tmp"
  printf '%s=%s\n' "$key" "$value" >> "$file.tmp"
  mv "$file.tmp" "$file"
}

start_mock_mds() {
  local port="${MOCK_MDS_PORT:-4501}"
  local props="$MOCK_MDS_HOME/application.properties"
  local log="${MOCK_MDS_LOG:-${REPORT_DIR:-out}/mock-mds.log}"
  local wait_s="${MOCK_MDS_WAIT_SECONDS:-60}"
  if ! [[ "$wait_s" =~ ^[0-9]+$ ]]; then
    echo "(MOCK_MDS_WAIT_SECONDS=$wait_s is not an integer — using 60)" >&2
    wait_s=60
  fi

  # min == max pins the port. Left as a range, the MDS takes the first free port
  # in it, and a test hardcoding 4501 would talk to whatever else got there first.
  set_prop "$props" server.minport "$port"
  set_prop "$props" server.maxport "$port"
  [[ -n "${MOCK_MDS_AUTH_SERVER_URL:-}" ]] && set_prop "$props" mosip.auth.server.url "$MOCK_MDS_AUTH_SERVER_URL"
  [[ -n "${MOCK_MDS_AUTH_APP_ID:-}" ]]     && set_prop "$props" mosip.auth.appid "$MOCK_MDS_AUTH_APP_ID"
  [[ -n "${MOCK_MDS_AUTH_CLIENT_ID:-}" ]]  && set_prop "$props" mosip.auth.clientid "$MOCK_MDS_AUTH_CLIENT_ID"
  [[ -n "${MOCK_MDS_AUTH_SECRET_KEY:-}" ]] && set_prop "$props" mosip.auth.secretkey "$MOCK_MDS_AUTH_SECRET_KEY"
  [[ -n "${MOCK_MDS_IDA_SERVER_URL:-}" ]]  && set_prop "$props" mosip.ida.server.url "$MOCK_MDS_IDA_SERVER_URL"

  mkdir -p "$(dirname "$log")"
  # The MDS resolves application.properties, Biometric Devices/, Profile/ and
  # files/ against its working directory, hence the cd. --enable-preview because
  # the published jar is compiled with it.
  # shellcheck disable=SC2086 # MOCK_MDS_JAVA_OPTS is a flag list, split on purpose
  ( cd "$MOCK_MDS_HOME" && exec java --enable-preview -Djava.awt.headless=true ${MOCK_MDS_JAVA_OPTS:-} \
      -cp 'lib/*' io.mosip.mock.sbi.test.TestMockSBI \
      "mosip.mock.sbi.device.purpose=${MOCK_MDS_PURPOSE:-Auth}" \
      "mosip.mock.sbi.biometric.type=${MOCK_MDS_BIOMETRIC_TYPE:-Biometric Device}" \
      "mosip.mock.sbi.biometric.image.type=${MOCK_MDS_IMAGE_TYPE:-WSQ}" ) > "$log" 2>&1 &
  local pid=$!

  # Any HTTP answer to a device discovery means the SBI listener is up. The device
  # list itself is the tests' business, not the entrypoint's.
  local deadline=$((SECONDS + wait_s))
  until curl -s -o /dev/null --max-time 2 -X MOSIPDISC -H 'Content-Type: application/json' \
      -d '{"type":"Biometric Device"}' "http://127.0.0.1:$port/device"; do
    if ! kill -0 "$pid" 2>/dev/null || (( SECONDS >= deadline )); then
      # Running on is deliberate, as with the conformance suite wait in run-all.sh:
      # only the biometric scenarios need the MDS, and they fail on their own with
      # a connection error. Aborting here would leave every other surface unrun.
      echo "(mock MDS not answering on 127.0.0.1:$port — running anyway; biometric scenarios will fail. Last log lines from $log:)" >&2
      tail -n 20 "$log" >&2
      return 0
    fi
    sleep 1
  done
  export MOCK_MDS_BASE_URL="http://127.0.0.1:$port"
  echo "== mock MDS: purpose=${MOCK_MDS_PURPOSE:-Auth} url=$MOCK_MDS_BASE_URL log=$log =="
}

# --check and --help resolve and print only; starting a JVM for them would just
# slow them down.
needs_mds=1
for arg in "$@"; do
  case "$arg" in --check|-h|--help) needs_mds=0 ;; esac
done

if [[ "${MOCK_MDS_ENABLED:-true}" == "true" ]] && (( needs_mds )); then
  start_mock_mds
fi

exec ./run-all.sh "$@"
