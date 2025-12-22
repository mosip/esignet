INSERT INTO key_policy_def (app_id, key_validity_duration, pre_expire_days, access_allowed, is_active, cr_by, cr_dtimes) VALUES
('ROOT', 2920, 1125, 'NA', TRUE, 'mosipadmin', NOW()),
('OIDC_SERVICE', 1095, 60, 'NA', TRUE, 'mosipadmin', NOW()),
('OIDC_PARTNER', 1095, 60, 'NA', TRUE, 'mosipadmin', NOW()),
('BINDING_SERVICE', 1095, 60, 'NA', TRUE, 'mosipadmin', NOW()),
('MOCK_BINDING_SERVICE', 1095, 50, 'NA', TRUE, 'mosipadmin', NOW());