# IDP
Open ID based Identity provider for large scale authentication.

## Prerequisites
* Make sure 1.2.0.1-B2 DB changes for IDA and PMS are up to date.
* If not upgraded, IDA DB using the [release script](https://github.com/mosip/id-authentication/tree/1.2.0.1-B2/db_release_scripts).
* If not upgraded, PMS DB using the [release script](https://github.com/mosip/partner-management-services/tree/1.2.0.1-B2/db_release_scripts).

## Initialize IDP DB
* To initialize IDP DB, run below script. 
  ```sh
  ./init_db.sh
  ```