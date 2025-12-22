CREATE TABLE IF NOT EXISTS client_detail(
    id varchar(100) NOT NULL,
    name varchar(600) NOT NULL,
    rp_id varchar(100) NOT NULL,
    logo_uri varchar(2048) NOT NULL,
    redirect_uris varchar(2048) NOT NULL,
    claims varchar(2048) NOT NULL,
    acr_values varchar(1024) NOT NULL,
    public_key varchar(1024) NOT NULL,
    public_key_hash varchar(128) NOT NULL,
    grant_types varchar(512) NOT NULL,
    auth_methods varchar(512) NOT NULL,
    status varchar(20) NOT NULL,
    additional_config varchar(2048),
    cr_dtimes timestamp NOT NULL,
    upd_dtimes timestamp,
    CONSTRAINT pk_clntdtl_id PRIMARY KEY (id),
    CONSTRAINT uk_clntdtl_public_key_hash UNIQUE (public_key_hash)
);

CREATE TABLE IF NOT EXISTS public_key_registry(
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


CREATE TABLE IF NOT EXISTS key_alias(
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

CREATE TABLE  IF NOT EXISTS key_policy_def(
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

CREATE TABLE  IF NOT EXISTS key_store(
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

CREATE TABLE  IF NOT EXISTS client_detail(
    id varchar(100) NOT NULL,
    name varchar(600) NOT NULL,
    rp_id varchar(100) NOT NULL,
    logo_uri varchar(2048) NOT NULL,
    redirect_uris varchar(2048) NOT NULL,
    claims varchar(2048) NOT NULL,
    acr_values varchar(1024) NOT NULL,
    public_key varchar(1024) NOT NULL,
    public_key_hash varchar(128) NOT NULL,
    grant_types varchar(512) NOT NULL,
    auth_methods varchar(512) NOT NULL,
    status varchar(20) NOT NULL,
    additional_config varchar(2048),
    cr_dtimes timestamp NOT NULL,
    upd_dtimes timestamp,
    CONSTRAINT pk_clntdtl_id PRIMARY KEY (id),
    CONSTRAINT uk_clntdtl_public_key_hash UNIQUE (public_key_hash)
);

CREATE TABLE  IF NOT EXISTS consent_history (
    id varchar(36) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    claims VARCHAR(1024) NOT NULL,
    authorization_scopes VARCHAR(512) NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR(1024),
    hash VARCHAR(1024),
    accepted_claims VARCHAR(1024),
    permitted_scopes VARCHAR(1024),
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_consent_history_psu_client ON consent_history(psu_token, client_id);

CREATE TABLE IF NOT EXISTS ca_cert_store(
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


MERGE INTO KEY_POLICY_DEF (APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES)  KEY(APP_ID) VALUES ('ROOT', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('OIDC_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('OIDC_PARTNER', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('BINDING_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('MOCK_IDA_SERVICES', 1095, 50, 'NA', true, 'mosipadmin', now());

MERGE INTO client_detail (id, name, rp_id, logo_uri, redirect_uris, claims, acr_values, public_key, public_key_hash, grant_types, auth_methods, status, cr_dtimes, upd_dtimes)  KEY(id) VALUES ('healthservicev1', 'Health service', 'Bharathi-Inc', 'http://localhost:5000/images/Util%20logo.png', '["http:\/\/health-services.com\/userprofile","https:\/\/health-services.com\/userprofile","http:\/\/health-services.com:5000\/userprofile","http:\/\/localhost:5000\/userprofile"]', '["given_name","email","gender","phone_number","birthdate","picture"]', '["mosip:idp:acr:static-code"]', '{"kty":"RSA","kid":"1bbdc9de-c24f-4801-b6b3-691ac07641af","use":"sig","alg":"RS256","n":"wXGQA574CU-WTWPILd4S3_1sJf0Yof0kwMeNctXc1thQo70Ljfn9f4igpRe7f8qNs_W6dLuLWemFhGJBQBQ7vvickECKNJfo_EzSD_yyPCg7k_AGbTWTkuoObHrpilwJGyKVSkOIujH_FqHIVkwkVXjWc25Lsb8Gq4nAHNQEqqgaYPLEi5evCR6S0FzcXTPuRh9zH-cM0Onjv4orrfYpEr61HcRp5MXL55b7yBoIYlXD8NfalcgdrWzp4VZHvQ8yT9G5eaf27XUn6ZBeBf7VnELcKFTyw1pK2wqoOxRBc8Y1wO6rEy8PlCU6wD-mbIzcjG1wUfnbgvJOM4A5G41quQ","e":"AQAB"}', '051e07e2fa6c03209c39b4e7303cf07dc387e6f9f86633a445659791e5542385', '["authorization_code"]', '["private_key_jwt"]', 'ACTIVE', now(), now());