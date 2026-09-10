#!/bin/bash
set -euo pipefail

cd "${work_dir:?work_dir must be set}"

# Cucumber's html:reports plugin writes a FILE named "reports". The image used to mkdir that
# path as a directory, which makes TestNGCucumberRunner.setUpClass fail with FileNotFoundException.
if [[ -d reports ]]; then
  rm -rf reports
fi
mkdir -p target test-output screenshots testng-report

echo "Java: $(java -version 2>&1 | head -1)"
echo "Chrome: $(google-chrome --version 2>/dev/null || chromium --version 2>/dev/null || echo missing)"
echo "ChromeDriver: $(chromedriver --version 2>/dev/null || echo missing)"
echo "ENV_ENDPOINT=${ENV_ENDPOINT:-}"
echo "ENV_USER=${ENV_USER:-}"
echo "ENV_TESTLEVEL=${ENV_TESTLEVEL:-smokeAndRegression}"
echo "MODULES=${MODULES:-esignet}"

JAR=$(ls -1 uitest-esignet-*-jar-with-dependencies.jar 2>/dev/null | head -1)
if [[ -z "${JAR}" ]]; then
  JAR=$(ls -1 uitest-esignet-*.jar 2>/dev/null | head -1)
fi
if [[ -z "${JAR}" ]]; then
  echo "Shaded uitest-esignet jar not found in $(pwd)" >&2
  ls -la
  exit 1
fi

# JVM -D flags must precede -jar. RUN_DOCKER tells BaseTestUtil to use the image's ChromeDriver.
# Always pass -Denv.endpoint (empty is fine). apitest-commons ConfigManager calls
# System.getProperty("env.endpoint").replace(...) for blank signup/inji URLs and NPEs if the
# property is missing.
JAVA_ARGS=(
  -Denv.endpoint="${ENV_ENDPOINT:-}"
  -Denv.user="${ENV_USER:-api-internal.esqa}"
  -Dmodules="${MODULES:-esignet}"
  -Denv.testLevel="${ENV_TESTLEVEL:-smokeAndRegression}"
  -DrunDocker=yes
  -Dheadless=true
  # Same-container Chrome + Mock SBI; BrowserStack cannot reach localhost:4501-4510.
  -DrunOnBrowserStack=false
  -DuseMockMds=true
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
exec java "${JAVA_ARGS[@]}" "${EXTRA[@]}" -jar "${JAR}"
