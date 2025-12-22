\c mosip_esignet

----- TRUNCATE esignet.client_detail TABLE Data and It's reference Data and COPY Data from CSV file -----
TRUNCATE TABLE esignet.client_detail cascade ;

\ir dml/esignet-key_policy_def.sql