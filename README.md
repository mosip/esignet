[![Maven Package upon a push](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml/badge.svg?branch=1.0.0)](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mosip_esignet&id=mosip_esignet&metric=alert_status)](https://sonarcloud.io/dashboard?id=mosip_esignet)
# e-Signet Project
## Overview
This repository contains the implementation of Authorization Code flow of OAuth 2.0. Supports all the mandatory features of OIDC (Open ID Connect) specification.

e-Signet repository contains following:

1. e-Signet-core - Library containing all the common interfaces, DTOs and utils that is used as dependency in the other esignet module libraries and services
2. e-Signet-service - Deployable API service containing all the OIDC and UI controllers.
3. e-Signet-integration-api - Library containing all the integration interfaces.
4. client-management-service-impl - Client management implementations classes.
5. oidc-service-impl - Oauth and OIDC implementation classes.
6. binding-service-impl - key and individualId binding service implementation classes.
7. db_scripts - Contains all the db scripts required to setup or upgrade the DB for esignet module.


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

## Installing in k8s cluster using helm
### Pre-requisites
1. Set the kube config file of the Mosip cluster having dependent services is set correctly in PC.
1. Make sure [DB setup](db_scripts/README.md#install-in-existing-mosip-k8-cluster) is done.
1. Add / merge below mentioned properties files into existing config branch:
   * [esignet-default.properties](https://github.com/mosip/mosip-config/blob/v1.2.0.1-B3/esignet-default.properties)
   * [application-default.properties](https://github.com/mosip/mosip-config/blob/v1.2.0.1-B3/application-default.properties)
1. Below are the dependent services required for esignet service:
   | Chart | Chart version |
   |---|---|
   |[Keycloak](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/external/iam) | 7.1.18 |
   |[Keycloak-init](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/external/iam) | 12.0.1-B3 |
   |[Postgres](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/external/postgres) | 10.16.2 |
   |[Postgres Init](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/external/postgres) | 12.0.1-B3 |
   |[Minio](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/external/object-store) | 10.1.6 |
   |[Kafka](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/external/kafka) | 0.4.2 |
   |[Config-server](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/mosip/config-server) | 12.0.1-B3 |
   |[Websub](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B1/deployment/v3/mosip/websub) | 12.0.1-B2 |
   |[Artifactory server](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B4/deployment/v3/mosip/artifactory) | 12.0.1-B3 |
   |[Keymanager service](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B2/deployment/v3/mosip/keymanager) | 12.0.1-B2 |
   |[Kernel services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B1/deployment/v3/mosip/kernel) | 12.0.1-B2 |
   |[Biosdk service](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B2/deployment/v3/mosip/biosdk) | 12.0.1-B3 |
   |[Idrepo services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B1/deployment/v3/mosip/idrepo) | 12.0.1-B2 |
   |[Pms services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B3/deployment/v3/mosip/pms) | 12.0.1-B3 |
   |[IDA services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B3/deployment/v3/mosip/ida) | 12.0.1-B3 |

### Install
* Install `kubectl` and `helm` utilities.
* Run `install-all.sh` to deploy esignet services.
  ```
  cd helm
  ./install-all.sh
  ```

### Delete
* Run `delete-all.sh` to remove esignet services.
  ```
  cd helm
  ./delete-all.sh
  ```

### Restart
* Run `restart-all.sh` to restart esignet services.
  ```
  cd helm
  ./restart-all.sh
  ```

## Onboard esignet
* Run onboarder's [install.sh](partner-onboarder) script .
### Configurational steps after onboarding is completed.
*  Below mentioned onboarding steps are added after 1.2.0.1-b3
   *  Onboarding the default esignet partner
   *  Onboarding the default resident-oidc partner

###1. Onboarding the default esignet partner
*  After successfull partner onboarder run for esignet , download html reports from `onboarder` bucket of object store .
*  Get `licensekey` from  response body of  request `create-the-MISP-license-key-for-partner` from the report **_e-signet.html_**
*  Update & commit  value of  `mosip.esignet.misp.license.key`  parameter with `licensekey` value from last step in **esignet-default.properties** .
*  Restart  esignet pod.

###2.Onboarding the default resident-oidc partner
*  After successfull partner onboarder run for resident-oidc , download html reports from `onboarder` bucket of object store .
*  Get `clientId` from  response body of  request `create-oidc-client` from the report **_resident-oidc.html_** .
*  Update & commit  value of  `mosip.iam.module.clientID`  parameter with `clientId` value from last step in **resident-default.properties** .
*  Restart resident pod.  

## APIs
API documentation is available [here](https://mosip.stoplight.io/docs/identity-provider/branches/main/6f1syzijynu40-identity-provider).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

