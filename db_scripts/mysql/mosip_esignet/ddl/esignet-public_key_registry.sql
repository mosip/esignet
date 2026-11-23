-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : public_key_registry
-- Purpose    : Public Key Registry: Table to store Id Hash and its respective PSU Token,Public Key and Wallet Binding Id.
--
-- MySQL port of db_scripts/postgresql/mosip_esignet/ddl/esignet-public_key_registry.sql (column documentation
-- is kept in that file). Date/time columns are datetime(6): microsecond precision, no 2038 upper bound.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE public_key_registry(
    id_hash varchar(100) NOT NULL,
    auth_factor varchar(25) NOT NULL,
	psu_token varchar(256) NOT NULL,
	public_key varchar(2500) NOT NULL,
	expire_dtimes datetime(6) NOT NULL,
	wallet_binding_id varchar(256) NOT NULL,
	public_key_hash varchar(100) NOT NULL,
	certificate varchar(4000) NOT NULL,
	cr_dtimes datetime(6) NOT NULL,
	thumbprint varchar(128) NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (id_hash, auth_factor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
