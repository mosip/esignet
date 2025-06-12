-- MySQL version of mosip_esignet database schema

-- Create DATABASE mosip_esignet
-- The ENCODING, LC_COLLATE, LC_CTYPE, TABLESPACE, OWNER, TEMPLATE clauses are PostgreSQL-specific and removed.
CREATE DATABASE IF NOT EXISTS mosip_esignet
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

-- Comment on database is not directly supported in MySQL CREATE DATABASE like PostgreSQL.
-- You can add a comment to the database in MySQL after creation, but it's often omitted in init scripts.
-- For example: ALTER DATABASE mosip_esignet COMMENT 'e-Signet related data is stored in this database';

-- Use the mosip_esignet database
USE mosip_esignet;

-- DROP SCHEMA and CREATE SCHEMA are PostgreSQL-specific concepts.
-- In MySQL, tables are directly created within the selected database.
-- ALTER SCHEMA and ALTER DATABASE SET search_path are also PostgreSQL-specific and are removed.

-- Table: client_detail
CREATE TABLE IF NOT EXISTS client_detail (
  id VARCHAR(100) NOT NULL PRIMARY KEY,
  name VARCHAR(600) NOT NULL,
  rp_id VARCHAR(100) NOT NULL,
  logo_uri VARCHAR(2048) NOT NULL,
  redirect_uris TEXT NOT NULL, -- character varying without length becomes TEXT
  claims TEXT NOT NULL, -- character varying without length becomes TEXT
  acr_values TEXT NOT NULL, -- character varying without length becomes TEXT
  public_key JSON NOT NULL, -- jsonb becomes JSON
  grant_types TEXT NOT NULL, -- character varying without length becomes TEXT
  auth_methods TEXT NOT NULL, -- character varying without length becomes TEXT
  status VARCHAR(20) NOT NULL,
  additional_config JSON, -- jsonb becomes JSON
  cr_dtimes DATETIME NOT NULL, -- timestamp becomes DATETIME
  upd_dtimes DATETIME, -- timestamp becomes DATETIME
  public_key_n VARCHAR(64) GENERATED ALWAYS AS (SHA2(JSON_UNQUOTE(JSON_EXTRACT(public_key, '$.n')), 256)) STORED,
  UNIQUE INDEX idx_public_key_n (public_key_n)
);

-- PostgreSQL unique index on JSON field expression ((public_key->>'n'))
-- MySQL equivalent often involves a generated column or a functional index (MySQL 8+)
-- For simplicity and broad compatibility, if 'n' is often used, you might extract it into a separate column.
-- If public_key->>'n' extracts a string, VARCHAR is appropriate.
-- For MySQL 8+, you can create functional index if needed
-- CREATE UNIQUE INDEX unique_n_value ON client_detail (JSON_UNQUOTE(JSON_EXTRACT(public_key, '$.n')));


-- Table: consent_detail
CREATE TABLE IF NOT EXISTS consent_detail (
    id BINARY(16) NOT NULL PRIMARY KEY, -- MySQL's preferred way to store UUIDs efficiently is as BINARY(16)
    client_id VARCHAR(255) NOT NULL, -- VARCHAR is typical for VARCHAR without length
    psu_token VARCHAR(255) NOT NULL,
    claims TEXT NOT NULL, -- VARCHAR without length becomes TEXT
    authorization_scopes TEXT NOT NULL, -- VARCHAR without length becomes TEXT
    cr_dtimes DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- TIMESTAMP DEFAULT NOW() becomes DATETIME DEFAULT CURRENT_TIMESTAMP
    expire_dtimes DATETIME, -- TIMESTAMP becomes DATETIME
    signature TEXT, -- VARCHAR without length becomes TEXT
    hash TEXT, -- VARCHAR without length becomes TEXT
    accepted_claims TEXT, -- VARCHAR without length becomes TEXT
    permitted_scopes TEXT, -- VARCHAR without length becomes TEXT
    CONSTRAINT unique_client_token UNIQUE (client_id, psu_token)
);

-- CREATE INDEX IF NOT EXISTS idx_consent_psu_client ON consent_detail(psu_token, client_id);

