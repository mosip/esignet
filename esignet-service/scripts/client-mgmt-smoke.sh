#!/usr/bin/env bash
set -uo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SIGNING_KEY="${SIGNING_KEY:-${ROOT_DIR}/keys/signing.key}"
RP_ID="${RP_ID:-decl-ou-1}"
REDIRECT_URI="${REDIRECT_URI:-https://localhost:3000}"
CLIENT_MGMT_SCOPE="${CLIENT_MGMT_SCOPE:-client_mgmt}"
CLIENT_MGMT_ISSUER="${CLIENT_MGMT_ISSUER_URL:-}"

PASSED=0
FAILED=0
SKIPPED=0

pass() {
	PASSED=$((PASSED + 1))
	printf '  PASS  %s\n' "$1"
}

fail() {
	FAILED=$((FAILED + 1))
	printf '  FAIL  %s\n' "$1" >&2
	if [ -n "${2:-}" ]; then
		printf '        %s\n' "$2" >&2
	fi
}

skip() {
	SKIPPED=$((SKIPPED + 1))
	printf '  SKIP  %s\n' "$1"
}

summary() {
	echo ""
	echo "----------------------------------------"
	printf 'Client-mgmt smoke: %d passed, %d failed, %d skipped (baseUrl=%s)\n' "$PASSED" "$FAILED" "$SKIPPED" "$BASE_URL"
	echo "----------------------------------------"
	if [ "$FAILED" -gt 0 ]; then
		exit 1
	fi
	echo "Client-mgmt smoke OK"
}

mgmt_auth_header() {
	if [ -n "$CLIENT_MGMT_ISSUER" ]; then
		if [ ! -f "$SIGNING_KEY" ]; then
			fail "client-mgmt auth" "missing signing key: ${SIGNING_KEY} (run ./make.sh keys)"
			return 1
		fi
		local jwks kid token
		jwks=$(curl -sf "${BASE_URL}/.well-known/jwks.json") || {
			fail "fetch JWKS" "GET ${BASE_URL}/.well-known/jwks.json failed"
			return 1
		}
		kid=$(printf '%s' "$jwks" | jq -r '.keys[0].kid // empty')
		if [ -z "$kid" ]; then
			fail "resolve JWKS kid" "no keys in JWKS document"
			return 1
		fi
		token=$(python3 "${SCRIPT_DIR}/sign-mgmt-token.py" \
			--issuer "$CLIENT_MGMT_ISSUER" \
			--scope "$CLIENT_MGMT_SCOPE" \
			--key "$SIGNING_KEY" \
			--kid "$kid") || {
			fail "sign client-mgmt token" "sign-mgmt-token.py failed"
			return 1
		}
		printf 'Authorization: Bearer %s' "$token"
		return 0
	fi
	return 0
}

