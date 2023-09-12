## MOSIP Database Upgrade and Rollback scripts inventory guidelines on postgresql database.
## Prerequisities
* Postgres client (psql) has to be installed on the deployment servers.
* DB Server and access details.
* Necessary details needs to be updated in peoperties file against to the relevant variables being used 

## Properties file variable details and description,Properties file has to be updated with the required details for each database before proceeding with upgrade or rollback steps.

**ACTION=** select action as per the requirement 'upgrade' or 'rollback'

**MOSIP_DB_NAME=** Database name for which the deployment is scheduled.

**DB_SERVERIP=** Contains details of Destination SERVER_IP(Ex_1: 10.0.0.1) (Ex_2: postgres.sandbox.net) where the deployment is targeted.

**DB_PORT=** Contains the postgres server port details where the postgres is allowed to connect. Ex: 5433

**SU_USER=** Contains the postgres super-user name to connect to the postgres database i.e. postgres

**SU_USER_PWD=** Contains the password for postgres super user

**SYS_ADMIN_USER=** Variable contains the sysadmin use name which indeed is going to be the super user for the remaining actions going to be performed by shell script.

**SYS_ADMIN_PWD=** Contains the credential details for SYSADMIN_USER.

**DEFAULT_DB_NAME=** Default database name to connect with respective postgres server i.e. ex: postgres

**DBUSER_PWD=** Contains the password for postgres db common secret

**CURRENT_VERSION=** Contains current version

**UPGRADE_VERSION=** Contains upgrade version