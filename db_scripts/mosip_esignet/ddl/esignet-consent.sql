-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : consent
-- Purpose    : To store user consent details
--
-- Create By   	: Hitesh C
-- Created Date	: May-2023
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

create table consent (
    id UUID NOT NULL,
    client_id VARCHAR NOT NULL,
    psu_value VARCHAR NOT NULL,
    claims VARCHAR NOT NULL,
    authorization_scopes VARCHAR NOT NULL,
    created_on TIMESTAMP DEFAULT NOW() NOT NULL,
    expiration TIMESTAMP,
    signature VARCHAR,
    hash VARCHAR,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_consent_psu_client ON consent(psu_value, client_id);

COMMENT ON TABLE consent IS 'Contains user consent details';

COMMENT ON COLUMN consent.id IS 'UUID : Unique id associated with each consent';
COMMENT ON COLUMN consent.client_id IS 'Client_id: associated with ';
COMMENT ON COLUMN consent.psu_value IS 'PSU value associated with user consent';
COMMENT ON COLUMN consent.claims IS 'Json of requested and user accepted claims';
COMMENT ON COLUMN consent.authorization_scopes IS 'Json string of user accepted authorization scope';
COMMENT ON COLUMN consent.created_on IS 'Consent creation date';
COMMENT ON COLUMN consent.expiration IS 'Expiration date';
COMMENT ON COLUMN consent.signature IS 'Signature of consent object ';
COMMENT ON COLUMN consent.hash IS 'hash of consent object';

