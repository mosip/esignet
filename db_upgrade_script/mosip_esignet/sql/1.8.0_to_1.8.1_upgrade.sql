-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.

\c mosip_esignet

DO $$
BEGIN

-- Add strict_audience_check feature for fapi2.0 profile
-- This enables strict audience validation for client assertion JWT, allowing only issuer as valid audience
INSERT INTO server_profile(profile_name, feature, additional_config_key)
VALUES ('fapi2.0', 'strict_audience_check', 'client_auth_assertion_audience')
ON CONFLICT (profile_name, feature) DO NOTHING;

END;
$$;

