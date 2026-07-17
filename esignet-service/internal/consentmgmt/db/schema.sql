-- Reference schema for the consent_detail table used by the consent enforcer.
-- This mirrors the existing db_scripts/mosip_esignet/ddl/esignet-consent.sql table; the consent
-- enforcer reuses that table rather than provisioning a new one. The signature column is unused by
-- this Go implementation (linked-authorization signature verification is out of scope).
CREATE TABLE consent_detail (
	id                   VARCHAR(36)   NOT NULL,
	client_id            VARCHAR(256)  NOT NULL,
	psu_token            VARCHAR(256)  NOT NULL,
	claims               VARCHAR(2048) NOT NULL,
	authorization_scopes VARCHAR(1024) NOT NULL,
	cr_dtimes            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
	expire_dtimes        TIMESTAMP,
	signature            VARCHAR(1024),
	hash                 VARCHAR(100),
	accepted_claims      VARCHAR(1024),
	permitted_scopes     VARCHAR(1024),
	PRIMARY KEY (id),
	CONSTRAINT unique_client_token UNIQUE (client_id, psu_token)
);

CREATE INDEX idx_consent_psu_client ON consent_detail(psu_token, client_id);

-- Reference schema for the consent_history table (append-only audit trail). This mirrors the
-- existing db_scripts/mosip_esignet/ddl/esignet-consent_history.sql table; a new snapshot row is
-- inserted on every consent record. It has no unique constraint on (client_id, psu_token).
CREATE TABLE consent_history (
	id                   VARCHAR(36)   NOT NULL,
	client_id            VARCHAR(256)  NOT NULL,
	psu_token            VARCHAR(256)  NOT NULL,
	claims               VARCHAR(2048) NOT NULL,
	authorization_scopes VARCHAR(1024) NOT NULL,
	cr_dtimes            TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
	expire_dtimes        TIMESTAMP,
	signature            VARCHAR(1024),
	hash                 VARCHAR(100),
	accepted_claims      VARCHAR(1024),
	permitted_scopes     VARCHAR(1024),
	PRIMARY KEY (id)
);

CREATE INDEX idx_consent_history_psu_client ON consent_history(psu_token, client_id);
