-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : openid_profile
-- Purpose    : Openid profile: static table to store the profile and feature(as part of profile) mapping
--
-- Create By   	: Md Humair K
-- Created Date	: Nov-2025
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------
\c mosip_esignet

CREATE TABLE openid_profile (
    profile_name character varying(100) NOT NULL,
    feature character varying(100) NOT NULL,
    CONSTRAINT pk_openid_profile PRIMARY KEY (profile_name, feature)
);

COMMENT ON TABLE openid_profile IS 'Static table for global configuration: profile name and feature mapping.';
COMMENT ON COLUMN openid_profile.profile_name IS 'Profile name for configuration.';
COMMENT ON COLUMN openid_profile.feature IS 'Feature enabled for the profile.';
