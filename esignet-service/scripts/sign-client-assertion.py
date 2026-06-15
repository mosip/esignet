#!/usr/bin/env python3
"""Sign an OAuth client assertion JWT for private_key_jwt token requests."""
from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import uuid


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def sign_client_assertion(client_id: str, token_endpoint: str, key_path: str, kid: str) -> str:
    header = {"alg": "RS256", "typ": "JWT", "kid": kid}
    now = int(time.time())
    payload = {
        "iss": client_id,
        "sub": client_id,
        "aud": token_endpoint,
        "jti": str(uuid.uuid4()),
        "iat": now,
        "exp": now + 300,
    }
    signing_input = (
        f"{b64url(json.dumps(header, separators=(',', ':')).encode())}."
        f"{b64url(json.dumps(payload, separators=(',', ':')).encode())}"
    )
    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".txt") as handle:
        handle.write(signing_input)
        tmp_path = handle.name
    try:
        signature = subprocess.check_output(
            ["openssl", "dgst", "-sha256", "-sign", key_path, "-binary", tmp_path]
        )
    finally:
        os.unlink(tmp_path)
    return f"{signing_input}.{b64url(signature)}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-id", required=True)
    parser.add_argument("--token-endpoint", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--kid", required=True)
    args = parser.parse_args()
    print(sign_client_assertion(args.client_id, args.token_endpoint, args.key, args.kid))
    return 0


if __name__ == "__main__":
    sys.exit(main())
