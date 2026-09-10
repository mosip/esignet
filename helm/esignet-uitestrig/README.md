# esignet-uitestrig

> Purpose-built chart for `uitest-esignet` — not the generic
> [`mosip-functional-tests/helm/uitestrig`](https://github.com/mosip/mosip-functional-tests/tree/develop/helm/uitestrig)
> chart. That chart works, but has no lever for container `resources` or a
> reports PVC at all (confirmed against its own `templates/cronjob.yaml`),
> and its `templates/secrets.yaml` is missing the `---` document separator
> `templates/configmaps.yaml` has between loop iterations — with more than
> one entry under `uitestrig.secrets`, the rendered manifest becomes one
> malformed multi-document YAML file and every secret but the last is
> silently dropped. This chart fixes both gaps by being its own thing,
> named `esignet-uitestrig` (not `uitestrig`) so it can never collide with
> that one in a shared chart repo.

## Introduction

Runs the **Java** UI automation suite (`ui-test/`, Cucumber + TestNG +
Selenium, image `uitest-esignet`) against the eSignet deployment in this
cluster, on a schedule (`CronJob`) or on demand (`Job`). Built from
[mosip/esignet#2544](https://github.com/mosip/esignet/issues/2544)'s gap
analysis.

This is **not** related to the Go `api-test` harness or
[`helm/esignet-apitestrig`](../esignet-apitestrig) — `develop-go` in the
image tag just means "built from the `develop-go` branch," which happens to
contain both suites side by side. `ui-test` itself is 100% Java, runs as a
JVM process, and pushes its own reports to S3 in-process
(`BaseTest.pushReportsToS3`) rather than via an external uploader sidecar.

## Prerequisites

- eSignet already deployed and reachable.
- A pre-provisioned test identity and OIDC client(s) for the UI flows (see
  `uitestrig.secret`).
- If running in-cluster Chromium (the default — see "Browser" below), no
  further setup. If using BrowserStack instead, network egress from the
  cluster to BrowserStack and a valid account.

## Configuration model

**Two separate objects**, both mounted into the container as **files**, one
per key, rather than via `envFrom` — several of the harness's own config
keys contain hyphens (`keycloak-external-url`, `push-reports-to-s3`, ...),
and while `envFrom` accepts those syntactically, the kubelet silently
*drops* envFrom-sourced keys containing `-` on Kubernetes versions before
1.32 (`RelaxedEnvironmentVariableValidation` is beta in 1.32, GA in 1.34).
A wrapper script (baked into the pod template, not something you configure)
reads each mounted file and injects it into the JVM's process environment
via `env KEY=VALUE ...` before exec'ing the image's own entrypoint — this
works on any Kubernetes version, since filenames have no such restriction:

- **`uitestrig.configMap`** → a ConfigMap (non-secret)
- **`uitestrig.secret`** → a Secret (credentials, PII)

**Every key in both is only rendered when non-empty.** This isn't cosmetic —
`apitest-commons`' `ConfigManager` does `System.getenv(key) ?? propsMap.get(key)`,
so an environment variable that's *present but empty* still overrides
(wipes) the JAR's own `config.properties` default, rather than falling
through to it. Leaving a value blank in `values.yaml` omits that key from
the rendered ConfigMap/Secret entirely, letting the JAR default apply — see
[mosip/esignet#2544](https://github.com/mosip/esignet/issues/2544) §2/§4 for
the full list of keys that ship safe JAR defaults and must never be set to
`""`.

Plain env vars (`uitestrig.extraEnvVars` — `ENV_ENDPOINT`, `MODULES`, etc.)
are the ones `entrypoint.sh` itself reads directly to build the `java`
command line; these use ordinary `env:` entries since none of their names
contain hyphens.

## Browser

Defaults to **in-cluster Chromium** (`runOnBrowserStack=false` in
`uitestrig.configMap`) — the JAR's own default is `runOnBrowserStack=true`,
so this must be set explicitly. No `/dev/shm` volume is needed:
`ui-test`'s own `BaseTestUtil` unconditionally adds `--no-sandbox` and
`--disable-dev-shm-usage` to `ChromeOptions` (confirmed against
`src/main/java/utils/BaseTestUtil.java`), which works around the shared-memory
requirement at the application level.

To use BrowserStack instead: set `uitestrig.browserstack.enabled: true` plus
`username`/`accessKey`, and set `uitestrig.configMap.runOnBrowserStack: "true"`
yourself (the chart doesn't flip that for you, since it's ultimately a
harness-config concern, not purely a "which secret" concern).

## Reports

Mounted at `reports.mountPath` (`/home/mosip/test-output`) and
`reports.screenshotsMountPath` (`/home/mosip/screenshots`) — **not**
`/home/mosip/testrig/report` (the API rig's old path, wrong for this image).
Two options, matching
[mosip/esignet#2544](https://github.com/mosip/esignet/issues/2544) §6(i):

1. **In-process S3 push** (no sidecar, unlike `apitestrig`) — set
   `uitestrig.configMap`'s `push-reports-to-s3: "yes"` plus
   `s3-host`/`s3-region`/`s3-account`, and `uitestrig.secret`'s
   `s3-user-key`/`s3-user-secret`.
2. **PVC** — set `reports.persistence.enabled: true`. The generic
   `mosip/uitestrig` chart has no PVC template at all; this fills that gap.

Without either, the report only exists inside the pod until it's
garbage-collected — retrievable via `kubectl cp` in the meantime.

An init container (`prepare-report-dirs`) always runs first to recreate
`test-output/SparkReport/`, which the image bakes in at build time but a
fresh PVC/`emptyDir` mount would otherwise hide.

## Self-signed TLS (`uitestrig.enableInsecure`)

**Unverified against a live cluster — test before relying on it.** Imports
`uitestrig.tls.caCert` (required when this is enabled) into the JVM's own
`cacerts` truststore, following the same pattern as the old Java
`apitestrig` chart
([`mosip-functional-tests/helm/apitestrig`](https://github.com/mosip/mosip-functional-tests/tree/develop/helm/apitestrig)),
adapted for this image's actual base: `eclipse-temurin`'s `JAVA_HOME` is
`/opt/java/openjdk` (confirmed against Adoptium/Temurin's own container
docs), not `/usr/local/openjdk-11` like that older chart's image. Eclipse
Temurin's own built-in `USE_SYSTEM_CA_CERTS` auto-handling doesn't apply
here since `uitest-esignet`'s `Dockerfile` sets its own `ENTRYPOINT`,
bypassing the base image's cert-processing entrypoint entirely — hence
doing this by hand via an init container instead.

Unlike the old chart's approach (`openssl s_client` fetching whatever
certificate the target host presents at deploy time, then trusting it
unconditionally), this chart requires you to supply the actual PEM content
via `uitestrig.tls.caCert` — obtain it through a channel you actually
trust (e.g. copy it directly from the eSignet ingress/load balancer's own
TLS config), not by pointing this chart at a live endpoint and trusting
whatever comes back. Fetching-and-trusting blindly is a TOFU gap
([CWE-295](https://cwe.mitre.org/data/definitions/295.html)) — a network
or DNS attacker could otherwise get their own certificate trusted by test
traffic.

## CronJob vs Job

Same as [`helm/esignet-apitestrig`](../esignet-apitestrig#cronjob-vs-job) —
`triggerKind: cronjob` (default, scheduled) or `triggerKind: job` (one-shot,
Job name suffixed with `.Release.Revision`).

## Resources

Defaults to `requests: 500m CPU / 2Gi memory`, `limits: 2 CPU / 4Gi memory`
— headless Chromium plus the JVM in one pod needs real headroom
([mosip/esignet#2544](https://github.com/mosip/esignet/issues/2544) §3.2).
`uitestrig.extraEnvVars.JAVA_EXTRA_OPTS` defaults to `-Xmx2g`, deliberately
well under the memory limit rather than the old Java `apitestrig` chart's
`-Xms2600M` on a much smaller container.
