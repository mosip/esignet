INSERT INTO server_profile (profile_name, feature, additional_config_key) VALUES
('fapi2.0', 'PAR', 'require_pushed_authorization_requests'),
('fapi2.0', 'DPOP', 'dpop_bound_access_tokens'),
('fapi2.0', 'PKCE', 'require_pkce'),
('fapi2.0', 'strict_audience_check', 'client_auth_assertion_audience');
