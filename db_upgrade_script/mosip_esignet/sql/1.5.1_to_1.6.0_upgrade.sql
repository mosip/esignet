\c mosip_esignet
DO $$
BEGIN

ALTER TABLE client_detail ADD COLUMN additional_config jsonb;

ALTER TABLE client_detail
  ALTER COLUMN name TYPE character varying(600);

END $$