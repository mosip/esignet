-- Run by deploy.sh from this directory against the eSignet database (SOURCE paths are resolved
-- against the mysql client's working directory).

SOURCE ddl/esignet-client_detail.sql
SOURCE ddl/esignet-key_alias.sql
SOURCE ddl/esignet-key_policy_def.sql
SOURCE ddl/esignet-key_store.sql
SOURCE ddl/esignet-public_key_registry.sql
SOURCE ddl/esignet-consent.sql
SOURCE ddl/esignet-consent_history.sql
SOURCE ddl/esignet-ca_cert_store.sql
SOURCE ddl/esignet-server_profile.sql
