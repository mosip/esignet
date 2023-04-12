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
