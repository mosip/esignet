\c :mosipdbname

----- TRUNCATE esignet.client_detail TABLE Data and It's reference Data and insert data from sql file -----
TRUNCATE TABLE esignet.client_detail cascade ;
TRUNCATE TABLE esignet.server_profile CASCADE;

\ir dml/esignet-key_policy_def.sql

\ir dml/esignet-server_profile.sql
