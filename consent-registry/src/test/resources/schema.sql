create table consent (
    id UUID NOT NULL,
    client_id VARCHAR NOT NULL,
    psu_value VARCHAR NOT NULL,
    claims VARCHAR NOT NULL,
    authorization_scopes VARCHAR NOT NULL,
    created_on TIMESTAMP DEFAULT NOW() NOT NULL,
    expiration TIMESTAMP,
    signature VARCHAR,
    hash VARCHAR,
    signed_by VARCHAR,
   CONSTRAINT pk_consent primary key (id)
);