run_client_mgmt_smoke() {
	echo "Client management smoke — ${BASE_URL}"
	echo ""

	local auth_header client_id suffix create_body create_resp get_resp update_body update_resp
	auth_header=$(mgmt_auth_header) || return 1

	suffix=$(date +%s)
	client_id="smoke-client-${suffix}"
	local public_key='{"kty":"RSA","n":"smoke-'"${suffix}"'","e":"AQAB"}'

	create_body=$(jq -n \
		--arg rt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
		--arg id "$client_id" \
		--arg rp "$RP_ID" \
		--arg uri "$REDIRECT_URI" \
		--argjson pk "$public_key" \
		'{
			requestTime: $rt,
			request: {
				clientId: $id,
				clientName: "Smoke Test Client",
				clientNameLangMap: {eng: "Smoke Test Client"},
				relyingPartyId: $rp,
				logoUri: "https://example.com/logo.png",
				redirectUris: [$uri],
				userClaims: ["name", "email"],
				authContextRefs: ["mosip:idp:acr:static-code"],
				publicKey: $pk,
				grantTypes: ["authorization_code"],
				clientAuthMethods: ["private_key_jwt"]
			}
		}')

	local create_http create_json
	create_json=$(mktemp)
	if [ -n "$auth_header" ]; then
		create_http=$(curl -s -o "$create_json" -w '%{http_code}' -X POST "${BASE_URL}/client-mgmt/client" \
			-H 'Content-Type: application/json' \
			-H "$auth_header" \
			-d "$create_body") || create_http="000"
	else
		create_http=$(curl -s -o "$create_json" -w '%{http_code}' -X POST "${BASE_URL}/client-mgmt/client" \
			-H 'Content-Type: application/json' \
			-d "$create_body") || create_http="000"
	fi

	if [ "$create_http" = "200" ] \
		&& [ "$(jq -r '.response.clientId // empty' "$create_json")" = "$client_id" ] \
		&& [ "$(jq -r '.response.status // empty' "$create_json")" = "ACTIVE" ]; then
		pass "POST /client-mgmt/client → ACTIVE"
	else
		fail "POST /client-mgmt/client → ACTIVE" "HTTP ${create_http}: $(head -c 300 "$create_json" 2>/dev/null)"
		rm -f "$create_json"
		return 1
	fi

	local get_http get_json
	get_json=$(mktemp)
	if [ -n "$auth_header" ]; then
		get_http=$(curl -s -o "$get_json" -w '%{http_code}' \
			-H "$auth_header" \
			"${BASE_URL}/client-mgmt/client/${client_id}") || get_http="000"
	else
		get_http=$(curl -s -o "$get_json" -w '%{http_code}' \
			"${BASE_URL}/client-mgmt/client/${client_id}") || get_http="000"
	fi

	if [ "$get_http" = "200" ] \
		&& [ "$(jq -r '.response.clientId // empty' "$get_json")" = "$client_id" ]; then
		pass "GET /client-mgmt/client/{id}"
	else
		fail "GET /client-mgmt/client/{id}" "HTTP ${get_http}: $(head -c 300 "$get_json" 2>/dev/null)"
		rm -f "$create_json" "$get_json"
		return 1
	fi

	update_body=$(jq -n \
		--arg rt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
		--arg uri "$REDIRECT_URI" \
		'{
			requestTime: $rt,
			request: {
				clientName: "Smoke Test Client (updated)",
				clientNameLangMap: {eng: "Smoke Test Client (updated)"},
				status: "active",
				logoUri: "https://example.com/logo-updated.png",
				redirectUris: [$uri],
				userClaims: ["name", "email"],
				authContextRefs: ["mosip:idp:acr:static-code"],
				grantTypes: ["authorization_code"],
				clientAuthMethods: ["private_key_jwt"]
			}
		}')

	local update_http update_json
	update_json=$(mktemp)
	if [ -n "$auth_header" ]; then
		update_http=$(curl -s -o "$update_json" -w '%{http_code}' -X PUT "${BASE_URL}/client-mgmt/client/${client_id}" \
			-H 'Content-Type: application/json' \
			-H "$auth_header" \
			-d "$update_body") || update_http="000"
	else
		update_http=$(curl -s -o "$update_json" -w '%{http_code}' -X PUT "${BASE_URL}/client-mgmt/client/${client_id}" \
			-H 'Content-Type: application/json' \
			-d "$update_body") || update_http="000"
	fi

	if [ "$update_http" = "200" ] \
		&& [ "$(jq -r '.response.clientId // empty' "$update_json")" = "$client_id" ] \
		&& [ "$(jq -r '.response.status // empty' "$update_json")" = "ACTIVE" ]; then
		pass "PUT /client-mgmt/client/{id} → ACTIVE"
	else
		fail "PUT /client-mgmt/client/{id} → ACTIVE" "HTTP ${update_http}: $(head -c 300 "$update_json" 2>/dev/null)"
	fi

	rm -f "$create_json" "$get_json" "$update_json"
	echo ""
}

if [ "${SKIP_CLIENT_MGMT_SMOKE:-0}" = "1" ]; then
	skip "client-mgmt smoke (SKIP_CLIENT_MGMT_SMOKE=1)"
	summary
fi

if ! command -v jq >/dev/null 2>&1; then
	skip "client-mgmt smoke (jq not installed)"
	summary
fi

run_client_mgmt_smoke || true
summary
