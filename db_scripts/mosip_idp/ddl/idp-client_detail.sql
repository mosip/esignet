-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_idp
-- Table Name : client_detail
-- Purpose    : Client Detail: Table to store all registered OIDC client details.
--           
-- Create By   	: Anusha S E
-- Created Date	: Aug-2022
-- 
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

-- object: client_detail.client_detail | type: TABLE --
-- DROP TABLE IF EXISTS client_detail.client_detail CASCADE;
CREATE TABLE client_detail(
	id character varying(50) NOT NULL,
	name character varying(256) NOT NULL,
	rp_id character varying(50) NOT NULL,
	logo_uri character varying(256) NOT NULL,
	redirect_uris character varying NOT NULL,
	claims character varying NOT NULL,
	acr_values character varying NOT NULL,
	public_key character varying NOT NULL,
	grant_types character varying(256) NOT NULL,
	auth_methods character varying(256) NOT NULL,
	status character varying(20) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_dtimes timestamp,
	CONSTRAINT pk_client_detail PRIMARY KEY (id)
);

COMMENT ON TABLE client_detail.id IS 'Client ID: Unique id assigned to registered OIDC client.';
COMMENT ON COLUMN client_detail.name IS 'Client Name: Registered name of OIDC client.';
COMMENT ON COLUMN client_detail.logo_uri IS 'Client Logo URL: Client logo to be displayed on IDP UI.';
COMMENT ON COLUMN client_detail.redirect_uris IS 'Recirect URLS: Comma separated client redirect URLs.';
COMMENT ON COLUMN client_detail.rp_id IS 'Relaying Party Id: Id of the Relaying Party who has created this OIDC client.';
COMMENT ON COLUMN client_detail.status IS 'Client status: Allowed values - ACTIVE / INACTIVE.';
COMMENT ON COLUMN client_detail.public_key IS 'Public key: JWK format.';
COMMENT ON COLUMN client_detail.grant_types IS 'Grant Types: Allowed grant types for the client, comma separated string.';
COMMENT ON COLUMN client_detail.auth_methods IS 'Client Auth methods: Allowed token endpoint authentication methods, comma separated string.';
COMMENT ON COLUMN client_detail.claims IS 'Requested Claims: claims json as per policy defined for relaying party, comma separated string.';
COMMENT ON COLUMN client_detail.acr_values IS 'Allowed Authentication context References(acr), comma separated string.';
COMMENT ON COLUMN key_policy_def.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
COMMENT ON COLUMN key_policy_def.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- ddl-end --
