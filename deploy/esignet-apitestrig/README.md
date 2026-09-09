# eSignet API Test Rig (Go)

## Introduction
Runs the Go-based [`api-test`](../../api-test) harness against the eSignet
deployment in this cluster, via the
[`../../helm/esignet-apitestrig`](../../helm/esignet-apitestrig) chart.

`install.sh` mirrors [`../esignet/install.sh`](../esignet/install.sh)'s own
pattern: `helm repo add`/`helm repo update` against the published
[`mosip-helm`](https://mosip.github.io/mosip-helm) repo, then
`helm upgrade --install` by chart name and `CHART_VERSION`, not a local
path. **This only works once `helm/esignet-apitestrig` has merged upstream
and MOSIP's CI has published it** — until then, `helm install` fails with
"chart not found", which is expected while this lives on a feature branch.
Bump `install.sh`'s `CHART_VERSION` alongside `helm/esignet-apitestrig/Chart.yaml`'s
own `version` whenever either changes.

`install.sh` only asks two questions:
1. the eSignet base URL (the one thing that realistically changes every run)
2. whether you've actually reviewed/updated `values.yaml`

Everything else that used to be an interactive prompt — Keycloak, TLS
verification, the test identity, OTP/PMS settings, surfaces, report storage,
conformance suite/plan config, cron schedule — is now a value in one of two
files you edit directly beforehand.

## Setup

1. **`values.yaml`** (tracked in git) — non-secret settings. Open it and
   fill in your environment's Keycloak token URL, OTP/PMS values, surfaces,
   report storage, etc. Defaults are pre-filled from a known-working
   configuration as a starting point — check every value, don't assume they
   fit your environment.

2. **`values.secret.yaml`** (gitignored) — secrets. Copy the example and
   fill in real values:
   ```bash
   cp values.secret.yaml.example values.secret.yaml
   ```
   Holds `KEYCLOAK_CLIENT_SECRET`, the test identity (`INDIVIDUAL_ID` —
   PII, kept out of `values.yaml`/git deliberately), and S3 access/secret
   keys. `install.sh` refuses to run without this file present.

3. **Conformance plan config** — `values.yaml` defaults to running
   `conformance,api,e2e` with the in-pod suite
   (`apitestrig.conformanceSuite.enabled: true`), which needs a private plan
   config (a JWKS). Paste your real plan JSON into
   `apitestrig.conformancePlanConfig.files` in `values.secret.yaml`
   (placeholders are there by default in `values.secret.yaml.example`) —
   `helm upgrade` renders it into a Secret itself as part of `./install.sh`,
   no separate `kubectl` step needed. Filenames must match
   `plans[].config_file`'s basename exactly (see `config.mosip.json`'s own
   `_comment` block) — typically `esignet-config.json` and
   `esignet-fapi2-config.json`.

   (If you'd rather manage this Secret yourself outside of Helm — sealed-secrets,
   external-secrets, etc. — set `apitestrig.conformancePlanConfig.existingSecret`
   to that Secret's name in `values.yaml` instead, and leave `files` out of
   `values.secret.yaml`.)

   Without a filled-in plan, the run fails with a `config_file ... not
   readable` error. If you don't have these plan files yet, set
   `apitestrig.surfaces: "api,e2e"` in `values.yaml` until you do — see
   "Conformance surface" below for
   details.

## Install
```bash
./install.sh
```
You'll be prompted for the eSignet base URL, then asked to confirm
`values.yaml` is ready. Everything else comes from the two files above.

## Uninstall
```bash
./delete.sh
```

## Conformance surface
Set `apitestrig.surfaces: "conformance,api,e2e"` in `values.yaml`, plus:
- `apitestrig.conformancePlanConfig.enabled: true`, plus either the plan JSON
  in `values.secret.yaml`'s `conformancePlanConfig.files` (the default here) or
  `existingSecret` pointing at a Secret you manage yourself — see
  [`helm/esignet-apitestrig/README.md`](../../helm/esignet-apitestrig/README.md#conformance-plan-config).
- Either `apitestrig.conformanceSuite.enabled: true` to run the suite
  in-pod (no separate deployment, no Kubernetes version requirement — see
  that same README's "Running the conformance suite itself" section), or
  point `apitestrig.extraEnvVars.CONFORMANCE_BASE_URL` at a suite you're
  running elsewhere.

Without the plan config Secret, the conformance surface fails with a
`config_file ... not readable` error — see
[mosip/esignet#2434](https://github.com/mosip/esignet/issues/2434) §5a/§6ii
for background.

## Run manually

#### Rancher UI
Trigger the CronJob's job manually from the Rancher UI, same as any other
CronJob.

#### CLI
```sh
kubectl --kubeconfig=<k8s-config-file> -n esignet create job \
  --from=cronjob/esignet-apitestrig <job-name>
```

Reports land wherever `values.yaml`'s `reports.*` settings point — a PVC at
`/app/out` inside the pod (`reports.persistence.enabled: true`), and/or
S3/MinIO (`reports.s3.enabled: true`). For the PVC case:
```sh
kubectl -n esignet cp <pod-name>:/app/out ./out
```
