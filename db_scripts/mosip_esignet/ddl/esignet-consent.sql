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

create table consent_detail (
    id UUID NOT NULL,
    client_id VARCHAR NOT NULL,
    psu_token VARCHAR NOT NULL,
    claims VARCHAR NOT NULL,
    authorization_scopes VARCHAR NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT NOW() NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR,
    hash VARCHAR,
    accepted_claims VARCHAR,
    permitted_scopes VARCHAR,
    PRIMARY KEY (id),
    CONSTRAINT unique_client_token UNIQUE (client_id, psu_token)
);

CREATE INDEX IF NOT EXISTS idx_consent_psu_client ON consent_detail(psu_token, client_id);

COMMENT ON TABLE consent_detail IS 'Contains user consent details';

COMMENT ON COLUMN consent_detail.id IS 'UUID : Unique id associated with each consent';
COMMENT ON COLUMN consent_detail.client_id IS 'Client_id: associated with relying party';
COMMENT ON COLUMN consent_detail.psu_token IS 'PSU token associated with user consent';
COMMENT ON COLUMN consent_detail.claims IS 'Json of requested and user accepted claims';
COMMENT ON COLUMN consent_detail.authorization_scopes IS 'Json string of requested authorization scope';
COMMENT ON COLUMN consent_detail.cr_dtimes IS 'Consent creation date';
COMMENT ON COLUMN consent_detail.expire_dtimes IS 'Expiration date';
COMMENT ON COLUMN consent_detail.signature IS 'Signature of consent object ';
COMMENT ON COLUMN consent_detail.hash IS 'hash of consent object';
COMMENT ON COLUMN consent_detail.accepted_claims IS 'Accepted Claims by the user';
COMMENT ON COLUMN consent_detail.permitted_scopes IS 'Accepted Scopes by the user';

