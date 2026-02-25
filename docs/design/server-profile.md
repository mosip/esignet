# Overview

This document describes the design and configuration of OpenID Server Profiles in eSignet. Server profiles enable organizations to enforce standards-based security features across all OIDC clients in a deployment, ensuring compliance with security standards like FAPI 2.0 while providing flexibility for custom security configurations.

It covers the following topics:
- Profile Configuration
- Profile-to-Feature Mapping
- Creating Custom Profiles
- Client Registration Behavior
- Runtime Execution

# Profile Configuration

On application startup, eSignet reads the global OpenID profile value from `mosip.esignet.server.profile` in `application_default.properties`.

The configured profile determines the default standards behavior for the deployment:

| Profile | PAR | DPoP | JWE | PKCE | Description |
|---------|-----|------|-----|------|-------------|
| `fapi2.0` | ✅ | ✅ | ❌ | ✅ | FAPI 2.0 Security Profile compliant - Enforces Pushed Authorization Requests, DPoP token binding, and PKCE for enhanced security |
| `none` | ❌ | ❌ | ❌ | ❌ | No enforced defaults; all security features are driven by client-level configuration via `additionalConfig` |

## Configuration Property

```properties
# Server profile options:
#   fapi2.0              - FAPI 2.0 compliance with PAR, DPoP, and PKCE enforced
#   none                 - No enforced defaults; client-level configuration applies
#   <custom-defined>     - Custom profile defined in server_profile table
mosip.esignet.server.profile=none
```

# Profile-to-Feature Mapping

eSignet loads the profile-to-feature mapping from a database table (`server_profile`) that defines which features (PAR, DPoP, JWE, PKCE) are enabled for each profile. This database-driven approach allows for dynamic profile management without code changes.

## Server Profile Table Structure

| Profile | Feature | Additional Config Key |
|---------|---------|----------------------|
| `fapi2.0` | PAR | `require_pushed_authorization_requests` |
| `fapi2.0` | DPOP | `dpop_bound_access_tokens` |
| `fapi2.0` | PKCE | `require_pkce` |

The table maps each profile to its enabled features along with the corresponding `additionalConfig` key that controls the feature at runtime.

# Creating Custom Profiles

You can create your own custom profile with any combination of PAR, DPoP, JWE, and PKCE features by adding new entries to the `server_profile` table. This flexibility allows organizations to define security profiles that match their specific compliance requirements without requiring any code modifications.

## Example: Creating a Custom Profile

To create a profile named `custom-secure` that enforces only PAR and PKCE:

```sql
INSERT INTO server_profile (profile_name, additional_config_key, feature) VALUES
('custom-secure', 'require_pushed_authorization_requests', 'PAR'),
('custom-secure', 'require_pkce', 'PKCE');
```

After adding the profile to the database, configure eSignet to use it:

```properties
# Can be set to: fapi2.0 | none | <custom-defined-profile>
mosip.esignet.server.profile=custom-secure
```

This allows deployments to define their own security posture by mixing and matching features as needed.

# Client Registration Behavior

When an OIDC client is created or updated via the Client Management API, the behavior depends on the configured global profile:

## Profile Mode: `none`

The relying party has full control over security features. They may explicitly configure PAR, DPoP, PKCE, and JWE via the `additionalConfig` field in the client registration request. This mode is suitable for deployments where different clients require different security configurations.

## Profile Mode: Specific Profile (`fapi2.0` or custom)

The client-level flags provided in `additionalConfig` are ignored and the OIDC transaction will be updated by the selected profile's enforced feature set. This ensures uniform security enforcement across all clients in the deployment.

# Runtime Execution

During transaction initiation and runtime execution:

1. **Profile Resolution**: eSignet resolves the effective configuration for the transaction based on the selected global profile from the `server_profile` table.

2. **Feature Enforcement**: Authentication and authorization flows strictly enforce PAR, DPoP, JWE, and PKCE according to the active profile. Non-compliant requests are rejected with appropriate error responses.

3. **Client Override Handling**: Client-specific overrides (via `additionalConfig`) are applied **only when the profile is `none`**. For all other profiles, the server-level configuration takes precedence.

# Summary

The OpenID Server Profile feature provides a powerful mechanism for organizations to:

- **Enforce compliance**: Apply security standards like FAPI 2.0 uniformly across all clients
- **Maintain flexibility**: Allow client-specific configurations when using `none` profile
- **Extend easily**: Create custom profiles via database entries without code changes
- **Ensure consistency**: Override client-level settings with server-level profiles for predictable security behavior

# References

- [OpenID FAPI2 Baseline Profile](https://openid.net/specs/fapi-2_0-baseline.html)
- [FAPI 2.0 Compliance in eSignet](fapi2-compliance.md)
- [OAuth 2.0 Pushed Authorization Requests (PAR)](https://www.rfc-editor.org/rfc/rfc9126)
- [OAuth 2.0 Demonstrating Proof-of-Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)

