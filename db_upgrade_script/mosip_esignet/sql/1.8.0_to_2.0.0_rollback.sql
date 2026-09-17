-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.

\c mosip_esignet

DELETE FROM esignet.key_policy_def WHERE APP_ID='BASE';
INSERT INTO esignet.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('BINDING_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now());
INSERT INTO esignet.key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('MOCK_BINDING_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now());

-- key_policy_def app_ids reverted above, so existing key_alias entries reference policies
-- that no longer exist; truncate so keys regenerate fresh under the restored app_id.
TRUNCATE TABLE esignet.key_alias;

-- Restore consent_detail from the pre-upgrade snapshot, then drop the snapshot table.
TRUNCATE TABLE esignet.consent_detail;
INSERT INTO esignet.consent_detail SELECT * FROM esignet.consent_detail_bkp_1_8_0;
DROP TABLE IF EXISTS esignet.consent_detail_bkp_1_8_0;
