CREATE TABLE public_key_registry(
	psu_token character varying(256) NOT NULL,
	public_key character varying(50) NOT NULL,
	expire_dtimes timestamp NOT NULL,
	wallet_binding_id character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (psu_token),
	CONSTRAINT uk_public_key_registry UNIQUE (public_key)
);
CREATE TABLE id_token_mapping (
	id_hash character varying(50) NOT NULL,
	psu_token character varying(256) NOT NULL,
	CONSTRAINT pk_id_token_mapping PRIMARY KEY (id_hash)
);