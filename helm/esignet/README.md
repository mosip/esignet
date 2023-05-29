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

## Automating MISP Partner License key for e-Signet module
* Added `misp_key.sh` script through which the MISP license key is obtained with the following endpoint:
`v1/partnermanager/misps/$MISP_PARTNER_ID/licenseKey`
* The above license key is passed through the `config-server` as placeholder named `mosip.esignet.misp.key` in `esignet-default.properties` file and then saved as a secret called `onboarder-keys` in the kubernetes environment.
* This change is a part of the `install.sh` script of e-Signet service.

## Uninstall
```
./delete.sh
```
