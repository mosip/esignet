CREATE TABLE consent_history (
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
CREATE INDEX idx_consent_history_psu_client ON consent_history(psu_token, client_id);

CREATE TABLE consent_detail (
    id VARCHAR(36) NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    psu_token VARCHAR(256) NOT NULL,
    claims VARCHAR(1024) NOT NULL,
    authorization_scopes VARCHAR(1024) NOT NULL,
    cr_dtimes TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expire_dtimes TIMESTAMP,
    signature VARCHAR(1024),
    hash VARCHAR(1024),
    accepted_claims VARCHAR(1024),
    permitted_scopes VARCHAR(1024),
    PRIMARY KEY (id),
    CONSTRAINT unique_client_token UNIQUE (client_id, psu_token)
);
CREATE INDEX idx_consent_psu_client ON consent_detail(psu_token, client_id);