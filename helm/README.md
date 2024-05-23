# **Esignet Deployment**

## **Overview**
This guide will walk you through the deployment process of the Esignet application. The setup involves creating a Kubernetes cluster, setting up Nginx, installing Istio, and configuring the necessary dependent services.

## Prerequisites
Before you begin, ensure you have the following installed and configured on your system:

Kubernetes (kubectl)
Helm
Nginx
Istio
* [reference-link](https://github.com/mosip/k8s-infra/blob/main/mosip/on-prem/README.md)

## Storage class setup

* [nfs-clinet](https://github.com/mosip/k8s-infra/tree/main/nfs)

## Istio installation

* [istio](https://github.com/mosip/k8s-infra/blob/main/mosip/on-prem/README.md#istio-for-service-discovery-and-ingress)

## Nginx setup

* [nginx](https://github.com/mosip/k8s-infra/tree/main/mosip/on-prem/nginx)

## Esignet dependent modules installation

* postgres
* iam
* kafka
* artifactory
* config-server
* redis
* esignet
* oidc-ui

## Run install-all.sh() TODO

### Once all the services are up and running, perform the onboarding process for Esignet

* [partner-onboarding](https://github.com/mosip/esignet/tree/v1.4.0/partner-onboarder)







