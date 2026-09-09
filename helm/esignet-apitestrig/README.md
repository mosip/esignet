# esignet-apitestrig (eSignet, Go)

> Named `esignet-apitestrig`, not `apitestrig` — that name is already used by
> [`mosip-functional-tests/helm/apitestrig`](https://github.com/mosip/mosip-functional-tests/tree/develop/helm/apitestrig)
> (the Java-based test rig), a structurally incompatible chart. Keeping this
> distinct avoids any collision if both ever end up in the same chart
> repository (e.g. `mosip-helm`).

Helm chart to run the Go-based [`api-test`](../../api-test) harness against a
deployed eSignet instance, either on a schedule (`CronJob`) or as a one-off
run (`Job`).

Modeled on
[`mosip-functional-tests/helm/apitestrig`](https://github.com/mosip/mosip-functional-tests/tree/develop/helm/apitestrig),
adapted for this harness's single-image, single-binary, config-file-driven
design (see `api-test/Dockerfile` and `api-test/run-all.sh`) rather than the
Java testrig's one-image-per-module model.

## Prerequisites

- The eSignet deployment this rig targets must already be reachable from the
  cluster (`apitestrig.extraEnvVars.MOSIP_ESIGNET_BASE_URL`).
- If you plan to run the `conformance` surface, it needs a real OpenID
  Conformance Suite to talk to — either point
  `apitestrig.extraEnvVars.CONFORMANCE_BASE_URL` at one already running
  elsewhere, or set `apitestrig.conformanceSuite.enabled: true` to run one
  in-pod (see "Running the conformance suite itself" below) — this chart
  does not do either for you automatically. Either way you also need the
  private plan config via `apitestrig.conformancePlanConfig` (see
  "Conformance plan config" below).
  Without both, leave `conformance` out of `apitestrig.surfaces` — the config
  files this chart's images ship (`config.mosip.json` etc.) default to
  including it, and will fail with a "config_file ... not readable" error
  otherwise (see [mosip/esignet#2434](https://github.com/mosip/esignet/issues/2434)).
- `helm dependency build` (pulls the Bitnami `common` chart used for labels
  and image-name helpers).

## Quick start

```bash
cd helm/esignet-apitestrig
helm dependency build

# One-off run against a deployed environment, api+e2e only
helm install apitestrig-smoke . \
  --set triggerKind=job \
  --set apitestrig.surfaces=api\\,e2e \
  --set apitestrig.extraEnvVars.MOSIP_ESIGNET_BASE_URL=https://esignet.example.net/v1/esignet \
  --set apitestrig.extraEnvVars.KEYCLOAK_TOKEN_URL=https://keycloak.example.net/realms/mosip/protocol/openid-connect/token \
  --set apitestrig.extraEnvVarsSecret.KEYCLOAK_CLIENT_SECRET=<secret>
```

```bash
# Nightly regression against the mosip config
helm install apitestrig . \
  --set apitestrig.extraEnvVars.MOSIP_ESIGNET_BASE_URL=https://esignet.example.net/v1/esignet \
  --set apitestrig.extraEnvVarsSecret.KEYCLOAK_CLIENT_SECRET=<secret>
```

## Configuration model

The image already bakes in `data/config/config.{mock,mosip,sunbird}.json` —
`apitestrig.configFile` just selects the in-image path (default:
`config.mosip.json`). Set `apitestrig.configOverride` only if you need a
config that isn't one of the shipped ones; it's rendered into a ConfigMap and
mounted instead.

Layering, matching `run-all.sh`'s own precedence (file → `config.local.json`
overlay → environment):

| Layer | Source | Values key |
|---|---|---|
| Base config | baked into image, or `configOverride` | `apitestrig.configFile` / `apitestrig.configOverride` |
| Credentials overlay | mounted Secret, at `CONFIG_LOCAL` | `apitestrig.configLocal.*` |
| Non-secret overrides | container env | `apitestrig.extraEnvVars` |
| Secret overrides | container env via `secretKeyRef` | `apitestrig.extraEnvVarsSecret` |
| Shared cluster config/secrets | `envFrom` | `apitestrig.extraEnvVarsCM` / `extraEnvVarsSecretRefs` |

`apitestrig.configLocal.existingSecret` lets you supply the overlay via a
Secret you manage outside Helm (sealed-secrets, external-secrets, etc.)
instead of `apitestrig.configLocal.data` in values.

## Reports

`REPORT_DIR` is set to `reports.mountPath` (default `/app/out`). By default
it's backed by a PVC (`reports.persistence`) — set
`reports.persistence.existingClaim` to reuse a PVC across runs.

To also (or instead) push each run's report to S3-compatible storage (AWS S3
or MinIO), set `reports.s3.enabled: true` plus `reports.s3.endpoint`,
`.bucket`, and credentials (`reports.s3.accessKey`/`secretKey`, or
`reports.s3.existingSecret` to supply your own Secret). This adds a second
`report-uploader` container to the pod that waits for the harness to finish
(run-all.sh has no S3 awareness of its own, so completion is signalled via a
marker file on the shared reports volume) and then runs `mc cp` to upload
everything under `reports.s3.pathPrefix/<UTC timestamp>/` in the bucket. Set
`reports.persistence.enabled: false` if you don't want a PVC kept around once
reports are pushed to S3 — the reports volume becomes an `emptyDir` instead.

```bash
helm upgrade --install apitestrig . \
  --set reports.s3.enabled=true \
  --set reports.s3.endpoint=http://minio.minio:9000 \
  --set reports.s3.bucket=apitestrig-reports \
  --set reports.s3.accessKey=<key> \
  --set reports.s3.secretKey=<secret> \
  --set reports.persistence.enabled=false   # optional: skip the PVC entirely
```

## Conformance plan config

The `conformance` surface needs a private plan config (a JWKS, per
[`api-test/docs/conformance-suite.md`](../../api-test/docs/conformance-suite.md))
at exactly the relative path the selected config's `plans[].config_file`
names — always `conformance-suite-private/<file>.json`. Set
`apitestrig.conformancePlanConfig.enabled: true` and either point
`existingSecret` at a Secret you manage out-of-band (one key per plan file,
e.g. `esignet-config.json`), or fill in `files` for a Helm-managed one:

```bash
kubectl create secret generic esignet-conformance-plan -n esignet \
  --from-file=esignet-config.json=./conformance-suite-private/esignet-config.json \
  --from-file=esignet-fapi2-config.json=./conformance-suite-private/esignet-fapi2-config.json
```

**`CONFORMANCE_BASE_URL` must match whatever host your plan file's
`client`/`client2` were registered against in eSignet** (redirect URI
`<CONFORMANCE_BASE_URL>/test/a/<alias>/callback`) — the harness calls back
into the suite using that exact URL (`internal/conformance/client.go`'s
`DeliverCallback`), so changing it means re-registering those clients. If
your plan was built against the suite's own conventional local address
(`https://localhost.emobix.co.uk:8443`), keep using that literal value
rather than pointing at a different Service URL, even once the suite is
actually running elsewhere — see `apitestrig.conformanceSuite` below for how
that stays consistent automatically when running the suite in-pod.

## Running the conformance suite itself (`apitestrig.conformanceSuite`)

The `conformance` surface is a *client* — it needs a real OpenID Conformance
Suite server to talk to; it can't run standalone. `apitestrig.conformanceSuite`
runs the suite (mongodb + server + nginx) as regular containers in the same
pod, using the exact images/env from
[`api-test/docker-compose.yml`](../../api-test/docker-compose.yml)'s own
local-dev recipe:

```bash
helm upgrade --install apitestrig . \
  --set 'apitestrig.surfaces=conformance\,api\,e2e' \
  --set apitestrig.conformancePlanConfig.enabled=true \
  --set apitestrig.conformancePlanConfig.existingSecret=esignet-conformance-plan \
  --set apitestrig.extraEnvVars.CONFORMANCE_BASE_URL=https://localhost.emobix.co.uk:8443 \
  --set apitestrig.conformanceSuite.enabled=true
```

`https://localhost.emobix.co.uk:8443` already resolves to `127.0.0.1` via
public DNS, and pod containers share a network namespace regardless of
container type — so once the suite's containers are up, that URL just
reaches them directly, no separate Service needed and no client
re-registration required. No Kubernetes version requirement for this part.

**The one real wrinkle**: mongodb/server/httpd are long-running processes
that never exit on their own, and a Job only completes once *every*
container in the pod exits — left alone, they'd block every run from ever
completing. Fixed with `shareProcessNamespace: true` on the pod plus a small
`conformance-reaper` container that waits for the same completion marker
`report-uploader` already watches for, then kills the suite's processes by
name (`mongod`, `java`, `nginx`) so their containers exit and the Job can
finish — a well-established pattern from before native sidecars existed
(K8s 1.28), and it works on any Kubernetes version. Note `shareProcessNamespace`
means every container in this pod can see and signal every other container's
processes — acceptable here since this pod only ever runs images this chart
already controls, but worth knowing if you're auditing it.

Nothing here enforces startup ordering (mongo ready before server, server
before httpd) the way native sidecars would — this relies on the same
"loose" ordering `api-test/docker-compose.yml`'s own `depends_on` (without
healthcheck conditions) already relies on: Spring Boot retries its Mongo
connection on startup, and the harness's own `SUITE_WAIT_SECONDS` retries
reaching the suite through httpd.

Three details are **assumed, not yet verified against a real cluster**:
the suite server's internal port (not actually used by anything in this
design, but worth knowing if you need to debug it directly), whether the
nginx image's baked-in config proxies to hostnames `server`/`mongodb`
(Docker Compose's automatic per-service DNS) — a `hostAliases` entry maps
both to `127.0.0.1` to cover that, but it's inferred from Compose
convention, not confirmed against the image itself — and whether `mongod`/
`java`/`nginx` are actually the process names these images report (standard
for these kinds of images, but not confirmed). If reaping doesn't work,
`apitestrig.conformanceSuite.reaper.processNames` is where to fix it.


The `client_id`s inside that plan file must already be pre-registered in
eSignet as `private_key_jwt`, with a redirect URI of
`<CONFORMANCE_BASE_URL>/test/a/<alias>/callback` — an environment-provisioning
step this chart can't do for you.

## CronJob vs Job

- `triggerKind: cronjob` (default) — scheduled via `crontime`, mirrors the
  reference chart's nightly-run model.
- `triggerKind: job` — a single run per `helm install`/`helm upgrade`, handy
  for CI-triggered smoke tests against a freshly-deployed environment. The Job
  name is suffixed with `.Release.Revision` so repeated `helm upgrade` calls
  don't collide with an already-completed Job.
