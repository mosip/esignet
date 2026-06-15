#!/usr/bin/env bash
set -uo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REDIRECT_URI="https://localhost:3000"
SCOPE="openid profile email"
USERNAME="decl-user-1"
PASSWORD="TempPassword123!"
PUBLIC_CLIENT_ID="decl-public-client-1"
JWT_CLIENT_ID="decl-jwt-client-1"
JWT_CLIENT_KEY="${SCRIPT_DIR}/fixtures/smoke-jwt-client.key"
JWT_CLIENT_KID="1a477d6c63d30c8a8f1c52d8"
CLIENT_ASSERTION_TYPE="urn:ietf:params:oauth:client-assertion-type:jwt-bearer"

PASSED=0
FAILED=0

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

summary() {
	echo ""
	echo "----------------------------------------"
	printf 'OAuth smoke: %d passed, %d failed (baseUrl=%s)\n' "$PASSED" "$FAILED" "$BASE_URL"
	echo "----------------------------------------"
	if [ "$FAILED" -gt 0 ]; then
		exit 1
	fi
	echo "OAuth smoke OK"
}

sign_client_assertion() {
	local client_id="$1"
	python3 "${SCRIPT_DIR}/sign-client-assertion.py" \
		--client-id "$client_id" \
		--token-endpoint "${BASE_URL}/oauth2/token" \
		--key "$JWT_CLIENT_KEY" \
		--kid "$JWT_CLIENT_KID"
}

