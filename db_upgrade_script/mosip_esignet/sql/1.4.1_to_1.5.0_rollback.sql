\c mosip_esignet

-- Delete the existing entry for MOCK_BINDING_SERVICE from the key_policy_def
DELETE FROM esignet.KEY_POLICY_DEF WHERE APP_ID = 'MOCK_BINDING_SERVICE';