CREATE TABLE IF NOT EXISTS public_key_registry(
	individual_id character varying(50) NOT NULL,
	psu_token character varying(256) NOT NULL,
	public_key character varying(50) NOT NULL,
	expires_on timestamp NOT NULL,
	cr_dtimes timestamp NOT NULL,
	CONSTRAINT pk_public_key_registry PRIMARY KEY (individual_id),
	CONSTRAINT uk_public_key_registry UNIQUE (public_key)
);