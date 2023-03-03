[![Maven Package upon a push](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml/badge.svg?branch=develop)](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mosip_esignet&id=mosip_esignet&metric=alert_status)](https://sonarcloud.io/dashboard?id=mosip_esignet)
# e-Signet Project
## Overview
This repository contains the implementation of Authorization Code flow of OAuth 2.0. Supports all the mandatory features of OIDC (Open ID Connect) specification.

e-Signet repository contains following:

1. esignet-core - Library containing all the common interfaces, DTOs and utils that is used as dependency in the other esignet module libraries and services
2. esignet-service - Deployable API service containing all the OIDC and UI controllers.
3. esignet-integration-api - Library containing all the integration interfaces.
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

## APIs
API documentation is available [here](https://mosip.stoplight.io/docs/identity-provider/branches/main/6f1syzijynu40-identity-provider).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

