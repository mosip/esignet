CREATE TABLE client_detail (
	id                  varchar(100)  NOT NULL,
	name                varchar(600)  NOT NULL,
	rp_id               varchar(100)  NOT NULL,
	logo_uri            varchar(2048) NOT NULL,
	redirect_uris       varchar(2048) NOT NULL,
	claims              varchar(2048) NOT NULL,
	acr_values          varchar(1024) NOT NULL,
	public_key          varchar(1024) NOT NULL,
	public_key_hash     varchar(128)  NOT NULL,
	enc_public_key      varchar(1024),
	enc_public_key_hash varchar(128),
	enc_public_key_cert varchar(4000),
	grant_types         varchar(512)  NOT NULL,
	auth_methods        varchar(512)  NOT NULL,
	status              varchar(20)   NOT NULL,
	additional_config   varchar(2048),
	cr_dtimes           timestamptz   NOT NULL,
	upd_dtimes          timestamptz,
	CONSTRAINT pk_clntdtl_id          PRIMARY KEY (id),
	CONSTRAINT uk_clntdtl_public_key_hash UNIQUE (public_key_hash)
);

CREATE INDEX idx_clntdtl_rp_id  ON client_detail(rp_id);
CREATE INDEX idx_clntdtl_status ON client_detail(status);