-- Table: consent_history
CREATE TABLE IF NOT EXISTS consent_history (
    id BINARY(16) NOT NULL PRIMARY KEY, -- MySQL's preferred way to store UUIDs efficiently is as BINARY(16)
    client_id VARCHAR(255) NOT NULL,
    psu_token VARCHAR(255) NOT NULL,
    claims TEXT NOT NULL,
    authorization_scopes TEXT NOT NULL,
    cr_dtimes DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL, -- TIMESTAMP DEFAULT NOW() becomes DATETIME DEFAULT CURRENT_TIMESTAMP
    expire_dtimes DATETIME,
    signature TEXT,
    hash TEXT,
    accepted_claims TEXT,
    permitted_scopes TEXT
);

-- Table: key_alias
CREATE TABLE IF NOT EXISTS key_alias (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    ref_id VARCHAR(128),
    key_gen_dtimes DATETIME,
    key_expire_dtimes DATETIME,
    status_code VARCHAR(36),
    lang_code VARCHAR(3),
    cr_by VARCHAR(256) NOT NULL,
    cr_dtimes DATETIME NOT NULL,
    upd_by VARCHAR(256),
    upd_dtimes DATETIME,
    is_deleted TINYINT(1) DEFAULT 0, -- boolean becomes TINYINT(1), FALSE becomes 0
    del_dtimes DATETIME,
    cert_thumbprint VARCHAR(100),
    uni_ident VARCHAR(50),
    CONSTRAINT uni_ident_const UNIQUE (uni_ident)
);

-- Table: key_policy_def
CREATE TABLE IF NOT EXISTS key_policy_def (
    app_id VARCHAR(36) NOT NULL PRIMARY KEY,
    key_validity_duration SMALLINT,
    is_active TINYINT(1) NOT NULL, -- boolean becomes TINYINT(1)
    pre_expire_days SMALLINT,
    access_allowed VARCHAR(1024),
    cr_by VARCHAR(256) NOT NULL,
    cr_dtimes DATETIME NOT NULL,
    upd_by VARCHAR(256),
    upd_dtimes DATETIME,
    del_dtimes DATETIME,
    is_deleted TINYINT(1) DEFAULT 0 -- boolean becomes TINYINT(1), FALSE becomes 0
);

-- Table: key_store
CREATE TABLE IF NOT EXISTS key_store (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  master_key VARCHAR(36) NOT NULL,
  private_key VARCHAR(2500) NOT NULL,
  certificate_data TEXT NOT NULL, -- character varying without length becomes TEXT
  cr_by VARCHAR(256) NOT NULL,
  cr_dtimes DATETIME NOT NULL,
  upd_by VARCHAR(256),
  upd_dtimes DATETIME,
  is_deleted TINYINT(1) DEFAULT 0, -- boolean becomes TINYINT(1), FALSE becomes 0
  del_dtimes DATETIME
);

-- Table: public_key_registry
CREATE TABLE IF NOT EXISTS public_key_registry (
    id_hash VARCHAR(100) NOT NULL,
    auth_factor VARCHAR(25) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    public_key TEXT NOT NULL, -- character varying without length becomes TEXT
    expire_dtimes DATETIME NOT NULL,
    wallet_binding_id VARCHAR(256) NOT NULL,
    public_key_hash VARCHAR(100) NOT NULL,
    certificate TEXT NOT NULL, -- character varying without length becomes TEXT
    cr_dtimes DATETIME NOT NULL,
    thumbprint TEXT NOT NULL, -- character varying without length becomes TEXT
    CONSTRAINT pk_public_key_registry PRIMARY KEY (id_hash, auth_factor)
);

-- Insert statements remain largely the same, 'now()' is acceptable in MySQL
INSERT INTO key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('ROOT', 2920, 1125, 'NA', 1, 'mosipadmin', now()); -- true becomes 1
INSERT INTO key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('OIDC_SERVICE', 1095, 50, 'NA', 1, 'mosipadmin', now()); -- true becomes 1
INSERT INTO key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('OIDC_PARTNER', 1095, 50, 'NA', 1, 'mosipadmin', now()); -- true becomes 1
INSERT INTO key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('BINDING_SERVICE', 1095, 50, 'NA', 1, 'mosipadmin', now()); -- true becomes 1
INSERT INTO key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('MOCK_BINDING_SERVICE', 1095, 50, 'NA', 1, 'mosipadmin', now()); -- true becomes 1


-- MySQL version of mosip_mockidentitysystem database schema

-- Create DATABASE mosip_mockidentitysystem
CREATE DATABASE IF NOT EXISTS mosip_mockidentitysystem
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

