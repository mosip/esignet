-- name: GetConsent :one
SELECT * FROM consent_detail
WHERE client_id = $1 AND psu_token = $2
LIMIT 1;

-- name: UpsertConsent :exec
INSERT INTO consent_detail (
	id,
	client_id,
	psu_token,
	claims,
	authorization_scopes,
	hash,
	accepted_claims,
	permitted_scopes,
	cr_dtimes,
	expire_dtimes
) VALUES (
	$1, $2, $3, $4, $5, $6, $7, $8, $9, $10
)
ON CONFLICT (client_id, psu_token) DO UPDATE SET
	claims               = EXCLUDED.claims,
	authorization_scopes = EXCLUDED.authorization_scopes,
	hash                 = EXCLUDED.hash,
	accepted_claims      = EXCLUDED.accepted_claims,
	permitted_scopes     = EXCLUDED.permitted_scopes,
	cr_dtimes            = EXCLUDED.cr_dtimes,
	expire_dtimes        = EXCLUDED.expire_dtimes;

-- name: InsertConsentHistory :exec
INSERT INTO consent_history (
	id,
	client_id,
	psu_token,
	claims,
	authorization_scopes,
	hash,
	accepted_claims,
	permitted_scopes,
	cr_dtimes,
	expire_dtimes
) VALUES (
	$1, $2, $3, $4, $5, $6, $7, $8, $9, $10
);

-- name: DeleteConsent :exec
DELETE FROM consent_detail
WHERE client_id = $1 AND psu_token = $2;
