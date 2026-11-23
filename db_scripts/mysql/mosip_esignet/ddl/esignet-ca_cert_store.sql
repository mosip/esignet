-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : ca_cert_store
-- Purpose    : CA Certificate Store Table
--
-- MySQL port of db_scripts/mosip_esignet/ddl/esignet-ca_cert_store.sql (column documentation is
-- kept in that file). Date/time columns are datetime(6): microsecond precision, no 2038 upper bound.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE ca_cert_store(
	cert_id varchar(36) NOT NULL,
	cert_subject varchar(500) NOT NULL,
	cert_issuer varchar(500) NOT NULL,
	issuer_id varchar(36) NOT NULL,
	cert_not_before datetime(6),
	cert_not_after datetime(6),
	crl_uri varchar(120),
	cert_data varchar(4000),
	cert_thumbprint varchar(100),
	cert_serial_no varchar(50),
	partner_domain varchar(36),
	cr_by varchar(256),
	cr_dtimes datetime(6),
	upd_by varchar(256),
	upd_dtimes datetime(6),
	is_deleted boolean DEFAULT FALSE,
	del_dtimes datetime(6),
	ca_cert_type varchar(25),
	CONSTRAINT pk_cacs_id PRIMARY KEY (cert_id),
	CONSTRAINT cert_thumbprint_unique UNIQUE (cert_thumbprint,partner_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
