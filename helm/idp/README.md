# IDP

Helm chart for installing IDP module.

## TL;DR

```console
$ helm repo add mosip https://mosip.github.io
$ helm install my-release mosip/idp
```

## Introduction

IDP is part of the IDP modules, but has a separate Helm chart so as to install and manage it in a completely indepedent namespace.

## Prerequisites

- Kubernetes 1.12+
- Helm 3.1.0
- PV provisioner support in the underlying infrastructure
- ReadWriteMany volumes for deployment scaling

## Overview
Refer [Commons](https://docs.mosip.io/1.2.0/modules/commons).

## Install 
```
./install.sh
```
## Uninstall
```
./delete.sh
```
