# eSignet

Open ID Connect based identity provider for large-scale authentication, from [MOSIP](https://mosip.io). This repository holds the Go-based `esignet-service` embedder (the OIDC/OAuth2 provider itself), its UI, and supporting deployment/test tooling.

## Repository layout

| Path | Purpose |
|------|---------|
| [`esignet-service/`](esignet-service/README.md) | Go service embedding the ThunderID authorization engine — PostgreSQL-backed client management, Redis-backed session/flow storage, pluggable authentication (mock, MOSIP IDA, SunbirdRC KBI). The core of this repo. |
| [`oidc-ui/`](oidc-ui/README.md) | React + TypeScript + Vite UI for the OIDC login/consent screens. |
| [`postman-collection/`](postman-collection/README.md) | Postman collection + environment for manual/scripted checks against `esignet-service`. |
| [`docker-compose/`](docker-compose/docker-compose.yaml) | Local mock stack: Postgres, Redis, mock-identity-system, and eSignet (PKCS12 keystore). |
| [`deploy/`](deploy/README.md) | Kubernetes deployment guide and scripts. |
| `helm/` | Helm charts (`esignet`, `oidc-ui`). |
| [`db_scripts/`](db_scripts/README.md) | SQL scripts to create the database and tables. |
| [`db_upgrade_script/`](db_upgrade_script/README.md) | SQL migration (upgrade/rollback) scripts, named by version. |
| [`partner-onboarder/`](partner-onboarder/README.md) | Exchanges certificates for the eSignet MISP partner. |
| [`api-test/`](api-test/README.md) | Java (REST Assured + TestNG) API automation test rig. |
| [`ui-test/`](ui-test/README.md) | Cucumber + TestNG + Selenium UI automation framework. |
| [`performance-test/`](performance-test/README.md) | JMeter performance test scripts. |

## Quick start (local mock)

```bash
# 1. Mock stack: Postgres, Redis, mock-identity-system, eSignet (PKCS12, no SoftHSM)
cd docker-compose && docker compose up -d

# 2. Or run the Go service on the host against compose Postgres/Redis:
cd ../esignet-service
cp .env.example .env   # defaults to PKCS12 + mock auth
./make.sh run
```

Each subproject is independently built and tested; see its own README (linked above) for its specific prerequisites and commands.

## License

Mozilla Public License 2.0 — see [LICENSE](LICENSE).
