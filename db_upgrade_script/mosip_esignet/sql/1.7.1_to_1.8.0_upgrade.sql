-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.

\c mosip_esignet

DO $$
BEGIN

CREATE TABLE IF NOT EXISTS ca_cert_store(
	cert_id varchar(36) NOT NULL,
	cert_subject varchar(500) NOT NULL,
	cert_issuer varchar(500) NOT NULL,
	issuer_id varchar(36) NOT NULL,
	cert_not_before timestamp,
	cert_not_after timestamp,
	crl_uri varchar(120),
	cert_data varchar(4000),
	cert_thumbprint varchar(100),
	cert_serial_no varchar(50),
	partner_domain varchar(36),
	cr_by varchar(256),
	cr_dtimes timestamp,
	upd_by varchar(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp,
	ca_cert_type varchar(25),
	CONSTRAINT pk_cacs_id PRIMARY KEY (cert_id),
	CONSTRAINT cert_thumbprint_unique UNIQUE (cert_thumbprint,partner_domain)
);


-- Client Detail migration

-- Create a function to compute SHA-256 hash for public key
CREATE OR REPLACE FUNCTION compute_public_key_hash(jwk_data jsonb)
RETURNS varchar AS $_func$
DECLARE
    key_type varchar;
    data_to_hash varchar;
    n_value varchar;
    x_value varchar;
    y_value varchar;
BEGIN
    key_type := jwk_data->>'kty';

    IF key_type IS NULL THEN
        RAISE EXCEPTION 'Missing kty field in JWK';
    END IF;

    -- Compute hash based on key type
    IF key_type = 'RSA' THEN
        n_value := jwk_data->>'n';
        IF n_value IS NULL THEN
            RAISE EXCEPTION 'Missing n field in RSA JWK';
        END IF;
        data_to_hash := n_value;
    ELSIF key_type = 'EC' THEN
        x_value := jwk_data->>'x';
        y_value := jwk_data->>'y';
        IF x_value IS NULL OR y_value IS NULL THEN
            RAISE EXCEPTION 'Missing x or y field in EC JWK';
        END IF;
        data_to_hash := x_value || y_value;
    ELSE
        RAISE EXCEPTION 'Unsupported key type: %', key_type;
    END IF;

    RETURN encode(sha256(data_to_hash::bytea), 'hex');
END;
$_func$ LANGUAGE plpgsql;

DROP INDEX IF EXISTS unique_n_value;

-- Add and backfill public_key_hash
ALTER TABLE client_detail ADD COLUMN IF NOT EXISTS public_key_hash varchar(128);

UPDATE client_detail
SET public_key_hash = compute_public_key_hash(public_key::jsonb)
WHERE public_key_hash IS NULL AND public_key IS NOT NULL;

-- Add unique constraint on public_key_hash
ALTER TABLE client_detail ADD CONSTRAINT uk_clntdtl_public_key_hash UNIQUE (public_key_hash);

-- Migrate public_key from jsonb to varchar(1024)
ALTER TABLE client_detail
    ALTER COLUMN public_key TYPE varchar(1024)
    USING public_key::text;

ALTER TABLE client_detail ALTER COLUMN public_key_hash SET NOT NULL;

-- Align other columns to target lengths and nullability
ALTER TABLE client_detail ALTER COLUMN redirect_uris TYPE varchar(2048);
ALTER TABLE client_detail ALTER COLUMN claims TYPE varchar(2048);
ALTER TABLE client_detail ALTER COLUMN acr_values TYPE varchar(1024);
ALTER TABLE client_detail ALTER COLUMN grant_types TYPE varchar(512);
ALTER TABLE client_detail ALTER COLUMN auth_methods TYPE varchar(512);

-- Migrate additional_config from jsonb to varchar(2048)
ALTER TABLE client_detail
    ALTER COLUMN additional_config TYPE varchar(2048)
    USING additional_config::text;

-- Drop helper function
DROP FUNCTION IF EXISTS compute_public_key_hash(jsonb);

-- Consent Detail migration

ALTER TABLE consent_detail ALTER COLUMN id TYPE varchar(36);
ALTER TABLE consent_detail ALTER COLUMN client_id TYPE varchar(256);
ALTER TABLE consent_detail ALTER COLUMN psu_token TYPE varchar(256);
ALTER TABLE consent_detail ALTER COLUMN claims TYPE varchar(1024);
ALTER TABLE consent_detail ALTER COLUMN authorization_scopes TYPE varchar(1024);
ALTER TABLE consent_detail ALTER COLUMN signature TYPE varchar(1024);
ALTER TABLE consent_detail ALTER COLUMN hash TYPE varchar(1024);
ALTER TABLE consent_detail ALTER COLUMN accepted_claims TYPE varchar(1024);
ALTER TABLE consent_detail ALTER COLUMN permitted_scopes TYPE varchar(1024);

-- Consent History migration

ALTER TABLE consent_history ALTER COLUMN id TYPE varchar(36);
ALTER TABLE consent_history ALTER COLUMN client_id TYPE varchar(256);
ALTER TABLE consent_history ALTER COLUMN psu_token TYPE varchar(256);
ALTER TABLE consent_history ALTER COLUMN claims TYPE varchar(1024);
ALTER TABLE consent_history ALTER COLUMN authorization_scopes TYPE varchar(1024);
ALTER TABLE consent_history ALTER COLUMN signature TYPE varchar(1024);
ALTER TABLE consent_history ALTER COLUMN hash TYPE varchar(1024);
ALTER TABLE consent_history ALTER COLUMN accepted_claims TYPE varchar(1024);
ALTER TABLE consent_history ALTER COLUMN permitted_scopes TYPE varchar(1024);

-- Key Store migration

ALTER TABLE key_store ALTER COLUMN certificate_data TYPE varchar(4000);

-- Public Key Registry migration

ALTER TABLE public_key_registry ALTER COLUMN public_key TYPE varchar(2500);
ALTER TABLE public_key_registry ALTER COLUMN certificate TYPE varchar(4000);
ALTER TABLE public_key_registry ALTER COLUMN thumbprint TYPE varchar(128);

END;
$$;
