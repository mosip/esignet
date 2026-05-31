#!/usr/bin/env bash
set -uo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"

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

echo "OAuth smoke test — ${BASE_URL}"
echo ""

# --- Authorize ---
CODE_VERIFIER=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')
CODE_CHALLENGE=$(printf '%s' "$CODE_VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')

HTTP=$(curl -s -o /tmp/auth-body.txt -w '%{http_code}' -D /tmp/auth-hdrs.txt \
	"${BASE_URL}/oauth2/authorize?client_id=decl-public-client-1&redirect_uri=https%3A%2F%2Flocalhost%3A3000&response_type=code&scope=openid%20profile%20email&state=t&code_challenge=${CODE_CHALLENGE}&code_challenge_method=S256") || HTTP="000"

if [ "$HTTP" = "302" ]; then
	pass "GET /oauth2/authorize → 302"
else
	fail "GET /oauth2/authorize → 302" "got HTTP ${HTTP}: $(head -c 200 /tmp/auth-body.txt 2>/dev/null)"
	summary
fi

LOC=$(grep -i '^location:' /tmp/auth-hdrs.txt 2>/dev/null | tr -d '\r' | cut -d' ' -f2-)
if [ -n "$LOC" ] && python3 -c "from urllib.parse import urlparse,parse_qs; import sys; q=parse_qs(urlparse(sys.argv[1]).query); exit(0 if q.get('authId') and q.get('executionId') else 1)" "$LOC" 2>/dev/null; then
	pass "Location contains authId and executionId"
else
	fail "Location contains authId and executionId" "Location: ${LOC:-<missing>}"
	summary
fi

AUTH_ID=$(python3 -c "from urllib.parse import urlparse,parse_qs; import sys; print(parse_qs(urlparse(sys.argv[1]).query)['authId'][0])" "$LOC")
EID=$(python3 -c "from urllib.parse import urlparse,parse_qs; import sys; print(parse_qs(urlparse(sys.argv[1]).query)['executionId'][0])" "$LOC")

# --- Flow execute (resume) ---
INIT_HTTP=$(curl -s -o /tmp/init.json -w '%{http_code}' -X POST "${BASE_URL}/flow/execute" \
	-H 'Content-Type: application/json' -d "{\"executionId\":\"$EID\"}") || INIT_HTTP="000"

if [ "$INIT_HTTP" = "200" ] && [ "$(jq -r .challengeToken /tmp/init.json 2>/dev/null)" != "null" ] && [ -n "$(jq -r .challengeToken /tmp/init.json 2>/dev/null)" ]; then
	pass "POST /flow/execute (resume) → 200 + challengeToken"
else
	fail "POST /flow/execute (resume) → 200 + challengeToken" "HTTP ${INIT_HTTP}: $(head -c 200 /tmp/init.json 2>/dev/null)"
	summary
fi
CT=$(jq -r .challengeToken /tmp/init.json)

# --- Flow execute (credentials) ---
cat > /tmp/exec-req.json <<EOF
{"executionId":"$EID","challengeToken":"$CT","action":"action_001","inputs":{"username":"decl-user-1","password":"TempPassword123!"}}
EOF
EXEC_HTTP=$(curl -s -o /tmp/exec.json -w '%{http_code}' -X POST "${BASE_URL}/flow/execute" \
	-H 'Content-Type: application/json' -d @/tmp/exec-req.json) || EXEC_HTTP="000"
FLOW_STATUS=$(jq -r .flowStatus /tmp/exec.json 2>/dev/null)
ASSERT=$(jq -r .assertion /tmp/exec.json 2>/dev/null)

if [ "$EXEC_HTTP" = "200" ] && [ "$FLOW_STATUS" = "COMPLETE" ] && [ -n "$ASSERT" ] && [ "$ASSERT" != "null" ]; then
	pass "POST /flow/execute (credentials) → COMPLETE + assertion"
else
	fail "POST /flow/execute (credentials) → COMPLETE + assertion" "HTTP ${EXEC_HTTP} flowStatus=${FLOW_STATUS:-<none>}"
	summary
fi

# --- Auth callback ---
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
	summary
else
	fail "POST /oauth2/auth/callback → 200 + authorization code" "HTTP ${CB_HTTP}: $(head -c 200 /tmp/cb.json 2>/dev/null)"
	summary
fi

# --- Token ---
TOKEN_HTTP=$(curl -s -o /tmp/token.json -w '%{http_code}' -X POST "${BASE_URL}/oauth2/token" \
	-d "grant_type=authorization_code&code=${CODE}&redirect_uri=https://localhost:3000&client_id=decl-public-client-1&code_verifier=${CODE_VERIFIER}") || TOKEN_HTTP="000"

HAS_ACCESS=$(jq -r 'if .access_token then "yes" else "no" end' /tmp/token.json 2>/dev/null)
HAS_ID=$(jq -r 'if .id_token then "yes" else "no" end' /tmp/token.json 2>/dev/null)

if [ "$TOKEN_HTTP" = "200" ] && [ "$HAS_ACCESS" = "yes" ] && [ "$HAS_ID" = "yes" ]; then
	pass "POST /oauth2/token → access_token + id_token"
else
	fail "POST /oauth2/token → access_token + id_token" "HTTP ${TOKEN_HTTP}: $(head -c 300 /tmp/token.json 2>/dev/null)"
	summary
fi
ACCESS_TOKEN=$(jq -r .access_token /tmp/token.json)

# --- UserInfo ---
USERINFO_HTTP=$(curl -s -o /tmp/userinfo.json -w '%{http_code}' \
	-H "Authorization: Bearer ${ACCESS_TOKEN}" \
	"${BASE_URL}/oauth2/userinfo") || USERINFO_HTTP="000"

USERINFO_SUB=$(jq -r .sub /tmp/userinfo.json 2>/dev/null)

if [ "$USERINFO_HTTP" = "200" ] && [ -n "$USERINFO_SUB" ] && [ "$USERINFO_SUB" != "null" ]; then
	pass "GET /oauth2/userinfo → 200 + sub"
else
	fail "GET /oauth2/userinfo → 200 + sub" "HTTP ${USERINFO_HTTP}: $(head -c 300 /tmp/userinfo.json 2>/dev/null)"
	summary
fi

summary
