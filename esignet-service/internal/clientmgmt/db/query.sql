-- name: CreateClient :one
INSERT INTO client_detail (
	id,
	name,
	rp_id,
	logo_uri,
	redirect_uris,
	claims,
	acr_values,
	public_key,
	public_key_hash,
	enc_public_key,
	enc_public_key_hash,
	enc_public_key_cert,
	grant_types,
	auth_methods,
	status,
	additional_config,
	cr_dtimes
) VALUES (
	$1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17
)
RETURNING *;

-- name: GetClient :one
SELECT * FROM client_detail
WHERE id = $1 LIMIT 1;

-- name: UpdateClient :one
UPDATE client_detail SET
	name                = $2,
	logo_uri            = $3,
	redirect_uris       = $4,
	claims              = $5,
	acr_values          = $6,
	grant_types         = $7,
	auth_methods        = $8,
	status              = $9,
	additional_config   = $10,
	upd_dtimes          = $11
WHERE id = $1
RETURNING *;

-- name: PatchClient :one
UPDATE client_detail SET
	name                = $2,
	logo_uri            = $3,
	redirect_uris       = $4,
	claims              = $5,
	acr_values          = $6,
	grant_types         = $7,
	auth_methods        = $8,
	status              = $9,
	additional_config   = $10,
	enc_public_key      = $11,
	enc_public_key_hash = $12,
	enc_public_key_cert = $13,
	upd_dtimes          = $14
WHERE id = $1
  AND upd_dtimes IS NOT DISTINCT FROM $15
RETURNING *;
