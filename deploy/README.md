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
  * __Onprem RKE CLuster__ : Create RKE K8 cluster using mentioned [steps](https://github.com/mosip/k8s-infra/tree/v1.2.0.2/mosip/on-prem#mosip-k8s-cluster-setup-using-rke).
      * __Persistence__ : Setup storage class as per [steps](https://github.com/mosip/k8s-infra/tree/v1.2.0.1/mosip/on-prem#storage-classes).
      * __Istio service mesh__ : Setup Istio service mesh using [steps](https://github.com/mosip/esignet/blob/release-1.5.x/deploy/istio-gateway/install.sh).
      * __Nginx__ : Setup and configure nginx as per [steps](https://github.com/mosip/k8s-infra/blob/v1.2.0.2/mosip/on-prem/nginx).
      * __Logging__ : Setup logging as per [steps](https://github.com/mosip/k8s-infra/tree/v1.2.0.2/logging).
      * __Monitoring__ : Setup monitoring consisting elasticsearch, kibana, grafana using [steps](https://github.com/mosip/k8s-infra/tree/v1.2.0.2/monitoring).
  * __AWS EKS cluster__ : Create AWS EKS cluster using mentioned [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/aws#mosip-cluster-on-amazon-eks).
      * __Persistence__ : Setup storage class as per [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/aws#persistence).
      * __Ingress and Loadbalancer__ : Setup nginx and configure NLB for exposing services outside using [steps](https://github.com/mosip/k8s-infra/tree/main/mosip/aws#ingress-and-load-balancer-lb).
      * __Logging__ : Setup logging as per [steps](https://github.com/mosip/k8s-infra/tree/v1.2.0.2/logging).
      * __Monitoring__ : Setup monitoring consisting elasticsearch, kibana, grafana using [steps](https://github.com/mosip/k8s-infra/tree/v1.2.0.2/monitoring).
### Install Pre-requisites
* `esignet-global` configmap: For eSignet K8's env, `esignet-global` configmap in `esignet` namespace contains Domain related information. Follow below steps to add domain details for `esignet-global` configmap.
  * Copy `esignet-global-cm.yaml.sample` to `esignet-global-cm.yaml`.
  * Update the domain names in `esignet-global-cm.yaml` correctly for your environment.
* Install pre-requisites
  ```
  ./install-prereq.sh
  ```
### Initialise pre-requisites
* Update values file for postgres init [here](postgres/init_values.yaml).
* Create a google recaptcha v2 ("I am not a Robot") from Google with required domain name ex:[sandbox.mosip.net] [Recaptcha Admin](https://www.google.com/recaptcha/about/).
* Execute `initialise-prereq.sh` script to initialise postgres and keycloak and set esignet captcha.
  ```
  ./initialise-prereq.sh
  ```
### Install esignet, oidc and captcha service
```
./install-esignet.sh
```
## Onboarder
* If Esignet is getting deployed with MOSIP than we need to execute the onboarder for MISP partner.
* Onboarder [scripts](../partner-onboarder/).
