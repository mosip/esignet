-- Seed data, loaded into a freshly created database (no truncation is needed or performed).
-- Run by deploy.sh from this directory against the eSignet database.

-- NOW() in the seed rows is evaluated in the session time zone; pin it to UTC.
SET time_zone = '+00:00';

SOURCE dml/esignet-key_policy_def.sql
SOURCE dml/esignet-server_profile.sql
