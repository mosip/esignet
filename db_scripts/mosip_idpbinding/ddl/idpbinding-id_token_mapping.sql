-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_idpbinding
-- Table Name : id_token_mapping
-- Purpose    : id_token_mapping: Table to store Id Hash and respective PSU Tokens.
--           
-- Create By   	: Sowmya B
-- Created Date	: Nov-2022
-- 
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

-- object: id_token_mapping.id_token_mapping | type: TABLE --
-- DROP TABLE IF EXISTS id_token_mapping .id_token_mapping  CASCADE;
CREATE TABLE id_token_mapping (
	id_hash character varying(50) NOT NULL,
	psu_token character varying(256) NOT NULL,
	CONSTRAINT pk_id_token_mapping PRIMARY KEY (id_hash)
);

COMMENT ON TABLE id_token_mapping IS 'Contains Id hash and their respective PSU Tokens.';

COMMENT ON COLUMN id_token_mapping.id_hash IS 'Id Hash: Hash of individual id.';
COMMENT ON COLUMN public_key_registry.psu_token IS 'PSU Token: Partner Specific User Token.';
-- ddl-end --
