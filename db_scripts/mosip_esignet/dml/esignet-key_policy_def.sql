INSERT INTO key_policy_def (app_id, key_validity_duration, pre_expire_days, access_allowed, is_active, cr_by, cr_dtimes) VALUES
('ROOT', 2920, 1125, 'NA', TRUE, 'mosipadmin', NOW()),
('OIDC_SERVICE', 1095, 60, 'NA', TRUE, 'mosipadmin', NOW()),
('OIDC_PARTNER', 1095, 60, 'NA', TRUE, 'mosipadmin', NOW()),
-- BASE governs every Component Encryption Key (any application) and every
-- symmetric/AES key, regardless of which application owns them — see
-- esignet-service/internal/keymanager/service.go's policyForKeyTier. Without
-- this row, generating a Component Encryption Key or resolving an existing
-- symmetric key fails with a policy-not-found error.
('BASE', 1095, 60, 'NA', TRUE, 'mosipadmin', NOW());