## Overview

Docker Compose setup to run eSignet (Go) with the mock identity system. Not for production.

Local/mock eSignet uses a **PKCS12** file keystore so SoftHSM and `libpkcs11-proxy.so` are not required. Kubernetes/helm deployments keep **PKCS11** and install the HSM client from `client.zip` at container start.

## Bring up the mock stack

```bash
cd docker-compose
docker compose up -d
```

Services:

| Service | Port | Notes |
|---------|------|--------|
| Postgres | 5455 | `mosip_esignet` and `mosip_mockidentitysystem` from `init.sql` |
| Redis | 6379 | Runtime / session store |
| mock-identity-system | 8082 | Mock IDA used when `MOSIP_ESIGNET_AUTHN_PROVIDER=mock` |
| esignet | 8088 | Go eSignet; PKCS12 keystore at `/home/mosip/keys/esignet.p12` |

Health: `curl -s http://localhost:8088/health`

## Mock relying party (optional)

```bash
docker compose --file dependent-docker-compose.yaml up -d
```

That starts the mock relying party UI/service used to exercise the OIDC flow. See [esignet-mock-services](https://github.com/mosip/esignet-mock-services).
