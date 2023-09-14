## MOSIP Database Upgrade and Rollback scripts inventory guidelines on postgresql database.
## Prerequisities
* Postgres client (psql) has to be installed on the deployment servers.
* DB Server and access details.
* Copy database upgrade scripts(DDL, DML, .SH ... etc) from git/repository on to the DB deployment server.
* 
* Necessary details needs to be updated in peoperties file against to the relevant variables being used 

## Properties file variable details and description,Properties file has to be updated with the required details for each database before proceeding with upgrade or rollback steps.

**ACTION=** Action to be taken, possible values: upgrade or rollback

**MOSIP_DB_NAME=** Destination DB name to upgrade

**DB_SERVERIP=** DB Server Ip

**DB_PORT=** DB Server Port

**SU_USER=** Superuser to connect to DB server

**SU_USER_PWD=** Superuser password to connect to DB

**DEFAULT_DB_NAME=** Default database name to connect with respective postgres server i.e. ex: postgres

**DBUSER_PWD=** DB user password for required postgres DB

**CURRENT_VERSION=** Current DB version

**UPGRADE_VERSION=** final DB version after upgrade

## DB upgrade/rollback deployment:
**Step-1:** Make modification to all the respective database properties files **(<<schema>>_upgrade.properties)** in the respective database directories.

**Step-2:** DB upgrade deployment for all databases, run the **"<<schema>>_upgrade.sh"** script with upgrade version as parameter. run below command

```bash
./upgrade.sh upgrade.properties
```
**or**
```bash
bash upgrade.sh upgrade.properties
```
**Note:** Observe the deployment and make sure DB should be updated
### Post Deployment Validation
**Note:** If you encounter the following messages then please recheck the details(ip address, port number, database name, password) entered in the properties file, the message would be as follows:

<psql: could not translate host name "52.172.12.285" to address: Name or service not known>.

<psql: FATAL:  password authentication failed for user "postgress">

<psql: FATAL:  database "postgress" does not exist>

**Key points during or after the script execution:**

* Accessing the right path for DB deploy

* Check for any active connections

* Creates roles, creating Database, schemas, granting access, creating respective tables.

* Loading data or DML operations valid only for those DB's which carries DML actions.

Kindly ignore **NOTICE** or **SKIPPING** messages. As these messages states that particular action is already in place hence sql script ignore performing again.