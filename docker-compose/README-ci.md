# API & UI test automation on pull requests

How the `run-apitest-pr` CI job brings up eSignet with Docker Compose and runs the API and UI
test suites against it, and how the "ChromeDriver not available" failure in the UI tests was fixed.

Issue: **#2618**

---

## Table of Contents

1. [What runs in CI](#what-runs-in-ci)
2. [Files involved](#files-involved)
3. [The ChromeDriver issue](#the-chromedriver-issue)
4. [Other changes needed for the UI tests](#other-changes-needed-for-the-ui-tests)
5. [Test reports](#test-reports)
6. [Running it locally](#running-it-locally)
7. [Troubleshooting](#troubleshooting)

---

## What runs in CI

The `run-apitest-pr` job in [`.github/workflows/push-trigger.yml`](../.github/workflows/push-trigger.yml)
runs on every push and pull request, on `ubuntu-latest`:

1. **Build the ui-test jar** with `mvn package` in `ui-test/` (the ui-test image copies it from `target/`).
2. **Remove residual containers and volumes** so the run starts from an empty database (see
   [Clean volumes on every run](#clean-volumes-on-every-run)).
3. **Start the stack** with [`docker-compose-ci.yaml`](docker-compose-ci.yaml): Postgres, mock identity
   system, esignet, esignet-ui, mock relying party service and UI.
4. **Wait** until `http://localhost:8080/health` returns `ok`.
5. **Run the API tests** (`apitest` service), copy and upload the report.
6. **Run the UI tests** (`ui-test` service), copy and upload the report.
7. **Print logs** and shut the stack down, removing its volumes.

Test failures do **not** fail the job. Each suite's exit code is printed in the log, and the result
is read from the reports (see [Test reports](#test-reports)). The job still fails if the jar
build fails, the stack does not start, or esignet never becomes healthy.

### Services

| Service | Image | Host port | Notes |
|---|---|---|---|
| `database` | `postgres:16-bookworm` | 5455 | Seeded by `init-ci.sql` |
| `mock-identity-system` | `mosipqa/mock-identity-system:0.14.x` | 8082 | |
| `esignet` | built from `esignet-service/` | 8080 | `MOSIP_ESIGNET_AUTHN_PROVIDER=mock` |
| `esignet-ui` | built from `oidc-ui/` | 3000 | nginx config: `nginx-ci.conf` |
| `mock-relying-party-service` | `mosipqa/mock-relying-party-service:0.14.x` | 8888 | |
| `mock-relying-party-ui` | `mosipqa/mock-relying-party-ui:0.14.x` | 5000 | nginx config: `nginx-ci-mock.conf` |
| `apitest` | built from `api-test/` | — | One-shot test run |
| `ui-test` | built from `ui-test/` | — | One-shot test run, host network |

Startup order is enforced with `depends_on: condition: service_healthy`, so each service waits for
its dependencies' health checks.

---

## Files involved

| File | Purpose |
|---|---|
| [`.github/workflows/push-trigger.yml`](../.github/workflows/push-trigger.yml) | New `run-apitest-pr` job |
| [`docker-compose/docker-compose-ci.yaml`](docker-compose-ci.yaml) | The CI stack and both test runners |
| [`docker-compose/init-ci.sql`](init-ci.sql) | Creates `mosip_esignet` and `mosip_mockidentitysystem`, key policies, the `esignet-mock-client` OIDC client and a mock identity (synthetic CI data) |
| [`docker-compose/nginx-ci.conf`](nginx-ci.conf) | esignet-ui on port 3000; proxies `/v1/esignet/` and `/.well-known/*` to `esignet:8080` |
| [`docker-compose/nginx-ci-mock.conf`](nginx-ci-mock.conf) | Mock RP UI on port 5000; proxies `/mock-relying-party-service/` to `mock-relying-party-service:8888` |
| [`ui-test/Dockerfile`](../ui-test/Dockerfile) | Switched from Google Chrome to Debian Chromium (see below) |
| [`ui-test/entrypoint.sh`](../ui-test/entrypoint.sh) | Unsets `DISPLAY` before starting the tests |

---

## The ChromeDriver issue

### Symptom

The `ui-test` container started, but every scenario failed because no browser session could be
created. ChromeDriver reported:

```
invalid session id: session deleted as the browser has closed the connection
from disconnected: Unable to receive message from renderer
```

Running Chrome directly inside the container showed the real cause:

```
qemu: uncaught target signal 6 (Aborted) - core dumped
```

### Root cause

The old `ui-test/Dockerfile` always downloaded the **amd64** builds of Google Chrome and
ChromeDriver. Google publishes no Linux arm64 build of Chrome or Chrome for Testing.

On an **arm64** Docker host (Apple Silicon, Windows on ARM, `ubuntu-24.04-arm` runners) the image is
therefore built as `linux/amd64` and runs under QEMU emulation. Chrome's renderer process crashes
under emulation, so ChromeDriver loses the browser as soon as a session starts. The Chrome and
ChromeDriver versions matched; version mismatch was not the problem.

A second, separate cause of the same error message was `ENV DISPLAY=:99` in the Dockerfile. There is
no X server at `:99` in the container, so headless Chrome's GPU process crash-loops when `DISPLAY` is
set.

### Fix

[`ui-test/Dockerfile`](../ui-test/Dockerfile) now:

- uses **`debian:trixie-slim`** as the base and copies the Java 21 runtime in from
  `eclipse-temurin:21-jre`. Debian rather than Ubuntu because Ubuntu ships Chromium only as a
  snap, which does not run in Docker;
- installs **`chromium`** and **`chromium-driver`** from Debian. Debian builds both for amd64 and arm64,
  and the two packages always have matching versions;
- sets `CHROME_BIN=/usr/bin/chromium` and `CHROMEDRIVER_PATH=/usr/bin/chromedriver`;
- downloads `kubectl` for the build architecture (`TARGETARCH`) instead of always amd64;
- no longer sets `DISPLAY`, and [`entrypoint.sh`](../ui-test/entrypoint.sh) runs `unset DISPLAY` before
  starting Java.

No Java changes were needed. `BaseTestUtil` already looks for `/usr/bin/chromium` and
`/usr/bin/chromedriver`, and passes `--headless` (instead of `--headless=new`) when the binary is
Chromium.

The same image now runs natively on both amd64 (GitHub's `ubuntu-latest`) and arm64.

---

## Other changes needed for the UI tests

### Test URLs use `localhost`

The `ui-test` service runs with `network_mode: host`. The browser must open the same
`localhost:5000` / `localhost:3000` URLs that the mock RP UI redirects to, so it has to share the
host's network.

Host networking bypasses Docker's DNS, so Compose service names do not resolve inside this
container. The test URLs therefore point at the published host ports:

```yaml
baseurl: http://localhost:5000/
eSignetbaseurl: http://localhost:3000/
```

For the same reason the database is reached at `localhost:5455`.

### Images are built by Compose

`esignet`, `esignet-ui` and `ui-test` have a `build:` context and `pull_policy: build`:

```yaml
image: ui-test:ci
pull_policy: build
build: ./../ui-test
```

Every `docker compose up` rebuilds them from the checked-out source. Unchanged layers come from
cache, so this is quick, and a stale local image (for example one still built with amd64 Google
Chrome) is never reused.

`esignet` and `esignet-ui` are multi-stage builds that compile from source. `ui-test` is not: its
Dockerfile copies `target/uitest-esignet-*-jar-with-dependencies.jar`, so the jar has to be built
first.

### Clean volumes on every run

Postgres keeps its data in a Docker volume, and Compose keeps anonymous volumes when it recreates a
container. Leftover data breaks esignet: the database still holds key aliases from an earlier
esignet container, while a new container starts with an empty keystore, and esignet exits with
`pkcs12: certificate alias "..." not found`.

The job therefore removes residual containers and volumes before starting the stack, and again at
shutdown:

```yaml
- name: Remove residual containers and volumes
  run: |
    docker compose -f docker-compose/docker-compose-ci.yaml down -v --remove-orphans
    docker volume prune -af
    docker volume ls

- name: Start Full Stack
  run: docker compose -f docker-compose/docker-compose-ci.yaml up -d --renew-anon-volumes database ...

- name: Shutdown
  if: always()
  run: docker compose -f docker-compose/docker-compose-ci.yaml down -v --remove-orphans
```

| Command | What it removes |
|---|---|
| `down -v --remove-orphans` | This stack's containers and their volumes, plus containers of services no longer in the compose file |
| `docker volume prune -af` | **Every** unused volume on the machine, not only this stack's |
| `docker volume ls` | Nothing; prints what is left, so the log shows the cleanup worked |
| `up --renew-anon-volumes` | Gives any recreated container a new, empty anonymous volume instead of reusing the old one |

`docker volume prune -af` is safe on GitHub-hosted runners, which start fresh for every job. Do not
run it on a shared self-hosted runner or on your own machine, where it would also delete other
projects' volumes. Locally, `down -v --remove-orphans` is enough.

### Building the ui-test jar

The jar is built with `mvn package`, not `install`:

```bash
mvn -B -U package -DskipTests -Dgpg.skip=true -Dmaven.gitcommitid.skip=true -s "$GITHUB_WORKSPACE/settings.xml"
```

- The shade plugin produces the `-jar-with-dependencies.jar` in the `package` phase, which is all the
  Docker build needs. `install` would also run `verify` (GPG signing) and copy the jar into
  `~/.m2`, which nothing uses.
- `-DskipTests`: this project's tests are the UI suite itself, which can only run once the stack is up.
- `-s settings.xml` uses the same repositories as the `build-uitest-esignet-local` job
  (`central.sonatype.com`, `repo.danubetech.com`).

---

## Test reports

Both suites write reports that the job copies out of the container after the run:

| Suite | Path in container | Artifact |
|---|---|---|
| API | `/home/esignet/test-report` | `esignet-api-test-report` |
| UI | `/home/mosip/test-output` | `esignet-ui-test-report` |

For each suite the job:

1. replaces `:` in file names with `_` (artifact names cannot contain `:`);
2. prints every report file name in the log and on the run's **Summary** page;
3. zips the folder and uploads the zip as the artifact. GitHub zips artifacts again on download, so
   the downloaded file contains the report zip.

The UI report's file name includes the counts, so the Summary page shows the result without
downloading anything:

```
EsignetUi-<env>-<lang>-<timestamp>-T-<total>-P-<passed>-F-<failed>-S-<skipped>-KI-<known issues>.html
```

---

## Running it locally

Prerequisites: Docker with Compose v2, JDK 21 and Maven. On **Docker Desktop**, host networking
must be turned on (Docker Desktop 4.34+, *Settings → Resources → Network → Enable host
networking*). Without it, `localhost` inside the `ui-test` container does not reach the other
services.

From the repository root:

```bash
# 1. Build the ui-test jar
(cd ui-test && mvn -B package -DskipTests -Dgpg.skip=true -Dmaven.gitcommitid.skip=true)

# 2. Remove containers and volumes left from an earlier run
#    (only this stack's; do not use `docker volume prune -af` locally)
docker compose -f docker-compose/docker-compose-ci.yaml down -v --remove-orphans

# 3. Start the stack and wait until everything is healthy
docker compose -f docker-compose/docker-compose-ci.yaml up -d --wait --renew-anon-volumes \
  database mock-identity-system esignet esignet-ui mock-relying-party-service mock-relying-party-ui

# 4. Run the API tests
docker compose -f docker-compose/docker-compose-ci.yaml up --exit-code-from apitest apitest

# 5. Run the UI tests
docker compose -f docker-compose/docker-compose-ci.yaml up --exit-code-from ui-test ui-test

# 6. Copy the reports out, then tear everything down including volumes
docker cp apitest:/home/esignet/test-report/. ci-reports
docker cp uitest:/home/mosip/test-output/. ui-reports
docker compose -f docker-compose/docker-compose-ci.yaml down -v --remove-orphans
```

Quick checks once the stack is up:

```bash
curl http://localhost:8080/health                                          # ok
curl -o /dev/null -w "%{http_code}\n" http://localhost:3000/               # 200
curl -o /dev/null -w "%{http_code}\n" http://localhost:3000/v1/esignet/.well-known/openid-configuration  # 200
curl -o /dev/null -w "%{http_code}\n" http://localhost:5000/               # 200
```

---

## Troubleshooting

### `Unable to receive message from renderer` / `session deleted as the browser has closed the connection`

Check that the ui-test image matches the host architecture and uses Chromium:

```bash
docker image inspect ui-test:ci --format '{{.Os}}/{{.Architecture}}'
docker run --rm --entrypoint bash ui-test:ci -c 'uname -m; chromium --version; chromedriver --version'
```

If the image is `linux/amd64` on an arm64 host, or shows Google Chrome, it is an old image. Rebuild
with `docker compose -f docker-compose/docker-compose-ci.yaml build ui-test`.

### `exec /home/mosip/entrypoint.sh: no such file or directory`

The entrypoint script has Windows (CRLF) line endings, so the `#!/bin/bash` line is not recognised.
This happens on Windows checkouts with `core.autocrlf=true`; the files are stored with LF in Git and
CI is not affected. Convert the working copy back to LF and rebuild:

```bash
sed -i 's/\r$//' esignet-service/docker-entrypoint.sh ui-test/entrypoint.sh oidc-ui/configure_start.sh
```

### esignet exits with `pkcs12: certificate alias "..." not found`

The database still holds key aliases from a previous esignet container, while the recreated
container starts with an empty keystore at `/opt/mosip/test.pfx`. Remove the stack together with
its volumes and start again:

```bash
docker compose -f docker-compose/docker-compose-ci.yaml down -v --remove-orphans
```

The problem appears when only esignet is recreated (for example by `up` after a rebuild) while the
old Postgres container and its data stay. Taking the whole stack down fixes it; `-v` also deletes
the old volumes so they do not pile up. CI removes residual containers and volumes before every
run (see [Clean volumes on every run](#clean-volumes-on-every-run)).

### `Shaded uitest-esignet jar not found` or the ui-test image fails to build

`ui-test/target/uitest-esignet-*-jar-with-dependencies.jar` is missing. Run the Maven build from
step 1 of [Running it locally](#running-it-locally) before building the image.

### UI tests cannot reach `localhost:3000` / `localhost:5000`

Host networking is not enabled in Docker Desktop (see the prerequisites in
[Running it locally](#running-it-locally)). On Linux it works without any setting.
