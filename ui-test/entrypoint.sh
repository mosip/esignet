#!/bin/bash
set -euo pipefail

cd /home/mosip

echo "Java: $(java -version 2>&1 | head -1)"
echo "Chrome: $(google-chrome --version 2>/dev/null || echo missing)"
echo "ChromeDriver: $(chromedriver --version 2>/dev/null || echo missing)"
echo "ENV_ENDPOINT=${ENV_ENDPOINT:-}"
echo "ENV_USER=${ENV_USER:-}"
echo "ENV_TESTLEVEL=${ENV_TESTLEVEL:-smokeAndRegression}"
echo "MODULES=${MODULES:-esignet}"

if [[ -z "${ENV_ENDPOINT:-}" ]]; then
  echo "ENV_ENDPOINT is required (e.g. https://api-internal.esqa.mosip.net)" >&2
  exit 1
fi

JAR=$(ls -1 uitest-esignet-*-jar-with-dependencies.jar 2>/dev/null | head -1)
if [[ -z "${JAR}" ]]; then
  echo "Shaded uitest-esignet jar not found in $(pwd)" >&2
  ls -la
  exit 1
fi

# JVM -D flags must precede -jar. RUN_DOCKER tells BaseTestUtil to use the image's ChromeDriver.
JAVA_ARGS=(
  -Denv.endpoint="${ENV_ENDPOINT}"
  -Denv.user="${ENV_USER:-api-internal.esqa}"
  -Dmodules="${MODULES:-esignet}"
  -Denv.testLevel="${ENV_TESTLEVEL:-smokeAndRegression}"
  -DrunDocker=yes
  -Dheadless=true
)

if [[ -n "${CUCUMBER_FILTER_TAGS:-}" ]]; then
  JAVA_ARGS+=(-Dcucumber.filter.tags="${CUCUMBER_FILTER_TAGS}")
fi
if [[ -n "${RUN_ONLY_SCENARIO:-}" ]]; then
  JAVA_ARGS+=(-DrunOnlyScenario="${RUN_ONLY_SCENARIO}")
fi
if [[ -n "${FEATURE_FILES_TO_EXECUTE:-}" ]]; then
  JAVA_ARGS+=(-DfeatureFilesToExecute="${FEATURE_FILES_TO_EXECUTE}")
fi

# JAVA_EXTRA_OPTS: extra JVM flags from Rancher, space-separated (e.g. -Xmx2g)
# shellcheck disable=SC2206
EXTRA=( ${JAVA_EXTRA_OPTS:-} )

echo "Starting ${JAR}"
exec java "${EXTRA[@]}" "${JAVA_ARGS[@]}" -jar "${JAR}"
