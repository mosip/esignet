-[![Maven Package upon a push](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml/badge.svg?branch=develop)](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=mosip_esignet&id=mosip_esignet&metric=alert_status)](https://sonarcloud.io/dashboard?id=mosip_esignet)
# e-Signet Project
## Overview
This repository contains the implementation of 
* Authorization Code flow of OAuth 2.0. Supports all the mandatory features of OIDC (Open ID Connect) specification.
* VC Issuance Flow, supports only [wallet initiated flow](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-12.html#name-authorization-code-flow)
* Proof key code exchange support added. Mandatory for VCI flow.
* Supports basic mandatory features of OpenId Connect for identity assurance specification.

e-Signet repository contains following:

1. esignet-core - Library containing all the common interfaces, DTOs and utils that is used as dependency in the other esignet module libraries and services
2. esignet-service - Deployable API service containing all the OIDC and UI controllers.
3. esignet-integration-api - Library containing all the integration interfaces.
4. client-management-service-impl - Client management implementations classes.
5. oidc-service-impl - Oauth and OIDC implementation classes.
6. binding-service-impl - key and individualId binding service implementation classes.
7. consent-service-impl - Service to manage user consent per client. 
8. vci-service-impl - Credential issuance service implementation classes.
9. db_scripts - Contains all the db scripts required to do fresh setup of esignet module.
10. db_upgrade_scripts - Contains all the db scripts required to upgrade the DB for esignet module.

## Databases
Refer to [SQL scripts](db_scripts).

## Build (for developers)
The project requires JDK 11.
1. Build:
    ```
    $ mvn clean install -Dgpg.skip=true
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
* Run onboarder's [install.sh](partner-onboarder) script.

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

## Run eSignet (for developers)
To simplify running eSignet in local for developers we have added [Docker Compose Setup](docker-compose/README.md). 
This docker-compose includes eSignet service and UI along with mock-identity-system to test the local deployment. 

  
## APIs
API documentation is available [here](docs/esignet-openapi.yaml).

## Documentation
eSignet documentation is available [here](https://docs.esignet.io/).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

