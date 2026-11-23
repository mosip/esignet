-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : key_alias
-- Purpose    : Key Alias table
--
-- MySQL port of db_scripts/postgresql/mosip_esignet/ddl/esignet-key_alias.sql (column documentation is kept in
-- that file). Date/time columns are datetime(6): microsecond precision, no 2038 upper bound.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE key_alias(
    id varchar(36) NOT NULL,
    app_id varchar(36) NOT NULL,
    ref_id varchar(128),
    key_gen_dtimes datetime(6),
    key_expire_dtimes datetime(6),
    status_code varchar(36),
    lang_code varchar(3),
    cr_by varchar(256) NOT NULL,
    cr_dtimes datetime(6) NOT NULL,
    upd_by varchar(256),
    upd_dtimes datetime(6),
    is_deleted boolean DEFAULT FALSE,
    del_dtimes datetime(6),
    cert_thumbprint varchar(100),
    uni_ident varchar(50),
    CONSTRAINT pk_keymals_id PRIMARY KEY (id),
    CONSTRAINT uni_ident_const UNIQUE (uni_ident)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE INDEX idx_key_alias_cert_thumbprint ON key_alias(cert_thumbprint);
