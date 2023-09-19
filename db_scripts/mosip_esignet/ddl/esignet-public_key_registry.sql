-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : public_key_registry
-- Purpose    : Public Key Registry: Table to store Id Hash and its respective PSU Token,Public Key and Wallet Binding Id.
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
    id_hash character varying(100) NOT NULL,
    auth_factor character varying(25) NOT NULL,
	psu_token character varying(256) NOT NULL,
	public_key character varying NOT NULL,
	expire_dtimes timestamp NOT NULL,
	wallet_binding_id character varying(256) NOT NULL,
	public_key_hash character varying(100) NOT NULL,
	certificate character varying NOT NULL,
	cr_dtimes timestamp NOT NULL,
	thumbprint character varying NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (id_hash, auth_factor)
);

COMMENT ON TABLE public_key_registry IS 'Contains id_hash and their respective PSU Tokens,public keys and wallet binding ids.';

COMMENT ON COLUMN public_key_registry.id_hash IS 'Contains Id hash.';
COMMENT ON COLUMN public_key_registry.psu_token IS 'PSU Token: Partner Specific User Token.';
COMMENT ON COLUMN public_key_registry.public_key IS 'Public Key: Used to validate JWT signature and encrypt Wallet Binding Id.';
COMMENT ON COLUMN public_key_registry.expire_dtimes IS 'Expiry DateTimestamp : Date and Timestamp of the expiry of the binding entry.';
COMMENT ON COLUMN public_key_registry.wallet_binding_id IS 'Wallet Binding Id: hash of PSU  Token and salt.';
COMMENT ON COLUMN public_key_registry.public_key_hash IS 'Public Key Hash: Hash of  Public Key.';
COMMENT ON COLUMN public_key_registry.auth_factor IS 'Supported auth factor type.';
COMMENT ON COLUMN public_key_registry.certificate IS 'Signed certificate';
COMMENT ON COLUMN public_key_registry.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted.';
COMMENT ON COLUMN public_key_registry.thumbprint IS 'Thumbprint generated from the certificate'
-- ddl-end --