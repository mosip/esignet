-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_idpbinding
-- Table Name : public_key_registry
-- Purpose    : Public Key Registry: Table to store PSU Token and its respective Public Key and Wallet Binding Id.
--           
-- Create By   	: Himaja D
-- Created Date	: Nov-2022
-- 
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

-- object: public_key_registry.public_key_registry | type: TABLE --
-- DROP TABLE IF EXISTS public_key_registry.public_key_registry CASCADE;
CREATE TABLE public_key_registry(
	psu_token character varying(256) NOT NULL,
	public_key character varying(50) NOT NULL,
	expire_dtimes timestamp NOT NULL,
	wallet_binding_id character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (psu_token),
	CONSTRAINT uk_public_key_registry UNIQUE (public_key)
);

COMMENT ON TABLE public_key_registry IS 'Contains PSU Token and their respective public keys and wallet binding ids.';

COMMENT ON COLUMN public_key_registry.psu_token IS 'PSU Token: Partner Specific User Token.';
COMMENT ON COLUMN public_key_registry.public_key IS 'Public Key: Used to validate JWT signature and encrypt Wallet Binding Id.';
COMMENT ON COLUMN public_key_registry.expire_dtimes IS 'Expiry DateTimestamp : Date and Timestamp of the expiry of the binding entry.';
COMMENT ON COLUMN public_key_registry.wallet_binding_id IS 'Wallet Binding Id: hash of PSU  Token and salt.';
COMMENT ON COLUMN public_key_registry.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted.';
-- ddl-end --
