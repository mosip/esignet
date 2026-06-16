#!/usr/bin/env bash
# Generate a local-only RSA key for the private_key_jwt OAuth smoke client and
# update the declarative JWKS in app-declarative-jwt-client.yaml to match.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEY_PATH="${JWT_CLIENT_KEY:-${SCRIPT_DIR}/fixtures/smoke-jwt-client.key}"
YAML="${SCRIPT_DIR}/../data/repository/resources/applications/app-declarative-jwt-client.yaml"

mkdir -p "$(dirname "$KEY_PATH")"
openssl genrsa -out "$KEY_PATH" 2048
chmod 600 "$KEY_PATH"

KID_FILE="${SCRIPT_DIR}/fixtures/smoke-jwt-client.kid"

JWKS_JSON=$(python3 - "$KEY_PATH" <<'PY'
import base64, hashlib, json, subprocess, sys

def b64url_int(value: int) -> str:
    raw = value.to_bytes((value.bit_length() + 7) // 8, "big")
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")

key_path = sys.argv[1]
pub_pem = subprocess.check_output(["openssl", "rsa", "-in", key_path, "-pubout"])
der = subprocess.check_output(["openssl", "rsa", "-pubin", "-inform", "PEM", "-outform", "DER"], input=pub_pem)
kid = hashlib.sha256(der).hexdigest()[:24]

text = subprocess.check_output(["openssl", "rsa", "-in", key_path, "-noout", "-text"], text=True)
modulus_hex = ""
exponent = 65537
in_modulus = False
for line in text.splitlines():
    line = line.strip()
    if line.startswith("modulus:"):
        in_modulus = True
        continue
    if line.startswith("publicExponent:"):
        exponent = int(line.split("(")[0].split()[-1])
        break
    if in_modulus and line and line[0] in "0123456789abcdefABCDEF:":
        modulus_hex += line.replace(":", "")

modulus = int(modulus_hex, 16)
jwks = {
    "keys": [{
        "kty": "RSA",
        "n": b64url_int(modulus),
        "e": b64url_int(exponent),
        "kid": kid,
        "alg": "RS256",
        "use": "sig",
    }]
}
print(json.dumps({"jwks": jwks, "kid": kid}, separators=(",", ":")))
PY
)

KID=$(python3 -c 'import json,sys; print(json.loads(sys.stdin.read())["kid"])' <<<"$JWKS_JSON")
JWKS_ONLY=$(python3 -c 'import json,sys; print(json.dumps(json.loads(sys.stdin.read())["jwks"], separators=(",", ":")))' <<<"$JWKS_JSON")
printf '%s\n' "$KID" >"$KID_FILE"
chmod 600 "$KID_FILE"

python3 - "$YAML" "$JWKS_ONLY" <<'PY'
import json, re, sys

yaml_path, jwks_json = sys.argv[1], sys.argv[2]
escaped = jwks_json.replace("\\", "\\\\").replace('"', '\\"')
with open(yaml_path, encoding="utf-8") as handle:
    content = handle.read()
pattern = r'(^\s+value: ")(.*)(")\s*$'
new_content, count = re.subn(
    pattern,
    lambda m: f'{m.group(1)}{escaped}{m.group(3)}',
    content,
    count=1,
    flags=re.MULTILINE,
)
if count != 1:
    raise SystemExit(f"could not update JWKS in {yaml_path}")
with open(yaml_path, "w", encoding="utf-8") as handle:
    handle.write(new_content)
PY

echo "Wrote ${KEY_PATH}"
echo "Wrote ${KID_FILE} (kid=${KID})"
echo "Updated JWKS in ${YAML}"
