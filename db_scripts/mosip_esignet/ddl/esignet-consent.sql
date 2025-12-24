-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : consent_detail
-- Purpose    : To store user consent details
--
-- Create By   	: Hitesh C
-- Created Date	: May-2023
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

CREATE TABLE consent_detail (
    id VARCHAR(36) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    claims VARCHAR(1024) NOT NULL,
    authorization_scopes VARCHAR(1024) NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR(1024),
    hash VARCHAR(1024),
    accepted_claims VARCHAR(1024),
    permitted_scopes VARCHAR(1024),
    PRIMARY KEY (id),
    CONSTRAINT unique_client_token UNIQUE (client_id, psu_token)
);

CREATE INDEX idx_consent_psu_client ON consent_detail(psu_token, client_id);

-- COMMENT ON TABLE consent_detail IS 'Contains user consent details';
-- COMMENT ON COLUMN consent_detail.id IS 'UUID : Unique id associated with each consent';
-- COMMENT ON COLUMN consent_detail.client_id IS 'Client_id: associated with relying party';
-- COMMENT ON COLUMN consent_detail.psu_token IS 'PSU token associated with user consent';
-- COMMENT ON COLUMN consent_detail.claims IS 'Json of requested and user accepted claims';
-- COMMENT ON COLUMN consent_detail.authorization_scopes IS 'Json string of requested authorization scope';
-- COMMENT ON COLUMN consent_detail.cr_dtimes IS 'Consent creation date';
-- COMMENT ON COLUMN consent_detail.expire_dtimes IS 'Expiration date';
-- COMMENT ON COLUMN consent_detail.signature IS 'Signature of consent object ';
-- COMMENT ON COLUMN consent_detail.hash IS 'hash of consent object';
-- COMMENT ON COLUMN consent_detail.accepted_claims IS 'Accepted Claims by the user';
-- COMMENT ON COLUMN consent_detail.permitted_scopes IS 'Accepted Scopes by the user';

