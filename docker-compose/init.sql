CREATE DATABASE mosip_esignet
  ENCODING = 'UTF8' 
  LC_COLLATE = 'en_US.UTF-8' 
  LC_CTYPE = 'en_US.UTF-8' 
  TABLESPACE = pg_default 
  OWNER = postgres
  TEMPLATE  = template0;

COMMENT ON DATABASE mosip_esignet IS 'e-Signet related data is stored in this database';

\c mosip_esignet postgres

DROP SCHEMA IF EXISTS esignet CASCADE;
CREATE SCHEMA esignet;
ALTER SCHEMA esignet OWNER TO postgres;
ALTER DATABASE mosip_esignet SET search_path TO esignet,pg_catalog,public;

CREATE TABLE esignet.client_detail(
	id varchar(100) NOT NULL,
	name varchar(600) NOT NULL,
	rp_id varchar(100) NOT NULL,
	logo_uri varchar(2048) NOT NULL,
	redirect_uris varchar(2048) NOT NULL,
	claims varchar(2048) NOT NULL,
	acr_values varchar(1024) NOT NULL,
	public_key varchar(1024) NOT NULL,
	public_key_hash varchar(128) NOT NULL,
	enc_public_key varchar(1024),
	enc_public_key_hash varchar(128),
	enc_public_key_cert varchar(4000),
	grant_types varchar(512) NOT NULL,
	auth_methods varchar(512) NOT NULL,
	status varchar(20) NOT NULL,
	additional_config varchar(2048),
	cr_dtimes timestamp NOT NULL,
	upd_dtimes timestamp,
	CONSTRAINT pk_clntdtl_id PRIMARY KEY (id),
	CONSTRAINT uk_clntdtl_public_key_hash UNIQUE (public_key_hash)
);

CREATE TABLE esignet.consent_detail (
    id VARCHAR(36) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    claims VARCHAR(2048) NOT NULL,
    authorization_scopes VARCHAR(1024) NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR(1024),
    hash VARCHAR(100),
    accepted_claims VARCHAR(1024),
    permitted_scopes VARCHAR(1024),
    PRIMARY KEY (id),
    CONSTRAINT unique_client_token UNIQUE (client_id, psu_token)
);

CREATE INDEX idx_consent_psu_client ON esignet.consent_detail(psu_token, client_id);

create table esignet.consent_history (
    id varchar(36) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    claims VARCHAR(2048) NOT NULL,
    authorization_scopes VARCHAR(1024) NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR(1024),
    hash VARCHAR(1024),
    accepted_claims VARCHAR(1024),
    permitted_scopes VARCHAR(1024),
    PRIMARY KEY (id)
);
CREATE INDEX idx_consent_history_psu_client ON esignet.consent_history(psu_token, client_id);

CREATE TABLE esignet.key_alias(
    id varchar(36) NOT NULL,
    app_id varchar(36) NOT NULL,
    ref_id varchar(128),
    key_gen_dtimes timestamp,
    key_expire_dtimes timestamp,
    status_code varchar(36),
    lang_code varchar(3),
    cr_by varchar(256) NOT NULL,
    cr_dtimes timestamp NOT NULL,
    upd_by varchar(256),
    upd_dtimes timestamp,
    is_deleted boolean DEFAULT FALSE,
    del_dtimes timestamp,
    cert_thumbprint varchar(100),
    uni_ident varchar(50),
    CONSTRAINT pk_keymals_id PRIMARY KEY (id),
    CONSTRAINT uni_ident_const UNIQUE (uni_ident)
);

CREATE TABLE esignet.key_policy_def(
    app_id varchar(36) NOT NULL,
    key_validity_duration smallint,
    is_active boolean NOT NULL,
    pre_expire_days smallint,
    access_allowed varchar(1024),
    cr_by varchar(256) NOT NULL,
    cr_dtimes timestamp NOT NULL,
    upd_by varchar(256),
    upd_dtimes timestamp,
    is_deleted boolean DEFAULT FALSE,
    del_dtimes timestamp,
    CONSTRAINT pk_keypdef_id PRIMARY KEY (app_id)
);

CREATE TABLE esignet.key_store(
	id varchar(36) NOT NULL,
	master_key varchar(36) NOT NULL,
	private_key varchar(2500) NOT NULL,
	certificate_data varchar(4000) NOT NULL,
	cr_by varchar(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by varchar(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp,
	CONSTRAINT pk_keystr_id PRIMARY KEY (id)
);

CREATE TABLE esignet.public_key_registry(
    id_hash varchar(100) NOT NULL,
    auth_factor varchar(25) NOT NULL,
	psu_token varchar(256) NOT NULL,
	public_key varchar(2500) NOT NULL,
	expire_dtimes timestamp NOT NULL,
	wallet_binding_id varchar(256) NOT NULL,
	public_key_hash varchar(100) NOT NULL,
	certificate varchar(4000) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	thumbprint varchar(128) NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (id_hash, auth_factor)
);

CREATE TABLE esignet.ca_cert_store(
	cert_id varchar(36) NOT NULL,
	cert_subject varchar(500) NOT NULL,
	cert_issuer varchar(500) NOT NULL,
	issuer_id varchar(36) NOT NULL,
	cert_not_before timestamp,
	cert_not_after timestamp,
	crl_uri varchar(120),
	cert_data varchar(4000),
	cert_thumbprint varchar(100),
	cert_serial_no varchar(50),
	partner_domain varchar(36),
	cr_by varchar(256),
	cr_dtimes timestamp,
	upd_by varchar(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp,
	ca_cert_type varchar(25),
	CONSTRAINT pk_cacs_id PRIMARY KEY (cert_id),
	CONSTRAINT cert_thumbprint_unique UNIQUE (cert_thumbprint,partner_domain)
);