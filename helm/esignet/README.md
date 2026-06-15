# esignet

Helm chart for installing esignet module.

## TL;DR

```console
$ helm repo add mosip https://mosip.github.io
$ helm install my-release mosip/esignet
```

## Introduction

esignet is part of the esignet modules, but has a separate Helm chart so as to install and manage it in a completely independent namespace.

## Prerequisites

- Kubernetes 1.12+
- Helm 3.1.0
- PV provisioner support in the underlying infrastructure
- ReadWriteMany volumes for deployment scaling

## Overview
Refer [Commons](https://docs.mosip.io/1.2.0/modules/commons).

## Initialize keycloak for esignet
* To initialize keycloak for esignet, run below script.
  ```sh
  ./keycloak-init.sh
  ```

## Install 
```
./install.sh
```

## Uninstall
```
./delete.sh
```

## TLS trust configuration

The chart supports three modes for outbound HTTPS trust from the Java service:

1. **Public CAs (default)** — uses the JVM default truststore.
2. **`enable_insecure: true`** — development-only workaround that fetches a self-signed leaf certificate from `mosip-api-internal-host`.
3. **`customCA.enabled: true`** — recommended for on-prem deployments using a company-internal CA.

### Company internal CA

Create a Secret (or ConfigMap) with PEM-encoded root/intermediate certificate(s), then enable `customCA`:

```yaml
customCA:
  enabled: true
  secretName: company-internal-ca
  secretKey: ca.crt
```

Example:

```sh
kubectl -n esignet create secret generic company-internal-ca \
  --from-file=ca.crt=/path/to/company-ca-bundle.pem

helm upgrade --install esignet ./helm/esignet \
  --set customCA.enabled=true \
  --set customCA.secretName=company-internal-ca
```

`enable_insecure` and `customCA` are mutually exclusive.

Additional extension points:

- `extraVolumes` / `extraVolumeMounts` — mount extra volumes into the eSignet container
- `extraInitContainers` — append custom init containers after built-in truststore init containers
