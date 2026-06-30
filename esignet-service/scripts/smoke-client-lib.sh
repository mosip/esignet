#!/usr/bin/env bash
# Shared helpers for OAuth and client-mgmt smoke tests.
[[ -n "${SMOKE_CLIENT_LIB_LOADED:-}" ]] && return 0
SMOKE_CLIENT_LIB_LOADED=1

smoke_script_dir() {
	if [ -n "${SCRIPT_DIR:-}" ]; then
		printf '%s' "$SCRIPT_DIR"
		return 0
	fi
	cd "$(dirname "${BASH_SOURCE[1]}")" && pwd
}

smoke_root_dir() {
	local script_dir
	script_dir="$(smoke_script_dir)"
	cd "${script_dir}/.." && pwd
}

mgmt_auth_header() {
	if [ -n "${CLIENT_MGMT_ISSUER:-}" ]; then
		local root_dir signing_key
		root_dir="$(smoke_root_dir)"
		signing_key="${SIGNING_KEY:-${root_dir}/keys/signing.key}"
		if [ ! -f "$signing_key" ]; then
			printf 'missing signing key: %s (run ./make.sh keys)\n' "$signing_key" >&2
			return 1
		fi
		local script_dir jwks kid token
		script_dir="$(smoke_script_dir)"
		jwks=$(curl -sf "${BASE_URL}/.well-known/jwks.json") || {
			printf 'GET %s/.well-known/jwks.json failed\n' "$BASE_URL" >&2
			return 1
		}
		kid=$(printf '%s' "$jwks" | jq -r '.keys[0].kid // empty')
		if [ -z "$kid" ]; then
			printf 'no keys in JWKS document\n' >&2
			return 1
		fi
		token=$(python3 "${script_dir}/sign-mgmt-token.py" \
			--issuer "$CLIENT_MGMT_ISSUER" \
			--scope "${CLIENT_MGMT_SCOPE:-client_mgmt}" \
			--key "$signing_key" \
			--kid "$kid") || return 1
		printf 'Authorization: Bearer %s' "$token"
	fi
}

extract_public_jwk() {
	local key_path="$1"
	python3 - "$key_path" <<'PY'
import base64, hashlib, json, re, subprocess, sys

def b64url_int(value: int) -> str:
    raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")

def parse_hex_block(lines: list[str], start_label: str) -> int:
    value_hex = ""
    in_block = False
    hex_line = re.compile(r"^[0-9a-fA-F]{2}(?::[0-9a-fA-F]{2})*:?$")
    for line in lines:
        stripped = line.strip()
        if stripped.startswith(start_label + ":"):
            in_block = True
            continue
        if in_block:
            normalized = stripped.rstrip(":")
            if not hex_line.match(normalized):
                break
            value_hex += normalized.replace(":", "")
    if not value_hex:
        raise ValueError(f"missing {start_label} in openssl output")
    return int(value_hex, 16)

key_path = sys.argv[1]
pub_pem = subprocess.check_output(["openssl", "rsa", "-in", key_path, "-pubout"])
der = subprocess.check_output(["openssl", "rsa", "-pubin", "-inform", "PEM", "-outform", "DER"], input=pub_pem)
kid = hashlib.sha256(der).hexdigest()[:24]
text = subprocess.check_output(["openssl", "rsa", "-in", key_path, "-noout", "-text"], text=True)
lines = text.splitlines()
modulus = parse_hex_block(lines, "modulus")
public_exponent = int(re.search(r"publicExponent:\s*(\d+)", text).group(1))
public_jwk = {
    "kty": "RSA",
    "n": b64url_int(modulus),
    "e": b64url_int(public_exponent),
    "kid": kid,
    "alg": "RS256",
    "use": "sig",
}
print(json.dumps(public_jwk, separators=(",", ":")))
PY
}

generate_smoke_rsa_key() {
	local key_path
	key_path=$(mktemp)
	openssl genrsa -out "$key_path" 2048 >/dev/null 2>&1
	printf '%s' "$key_path"
}

load_smoke_env() {
	if [ -n "${SMOKE_ENV_LOADED:-}" ]; then
		return 0
	fi
	local root env_file
	root="$(smoke_root_dir)"
	env_file="${root}/.env"
	if [ -f "$env_file" ]; then
		set -a
		# shellcheck source=/dev/null
		source "$env_file"
		set +a
	fi
	SMOKE_ENV_LOADED=1
}

