[![Maven Package upon a push](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml/badge.svg?branch=develop)](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mosip_esignet&id=mosip_esignet&metric=alert_status)](https://sonarcloud.io/dashboard?id=mosip_esignet)
# eSignet Project
## Overview

eSignet offers a seamless and straightforward solution for incorporating an existing trusted identity database into the digital realm via plugins.

This repository contains limited OpenId protocol implementation with:
* OAuth 2.0 RFC 6749 - Authorization code flow support
* OAuth 2.0 RFC 7636 - PKCE security extension
* OAuth 2.0 RFC 7523 - JWT profile for client authentication
* RFC 7519 - ID token and access token as JWT
* OpenID Connect Discovery 1.0 - /.well-known/openid-configuration
* RFC 5785 - Followed for both openid and oauth well-knowns
* Identity Assurance 1.0

## High level overview of eSignet with external systems

![esignet-overview.png](docs/images/esignet-overview.png)


eSignet repository contains following:

1. esignet-core - Library containing all the common interfaces, DTOs and utils that is used as dependency in the other esignet module libraries and services
2. esignet-service - Deployable API service containing all the OIDC and UI controllers.
3. esignet-integration-api - Library containing all the integration interfaces.
4. client-management-service-impl - Client management implementations classes.
5. oidc-service-impl - Oauth and OIDC implementation classes.
6. binding-service-impl - key and individualId binding service implementation classes.
7. consent-service-impl - Service to manage user consent per client.
8. oidc-ui - eSignet UI react-app
9. postman-collection - Contains eSignet postman collection with environment files.
10. api-test - eSignet API automation codebase.
11. docker-compose - Contains docker compose setup for developers and IdP enthusiasts.
12. db_scripts - Folder contains all the db scripts required to do fresh setup of eSignet module.
13. db_upgrade_scripts - Folder contains all the db scripts required to upgrade the DB for eSignet module.
14. docs - Folder contains API documentation and readme doc images.

`NOTE: All the Verifiable Credential Issuance endpoints are moved to Inji Certify(Inji stack). Refer` [Inji Certify repository](https://github.com/mosip/inji-certify)` for more information.`

## Databases
Refer to [SQL scripts](db_scripts).

## Build (for developers)
The project requires JDK 11.
1. Build:
    ```
    $ mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true
    ```
## [Deployment in K8 cluster](deploy/README.md)

## Partner onboarding
* Perform Partner onboarding for esignet MISP partner using [steps](partner-onboarder/README.md) only if mosip-identity plugin is used.
## Run eSignet (for developers)
* To simplify running eSignet in local for developers we have added [Docker Compose Setup](docker-compose/README.md). 
* This docker-compose includes eSignet service and UI along with mock-identity-system to test the local deployment. 
## APIs
API documentation is available [here](docs/esignet-openapi.yaml).
## Documentation
eSignet documentation is available [here](https://docs.esignet.io/).
## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).