run_oauth_flow() {
	local label="$1"
	local client_id="$2"
	local auth_mode="$3"

	echo "${label}"
	echo ""

	CODE_VERIFIER=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')
	CODE_CHALLENGE=$(printf '%s' "$CODE_VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')

	HTTP=$(curl -s -o /tmp/auth-body.txt -w '%{http_code}' -D /tmp/auth-hdrs.txt \
		"${BASE_URL}/oauth2/authorize?client_id=${client_id}&redirect_uri=https%3A%2F%2Flocalhost%3A3000&response_type=code&scope=openid%20profile%20email&state=t&code_challenge=${CODE_CHALLENGE}&code_challenge_method=S256") || HTTP="000"

	if [ "$HTTP" = "302" ]; then
		pass "GET /oauth2/authorize → 302"
	else
		fail "GET /oauth2/authorize → 302" "got HTTP ${HTTP}: $(head -c 200 /tmp/auth-body.txt 2>/dev/null)"
		return 1
	fi

	LOC=$(grep -i '^location:' /tmp/auth-hdrs.txt 2>/dev/null | tr -d '\r' | cut -d' ' -f2-)
	if [ -n "$LOC" ] && python3 -c "from urllib.parse import urlparse,parse_qs; import sys; q=parse_qs(urlparse(sys.argv[1]).query); exit(0 if q.get('authId') and q.get('executionId') else 1)" "$LOC" 2>/dev/null; then
		pass "Location contains authId and executionId"
	else
		fail "Location contains authId and executionId" "Location: ${LOC:-<missing>}"
		return 1
	fi

	AUTH_ID=$(python3 -c "from urllib.parse import urlparse,parse_qs; import sys; print(parse_qs(urlparse(sys.argv[1]).query)['authId'][0])" "$LOC")
	EID=$(python3 -c "from urllib.parse import urlparse,parse_qs; import sys; print(parse_qs(urlparse(sys.argv[1]).query)['executionId'][0])" "$LOC")

	INIT_HTTP=$(curl -s -o /tmp/init.json -w '%{http_code}' -X POST "${BASE_URL}/flow/execute" \
		-H 'Content-Type: application/json' -d "{\"executionId\":\"$EID\"}") || INIT_HTTP="000"

	if [ "$INIT_HTTP" = "200" ] && [ "$(jq -r .challengeToken /tmp/init.json 2>/dev/null)" != "null" ] && [ -n "$(jq -r .challengeToken /tmp/init.json 2>/dev/null)" ]; then
		pass "POST /flow/execute (resume) → 200 + challengeToken"
	else
		fail "POST /flow/execute (resume) → 200 + challengeToken" "HTTP ${INIT_HTTP}: $(head -c 200 /tmp/init.json 2>/dev/null)"
		return 1
	fi
	CT=$(jq -r .challengeToken /tmp/init.json)

	cat > /tmp/exec-req.json <<EOF
{"executionId":"$EID","challengeToken":"$CT","action":"action_001","inputs":{"username":"$USERNAME","password":"$PASSWORD"}}
EOF
	EXEC_HTTP=$(curl -s -o /tmp/exec.json -w '%{http_code}' -X POST "${BASE_URL}/flow/execute" \
		-H 'Content-Type: application/json' -d @/tmp/exec-req.json) || EXEC_HTTP="000"
	FLOW_STATUS=$(jq -r .flowStatus /tmp/exec.json 2>/dev/null)
	ASSERT=$(jq -r .assertion /tmp/exec.json 2>/dev/null)

	if [ "$EXEC_HTTP" = "200" ] && [ "$FLOW_STATUS" = "COMPLETE" ] && [ -n "$ASSERT" ] && [ "$ASSERT" != "null" ]; then
		pass "POST /flow/execute (credentials) → COMPLETE + assertion"
	else
		fail "POST /flow/execute (credentials) → COMPLETE + assertion" "HTTP ${EXEC_HTTP} flowStatus=${FLOW_STATUS:-<none>}"
		return 1
	fi

	CB_HTTP=$(curl -s -o /tmp/cb.json -w '%{http_code}' -X POST "${BASE_URL}/oauth2/auth/callback" \
		-H 'Content-Type: application/json' \
		-d "{\"authId\":\"$AUTH_ID\",\"assertion\":\"$ASSERT\"}") || CB_HTTP="000"

	REDIRECT=$(jq -r '.redirect_uri // .redirectUri' /tmp/cb.json 2>/dev/null)
	CODE=""
	if [ -n "$REDIRECT" ] && [ "$REDIRECT" != "null" ]; then
		CODE=$(printf '%s' "$REDIRECT" | python3 -c "from urllib.parse import urlparse,parse_qs; import sys; u=urlparse(sys.stdin.read().strip()); print(parse_qs(u.query).get('code',[''])[0])" 2>/dev/null) || CODE=""
	fi

	if [ "$CB_HTTP" = "200" ] && [ -n "$CODE" ]; then
		pass "POST /oauth2/auth/callback → 200 + authorization code"
	elif [ "$CB_HTTP" = "200" ] && printf '%s' "$REDIRECT" | grep -q 'error='; then
		fail "POST /oauth2/auth/callback → 200 + authorization code" "redirect error: $(head -c 200 /tmp/cb.json)"
		return 1
	else
		fail "POST /oauth2/auth/callback → 200 + authorization code" "HTTP ${CB_HTTP}: $(head -c 200 /tmp/cb.json 2>/dev/null)"
		return 1
	fi

	if [ "$auth_mode" = "private_key_jwt" ]; then
		if [ ! -f "$JWT_CLIENT_KEY" ]; then
			fail "POST /oauth2/token (private_key_jwt) → access_token + id_token" "missing key: ${JWT_CLIENT_KEY}"
			return 1
		fi
		CLIENT_ASSERTION=$(sign_client_assertion "$client_id") || {
			fail "POST /oauth2/token (private_key_jwt) → access_token + id_token" "failed to sign client assertion"
			return 1
		}
		TOKEN_HTTP=$(curl -s -o /tmp/token.json -w '%{http_code}' -X POST "${BASE_URL}/oauth2/token" \
			-d "grant_type=authorization_code" \
			-d "code=${CODE}" \
			-d "redirect_uri=${REDIRECT_URI}" \
			-d "client_id=${client_id}" \
			-d "code_verifier=${CODE_VERIFIER}" \
			-d "client_assertion_type=${CLIENT_ASSERTION_TYPE}" \
			-d "client_assertion=${CLIENT_ASSERTION}") || TOKEN_HTTP="000"
		TOKEN_LABEL="POST /oauth2/token (private_key_jwt) → access_token + id_token"
	else
		TOKEN_HTTP=$(curl -s -o /tmp/token.json -w '%{http_code}' -X POST "${BASE_URL}/oauth2/token" \
			-d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}&client_id=${client_id}&code_verifier=${CODE_VERIFIER}") || TOKEN_HTTP="000"
		TOKEN_LABEL="POST /oauth2/token → access_token + id_token"
	fi

	HAS_ACCESS=$(jq -r 'if .access_token then "yes" else "no" end' /tmp/token.json 2>/dev/null)
	HAS_ID=$(jq -r 'if .id_token then "yes" else "no" end' /tmp/token.json 2>/dev/null)

	if [ "$TOKEN_HTTP" = "200" ] && [ "$HAS_ACCESS" = "yes" ] && [ "$HAS_ID" = "yes" ]; then
		pass "$TOKEN_LABEL"
	else
		fail "$TOKEN_LABEL" "HTTP ${TOKEN_HTTP}: $(head -c 300 /tmp/token.json 2>/dev/null)"
		return 1
	fi
	ACCESS_TOKEN=$(jq -r .access_token /tmp/token.json)

	USERINFO_HTTP=$(curl -s -o /tmp/userinfo.json -w '%{http_code}' \
		-H "Authorization: Bearer ${ACCESS_TOKEN}" \
		"${BASE_URL}/oauth2/userinfo") || USERINFO_HTTP="000"

	USERINFO_SUB=$(jq -r .sub /tmp/userinfo.json 2>/dev/null)

	if [ "$USERINFO_HTTP" = "200" ] && [ -n "$USERINFO_SUB" ] && [ "$USERINFO_SUB" != "null" ]; then
		pass "GET /oauth2/userinfo → 200 + sub"
	else
		fail "GET /oauth2/userinfo → 200 + sub" "HTTP ${USERINFO_HTTP}: $(head -c 300 /tmp/userinfo.json 2>/dev/null)"
		return 1
	fi

	echo ""
	return 0
}

echo "OAuth smoke test — ${BASE_URL}"
echo ""

if ! run_oauth_flow "Public client (PKCE)" "$PUBLIC_CLIENT_ID" "none"; then
	summary
fi

if ! run_oauth_flow "Confidential client (private_key_jwt)" "$JWT_CLIENT_ID" "private_key_jwt"; then
	summary
fi

summary
