\c mosip_esignet

DO $$
BEGIN

ALTER TABLE client_detail ADD COLUMN additional_config jsonb;

END $$