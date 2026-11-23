-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : key_policy_def
-- Purpose    : Key Policy definition table
--
-- MySQL port of db_scripts/postgresql/mosip_esignet/ddl/esignet-key_policy_def.sql (column documentation is
-- kept in that file). Date/time columns are datetime(6): microsecond precision, no 2038 upper bound.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE key_policy_def(
    app_id varchar(36) NOT NULL,
    key_validity_duration smallint,
    is_active boolean NOT NULL,
    pre_expire_days smallint,
    access_allowed varchar(1024),
    cr_by varchar(256) NOT NULL,
    cr_dtimes datetime(6) NOT NULL,
    upd_by varchar(256),
    upd_dtimes datetime(6),
    is_deleted boolean DEFAULT FALSE,
    del_dtimes datetime(6),
    CONSTRAINT pk_keypdef_id PRIMARY KEY (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
