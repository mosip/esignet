# e-Signet Database
Open ID based Identity provider for large scale authentication.

## Overview
This folder contains SQL scripts to create the eSignet database and tables on PostgreSQL and MySQL,
with one folder per database engine: `<engine>/<db name>/`.
The tables are described under `<engine>/<db name>/ddl/`.
Default data that's populated in the tables is present under `<engine>/<db name>/dml` folder.

## Layout
```
db_scripts/
├── init_db.sh, init_values.yaml, copy_cm_func.sh   PostgreSQL / Helm install (see below)
├── postgresql/mosip_esignet/                       PostgreSQL scripts (used by the Helm chart via `scriptsDir` and by CI)
│   ├── deploy.sh, *.sql
│   ├── ddl/
│   └── dml/
└── mysql/mosip_esignet/                            MySQL scripts, same internal shape (see its README.md)
    ├── deploy.sh, *.sql
    ├── ddl/
    └── dml/
```
If the PostgreSQL folder is moved, update `scriptsDir` in [`init_values.yaml`](init_values.yaml) and
[`deploy/postgres/init_values.yaml`](../deploy/postgres/init_values.yaml), and `DB_LOCATION` in
[`.github/workflows/db-test.yml`](../.github/workflows/db-test.yml).
Each engine folder is self-contained. The table definitions differ by design (MySQL uses `DATETIME(6)`,
a `TEXT` column with a CHECK, and an explicit collation), and the seed files are kept as one copy per
engine. Any schema or seed change must be made in both trees; seed files carry a "Keep in sync" header
naming their counterpart.

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
Developers may run the SQLs using the `<engine>/<db name>/deploy.sh` script, e.g.
`postgresql/mosip_esignet/deploy.sh` or `mysql/mosip_esignet/deploy.sh`.
