# e-Signet Database
Open ID based Identity provider for large scale authentication.

## Overview
This folder containers various SQL scripts to create database and tables in postgres.
The tables are described under `<db name>/ddl/`.
Default data that's populated in the tables is present under `<db name>/dml` folder.

## Prerequisites
* Make sure DB changes for IDA and PMS are up to date.
* If not upgraded, IDA DB using the [release script](https://github.com/mosip/id-authentication/tree/develop/db_release_scripts).
* If not upgraded, PMS DB using the [release script](https://github.com/mosip/partner-management-services/tree/develop/db_release_scripts).
* Command line utilities:
  - kubectl
  - helm
* Helm repos:
  ```sh
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo add mosip https://mosip.github.io/mosip-helm
  ```

## Install in existing MOSIP K8 Cluster
These scripts are automatically run with below mentioned script in existing k8 cluster with Postgres installed.
### Install
* Set your kube_config file or kube_config variable on PC.
* Update `init_values.yaml` with db-common-password from the postgres namespace in the required field `dbUserPasswords.dbuserPassword` and ensure `databases.mosip_esignet` is enabled.
  ```
  ./init_db.sh`
  ```

## Install for developers
Developers may run the SQLs using `<db name>/deploy.sh` script.
