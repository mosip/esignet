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