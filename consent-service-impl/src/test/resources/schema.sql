create table consent_detail (
    id UUID NOT NULL,
    client_id VARCHAR NOT NULL,
    psu_token VARCHAR NOT NULL,
    claims VARCHAR NOT NULL,
    authorization_scopes VARCHAR NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT NOW() NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_consent_psu_client ON consent_detail(psu_token, client_id, cr_dtimes);