# e-Signet Database
Open ID based Identity provider for large scale authentication.

## Overview
This folder containers various SQL scripts to create database and tables in postgres.
The tables are described under `<db name>/ddl/`.
Default data that's populated in the tables is present under `<db name>/dml` folder.

## Prerequisites
* Command line utilities:
  - kubectl
  - helm
* Helm repos:
  ```sh
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo add mosip https://mosip.github.io/mosip-helm
  ```

## Install in existing MOSIP K8 Cluster
1. Setup [Postgres server](https://github.com/mosip/mosip-infra/blob/v1.2.0.2/deployment/v3/external/postgres/install.sh) if not. Note: only install the server dont initialise.
1. Execute init script as mentioned below for existing k8 cluster with Postgres installed.
### Install
* Set your kube_config file or kube_config variable on PC.
* Update `init_values.yaml` with db-common-password from the postgres namespace in the required field `dbUserPasswords.dbuserPassword` and ensure `databases.mosip_esignet` is enabled.
  ```
  ./init_db.sh`
  ```

## Install for developers
Developers may run the SQLs using `<db name>/deploy.sh` script.