-- Comment on database is not directly supported in MySQL CREATE DATABASE like PostgreSQL.
-- For example: ALTER DATABASE mosip_mockidentitysystem COMMENT 'Mock identity related data is stored in this database';

-- Use the mosip_mockidentitysystem database
USE mosip_mockidentitysystem;

-- DROP SCHEMA and CREATE SCHEMA are PostgreSQL-specific and removed.
-- ALTER SCHEMA and ALTER DATABASE SET search_path are also PostgreSQL-specific and removed.

-- Table: key_alias
CREATE TABLE IF NOT EXISTS key_alias (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    ref_id VARCHAR(128),
    key_gen_dtimes DATETIME,
    key_expire_dtimes DATETIME,
    status_code VARCHAR(36),
    lang_code VARCHAR(3),
    cr_by VARCHAR(256) NOT NULL,
    cr_dtimes DATETIME NOT NULL,
    upd_by VARCHAR(256),
    upd_dtimes DATETIME,
    is_deleted TINYINT(1) DEFAULT 0, -- boolean becomes TINYINT(1), FALSE becomes 0
    del_dtimes DATETIME,
    cert_thumbprint VARCHAR(100),
    uni_ident VARCHAR(50),
    CONSTRAINT uni_ident_const UNIQUE (uni_ident)
);

-- Table: key_policy_def
CREATE TABLE IF NOT EXISTS key_policy_def (
    app_id VARCHAR(36) NOT NULL PRIMARY KEY,
    key_validity_duration SMALLINT,
    is_active TINYINT(1) NOT NULL, -- boolean becomes TINYINT(1)
    pre_expire_days SMALLINT,
    access_allowed VARCHAR(1024),
    cr_by VARCHAR(256) NOT NULL,
    cr_dtimes DATETIME NOT NULL,
    upd_by VARCHAR(256),
    upd_dtimes DATETIME,
    is_deleted TINYINT(1) DEFAULT 0 -- boolean becomes TINYINT(1), FALSE becomes 0
);

-- Table: key_store
CREATE TABLE IF NOT EXISTS key_store (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  master_key VARCHAR(36) NOT NULL,
  private_key VARCHAR(2500) NOT NULL,
  certificate_data TEXT NOT NULL, -- character varying without length becomes TEXT
  cr_by VARCHAR(256) NOT NULL,
  cr_dtimes DATETIME NOT NULL,
  upd_by VARCHAR(256),
  upd_dtimes DATETIME,
  is_deleted TINYINT(1) DEFAULT 0, -- boolean becomes TINYINT(1), FALSE becomes 0
  del_dtimes DATETIME
);

-- Table: kyc_auth
CREATE TABLE IF NOT EXISTS kyc_auth (
    kyc_token VARCHAR(255),
    individual_id VARCHAR(255),
    partner_specific_user_token VARCHAR(255),
    response_time DATETIME, -- TIMESTAMP becomes DATETIME
    transaction_id VARCHAR(255),
    validity INTEGER
);

-- Table: mock_identity
CREATE TABLE IF NOT EXISTS mock_identity (
  individual_id VARCHAR(36) NOT NULL PRIMARY KEY,
  identity_json TEXT NOT NULL -- VARCHAR without length becomes TEXT
);

-- Table: verified_claim
CREATE TABLE IF NOT EXISTS verified_claim (
    id VARCHAR(100) NOT NULL PRIMARY KEY,
	individual_id VARCHAR(36) NOT NULL,
	claim TEXT NOT NULL, -- VARCHAR without length becomes TEXT
	trust_framework TEXT NOT NULL, -- VARCHAR without length becomes TEXT
	detail TEXT, -- VARCHAR without length becomes TEXT
	cr_by VARCHAR(256) NOT NULL,
    cr_dtimes DATETIME NOT NULL,
    upd_by VARCHAR(256),
    upd_dtimes DATETIME,
    is_active TINYINT(1) DEFAULT 1 -- boolean becomes TINYINT(1), TRUE becomes 1
);

INSERT INTO key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('ROOT', 2920, 1125, 'NA', 1, 'mosipadmin', now()); -- true becomes 1
INSERT INTO key_policy_def(APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES) VALUES('MOCK_AUTHENTICATION_SERVICE', 1095, 50, 'NA', 1, 'mosipadmin', now()); -- true becomes 1