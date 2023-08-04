REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM audituser;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM sysadmin;

GRANT SELECT, INSERT, TRUNCATE, REFERENCES, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO audituser;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;

--adding new column thumbprint to public_key_registry
ALTER TABLE public_key_registry
ADD COLUMN thumbprint varchar;
