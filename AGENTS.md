# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

eSignet is a MOSIP-based OpenID Connect (OIDC) identity platform. It implements OAuth 2.0 Authorization Code Flow with PKCE, OpenID Connect Discovery 1.0, and supports wallet-based authentication via key binding to individual IDs. The system is designed as a plugin-based architecture where identity providers (MOSIP, Sunbird RC, mock) are swapped without changing the core service.

## Repository Structure

- **esignet-core** — Shared interfaces, DTOs, utilities, validators, and constants used across all modules
- **esignet-integration-api** — SPI (Service Provider Interface) definitions for plugin implementors
- **esignet-service** — Main deployable Spring Boot service; contains all OIDC/OAuth controllers, services, and config
- **oidc-service-impl** / **binding-service-impl** / **consent-service-impl** / **client-management-service-impl** — Default implementations of the integration API
- **esignet-with-plugins** — Docker image builder that bundles esignet-service with a specific plugin JAR
- **oidc-ui** — React 18 frontend
- **api-test** — REST Assured + TestNG automation suite
- **ui-test** — Selenium UI test suite
- **docker-compose** — Local development environment with mock services
- **db_scripts** — PostgreSQL schema initialization; **db_upgrade_script** — migration scripts

## Build Commands

### Backend (Maven, Java 21, Spring Boot 3.2.3)

```bash
# Build all modules (skip GPG signing and git commit ID plugin)
mvn clean install -Dgpg.skip=true -Dmaven.gitcommitid.skip=true

# Build a single module
mvn clean install -pl esignet-service -am -Dgpg.skip=true -Dmaven.gitcommitid.skip=true

# Run all backend tests
mvn test

# Run tests for a specific module
mvn test -pl esignet-service

# Run tests with coverage report
mvn clean test jacoco:report -pl esignet-service

# Build OpenAPI docs
mvn clean install -Popenapi-doc-generate-profile -Dgpg.skip=true
```

### Frontend (oidc-ui — React 18, Create React App)

```bash
cd oidc-ui
npm install
npm start           # Dev server at http://localhost:3000
npm run build       # Production build
npm test            # Interactive Jest test runner
npm run test:coverage  # Coverage report (80% threshold enforced)
npm run lint        # ESLint check
npm run lint:fix    # Auto-fix linting issues
```

## Local Development Setup

1. **Start infrastructure dependencies** (PostgreSQL, Redis, Kafka, mock services):
   ```bash
   cd docker-compose
   docker compose --file dependent-docker-compose.yml up
   ```

2. **Build mock plugins:**
   ```bash
   cd esignet-with-plugins
   mvn clean install -Dgpg.skip=true
   ```

3. **Run the backend** — In your IDE, add `esignet-with-plugins/target/esignet-mock-plugin.jar` to the classpath, then run `EsignetServiceApplication`. Swagger UI: `http://localhost:8088/v1/esignet/swagger-ui.html`

4. **Run the frontend:**
   ```bash
   cd oidc-ui && npm install && npm start
   ```

**Full demo stack via Docker Compose:**
```bash
cd docker-compose
docker compose --file docker-compose.yml up
```

## Key Configuration

- `esignet-service/src/main/resources/application-local.properties` — Local dev overrides
- `esignet-service/src/main/resources/application-default.properties` — Defaults with env-var placeholders
- `oidc-ui/.env` / `.env.development` / `.env.production` — Frontend environment config

Important env vars: `SPRING_DATASOURCE_URL`, `MOSIP_ESIGNET_DOMAIN_URL`, `MOSIP_KERNEL_KEYMANAGER_HSM_KEYSTORE_TYPE`, `MOSIP_ESIGNET_CAPTCHA_REQUIRED`

Database: PostgreSQL `mosip_esignet`; caching uses Redis in production, in-memory for local/test.

## Architecture: OIDC Flow and Plugin System

The core OIDC flow is implemented in **esignet-service** with state managed in a cache (Redis/in-memory):

```
preauth → authenticated → authcodegenerated → userinfo (token issued)
```

Controllers in `esignet-service`:
- `OAuthController` / `OpenIdConnectController` — Token, discovery endpoints
- `AuthorizationController` — Authorization code flow, PKCE
- `LinkedAuthorizationController` — Wallet-based QR code flow (uses Kafka for async)
- `KeyBindingController` — Binding individual ID to a cryptographic key
- `ClientManagementController` — OIDC client registration

**Plugin architecture:** `esignet-integration-api` defines interfaces that plugins implement:
- `AuthenticationWrapper` — delegates identity verification to the ID provider
- `KeyBindingWrapper` — handles device key binding
- `AuditPlugin` — audit event publishing

Plugins are loaded at runtime via the classpath JAR in `esignet-with-plugins`. Production uses `mosip-identity-plugin`; local dev uses `mock-plugin`.

## API Test Automation

```bash
cd api-test
mvn clean install -Dgpg.skip=true

# Run smoke tests
java -jar target/apitest-esignet-*.jar \
  -Dmodules=esignet \
  -Denv.user=api-internal.dev \
  -Denv.endpoint=https://api-internal.dev.mosip.net \
  -Denv.testLevel=smoke

# smoke + regression
-Denv.testLevel=smokeAndRegression
```