delete_smoke_client() {
	local client_id="$1"
	load_smoke_env
	if ! command -v psql >/dev/null 2>&1; then
		return 1
	fi
	local host port db user password
	host="${DATABASE_HOST:-localhost}"
	port="${DATABASE_PORT:-5432}"
	db="${DATABASE_NAME:-mosip_esignet}"
	user="${DATABASE_USERNAME:-postgres}"
	password="${DB_DBUSER_PASSWORD:-}"
	local escaped_id
	escaped_id=$(printf '%s' "$client_id" | sed "s/'/''/g")
	PGPASSWORD="$password" psql -h "$host" -p "$port" -U "$user" -d "$db" -v ON_ERROR_STOP=1 \
		-c "DELETE FROM esignet.client_detail WHERE id = '${escaped_id}';" >/dev/null
}

client_mgmt_request() {
	local method="$1"
	local path="$2"
	local body="${3:-}"
	local auth_header="${4:-}"
	local out_file http_code
	out_file=$(mktemp)
	if [ -n "$auth_header" ]; then
		if [ -n "$body" ]; then
			http_code=$(curl -s -o "$out_file" -w '%{http_code}' -X "$method" "${BASE_URL}${path}" \
				-H 'Content-Type: application/json' \
				-H "$auth_header" \
				-d "$body") || http_code="000"
		else
			http_code=$(curl -s -o "$out_file" -w '%{http_code}' -X "$method" "${BASE_URL}${path}" \
				-H "$auth_header") || http_code="000"
		fi
	else
		if [ -n "$body" ]; then
			http_code=$(curl -s -o "$out_file" -w '%{http_code}' -X "$method" "${BASE_URL}${path}" \
				-H 'Content-Type: application/json' \
				-d "$body") || http_code="000"
		else
			http_code=$(curl -s -o "$out_file" -w '%{http_code}' -X "$method" "${BASE_URL}${path}") || http_code="000"
		fi
	fi
	printf '%s\n%s' "$http_code" "$(cat "$out_file")"
	rm -f "$out_file"
}

ensure_smoke_client() {
	local client_id="$1"
	local create_body="$2"
	local auth_header="${3:-}"
	local response http_code body status

	delete_smoke_client "$client_id" 2>/dev/null || true

	response=$(client_mgmt_request POST "/client-mgmt/client" "$create_body" "$auth_header")
	http_code=$(printf '%s' "$response" | head -n1)
	body=$(printf '%s' "$response" | tail -n +2)
	status=$(jq -r '.response.status // empty' <<<"$body")
	if [ "$http_code" = "200" ] && [ "$(jq -r '.response.clientId // empty' <<<"$body")" = "$client_id" ] && [ "$status" = "ACTIVE" ]; then
		return 0
	fi

	printf 'failed to ensure client %s: HTTP %s: %s\n' "$client_id" "$http_code" "$(head -c 300 <<<"$body")" >&2
	return 1
}

build_smoke_client_create_body() {
	local client_id="$1"
	local client_name="$2"
	local auth_methods_json="$3"
	local public_key_json="$4"
	local additional_config_json="${5:-null}"

	jq -n \
		--arg rt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
		--arg id "$client_id" \
		--arg name "$client_name" \
		--arg rp "${RP_ID:-decl-ou-1}" \
		--arg uri "${REDIRECT_URI:-https://localhost:3000}" \
		--argjson auth_methods "$auth_methods_json" \
		--argjson public_key "$public_key_json" \
		--argjson additional_config "$additional_config_json" \
		'{
			requestTime: $rt,
			request: {
				clientId: $id,
				clientName: $name,
				clientNameLangMap: {eng: $name},
				relyingPartyId: $rp,
				logoUri: "https://example.com/logo.png",
				redirectUris: [$uri],
				userClaims: ["name", "email"],
				authContextRefs: ["mosip:idp:acr:static-code"],
				publicKey: $public_key,
				grantTypes: ["authorization_code"],
				clientAuthMethods: $auth_methods,
				additionalConfig: $additional_config
			}
		}'
}

ensure_smoke_oauth_clients() {
	load_smoke_env
	local auth_header jwt_key jwt_public_jwk create_body

	auth_header=$(mgmt_auth_header) || return 1

	jwt_key="${JWT_CLIENT_KEY:-$(smoke_script_dir)/fixtures/smoke-jwt-client.key}"
	if [ ! -f "$jwt_key" ]; then
		printf 'missing JWT client key: %s (run ./make.sh smoke-jwt-key)\n' "$jwt_key" >&2
		return 1
	fi
	jwt_public_jwk=$(extract_public_jwk "$jwt_key") || return 1
	create_body=$(build_smoke_client_create_body \
		"${JWT_CLIENT_ID:-decl-jwt-client-1}" \
		"Smoke JWT Client" \
		'["private_key_jwt"]' \
		"$jwt_public_jwk" \
		'{"pkce_required":true}') || return 1
	ensure_smoke_client "${JWT_CLIENT_ID:-decl-jwt-client-1}" "$create_body" "$auth_header" || return 1
}
