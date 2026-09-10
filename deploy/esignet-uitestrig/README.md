# eSignet UI Test Rig (Java)

## Introduction
Runs the **Java** UI automation suite (`ui-test/`, Cucumber + TestNG +
Selenium, image `uitest-esignet`) against the eSignet deployment in this
cluster, via the
[`../../helm/esignet-uitestrig`](../../helm/esignet-uitestrig) chart —
a purpose-built chart, not the generic
[`mosip/uitestrig`](https://github.com/mosip/mosip-functional-tests/tree/develop/helm/uitestrig)
chart this used to install (see that chart's real gaps in
`helm/esignet-uitestrig/README.md`'s header).

This is a **separate** installation from
[`esignet-apitestrig`](../esignet-apitestrig), which runs the Go `api-test`
harness. `develop-go` in the image tag just means "built from the
`develop-go` branch" — `ui-test` itself is 100% Java, and pushes its own
report to S3 in-process rather than via an uploader sidecar.

This installs into its **own `esignet-uitestrig` namespace**, separate from
`esignet-apitestrig`'s `esignet` namespace. The new chart names its
ConfigMap/Secret via `common.names.fullname`, so a shared namespace would
actually be safe too (unlike the generic `mosip/uitestrig` chart this
replaces) — this is a deliberate choice to keep the two rigs separated, not
a requirement.

`install.sh` only asks two questions:
1. the eSignet base URL (origin only, no path — different from
   `esignet-apitestrig`'s `MOSIP_ESIGNET_BASE_URL`, which includes
   `/v1/esignet`)
2. whether you've actually reviewed/updated `values.yaml`

Everything else — `baseurl`, consent DB host, report storage, browser
choice, self-signed TLS — is a value in one of two files you edit directly
beforehand.

## Setup

1. **`values.yaml`** (tracked in git) — non-secret settings. Every
   environment-specific value carries a `# UPDATE ...` marker — check each
   one, don't assume the pre-filled defaults fit your environment.

2. **`values.secret.yaml`** (gitignored) — secrets. Copy the example and
   fill in real values:
   ```bash
   cp values.secret.yaml.example values.secret.yaml
   ```
   Holds the consent DB password, Keycloak admin password, testrig client
   secret, pre-provisioned OIDC client(s), test identities (PII), and S3
   keys. `install.sh` refuses to run without this file present, and refuses
   to proceed while it still contains the literal `"changeme"` placeholder.

## Install
```bash
./install.sh
```

## Uninstall
```bash
./delete.sh
```

## Report storage
Two options — see `helm/esignet-uitestrig/README.md`'s "Reports" section
for the full detail:
1. **In-process S3 push** (recommended, no PVC needed) — set
   `uitestrig.configMap`'s `push-reports-to-s3: "yes"` in `values.yaml`
   plus `s3-host`/`s3-region`/`s3-account`, and `uitestrig.secret`'s
   `s3-user-key`/`s3-user-secret` in `values.secret.yaml`.
2. **PVC** — set `reports.persistence.enabled: true` in `values.yaml`.

Without either, the report only exists in the pod until it's
garbage-collected.

## Browser
Defaults to in-cluster Chromium. To use BrowserStack instead: set
`uitestrig.browserstack.enabled: true` in `values.yaml`, fill in
`uitestrig.browserstack.username`/`accessKey` in `values.secret.yaml`, and
set `uitestrig.configMap.runOnBrowserStack: "true"` yourself in
`values.yaml`.

## Self-signed TLS
Set `uitestrig.enableInsecure: true` and `uitestrig.tls.caCert` (the
eSignet host's CA cert, PEM) in `values.yaml`. **Unverified against a live
cluster** — see `helm/esignet-uitestrig/README.md`'s "Self-signed TLS"
section before relying on this in a real run.

## Run manually

#### Rancher UI
Trigger the CronJob's job manually from the Rancher UI, same as
`esignet-apitestrig`, in the `esignet-uitestrig` namespace.

* Supported test levels: `smoke`, `smokeAndRegression` (default). To
  change it, update `uitestrig.extraEnvVars.ENV_TESTLEVEL` in `values.yaml`
  and re-run `./install.sh`.
* To scope a run further, set (in `uitestrig.extraEnvVars`, and leave
  blank when not needed): `CUCUMBER_FILTER_TAGS`, `RUN_ONLY_SCENARIO`,
  `FEATURE_FILES_TO_EXECUTE`.

#### CLI
```sh
kubectl --kubeconfig=<k8s-config-file> -n esignet-uitestrig create job \
  --from=cronjob/esignet-uitestrig <job-name>
```
