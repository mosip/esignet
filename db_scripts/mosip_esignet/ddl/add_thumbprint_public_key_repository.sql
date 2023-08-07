REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA esignet FROM esignetuser;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA esignet FROM sysadmin;

GRANT SELECT, INSERT, TRUNCATE, REFERENCES, UPDATE, DELETE ON ALL TABLES IN SCHEMA esignet TO esignetuser;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA esignet TO postgres;

--adding new column thumbprint to public_key_registry
ALTER TABLE public_key_registry
ADD COLUMN thumbprint varchar;
