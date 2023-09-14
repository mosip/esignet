## MOSIP Database Upgrade and Rollback scripts inventory guidelines on postgresql database.
## Prerequisities
* Postgres client (psql) has to be installed on the deployment servers.
* DB Server and access details.
* Necessary details needs to be updated in peoperties file against to the relevant variables being used 

## Properties file variable details and description,Properties file has to be updated with the required details for each database before proceeding with upgrade or rollback steps.

**ACTION=** Action to be taken, possible values: upgrade or rollback

**MOSIP_DB_NAME=** Destination DB name to be upgrade

**DB_SERVERIP=** Contains details of Destination DB SERVER_IP(Ex_1: 10.0.0.1) (Ex_2: postgres.sandbox.net) where the 'upgrade' or 'rollback' is targeted.

**DB_PORT=** Contains the DB server port details where the Postgres is allowed to connect. Ex: 5433

**SU_USER=** Contains Super User to connect to DB server i.e. postgres

**SU_USER_PWD=** Contains Super User password to connect to DB server

**DEFAULT_DB_NAME=** Default database name to connect with respective postgres server i.e. ex: postgres

**DBUSER_PWD=** Contains DB user password for required postgres DB

**CURRENT_VERSION=** Contains Current DB version

**UPGRADE_VERSION=** Contains final DB version after upgrade