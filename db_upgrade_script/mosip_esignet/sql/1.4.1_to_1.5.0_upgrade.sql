\c mosip_esignet

truncate table consent_detail;
truncate table consent_history;

--  Insert the row if it does not exist in the key_policy_def
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM esignet.KEY_POLICY_DEF WHERE APP_ID = 'MOCK_BINDING_SERVICE') THEN
        INSERT INTO esignet.KEY_POLICY_DEF (APP_ID, KEY_VALIDITY_DURATION, PRE_EXPIRE_DAYS, ACCESS_ALLOWED, IS_ACTIVE, CR_BY, CR_DTIMES)
        VALUES ('MOCK_BINDING_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now());
    END IF;
END $$;