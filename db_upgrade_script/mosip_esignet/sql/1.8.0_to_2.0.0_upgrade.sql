-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.

\c mosip_esignet

INSERT INTO esignet.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('BASE', 1095, 50, 'NA', true, 'mosipadmin', now());
DELETE FROM esignet.key_policy_def WHERE APP_ID='BINDING_SERVICE';
DELETE FROM esignet.key_policy_def WHERE APP_ID='MOCK_BINDING_SERVICE';

-- key_policy_def app_ids changed above, so existing key_alias entries reference policies
-- that no longer exist; truncate so keys regenerate fresh under the new app_id.
TRUNCATE TABLE esignet.key_alias;

-- Consent data migration: consent_detail structure changed in 2.0.0, so 1.8.0 consent records
-- won't parse there — back them up to a snapshot table before truncating consent_detail.
CREATE TABLE esignet.consent_detail_bkp_1_8_0 AS TABLE esignet.consent_detail;

TRUNCATE TABLE esignet.consent_detail;
