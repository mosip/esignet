# Resource Servers and Permission Scopes

This document covers `resource_servers` configuration in esignet-service: how OAuth "permission"
scopes (non-OIDC scopes such as `payment:pay` or a Verifiable-Credential scope like
`mosip_identity_vc_ldp`) are bound to a resource server, authorized, consented to, and carried
through to the issued access token. It is aimed at deployment operators configuring
`esignet-service/data/deployment.yaml` and at integrators building OAuth clients that request
permission scopes.

## 1. Why this exists

Standard OIDC scopes (`openid`, `profile`, `email`, ...) map to user-attribute *claims* — they
control what goes into the ID token / userinfo response, via `scope_claims` in
`deployment.yaml`. Some clients also need to request *permissions* that are not attribute claims
at all — e.g. "issue this Verifiable Credential", or "make a payment on the user's behalf". These
are handled separately, as [RFC 8707](https://www.rfc-editor.org/rfc/rfc8707) resource-indicator
scopes:

- The requested permission scope(s) get bound to a specific **resource server** (the downstream
  API the resulting access token is meant for — e.g. the VCI credential endpoint).
- The access token's `aud` claim is set to that resource server's identifier.
- The user is shown a consent screen listing the requested permissions (in addition to any
  attribute consent), and only what they approve — intersected with what the client is actually
  allowed to request — ends up in the token's `scope`.

`resource_servers` in `deployment.yaml` is where this is configured.

## 2. Configuring `resource_servers`

```yaml
resource_servers:
  - id: esignet-default
    identifier: "https://esignet.example.com/resource"
    default: true
    scopes:
      payment:pay: "Make payments on the user's behalf"
  - id: vci-credential
    identifier: "${MOSIP_ESIGNET_HOST}/v1/esignet/vci/credential"
    scopes:
      mosip_identity_vc_ldp: "Issue a Verifiable Credential"
```

| Field | Meaning |
|---|---|
| `id` | Internal identifier for this resource server. Used for lookups after a request has already resolved to it; never sent by a client. Must be unique across the list. |
| `identifier` | The RFC 8707 `resource` request-parameter value **and** the access token's `aud` claim. Must be an absolute URI (e.g. `https://...`) — a bare `id`-style slug will be rejected by the OAuth layer as `invalid_target`. Must be unique across the list. |
| `default` | Whether this resource server is used when a request carries permission scopes but **no** `resource` parameter. At most one entry may set this to `true` — see [§4](#4-startup-validation-fail-loudly-on-ambiguous-config). |
| `scopes` | Map of permission-scope name → human-readable description (shown on the consent screen). A scope only exists, from the OAuth layer's point of view, if it's listed here on some resource server. |

`identifier` (and any other string value in this file) can reference an environment variable with
plain `${VAR}` syntax, since the whole file is expanded via `os.ExpandEnv` before parsing — see the
config-precedence note at the top of `deployment.yaml`. Note that this is *plain* substitution
only: bash-style `${VAR:-default}` is not supported and will silently expand to `""`.

## 3. How a request resolves to a resource server

1. **Client sends `resource=<identifier>`** (RFC 8707): the request binds to the resource server
   whose `identifier` matches exactly. If no entry matches, the request is rejected with
   `invalid_target`.
2. **Client omits `resource`**: the request binds to whichever resource server has `default: true`.
   If none is configured as default, and the request carries any permission scope, it's rejected
   with `invalid_target`.
3. Once bound, any requested permission scope **not** listed in that resource server's `scopes`
   map is silently dropped (not rejected) before the flow even starts — it never reaches the
   consent screen or the token.

There is no way to auto-select a resource server by matching the requested scope against multiple
resource servers' `scopes` maps — the underlying OAuth engine never passes the requested scopes
into resource-server resolution, only the `resource` parameter (or its absence). If a deployment
has more than one resource server, clients that want the non-default one **must** send `resource=`
explicitly; there's no server-side way to infer it from `scope=` alone.

## 4. Startup validation: fail loudly on ambiguous config

`deployment.yaml` is rejected at startup (the process will not come up) if:

- more than one resource server has `default: true`, or
- two entries share the same `id`, or
- two entries share the same `identifier`.

This is intentional: an ambiguous default resource server used to fail *silently* — permission
scopes for the "wrong" default were quietly dropped, so no consent purpose was ever shown for
them, with no error anywhere. Fixing the config is required; there is no fallback behavior for an
ambiguous set of defaults.

## 5. Per-client scope gating

Resolving *which resource server* and confirming a scope is *known* to it is necessary but not
sufficient — a scope is only actually authorized for a specific request if the requesting OAuth
client is also allowed to request it. That's configured independently, per client, via
`additionalConfig.allowed_authorization_scopes` on the OAuth client (client-management API /
`clientmgmt`):

```json
{
  "clientId": "inji-wallet",
  "additionalConfig": {
    "allowed_authorization_scopes": ["mosip_identity_vc_ldp"]
  }
}
```

A permission scope is only ever authorized when it is **both**:

1. defined on the resource server the request resolved to (§3), **and**
2. present in the requesting client's `allowed_authorization_scopes`.

This two-gate design exists because the two concerns are genuinely independent: the resource
server registry says which permissions *exist* and what audience they belong to (a deployment-wide
policy); the per-client allowlist says which clients may *request* them (a per-integration
policy). A client with no `allowed_authorization_scopes` configured is granted no permission scopes
at all — this is a fail-closed default, not an omission to fix.

## 6. End-to-end: from request to issued token

Once a client's request has resolved a resource server and the requested scopes have been
downscoped to that resource server's known `scopes` (§3), the following happens inside the
authentication flow, in order:

1. **`authorization_check` node** (`eSignetAuthorizationExecutor`,
   `esignet-service/internal/engine/executors/authorization_executor.go`) — intersects the (already
   resource-server-downscoped) requested permissions with the client's
   `allowed_authorization_scopes` and writes the result to flow runtime data as
   `authorized_permissions`.
2. **`consent_check` node** — builds a consent-screen purpose listing `authorized_permissions`
   (`esignet-service/internal/engine/consent_provider.go`, `buildPrompt`). The user
   approves/denies individual permissions; approved ones are recorded as `consented_permissions`.
3. **`auth_assert` node** — intersects `consented_permissions` with `authorized_permissions` again
   (defense against a stale stored-consent record granting a permission the user, or the client, no
   longer holds) and embeds the result in a signed internal assertion.
4. **Token issuance** — the assertion's permission set becomes the authorization code's scopes; at
   `/token`, the resource server is re-resolved (same rules as §3) to set the access token's `aud`,
   and the final scope set is downscoped against it one more time.

The upshot: a permission scope only survives to the final `scope` claim if it was requested, valid
for the resolved resource server, allowed for the client, and approved by the user, at every one of
these steps. Any single "no" along the way drops it — it does not fail the whole request.

## 7. Overriding per environment without rebuilding the image

The whole `resource_servers` list can be replaced (not merged) at deploy time via the
`MOSIP_ESIGNET_RESOURCE_SERVERS_JSON` environment variable, set to a JSON array in the same shape
as the YAML:

```
MOSIP_ESIGNET_RESOURCE_SERVERS_JSON='[{"id":"vci-credential","identifier":"https://esignet.example.com/v1/esignet/vci/credential","default":true,"scopes":{"mosip_identity_vc_ldp":"Issue a Verifiable Credential"}}]'
```

When set, it fully replaces whatever `resource_servers:` is in `deployment.yaml` (the file's value
is not merged with it), and is subject to the same validation as §4 — an ambiguous JSON override
fails startup the same way an ambiguous YAML config does.

Leave the env var unset (or empty) to use the value from `deployment.yaml` as-is.

## 8. Migrating from the legacy (Java) esignet configuration

The previous Spring-based esignet used flat properties for the same concept:

```properties
mosip.esignet.supported.credential.scopes={'mosip_identity_vc_ldp'}
mosip.esignet.credential.scope-resource-mapping={ 'mosip_identity_vc_ldp': '${mosip.esignet.domain.url}${server.servlet.path}/vci/credential' }
```

This maps onto `resource_servers` directly: group scopes by the URL they map to (usually all
credential scopes map to the same shared credential endpoint), and each distinct URL becomes one
resource server entry:

```yaml
resource_servers:
  - id: vci-credential
    identifier: "${MOSIP_ESIGNET_HOST}/v1/esignet/vci/credential"
    default: true
    scopes:
      mosip_identity_vc_ldp: "Issue a Verifiable Credential"
```

If VCI wallet clients cannot be relied on to send `resource=` explicitly (most OID4VCI wallets
don't), mark the VCI resource server `default: true` so it resolves automatically — this
reproduces the old scope→URL lookup behavior with no client-side changes. If you also need other,
non-VCI resource servers configured, only one resource server total can be `default`; the rest
require clients to pass `resource=` explicitly (§3). You must also register
`allowed_authorization_scopes` for each client that should be able to request a credential scope
(§5) — the legacy config had no per-client equivalent of this gate.

## 9. Troubleshooting

**Server won't start, error mentions `resource_servers`**: two entries are marked `default: true`,
or share an `id`/`identifier`. Fix `deployment.yaml` (or the `MOSIP_ESIGNET_RESOURCE_SERVERS_JSON`
override, if set) so at most one is default and all `id`/`identifier` values are unique.

**Consent screen never shows a permissions purpose / token has no scope for a permission scope you
requested**: check, in order —

1. Did the request resolve the resource server you expected? If you didn't send `resource=`, it
   resolved to whichever entry is `default: true` — not necessarily the one whose `scopes` map
   contains the scope you requested (§3).
2. Is the scope actually listed under that resource server's `scopes` map?
3. Does the requesting client have `additionalConfig.allowed_authorization_scopes` including that
   scope (§5)?
4. Was `prompt=consent` (or no prior stored consent) present, so the consent step actually ran
   rather than being skipped by a matching stored-consent hash?

**Access token has `aud: ""`**: the request carried a permission scope but no resource server
could be resolved — no `resource=` was sent, and no resource server is configured `default: true`.
Either configure a default, or have the client send `resource=` explicitly.
