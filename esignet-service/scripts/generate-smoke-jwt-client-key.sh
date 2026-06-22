#!/usr/bin/env bash
# Generate a local-only RSA key for the private_key_jwt OAuth smoke client and
# update the declarative JWKS in app-declarative-jwt-client.yaml to match.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEY_PATH="${JWT_CLIENT_KEY:-${SCRIPT_DIR}/fixtures/smoke-jwt-client.key}"
JWK_PATH="${JWT_CLIENT_JWK:-${SCRIPT_DIR}/fixtures/smoke-jwt-client.jwk.json}"
YAML="${SCRIPT_DIR}/../data/config/resources/applications/app-declarative-jwt-client.yaml"

mkdir -p "$(dirname "$KEY_PATH")"
openssl genrsa -out "$KEY_PATH" 2048
chmod 600 "$KEY_PATH"

KID_FILE="${SCRIPT_DIR}/fixtures/smoke-jwt-client.kid"

KEY_MATERIAL=$(python3 - "$KEY_PATH" <<'PY'
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
private_exponent = parse_hex_block(lines, "privateExponent")
prime1 = parse_hex_block(lines, "prime1")
prime2 = parse_hex_block(lines, "prime2")
exponent1 = parse_hex_block(lines, "exponent1")
exponent2 = parse_hex_block(lines, "exponent2")
coefficient = parse_hex_block(lines, "coefficient")

private_jwk = {
    "kty": "RSA",
    "n": b64url_int(modulus),
    "e": b64url_int(public_exponent),
    "d": b64url_int(private_exponent),
    "p": b64url_int(prime1),
    "q": b64url_int(prime2),
    "dp": b64url_int(exponent1),
    "dq": b64url_int(exponent2),
    "qi": b64url_int(coefficient),
    "kid": kid,
    "alg": "RS256",
    "use": "sig",
    "key_ops": ["sign"],
}
public_jwk = {
    "kty": "RSA",
    "n": private_jwk["n"],
    "e": private_jwk["e"],
    "kid": kid,
    "alg": "RS256",
    "use": "sig",
}
print(json.dumps({"private_jwk": private_jwk, "jwks": {"keys": [public_jwk]}, "kid": kid}, separators=(",", ":")))
PY
)

KID=$(python3 -c 'import json,sys; print(json.loads(sys.stdin.read())["kid"])' <<<"$KEY_MATERIAL")
PRIVATE_JWK=$(python3 -c 'import json,sys; print(json.dumps(json.loads(sys.stdin.read())["private_jwk"], separators=(",", ":")))' <<<"$KEY_MATERIAL")
JWKS_ONLY=$(python3 -c 'import json,sys; print(json.dumps(json.loads(sys.stdin.read())["jwks"], separators=(",", ":")))' <<<"$KEY_MATERIAL")

printf '%s\n' "$PRIVATE_JWK" >"$JWK_PATH"
chmod 600 "$JWK_PATH"
printf '%s\n' "$KID" >"$KID_FILE"
chmod 600 "$KID_FILE"

if [ -f "$YAML" ]; then
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
fi

echo "Wrote ${KEY_PATH}"
echo "Wrote ${JWK_PATH} (paste into Postman clientJwk)"
echo "Wrote ${KID_FILE} (kid=${KID})"
if [ -f "$YAML" ]; then
  echo "Updated JWKS in ${YAML}"
else
  echo "Skipped JWKS update (${YAML} not found)"
fi
