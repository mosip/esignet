CREATE TABLE public_key_registry(
    id_hash character varying(50) NOT NULL,
	psu_token character varying(256) NOT NULL,
	public_key character varying(50) NOT NULL,
	expire_dtimes timestamp NOT NULL,
	wallet_binding_id character varying(256) NOT NULL,
	public_key_hash character varying(50) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (id_hash)
);
