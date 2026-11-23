-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : server_profile
-- Purpose    : Server profile: static table to store the profile and feature(as part of profile) mapping
--
-- MySQL port of db_scripts/mosip_esignet/ddl/esignet-server_profile.sql.
-- -------------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS server_profile (
    profile_name VARCHAR(100) NOT NULL,
    feature VARCHAR(100) NOT NULL,
    additional_config_key VARCHAR(200) NOT NULL,
    CONSTRAINT pk_server_profile PRIMARY KEY (profile_name, feature)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
