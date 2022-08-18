-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_idp
-- Table Name : idp.client_detail
-- Purpose    : Client Detail: Table to store all registered OIDC client details.
--           
-- Create By   	: Anusha S E
-- Created Date	: Aug-2022
-- 
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------

-- object: idp.client_detail | type: TABLE --
-- DROP TABLE IF EXISTS idp.client_detail CASCADE;
CREATE TABLE idp.client_detail(
	id character varying(50) NOT NULL,
	name character varying(256) NOT NULL,
	rp_id character varying(50) NOT NULL,
	logo_uri character varying(256) NOT NULL,
	redirect_uris character varying NOT NULL,
	claims character varying NOT NULL,
	acr_values character varying NOT NULL,
	public_key character varying NOT NULL,
	grant_types character varying(256) NOT NULL,
	status character varying(20) NOT NULL,
	CONSTRAINT pk_client_detail PRIMARY KEY (id)
);
-- ddl-end --
COMMENT ON TABLE idp.id IS 'Client ID: Unique id assigned to registered OIDC client.';
-- ddl-end --
COMMENT ON COLUMN idp.name IS 'Client Name: Registered name of OIDC client.';
-- ddl-end --
COMMENT ON COLUMN idp.logo_uri IS 'Client Logo URL: Client logo to be displayed on IDP UI.';
-- ddl-end --
COMMENT ON COLUMN idp.redirect_uris IS 'Recirect URLS: Comma separated list of client redirect URLs.';
-- ddl-end --
COMMENT ON COLUMN idp.rp_id IS 'Relaying Party Id: Id of the Relaying Party who has created this OIDC client.';
-- ddl-end --
COMMENT ON COLUMN idp.status IS 'Client status: Allowed values - ACTIVE / INACTIVE.';
-- ddl-end --
COMMENT ON COLUMN idp.public_key IS 'Public key: JWKs data.';
-- ddl-end --
COMMENT ON COLUMN idp.grant_types IS 'Grant Types: Allowed grant types for the client.';
-- ddl-end --
COMMENT ON COLUMN idp.claims IS 'Requested Claims: claims json as per policy defined for relaying party.';
-- ddl-end --
COMMENT ON COLUMN idp.acr_values IS 'Allowed Authentication context References(acr) json';
-- ddl-end --

