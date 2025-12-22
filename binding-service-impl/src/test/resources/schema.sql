CREATE TABLE public_key_registry(
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