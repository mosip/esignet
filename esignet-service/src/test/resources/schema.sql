CREATE TABLE IF NOT EXISTS client_detail(
	id character varying(100) NOT NULL,
	name character varying(600) NOT NULL,
	rp_id character varying(100) NOT NULL,
	logo_uri character varying(2048) NOT NULL,
	redirect_uris character varying NOT NULL,
	claims character varying NOT NULL,
	acr_values character varying NOT NULL,
	public_key character varying NOT NULL,
	grant_types character varying NOT NULL,
	auth_methods character varying NOT NULL,
	status character varying(20) NOT NULL,
	additional_config character varying,
	cr_dtimes timestamp NOT NULL,
	upd_dtimes timestamp,
	CONSTRAINT pk_clntdtl_id PRIMARY KEY (id),
	CONSTRAINT uk_clntdtl_key UNIQUE (public_key)
);

CREATE TABLE IF NOT EXISTS public_key_registry(
    id_hash character varying(100) NOT NULL,
    auth_factor character varying(25) NOT NULL,
	psu_token character varying(256) NOT NULL,
	public_key character varying NOT NULL,
	expire_dtimes timestamp NOT NULL,
	wallet_binding_id character varying(256) NOT NULL,
	public_key_hash character varying(100) NOT NULL,
	certificate character varying NOT NULL,
	cr_dtimes timestamp NOT NULL,
	thumbprint character varying NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (id_hash, auth_factor)
);


CREATE TABLE IF NOT EXISTS key_alias(
    id character varying(36) NOT NULL,
    app_id character varying(36) NOT NULL,
    ref_id character varying(128),
    key_gen_dtimes timestamp,
    key_expire_dtimes timestamp,
    status_code character varying(36),
    lang_code character varying(3),
    cr_by character varying(256) NOT NULL,
    cr_dtimes timestamp NOT NULL,
    upd_by character varying(256),
    upd_dtimes timestamp,
    is_deleted boolean DEFAULT FALSE,
    del_dtimes timestamp,
    cert_thumbprint character varying(100),
    uni_ident character varying(50),
    CONSTRAINT pk_keymals_id PRIMARY KEY (id),
    CONSTRAINT uni_ident_const UNIQUE (uni_ident)
);

CREATE TABLE  IF NOT EXISTS key_policy_def(
    app_id character varying(36) NOT NULL,
    key_validity_duration smallint,
    is_active boolean NOT NULL,
    pre_expire_days smallint,
    access_allowed character varying(1024),
    cr_by character varying(256) NOT NULL,
    cr_dtimes timestamp NOT NULL,
    upd_by character varying(256),
    upd_dtimes timestamp,
    is_deleted boolean DEFAULT FALSE,
    del_dtimes timestamp,
    CONSTRAINT pk_keypdef_id PRIMARY KEY (app_id)
);

CREATE TABLE  IF NOT EXISTS key_store(
	id character varying(36) NOT NULL,
	master_key character varying(36) NOT NULL,
	private_key character varying(2500) NOT NULL,
	certificate_data character varying NOT NULL,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp,
	CONSTRAINT pk_keystr_id PRIMARY KEY (id)
);

CREATE TABLE  IF NOT EXISTS consent_detail (
    id UUID NOT NULL,
    client_id VARCHAR NOT NULL,
    psu_token VARCHAR NOT NULL,
    claims VARCHAR NOT NULL,
    authorization_scopes VARCHAR NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT NOW() NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR,
    hash VARCHAR,
    accepted_claims VARCHAR,
    permitted_scopes VARCHAR,
    PRIMARY KEY (id),
    CONSTRAINT unique_client_token UNIQUE (client_id, psu_token)
);

CREATE TABLE  IF NOT EXISTS consent_history (
    id UUID NOT NULL,
    client_id VARCHAR NOT NULL,
    psu_token VARCHAR NOT NULL,
    claims VARCHAR NOT NULL,
    authorization_scopes VARCHAR NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT NOW() NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR,
    hash VARCHAR,
    accepted_claims VARCHAR,
    permitted_scopes VARCHAR,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ca_cert_store(
	cert_id character varying(36) NOT NULL,
	cert_subject character varying(500) NOT NULL,
	cert_issuer character varying(500) NOT NULL,
	issuer_id character varying(36) NOT NULL,
	cert_not_before timestamp,
	cert_not_after timestamp,
	crl_uri character varying(120),
	cert_data character varying,
	cert_thumbprint character varying(100),
	cert_serial_no character varying(50),
	partner_domain character varying(36),
	cr_by character varying(256),
	cr_dtimes timestamp,
	upd_by character varying(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp,
	ca_cert_type character varying(25),
	CONSTRAINT pk_cacs_id PRIMARY KEY (cert_id),
	CONSTRAINT cert_thumbprint_unique UNIQUE (cert_thumbprint,partner_domain)
);


MERGE INTO KEY_POLICY_DEF (APP_ID,KEY_VALIDITY_DURATION,PRE_EXPIRE_DAYS,ACCESS_ALLOWED,IS_ACTIVE,CR_BY,CR_DTIMES)  KEY(APP_ID) VALUES ('ROOT', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('OIDC_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('OIDC_PARTNER', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('BINDING_SERVICE', 1095, 50, 'NA', true, 'mosipadmin', now()),  ('MOCK_IDA_SERVICES', 1095, 50, 'NA', true, 'mosipadmin', now());

MERGE INTO client_detail (id, name, rp_id, logo_uri, redirect_uris, claims, acr_values, public_key, grant_types, auth_methods, status, cr_dtimes, upd_dtimes)  KEY(id) VALUES ('healthservicev1', 'Health service', 'Bharathi-Inc', 'http://localhost:5000/images/Util%20logo.png', '["http:\/\/health-services.com\/userprofile","https:\/\/health-services.com\/userprofile","http:\/\/health-services.com:5000\/userprofile","http:\/\/localhost:5000\/userprofile"]', '["given_name","email","gender","phone_number","birthdate","picture"]', '["mosip:idp:acr:static-code"]', '{"kty":"RSA","kid":"1bbdc9de-c24f-4801-b6b3-691ac07641af","use":"sig","alg":"RS256","n":"wXGQA574CU-WTWPILd4S3_1sJf0Yof0kwMeNctXc1thQo70Ljfn9f4igpRe7f8qNs_W6dLuLWemFhGJBQBQ7vvickECKNJfo_EzSD_yyPCg7k_AGbTWTkuoObHrpilwJGyKVSkOIujH_FqHIVkwkVXjWc25Lsb8Gq4nAHNQEqqgaYPLEi5evCR6S0FzcXTPuRh9zH-cM0Onjv4orrfYpEr61HcRp5MXL55b7yBoIYlXD8NfalcgdrWzp4VZHvQ8yT9G5eaf27XUn6ZBeBf7VnELcKFTyw1pK2wqoOxRBc8Y1wO6rEy8PlCU6wD-mbIzcjG1wUfnbgvJOM4A5G41quQ","e":"AQAB"}', '["authorization_code"]', '["private_key_jwt"]', 'ACTIVE', now(), now());