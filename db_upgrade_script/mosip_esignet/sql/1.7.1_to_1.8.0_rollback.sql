-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.

\c mosip_esignet

DO $$
BEGIN

-- 1. Drop table created in 1.8.0
DROP TABLE IF EXISTS ca_cert_store;

-- 2. Revert client_detail changes

-- Drop unique constraint and column public_key_hash
ALTER TABLE client_detail DROP CONSTRAINT uk_clntdtl_public_key_hash;
ALTER TABLE client_detail DROP COLUMN public_key_hash;

-- Convert public_key back from varchar(1024) text representation to jsonb
ALTER TABLE client_detail
    ALTER COLUMN public_key TYPE jsonb
    USING public_key::jsonb;
CREATE UNIQUE INDEX unique_n_value ON client_detail ((public_key->>'n'));

-- Revert length changes
ALTER TABLE client_detail ALTER COLUMN redirect_uris TYPE varchar;
ALTER TABLE client_detail ALTER COLUMN claims TYPE varchar;
ALTER TABLE client_detail ALTER COLUMN acr_values TYPE varchar;
ALTER TABLE client_detail ALTER COLUMN grant_types TYPE varchar;
ALTER TABLE client_detail ALTER COLUMN auth_methods TYPE varchar;

-- Revert additional_config back to jsonb from varchar(2048)
ALTER TABLE client_detail
    ALTER COLUMN additional_config TYPE jsonb
    USING additional_config::jsonb;

-- 3. Revert consent_detail column type changes
ALTER TABLE consent_detail ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE consent_detail ALTER COLUMN client_id TYPE varchar;
ALTER TABLE consent_detail ALTER COLUMN psu_token TYPE varchar;
ALTER TABLE consent_detail ALTER COLUMN claims TYPE varchar;
ALTER TABLE consent_detail ALTER COLUMN authorization_scopes TYPE varchar;
ALTER TABLE consent_detail ALTER COLUMN signature TYPE varchar;
ALTER TABLE consent_detail ALTER COLUMN hash TYPE varchar;
ALTER TABLE consent_detail ALTER COLUMN accepted_claims TYPE varchar;
ALTER TABLE consent_detail ALTER COLUMN permitted_scopes TYPE varchar;

-- 4. Revert consent_history column type changes
ALTER TABLE consent_history ALTER COLUMN id TYPE uuid USING id::uuid;
ALTER TABLE consent_history ALTER COLUMN client_id TYPE varchar;
ALTER TABLE consent_history ALTER COLUMN psu_token TYPE varchar;
ALTER TABLE consent_history ALTER COLUMN claims TYPE varchar;
ALTER TABLE consent_history ALTER COLUMN authorization_scopes TYPE varchar;
ALTER TABLE consent_history ALTER COLUMN signature TYPE varchar;
ALTER TABLE consent_history ALTER COLUMN hash TYPE varchar;
ALTER TABLE consent_history ALTER COLUMN accepted_claims TYPE varchar;
ALTER TABLE consent_history ALTER COLUMN permitted_scopes TYPE varchar;

-- 5. Revert key_store column length change
ALTER TABLE key_store ALTER COLUMN certificate_data TYPE varchar;

-- 6. Revert public_key_registry column length changes
ALTER TABLE public_key_registry ALTER COLUMN public_key TYPE varchar;
ALTER TABLE public_key_registry ALTER COLUMN certificate TYPE varchar;
ALTER TABLE public_key_registry ALTER COLUMN thumbprint TYPE varchar;

END;
$$;
