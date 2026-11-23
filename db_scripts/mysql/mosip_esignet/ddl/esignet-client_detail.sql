-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : client_detail
-- Purpose    : Client Detail: Table to store all registered OIDC client details.
--
-- MySQL port of db_scripts/mosip_esignet/ddl/esignet-client_detail.sql (column documentation is
-- kept in that file). MySQL-specific choices:
--   * datetime(6): microsecond precision and no 2038 upper bound (TIMESTAMP has both limits).
--   * enc_public_key_cert is TEXT, not varchar(4000): with utf8mb4 the declared varchar widths of
--     this table would exceed MySQL's 65,535-byte row limit. The original 4,000-character limit is
--     kept by the CHECK constraint (enforced from MySQL 8.0.16).
--   * The primary key is always reported as PRIMARY by MySQL; the constraint name is ignored.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE client_detail(
	id varchar(100) NOT NULL,
	name varchar(600) NOT NULL,
	rp_id varchar(100) NOT NULL,
	logo_uri varchar(2048) NOT NULL,
	redirect_uris varchar(2048) NOT NULL,
	claims varchar(2048) NOT NULL,
	acr_values varchar(1024) NOT NULL,
	public_key varchar(1024) NOT NULL,
	public_key_hash varchar(128) NOT NULL,
	enc_public_key varchar(1024),
	enc_public_key_hash varchar(128),
	enc_public_key_cert text,
	grant_types varchar(512) NOT NULL,
	auth_methods varchar(512) NOT NULL,
	status varchar(20) NOT NULL,
	additional_config varchar(2048),
	cr_dtimes datetime(6) NOT NULL,
	upd_dtimes datetime(6),
	CONSTRAINT pk_clntdtl_id PRIMARY KEY (id),
	CONSTRAINT uk_clntdtl_public_key_hash UNIQUE (public_key_hash),
	CONSTRAINT chk_clntdtl_enc_cert_len CHECK (CHAR_LENGTH(enc_public_key_cert) <= 4000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
