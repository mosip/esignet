[![Maven Package upon a push](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml/badge.svg?branch=release-1.2.0.1)](https://github.com/mosip/esignet/actions/workflows/push_trigger.yml)
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
  
## Installing in k8s cluster using helm
### Pre-requisites
1. Ensure Kubernetes cluster along with ingress and storage class configured is already present. In case not follow mentioned steps from [here](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md)
        1. Setup [pre-requisites](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#prerequisites).
        1. Setup [Virtual machines](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#prerequisites)
        1. Nginx node configuration (1 in nos.) : 4vCPU, 8GB RAM, 50GB HDD (space for NFS).
        1. K8 Cluster configuration (3 in nos.) : 8cCPU, 32GB RAM, 30GB HDD.
    1. Setup [Wireguard](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#wireguard-bastion-node). (optional) required in case you want to restrict public private access.
    1. Setup [Docker](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#docker)
    1. Setup [K8 cluster](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#rke-cluster-setup)
    1. Register the cluster to [Rancher](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#rke-cluster-setup). (optional)
    1. Setup [Storage class](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#storage-classes)
    1. Setup [Istio](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#istio-for-service-discovery-and-ingress)
    1. Setup [Nginx](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/README.md#nginx) to access services out of cluster
    1. Complete DNS mapping
1. Ensure [DB setup](db_scripts/README.md#install-in-existing-mosip-k8-cluster) is done.
1. Add / merge below mentioned properties files into existing config branch:
    * [esignet-default.properties](https://github.com/mosip/mosip-config/blob/v1.4.1-ES/esignet-default.properties) 
    * [application-default.properties](https://github.com/mosip/mosip-config/blob/v1.4.1-ES/application-default.properties)
1. Ensure Keycloak is already installed in keycloak namespace. [Reference](https://github.com/mosip/mosip-infra/blob/v1.2.0.2/deployment/v3/external/iam/install.sh). Note: Ensure Keycloak initialisation is not done only install.
1. Setup [Minio server](https://github.com/mosip/mosip-infra/blob/v1.2.0.2/deployment/v3/external/object-store/minio/install.sh) for storing onboarder results.
1. Setup Config server as mentioned below:
    1. `cd helm/config-server`
    1. [./install.sh](helm/config-server/install.sh)
1. Setup Artifactory server.
    1. `cd helm/artifactory`
    1. [./install.sh](helm/artifactory/install.sh)
1. Below are the dependent services required for esignet service integrated with MOSIP IDA:
   | Chart | Chart version |
   |---|---|
   |[Kafka](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/external/kafka) | 0.4.2 |
   |[Websub](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/mosip/websub) | 12.0.1-B2 |
   |[Keymanager service](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B3/deployment/v3/mosip/keymanager) | 12.0.1-B2 |
   |[Kernel services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B3/deployment/v3/mosip/kernel) | 12.0.1-B2 |
   |[Biosdk service](https://github.com/mosip/mosip-infra/tree/v1.2.0.1-B3/deployment/v3/mosip/biosdk) | 12.0.1-B3 |
   |[Idrepo services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B3/deployment/v3/mosip/idrepo) | 12.0.1-B2 |
   |[Pms services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B3/deployment/v3/mosip/pms) | 12.0.1-B3 |
   |[IDA services](https://github.com/mosip/mosip-infra/blob/v1.2.0.1-B3/deployment/v3/mosip/ida) | 12.0.1-B3 |
### Install
* Install `kubectl` and `helm` utilities.
* Run `install-all.sh` to deploy esignet services.
  ```
  cd helm
  ./install-all.sh
  ```
* During the execution of the `install-all.sh` script, a prompt appears requesting information regarding the presence of a public domain and a valid SSL certificate on the server.
* If the server lacks a public domain and a valid SSL certificate, it is advisable to select the `n` option. Opting it will enable the `init-container` with an `emptyDir` volume and include it in the deployment process.
* The init-container will proceed to download the server's self-signed SSL certificate and mount it to the specified location within the container's Java keystore (i.e., `cacerts`) file.
* This particular functionality caters to scenarios where the script needs to be employed on a server utilizing self-signed SSL certificates.

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
* Run onboarder's [install.sh](partner-onboarder) script to exchange jwk certificates.
  
## APIs
API documentation is available [here](docs/esignet-openapi.yaml).

## Documentation
eSignet documentation is available [here](https://docs.esignet.io/).

## License
This project is licensed under the terms of [Mozilla Public License 2.0](LICENSE).

