-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.

\c mosip_esignet

DO $$
BEGIN

-- Remove strict_audience_check feature added in 1.8.1 for fapi2.0 profile
DELETE FROM server_profile
WHERE profile_name = 'fapi2.0'
  AND feature = 'strict_audience_check'
  AND additional_config_key = 'client_auth_assertion_audience';

END;
$$;

