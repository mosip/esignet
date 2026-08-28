# Technology Stack (Go)

eSignet is built using the below tools and technologies.

## Development

| Tool/Technology | Version | Description | License |
|---|---|---|---|
| [Go](https://go.dev/) | 1.26 | Go is a statically typed, compiled programming language designed by Google for simplicity, built-in concurrency, and fast compilation, commonly used for building networked and infrastructure services. | [BSD-3-Clause](https://github.com/golang/go/blob/master/LICENSE) |
| [net/http](https://pkg.go.dev/net/http) | Go 1.26 stdlib | net/http is Go's standard library package for building HTTP clients and servers; eSignet's HTTP entrypoint and routing are built directly on it, with no third-party web framework. | [BSD-3-Clause](https://github.com/golang/go/blob/master/LICENSE) |
| [ThunderID engine](https://github.com/thunder-id/thunderid) | pinned via `go.mod` `replace` directive | ThunderID is an OIDC/OAuth2 authorization engine providing flow orchestration and a pluggable authenticator/executor model; eSignet embeds it as the core of its authorization-code flow. | [Mozilla Public License 2.0](https://github.com/thunder-id/thunderid/blob/main/LICENSE) |
| [log/slog](https://pkg.go.dev/log/slog) | Go 1.26 stdlib | log/slog is Go's standard library package for structured, leveled logging; eSignet emits structured JSON logs through it. | [BSD-3-Clause](https://github.com/golang/go/blob/master/LICENSE) |
| [Keymanager](../esignet-service/internal/keymanager/README.md) (embedded) | part of `esignet-service` | Keymanager provides secure storage, provisioning, and management of cryptographic keys, including encryption/decryption and digital signature/verification operations. | [Mozilla Public License 2.0](../LICENSE) |
| [React JS](https://react.dev/) | 19 | React lets you build user interfaces out of individual pieces called components; it powers eSignet's login/consent UI. | [MIT License](https://github.com/facebook/react/blob/main/LICENSE) |
| [@thunderid/react](https://github.com/thunder-id/javascript-sdks/tree/main/packages/react) | 0.11.2 | ThunderID's React SDK supplies the actual login/OTP/biometric/KBI/consent screen rendering, OAuth state/PKCE handling, and i18n context that oidc-ui themes and embeds — the frontend counterpart to the backend's ThunderID engine. | [Apache License 2.0](https://github.com/thunder-id/javascript-sdks/blob/main/LICENSE) |

## Storage

| Tool/Technology | Version | Description | License |
|---|---|---|---|
| [Postgres](https://www.postgresql.org/) | 15 | PostgreSQL, also known as Postgres, is a free and open-source relational database management system (RDBMS) emphasizing extensibility and SQL compliance. eSignet uses it for OIDC client management and for the keymanager's key/certificate store. | [PostgreSQL License](https://opensource.org/license/postgresql/) |
| [Redis](https://redis.io/) | 6.2 and above (requires `GETDEL`, used to atomically fetch-and-delete authorization codes on redemption) | Redis is an open source, in-memory data store used as a database, cache, streaming engine, and message broker. eSignet uses it to hold runtime flow, session, and pushed-authorization-request state. | [BSD License](https://redis.io/docs/about/license/) |

## Deployment

| Tool/Technology | Version | Description | License |
|---|---|---|---|
| [Go modules](https://go.dev/ref/mod) | Go 1.26 | Go modules are Go's built-in dependency-management system, recording a project's dependencies and their versions in `go.mod`/`go.sum`. | [BSD-3-Clause](https://github.com/golang/go/blob/master/LICENSE) |
| [Docker](https://www.docker.com/) | 20.4 and above | Docker is a set of platform-as-a-service products that use OS-level virtualization to deliver software in packages called containers. | [Apache License 2.0](https://github.com/moby/moby/blob/master/LICENSE) |
| [npm](https://www.npmjs.com/) | Node.js 18 and above, npm 9 and above | npm is the package manager for the Node.js JavaScript platform; it installs and manages the UI's dependencies. | [Artistic License 2.0](https://docs.npmjs.com/policies/npm-license) |
| [Helm Chart (MOSIP)](https://github.com/mosip/mosip-helm) | depends on eSignet version | Helm helps manage Kubernetes applications — it helps define, install, and upgrade Kubernetes applications via versioned, shareable charts. | [Apache License 2.0](https://github.com/mosip/mosip-helm/blob/master/LICENSE) |
| [kattu (MOSIP)](https://github.com/mosip/kattu) | reusable CI/CD workflow | kattu holds the reusable GitHub Actions workflows used to build, test, and gate MOSIP projects' pull requests and releases. | [Apache License 2.0](https://github.com/mosip/kattu/blob/master/LICENSE) |

## Testing

| Tool/Technology | Version | Description | License |
|---|---|---|---|
| [Go testing](https://pkg.go.dev/testing) / [testify](https://github.com/stretchr/testify) | Go 1.26 stdlib / v1.11.1 | Go's built-in `testing` package, together with the testify toolkit (assertions and suite-based test structure), is used to write and run unit tests, invoked via `go test`. Used throughout `esignet-service` (e.g. `internal/engine/mock/authenticator_test.go`) via the `testify/suite` pattern described in `esignet-service/AGENTS.md`. | [BSD-3-Clause](https://github.com/golang/go/blob/master/LICENSE) / [MIT License](https://github.com/stretchr/testify/blob/master/LICENSE) |
| [Postman](https://www.postman.com/) | — | Postman is an API platform used to design, share, and run API requests and collections. `postman-collection/` ships a collection + environment for manually exercising `esignet-service`'s client-management and OAuth flows; there is no CI automation (e.g. Newman) wired up against it in this repo. | [Apache License 2.0](https://apache.org/licenses/LICENSE-2.0) |

## Services

| Service | Purpose |
|---|---|
| esignet-service | The OIDC/OAuth2 provider itself — a single Go binary embedding the ThunderID authorization engine, OIDC client management, and key lifecycle management (key generation, certificates, rotation, cryptographic operations). |
| oidc-ui | The React-based login and consent user interface that the OIDC/OAuth2 provider redirects end users to during an authorization request. |
| esignet-mock-services | A mock identity system used during local development and testing to simulate identity verification, OTP, and KYC without a live identity provider. |
| partner-onboarder | A utility that exchanges certificates to onboard eSignet as a MISP partner. |

## Endpoints

| Method(s) | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness check. |
| GET | `/metrics` | Prometheus metrics endpoint. |
| POST | `/client-mgmt/client` | Create an OIDC client. |
| GET, PUT, PATCH | `/client-mgmt/client/{client_id}` | Fetch, fully update, or partially update an OIDC client. |
| POST | `/client-mgmt/oidc-client`, `/client-mgmt/oauth-client` | Legacy client-creation endpoints, kept for backward compatibility. |
| PUT | `/client-mgmt/oidc-client/{client_id}`, `/client-mgmt/oauth-client/{client_id}` | Legacy client-update endpoints, kept for backward compatibility. |
| GET | `/system-info/certificate` | Fetch a certificate or CSR for a given application/reference id. |
| POST | `/system-info/uploadCertificate` | Upload/replace a certificate for a given application/reference id. |
| POST | `/oauth2/par` | Pushed Authorization Request endpoint (RFC 9126). |
| GET | `/oauth2/authorize` | OAuth2/OIDC authorization endpoint. |
| GET, POST | `/flow/meta`, `/flow/execute` | Declarative authentication-flow metadata and execution endpoints. |
| GET | `/oauth2/auth/callback` | Authorization callback endpoint. |
| POST | `/oauth2/token` | Token endpoint. |
| GET | `/oauth2/userinfo` | UserInfo endpoint. |
| GET | `/oauth2/jwks` | JSON Web Key Set endpoint. |
| POST | `/oauth2/introspect` | Token introspection endpoint. |
