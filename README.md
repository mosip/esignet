[![Maven Package upon a push](https://github.com/mosip/idp/actions/workflows/push_trigger.yml/badge.svg?branch=develop)](https://github.com/mosip/idp/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mosip_idp&id=mosip_idp&metric=alert_status)](https://sonarcloud.io/dashboard?id=mosip_idp)


# Identity Provider Project

## Overview

This repository contains the implementation of Authorization Code flow of OAuth 2.0. Supports all the mandatory features of OIDC (Open ID Connect) specification.

IdP repository contains following:

1. idp-core - Library containing all the common interfaces, DTOs and utils that is used as dependency in the other IdP module libraries and services
2. idp-service - Deployable API service containing all the OIDC and UI APIs
3. db_scripts - Contains all the db scripts required to setup or upgrade the DB for IdP module
4. authentication-wrapper - Library containing the implementations of AuthenticationWrapper interface to intergrate into MOSIP's IDA module and mock authentication service
5. idp-binding-service - Deployable API service containing the binding APIs


## Databases
Refer to [SQL scripts](db_scripts).

## Build & run (for developers)
The project requires JDK 11. 
1. Build and install:
    ```
    $ mvn clean install -Dgpg.skip=true
    ```
1. Build Docker for a service:
    ```
    $ docker build -f Dockerfile
    ```
## Installing in MOSIP's k8s cluster using helm
### Pre-requisites
1. MOSIP's 1.2.0.1-B2 version already deployed with IDA, PMP installed with its DB and keycloak dependencies.
1. Make sure kube config file of the Mosip cluster having dependent services is set correctly in PC.
1. Make sure [DB setup](db_scripts#initialize-idp-db) is done.
1. Add below mentioned property files to your config branch from [1.2.0.1-B2](https://github.com/mosip/mosip-config/tree/v1.2.0.1-B2).
1.  | Chart | Chart version |
    |---|---|
    |[Clamav](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/external/antivirus/clamav) | 2.4.1 |
    |[Keycloak](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/external/iam) | 7.1.18 |
    |[Keycloak-init](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/external/iam) | 12.0.1-B2 |
    |[Postgres](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/external/postgres) | 10.16.2 |
    |[Postgres Init](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/external/postgres) | 12.0.1-B1 |
    |[Minio](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/external/object-store) | 10.1.6 |
    |[Config-server](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/mosip/config-server) | 12.0.1-B2 |
    |[Artifactory server](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/mosip/artifactory) | 12.0.1-B2 |
    |[Keymanager service](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B2/deployment/v3/mosip/kernel/install.sh) | 12.0.1-B2 |
    |[Notifier service](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B2/deployment/v3/mosip/kernel/install.sh) | 12.0.1-B2 |
    |[Partner manager service](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B2/deployment/v3/mosip/pms/install.sh) | 12.0.1-B2 |
    |[Id-authentication](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B2/deployment/v3/mosip/ida/install.sh) | 12.0.1-B2 |
### Install
Install `kubectl` and `helm` utilities, then run:
```
cd helm
./install-all.sh [cluster-kubeconfig-file]
```
### Restart
```
cd helm
./restart-all.sh [cluster-kubeconfig-file]
```
### Delete
```
cd helm
./delete-all.sh [cluster-kubeconfig-file]
```
## APIs
API documentation is available [here](https://mosip.stoplight.io/docs/identity-provider/branches/main/6f1syzijynu40-identity-provider).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

