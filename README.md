[![Maven Package upon a push](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml/badge.svg?branch=release-1.4.x)](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mosip_esignet&id=mosip_esignet&metric=alert_status)](https://sonarcloud.io/dashboard?id=mosip_esignet)
# e-Signet Project
## Overview
This repository contains the implementation of 
* Authorization Code flow of OAuth 2.0. Supports all the mandatory features of OIDC (Open ID Connect) specification.
* VC Issuance Flow, supports only [wallet initiated flow](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-12.html#name-authorization-code-flow)

e-Signet repository contains following:

1. esignet-core - Library containing all the common interfaces, DTOs and utils that is used as dependency in the other esignet module libraries and services
2. esignet-service - Deployable API service containing all the OIDC and UI controllers.
3. esignet-integration-api - Library containing all the integration interfaces.
4. client-management-service-impl - Client management implementations classes.
5. oidc-service-impl - Oauth and OIDC implementation classes.
6. binding-service-impl - key and individualId binding service implementation classes.
7. consent-service-impl - Service to manage user consent per client. 
8. vci-service-impl - Credential issuance service implementation classes.
9. db_scripts - Contains all the db scripts required to setup or upgrade the DB for esignet module.

## Databases
Refer to [SQL scripts](db_scripts).

## Build (for developers)
The project requires JDK 11.
1. Build:
    ```
    $ mvn clean install -Dgpg.skip=true
    ```

## Run eSignet (for developers)
To simplify running eSignet in local for developers we have added [Docker Compose Setup](docker-compose/README.md). 
This docker-compose includes eSignet service and UI along with mock-identity-system to test the local deployment. 
  
## APIs
API documentation is available [here](docs/esignet-openapi.yaml).

## Documentation
eSignet documentation is available [here](https://docs.esignet.io/).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

