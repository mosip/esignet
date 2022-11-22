\c mosip_idpbinding

----- TRUNCATE idpbinding.public_key_registry TABLE Data and It's reference Data and COPY Data from CSV file -----
TRUNCATE TABLE idpbinding.public_key_registry cascade ;

-- \COPY idp.key_policy_def (APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) FROM './dml/idp-key_policy_def.csv' delimiter ',' HEADER  csv;













