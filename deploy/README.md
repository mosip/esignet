# Esignet Deployment in Kubernetes Environment
## Overview
* This guide will walk you through the deployment process of the Esignet application.
* The setup involves creating
  * Kubernetes cluster
  * Setting up Nginx
  * Installing Istio
  * Configuring storage class
  * Configuring the necessary dependent services
  * Deploying Esignet services
## Deployment
### K8 cluster
* Kubernetes cluster should be ready with storage class and ingress configured properly.
* Below is the document containing steps to create and configure K8 cluster.
  * __Onprem RKE CLuster__ : Create RKE K8 cluster using mentioned [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/on-prem#mosip-k8s-cluster-setup-using-rke).
      * __Persistence__ : Setup storage class as per [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/on-prem#storage-classes).
      * __Istio service mesh__ : Setup Istio service mesh using [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/on-prem#istio-for-service-discovery-and-ingress).
      * __Nginx__ : Setup and configure nginx 
  * __AWS EKS cluster__ : Create AWS EKS cluster using mentioned [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/aws#mosip-cluster-on-amazon-eks).
      * __Persistence__ : Setup storage class as per [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/aws#persistence).
      * __Ingress and Loadbalancer__ : Setup nginx and configure NLB for exposing services outside using [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/aws#ingress-and-load-balancer-lb).
### Install Pre-requisites
```
./install-prereq.sh
```
### Initialise pre-requisites
```
./initialise-prereq.sh
```
### Install config-server
```
./config-server/install.sh
```
### Install esignet, oidc and captcha service
```
./install-esignet.sh
```
## Onboarder
* If Esignet is getting deployed with MOSIP than we need to execute the onboarder for MISP partner.
* Onboarder [scripts](../partner-onboarder/).
