\c mosip_esignet

ALTER TABLE client_detail ADD COLUMN additional_config jsonb;

ALTER TABLE client_detail
  ALTER COLUMN name TYPE character varying(600);