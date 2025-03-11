\c mosip_esignet

DO $$
BEGIN

ALTER TABLE client_detail DROP COLUMN additional_config;

END $$