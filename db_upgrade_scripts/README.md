## MOSIP Database Upgrade and Rollback scripts inventory guidelines on postgresql database.
## Prerequisities
* Postgres client (psql) has to be installed on the deployment servers.
* DB Server and access details.
* Necessary details needs to be updated in peoperties file against to the relevant variables being used 

## Properties file variable details and description,Properties file has to be updated with the required details for each database before proceeding with upgrade or rollback steps.

**ACTION=** Action to be taken, possible values: upgrade or rollback

**MOSIP_DB_NAME=** Destination Database name to upgrade

**DB_SERVERIP=** Contains details of destination Database SERVER_IP(Ex_1: 10.0.0.1) (Ex_2: postgres.sandbox.net) where the 'upgrade' or 'rollback' is targeted.

**DB_PORT=** Contains the Database server port details where the Postgres is allowed to connect. Ex: 5433

**SU_USER=** Contains the Super User to connect to Database server i.e. postgres

**SU_USER_PWD=** Contains Super User password to connect to Database

**DEFAULT_DB_NAME=** Default database name to connect with respective postgres server i.e. ex: postgres

**DBUSER_PWD=** DB user password for required postgres Database

**CURRENT_VERSION=** Contains Current Database version

**UPGRADE_VERSION=** final Database version after upgrade
