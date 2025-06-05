\c mosip_esignet

ALTER TABLE client_detail DROP COLUMN additional_config;

ALTER TABLE client_detail
  ALTER COLUMN name TYPE character varying(256);