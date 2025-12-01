\c mosip_esignet

----- TRUNCATE esignet.client_detail TABLE Data and It's reference Data and COPY Data from CSV file -----
TRUNCATE TABLE esignet.client_detail cascade ;
TRUNCATE TABLE esignet.openid_profile CASCADE;

\COPY esignet.key_policy_def (APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) FROM './dml/esignet-key_policy_def.csv' delimiter ',' HEADER  csv;

-- Insert OpenID profile table
-- Ensure the CSV `esignet-openid_profile.csv` has columns in this exact order:
-- profile_name, feature
\COPY esignet.openid_profile (profile_name, feature) FROM './dml/esignet-openid_profile.csv' DELIMITER ',' HEADER CSV;