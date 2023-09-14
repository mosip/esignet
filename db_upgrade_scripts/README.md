## MOSIP Database Upgrade and Rollback scripts inventory guidelines on postgresql database.
## Prerequisities
* Postgres client (psql) has to be installed on the deployment servers.
* DB Server and access details.
* Necessary details needs to be updated in peoperties file against to the relevant variables being used 

## Properties file variable details and description,Properties file has to be updated with the required details for each database before proceeding with upgrade or rollback steps.

**ACTION=** Action to be taken, possible values: upgrade or rollback

**MOSIP_DB_NAME=** Destination DB name to upgrade

**DB_SERVERIP=** DB Server Ip

**DB_PORT=** DB Server Port

**SU_USER=** Super User to connect to DB server

**SU_USER_PWD=** Super User password to connect to DB

**DEFAULT_DB_NAME=** Default database name to connect with respective postgres server i.e. ex: postgres

**DBUSER_PWD=** DB user password for required postgres DB

**CURRENT_VERSION=** Contains Current DB version

**UPGRADE_VERSION=** final DB version after upgrade
