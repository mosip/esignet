# ESIGNET APITESTRIG

# INSTALL

This `install.sh` script automates the process of cloning `mosip-functional-tests` repository from a user-defined specific branch from a Git repository, running an installation script to deploy esignet apitestrig within the cluster, and cleaning up the repository afterward.

Note: This directory contains `values.yaml` file which contains the latest esignet apitestrig release changes with latest released Docker image and tag. The above install script uses this `values.yaml` by default to deploy apitestrig with latest changes.

## Prerequisites

- Ensure you have **git** installed on your machine.
- You need access to the Kubernetes cluster configuration file (`kubeconfig`).
- Ensure you have the necessary permissions to execute shell scripts.

## Script Usage

1. **Clone this script to your local machine**.
2. **Run the script using the following command**:
   ```bash
   ./script.sh <path-to-kubeconfig>
   ```

## TL;DR

```console
$ helm repo add mosip https://mosip.github.io
$ helm install my-release mosip/apitestrig -f values.yaml
```
