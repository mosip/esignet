-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_idpbinding
-- Table Name : public_key_registry
-- Purpose    : Public Key Registry: Table to store individualId and its respective PSU Token and Public Key.
--           
-- Create By   	: Anusha S E
-- Created Date	: Nov-2022
-- 
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

-- object: public_key_registry.public_key_registry | type: TABLE --
-- DROP TABLE IF EXISTS public_key_registry.public_key_registry CASCADE;
CREATE TABLE public_key_registry(
	individual_id character varying(50) NOT NULL,
	psu_token character varying(256) NOT NULL,
	public_key character varying(50) NOT NULL,
	expires_on timestamp NOT NULL,
	cr_dtimes timestamp NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (individual_id),
	CONSTRAINT uk_public_key_registry UNIQUE (public_key)
);

COMMENT ON TABLE public_key_registry IS 'Contains individualIds and their respective PSU Tokens and public keys.';

COMMENT ON COLUMN public_key_registry.individual_id IS 'Individual ID: Unique ID of an individual.';
COMMENT ON COLUMN public_key_registry.psu_token IS 'PSU Token: Partner Specific User Token.';
COMMENT ON COLUMN public_key_registry.public_key IS 'Public Key: used for authentication/encryption.';
COMMENT ON COLUMN public_key_registry.expires_on IS 'Expiry DateTimestamp : Date and Timestamp of the token expiry';
COMMENT ON COLUMN public_key_registry.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted.';
-- ddl-end --
