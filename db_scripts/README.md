# IDP
Open ID based Identity provider for large scale authentication.

## Prerequisites
* Make sure DB changes for IDA and PMS are up to date.
* If not upgraded, IDA DB using the [release script](https://github.com/mosip/id-authentication/tree/develop/db_release_scripts).
* If not upgraded, PMS DB using the [release script](https://github.com/mosip/partner-management-services/tree/develop/db_release_scripts).

## Initialize IDP DB
* To initialize IDP DB, run below script. 
  ```sh
  ./init_db.sh
  ```