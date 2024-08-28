# MinIO Operator Based Installation Guide

## Introduction
MinIO is a High Performance Object Storage released under GNU Affero General Public License v3.0. It is API compatible with Amazon S3 cloud storage service.  MinIO is required for on-prem setups. If you are installing MOSIP on cloud you need explore cloud native options that support S3 API.  On AWS, it would be S3.  

For production deployments it is recommended that MinIO Operator is used to deploy MinIO on Kubernetes. MinIO Operator supports deploying MinIO Tenants onto private and public cloud infrastructures ("Hybrid" Cloud).

## Prerequisites
- Kubernetes version 1.19.0 or later.

- Cluster contains a [namespace](https://github.com/minio/operator/blob/master/README.md#minio-tenant-namespace) for
  the MinIO Tenant.

- Cluster contains a [`StorageClass`](https://github.com/minio/operator/blob/master/README.md#default-storage-class)
  for the MinIO Tenant Persistent Volumes  (`PV`). The `StorageClass` *must* have `volumeBindingMode: WaitForFirstConsumer`

- [Kubernetes `krew`](https://github.com/kubernetes-sigs/krew)
  plugin manager available on the client machine. See the [`krew` installation documentation](https://krew.sigs.k8s.io/docs/user-guide/setup/install/).

- Minio Operator will deploy CertifiateSigningRequest (CSRs) and k8s needs to issue certificates for these CSRs. On an on-prem setup, if using RKE for k8s, to get k8s to issue CSRs, add the following in `cluster.yaml`.
  ```
  services:
    kube-controller:
      extra_args:
        cluster-signing-cert-file: "/etc/kubernetes/ssl/kube-ca.pem"
        cluster-signing-key-file: "/etc/kubernetes/ssl/kube-ca-key.pem"
  ```
  Then run `rke up`, to sync these changes.

## Deploy MinIO Cluster

### Install the MinIO Operator

Run the following command to install the MinIO Operator and Plugin using `krew`:

```sh
   kubectl krew update
   kubectl krew install minio
```

Run the following command to initialize the Operator:

```sh
kubectl minio init
```

### Create a New Tenant

The following `kubectl minio` command creates a MinIO Tenant with 1 node, 4 volumes, and a total capacity  of 8Gi. This configuration requires
*at least* 4 [Persistent Volumes](https://github.com/minio/operator#Local-Persistent-Volumes).

```sh
  kubectl minio tenant create minio-tenant-1 \
    --servers 1                              \
    --volumes 4                              \
    --capacity 8Gi                           \
    --namespace minio-tenant-1               \
    --storage-class local-storage
```

The above command, by default, deploys with TLS enabled. To change this behaviour here is what needs to be done:
- Write the tenant to a file without actually deploying it. Like the following
```sh
  kubectl minio tenant create minio-tenant-1 \
    --servers 1                              \
    --volumes 4                              \
    --capacity 8Gi                           \
    --namespace minio-tenant-1               \
    --storage-class local-storage            \
    -o > minio-tenant-1.yaml
```
- Now change the following line in the tenant file.
```
requestAutoCert: false
```
- Then deploy the tenant.
```sh
kubectl apply -f minio-tenant-1.yaml
```

### Connect to the Tenant

MinIO outputs credentials for connecting to the MinIO Tenant as part of the creation
process:

```sh

Tenant 'minio-tenant-1' created in 'minio-tenant-1' Namespace
  Username: admin
  Password: dbc978c2-bfbe-41bf-9dc6-699c76bafcd0
+-------------+------------------------+------------------+--------------+-----------------+
| APPLICATION |      SERVICE NAME      |     NAMESPACE    | SERVICE TYPE | SERVICE PORT(S) |
+-------------+------------------------+------------------+--------------+-----------------+
| MinIO       | minio                  | minio-tenant-1   | ClusterIP    | 443             |
| Console     | minio-tenant-1-console | minio-tenant-1   | ClusterIP    | 9090,9443       |
+-------------+------------------------+------------------+--------------+-----------------+

```

Copy the credentials to a secure location, such as a password protected key manager.
MinIO does *not* display these credentials again.

You can use the `kubectl port-forward` command to temporarily forward traffic from the local host to the MinIO Tenant.

- The `minio` service provides access to MinIO Object Storage operations. Port forward to localhost
port 9000 using the below command:


```sh
    kubectl port-forward svc/minio 9000:443 -n minio-tenant-1
```

- The `minio-tenant-1-console` service provides access to the MinIO Console. The
  MinIO Console supports GUI administration of the MinIO Tenant. Port forward to localhost
port 9001 using the below command:

```sh
    kubectl port-forward svc/minio-tenant-1-console 9001:9443 -n minio-tenant-1
```

For production workloads and applications *external* to the Kubernetes cluster, you must configure
[Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/) or a
[Load Balancer](https://kubernetes.io/docs/concepts/services-networking/service/#loadbalancer) to
expose the MinIO Tenant services.


## Create configmap and secret
Run the script and pass the access key and secret.  For region specify "".
```sh
cd ../
./cred.sh
```
