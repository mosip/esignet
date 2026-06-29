# Server Profile

This document describes the design and implementation of the Server Profile feature in eSignet. It provides a global toggle mechanism to enable OpenID/FAPI security features at the deployment level.

## Overview

The Server Profile feature allows deployments to enforce security standards like FAPI 2.0 across all OIDC transactions. On application startup, eSignet reads the global OpenID profile value from `mosip.esignet.server.profile` property and loads the corresponding feature mappings from the database.

The configured profile determines the default standards behavior for the deployment:
- **fapi2.0** → PAR, DPoP, and PKCE enabled
- **none** → No enforced defaults; behavior is driven by client-level configuration

## Basic Flow

1. On application startup, eSignet reads the global OpenID profile value from `mosip.esignet.server.profile` in `application-default.properties`.
2. eSignet loads the profile-to-feature mapping from the `server_profile` database table that defines which features (PAR, DPoP, PKCE) are enabled for the profile.
3. When an OIDC client initiates a transaction:
   - If the global profile is set to `none`, the relying party may explicitly configure PAR, DPoP, PKCE via the client management API.
   - If the global profile is `fapi2.0`, any client-level flags provided are overridden by the profile's enforced feature set.
4. During transaction initiation and runtime execution, eSignet resolves the effective configuration based on the selected global profile and enforces the features accordingly.

## Database Schema

### server_profile Table

A static table that stores the profile-to-feature mapping. No API endpoint is provided intentionally to keep profiles immutable at runtime.

```sql
CREATE TABLE IF NOT EXISTS server_profile (
    profile_name VARCHAR(100) NOT NULL,
    feature VARCHAR(100) NOT NULL,
    additional_config_key VARCHAR(200) NOT NULL,
    CONSTRAINT pk_server_profile PRIMARY KEY (profile_name, feature)
);
```

| Column                  | Description                                                |
|-------------------------|------------------------------------------------------------|
| `profile_name`          | Name of the server profile (e.g., `fapi2.0`)               |
| `feature`               | Feature identifier (e.g., `PAR`, `DPOP`, `PKCE`)           |
| `additional_config_key` | Configuration key used internally to enable the feature    |

### Pre-configured Data (FAPI 2.0)

```sql
INSERT INTO server_profile (profile_name, feature, additional_config_key) VALUES
('fapi2.0', 'PAR', 'require_pushed_authorization_requests'),
('fapi2.0', 'DPOP', 'dpop_bound_access_tokens'),
('fapi2.0', 'PKCE', 'require_pkce'),
('fapi2.0', 'strict_audience_check', 'client_auth_assertion_audience');
```

## Configuration

### Server Profile Property

Set the server profile in `application-default.properties`:

```properties
# Server profile can be either of fapi2.0, none, or custom profile names defined in server_profile table
mosip.esignet.server.profile=none
```

Or via environment variable:

```bash
MOSIP_ESIGNET_SERVER_PROFILE=fapi2.0
```

### Supported Values

| Profile   | Description                                                    |
|-----------|----------------------------------------------------------------|
| `none`    | No enforced defaults; behavior driven by client configuration  |
| `fapi2.0` | FAPI 2.0 Security Profile: PAR, DPoP, PKCE enabled             |
| Custom    | Any profile name defined in the `server_profile` table         |

## Feature Flags

The following features can be enabled via server profile:

| Feature                 | Config Key                               | Description                                |
|-------------------------|------------------------------------------|--------------------------------------------|
| PAR                     | `require_pushed_authorization_requests`  | Pushed Authorization Request mandatory     |
| DPOP                    | `dpop_bound_access_tokens`               | DPoP bound access tokens mandatory         |
| PKCE                    | `require_pkce`                           | Proof Key for Code Exchange mandatory      |
| STRICT_AUDIENCE_CHECK   | `client_auth_assertion_audience`         | Strict audience validation enabled         |
| JWE                     | `userinfo_response_type`                 | UserInfo response encrypted with JWE       |

## Runtime Behavior

### Priority Rules

1. **Server Profile Active (`fapi2.0`)**:
   - Server profile features are enforced for all clients
   - Client-level configuration flags are **overridden** by profile-defined features
   
2. **Server Profile = `none`**:
   - No server-level enforcement
   - Client-level `additionalConfig` flags determine behavior
   - Relying party can configure PAR, DPoP, PKCE via Client Management API

### PAR Enforcement

When PAR is enabled via the server profile, direct calls to `/authorize` endpoint without a valid `request_uri` will be rejected with an `invalid_request` error. Clients must use the `/oauth/par` endpoint first.

### PKCE Enforcement

When PKCE is enabled, the `code_challenge` parameter must be present in the authorization request. Requests without PKCE will be rejected with a `use_pkce` error.

### DPoP Enforcement

When DPoP is enabled, access tokens are bound to DPoP proofs. The token endpoint will require a valid DPoP header, and the userinfo endpoint will validate the DPoP binding.

## Adding New Profiles

To add a new server profile:

1. Insert records in `server_profile` table:

```sql
INSERT INTO server_profile (profile_name, feature, additional_config_key) VALUES
('new_profile', 'PAR', 'require_pushed_authorization_requests'),
('new_profile', 'DPOP', 'dpop_bound_access_tokens');
-- Add more features as needed
```

2. Update application configuration:

```properties
mosip.esignet.server.profile=new_profile
```

3. Restart eSignet application to load the new profile.

> **Note**: No API endpoint is provided to modify profiles at runtime. This is intentional to ensure profile immutability during operation.

## Client Configuration (when profile = none)

When the server profile is set to `none`, clients can configure features via the client management API:

```json
{
  "additionalConfig": {
    "require_pushed_authorization_requests": true,
    "dpop_bound_access_tokens": true,
    "require_pkce": true
  }
}
```

## Backward Compatibility

- Existing clients created before this feature continue to function
- Default profile is `none`, ensuring no breaking changes for existing deployments
- When switching to `fapi2.0` profile, review client configurations for compliance

## Summary of Server Profile Configurations

| Configuration     | Description                                        | Property                        |
|-------------------|----------------------------------------------------|---------------------------------|
| Server Profile    | Global profile for security feature enforcement    | `mosip.esignet.server.profile`  |
| Default Value     | `none`                                             |                                 |
| FAPI 2.0 Features | PAR, DPoP, PKCE, Strict Audience Check             |                                 |

## References

- [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-2_0-security-profile.html)
- [OAuth 2.0 Pushed Authorization Requests (PAR)](https://www.rfc-editor.org/rfc/rfc9126)
- [OAuth 2.0 Demonstrating Proof-of-Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)
- [OAuth 2.0 PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [eSignet FAPI 2.0 Compliance Design](./fapi2-compliance.md)
