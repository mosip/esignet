-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : consent_history
-- Purpose    : To store user consent details
--
-- Create By   	: Hitesh C
-- Created Date	: May-2023
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

CREATE TABLE consent_history (
    id varchar(36) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    claims VARCHAR(1024) NOT NULL,
    authorization_scopes VARCHAR(512) NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR(1024),
    hash VARCHAR(1024),
    accepted_claims VARCHAR(1024),
    permitted_scopes VARCHAR(1024),
    PRIMARY KEY (id)
);

CREATE INDEX idx_consent_history_psu_client ON consent_history(psu_token, client_id);

-- COMMENT ON TABLE consent_history IS 'Contains user consent details';
-- COMMENT ON COLUMN consent_history.id IS 'UUID : Unique id associated with each consent';
-- COMMENT ON COLUMN consent_history.client_id IS 'Client_id: associated with relying party';
-- COMMENT ON COLUMN consent_history.psu_token IS 'PSU token associated with user consent';
-- COMMENT ON COLUMN consent_history.claims IS 'Json of requested and user accepted claims';
-- COMMENT ON COLUMN consent_history.authorization_scopes IS 'Json string of requested authorization scope';
-- COMMENT ON COLUMN consent_history.cr_dtimes IS 'Consent creation date';
-- COMMENT ON COLUMN consent_history.expire_dtimes IS 'Expiration date';
-- COMMENT ON COLUMN consent_history.signature IS 'Signature of consent object ';
-- COMMENT ON COLUMN consent_history.hash IS 'hash of consent object';
-- COMMENT ON COLUMN consent_history.accepted_claims IS 'Accepted Claims by the user';
-- COMMENT ON COLUMN consent_history.permitted_scopes IS 'Accepted Scopes by the user';

