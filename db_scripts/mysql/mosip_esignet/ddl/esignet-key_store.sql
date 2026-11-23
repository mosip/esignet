-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : key_store
-- Purpose    : Key Store table
--
-- MySQL port of db_scripts/mosip_esignet/ddl/esignet-key_store.sql (column documentation is kept in
-- that file). Date/time columns are datetime(6): microsecond precision, no 2038 upper bound.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE key_store(
	id varchar(36) NOT NULL,
	master_key varchar(36) NOT NULL,
	private_key varchar(2500) NOT NULL,
	certificate_data varchar(4000) NOT NULL,
	cr_by varchar(256) NOT NULL,
	cr_dtimes datetime(6) NOT NULL,
	upd_by varchar(256),
	upd_dtimes datetime(6),
	is_deleted boolean DEFAULT FALSE,
	del_dtimes datetime(6),
	CONSTRAINT pk_keystr_id PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
