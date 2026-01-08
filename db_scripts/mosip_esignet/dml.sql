\c mosip_esignet

----- TRUNCATE esignet.client_detail TABLE Data and It's reference Data and insert data from sql file -----
TRUNCATE TABLE esignet.client_detail cascade ;
TRUNCATE TABLE esignet.openid_profile CASCADE;

\ir dml/esignet-key_policy_def.sql

-- Insert OpenID profile table
-- Ensure the CSV `esignet-openid_profile.csv` has columns in this exact order:
-- profile_name, feature
\COPY esignet.openid_profile (profile_name, feature, additional_config_key) FROM './dml/esignet-openid_profile.csv' DELIMITER ',' HEADER CSV;