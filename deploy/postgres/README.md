# Postgres installation on Kubernetes cluster

## Install 
```sh
./install.sh
```
* A random password will get assigned for `postgres` user if you have not specified a password. The password may be obtained from Rancher console.

## Test
* Make sure docker is running from machine you are testing.
* Postgres is accessible over "internal" channel, i.e. over Wireguard.  Make sure you have the Wireguard setup along with credentials to connect to internal load balancer.
* Connect to postgres:
```sh
docker run -it --rm postgres psql -h <hostname pointing to load balancer> -U postgres -p 5432
```
## Initialize DB
* Review `init_values.yaml` for  which DBs you would like to initialize.
* Run init postgres helm chart to create necessary DB, users, roles etc:
```sh
./postgres-init.sh
```
Be aware of version of helm chart corresponding to mosip version.

## Delete
Note that PVC and PV are not deleted after helm delete.  So if you would like to postgres again, make sure you delete PVC and PV.

## Init a specific DB
To initialized a specific db disable init of all others in `init_values.yaml` by settings `true` -> `false`.  Get db-user password with `get_pwd.sh`.  Provide the password in `init_values.yaml` and run `init_db.sh`.

## DB export

* Export all DB's to a single file via below command:
  ```
  pg_dumpall -c --if-exists -h <HOSTNAME> -p <PORT-NUMBER> -U <USERNAME> -f <BACKUP_FILE_NAME>.dump
  ```

## DB import

* Import DB's from backup file via below command:
  ```
  psql -h <HOSTNAME> -p <PORT-NUMBER> -U <USERNAME> -f <BACKUP_FILE_NAME>.dump
  ```

## DB Commons secret and postgres-postgresql secret creation

* Base64 Encoding and YAML Creation Script:

This script prompts the user for a password, encodes it in Base64, and generates a YAML file with the encoded password as a secret.

### Prerequisites:

- Python 3.x installed

### Usage:

1. Run the script:
```
python3 generate-secret-cm.py
```
2. Enter the password when prompted.
3. The script will create a ``db-common-secrets.yaml`` and ``postgres-postgresql.yaml``file in the same directory, containing the encoded password as a secret.
4. Then after the yaml file is created run the command:
```
kubectl create ns postgres
kubectl apply -f db-common-secrets.yaml
kubectl apply -f postgres-postgresql.yaml
```
5. After running the above command ``db-common-secrets`` and ``postgres-postgresql`` will be created as ``secrets`` in ``postgres`` namespace.

## DB Upgrade
* Used to upgrade the schemas and required data from one MOSIP version to another.
* The upgrade script facilitates the upgrade process from the previous version (n-1) to the current version (n). It follows the upgrade path if you are two versions away from the current version (n-2).
* In case of upgrade of multiple versions of same DB, do update all the details sequentially in `upgrade.csv`.
* Install in required Kubernetes cluster using below mentioned steps:
### Pre-requisites for upgrade
* DB_SERVERIP : Ip to access the DB server to be upgraded.
* DB_PORT : port for connecting to DB server.
* SU_USER : Super User for DB server.
* SU_USER_PWD : Super User Password
* PRIMARY_LANGUAGE_CODE : Primary language for MOSIP.
* Updated `upgrade.csv` with sequential details of upgrade to be performed.
### Install Upgrade
```
./postgres-upgrade.sh
```
### Delete Upgrade
```
helm delete postgres-upgrade -n postgres
```
## Troubleshooting
* If you face login issues even when the password entered is correct, it could be due to previous PVC, and PV.  Delete them, but exercise caution as this will delete all persistent data.
* If you face below error while importing db's.
  ```
  psql:all-db-backup.dump:139: ERROR:  option "locale" not recognized                                             
  LINE 1: ...late1 WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE = '...
  ```
  Then replace `LOCALE` with `LC_COLLATE` in `<BACKUP_FILE_NAME>.dump` file via sed command.
  ```
  sed -i 's/LOCALE/LC_COLLATE/g' <BACKUP_FILE_NAME>.dump
  ```

