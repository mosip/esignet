-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : consent_history
-- Purpose    : To store user consent details
--
-- MySQL port of db_scripts/mosip_esignet/ddl/esignet-consent_history.sql (column documentation is
-- kept in that file). Date/time columns are datetime(6): microsecond precision, no 2038 upper bound.
-- The unnamed primary key is reported by MySQL as PRIMARY.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE consent_history (
    id VARCHAR(36) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    claims VARCHAR(2048) NOT NULL,
    authorization_scopes VARCHAR(1024) NOT NULL,
    cr_dtimes DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    expire_dtimes DATETIME(6),
    signature VARCHAR(1024),
    hash VARCHAR(100),
    accepted_claims VARCHAR(1024),
    permitted_scopes VARCHAR(1024),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE INDEX idx_consent_history_psu_client ON consent_history(psu_token, client_id);
