# Audit Plugin Integration

The Audit plugin (called an *observability provider* in the engine) receives a stream of lifecycle events from eSignet's authentication-flow engine and forwards them to wherever an organization wants its audit trail to live — a SIEM, a log pipeline, a compliance database, or nowhere at all beyond the application log.

## The interface

A provider is a Go package that implements `providers.ObservabilityProvider` (package `github.com/thunder-id/thunderid/pkg/thunderidengine/providers`):

```go
// ObservabilityProvider defines the interface for the observability provider.
type ObservabilityProvider interface {
	// PublishEvent publishes an event to the observability system.
	// This is a no-op if observability is disabled.
	// The context carries the request trace ID used for correlated logging.
	PublishEvent(ctx context.Context, evt *Event)

	// IsEnabled returns true if observability is enabled and operational.
	IsEnabled() bool
}
```

Only two methods: `PublishEvent`, called once per lifecycle event, and `IsEnabled`, which the engine may use to skip event construction entirely when observability isn't active.

### The event payload

```go
// Event represents a generic analytics or audit event in the system.
type Event struct {
	TraceID   string                 `json:"trace_id"`   // correlates related events across the system
	EventID   string                 `json:"event_id"`   // unique identifier for this specific event
	Type      string                 `json:"type"`        // event type/name, e.g. "FLOW_STARTED"
	Timestamp time.Time              `json:"timestamp"`
	Component string                 `json:"component"`   // source component, e.g. "FlowEngine"
	Status    string                 `json:"status"`       // outcome: success / failure / in_progress / pending
	Data      map[string]interface{} `json:"data,omitempty"` // event-specific fields
}
```

`Status` is one of the untyped constants `providers.StatusSuccess` (`"success"`), `providers.StatusFailure` (`"failure"`), `providers.StatusInProgress` (`"in_progress"`), or `providers.StatusPending` (`"pending"`).

### What events are published

The events themselves come from the engine's flow executor, not from esignet-service code — a provider only ever receives them, it never generates them. The flow engine emits, with `Component` `FlowEngine`:

| Event `Type` | When |
|---|---|
| `FLOW_STARTED` | A new authentication flow begins |
| `FLOW_NODE_EXECUTION_STARTED` | Each flow node (screen, executor step) begins executing |
| `FLOW_NODE_EXECUTION_COMPLETED` | A node completes successfully |
| `FLOW_NODE_EXECUTION_FAILED` | A node fails |
| `FLOW_USER_INPUT_REQUIRED` | The flow is waiting on user input |
| `FLOW_COMPLETED` | The flow reaches its end node |
| `FLOW_FAILED` | The flow terminates in failure |

`Data` commonly carries some subset of: `user_id`, `username`, `client_id`, `app_id`, `execution_id`, `flow_type`, `node_id`, `node_type`, `node_status`, `executor_name`, `executor_type`, `step_number`, `attempt_number`, `auth_method`, `redirect_to`, `failed_step`, `scope`, `grant_type`, `jti`, `revocation_reason`, `message`, `error`, `duration_ms`, `latency_us`, `trace_parent` — which keys are present depends on the event type.

## How it's wired

There is no independent audit-provider selector — the observability provider is returned alongside the authn provider from the same per-backend `Init(...)` function (see [Authn Provider Integration](authn-provider-integration.md#how-to-register-a-new-provider)):

```go
func Init(...) (shared.ConsolidatedAuthnProvider, providers.ObservabilityProvider, error)
```

Whichever value your `Init` returns as the second return value is registered as the engine's observability provider for the lifetime of the process. If you're building a new authn provider and don't have an audit sink of your own, return the built-in no-op logger:

```go
return authnProvider, shared.NewNoopAuditor(), nil
```

## Reference implementations

### No-op / logging auditor

`shared.NewNoopAuditor()` ([`internal/engine/shared/noop_auditor.go`](../esignet-service/internal/engine/shared/noop_auditor.go)) is the fallback used by both the `mock` and `sunbird` providers — it has no external dependency; `PublishEvent` just logs the event's fields (id, type, status, component, trace ID, timestamp, data) through the application logger, and `IsEnabled` always returns `true`.

### MOSIP audit-manager auditor

`mosip.NewAuditor(...)` ([`internal/engine/mosip/auditor.go`](../esignet-service/internal/engine/mosip/auditor.go)) is the reference implementation of a real external sink — it maps each `Event` onto a MOSIP `mosip-audit-manager` record (`AuditRequest`, defined in [`internal/engine/mosip/model.go`](../esignet-service/internal/engine/mosip/model.go)) and posts it over HTTP. Used only when `MOSIP_ESIGNET_AUTHN_PROVIDER=mosip`.

**Event → audit record mapping** (see `AuditRequest` in `model.go` for the exact field/JSON-tag list):

| `AuditRequest` field | Derived from |
|---|---|
| `EventID`, `EventName` | `evt.Type` |
| `EventType` | `"SUCCESS"` / `"ERROR"` / `strings.ToUpper(evt.Status)` |
| `ActionTimeStamp` | `evt.Timestamp`, UTC, `2006-01-02T15:04:05.000Z` |
| `HostName`, `HostIP` | `os.Hostname()` (falls back to `"localhost"`) |
| `ApplicationID`, `ApplicationName` | fixed `"eSignet"` |
| `SessionUserID`, `SessionUserName` | `evt.Data["user_id"]` or `["username"]`, else `"no-user"` |
| `ID` | `evt.Data["execution_id"]` |
| `ModuleName`, `ModuleID` | `evt.Component` |
| `Description` | JSON of a fixed subset of `evt.Data`: `client_id`, `flow_type`, `app_id`, `error`, `duration_ms`, `redirect_to`, `failed_step`, `node_id` |

**Publishing behavior:**

- `PublishEvent` is fire-and-forget: it launches a goroutine per event, derived from `context.Background()` (not the triggering request's context, since the audit post must outlive that request) with a 15-second timeout, and carries the original trace ID forward so log lines still correlate.
- The record is POSTed to the configured audit-manager URL as `{"id": "ida", "requesttime": "<UTC ISO-8601>", "request": <AuditRequest>}` (the lowercase `requesttime` key is the actual wire format — see `AuditRequestWrapper` in `model.go`), with an `Authorization` cookie carrying a token obtained from MOSIP's authmanager (client-credentials style: client ID + secret + app ID) — see [`internal/engine/mosip/utils.go`](../esignet-service/internal/engine/mosip/utils.go) for the token-caching `tokenProvider`.
- On a `401`/`403` response the cached auth token is purged and the post is retried once with a freshly fetched token.
- Any other non-2xx response, or a transport failure, is logged and otherwise swallowed — a failed audit post never fails the authentication flow that triggered it.
- The shared `*http.Client` (`config.NewHTTPClient`) sets no `CheckRedirect`, so it follows redirects with Go's default policy: sensitive headers are stripped when the redirect target's *host* differs from the original, but that check is host-only — a same-host `https://` → `http://` redirect would still carry the `Authorization` cookie onto a cleartext connection. This is only reachable if the configured audit-manager URL is compromised or intentionally set up with such a redirect, but it means the URL scheme has to be trusted, not just the hostname.

**Environment variables** (read by `mosip.LoadConfig()`, `esignet-service/internal/engine/mosip/config.go`):

| Variable | Default | Purpose |
|---|---|---|
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT_MANAGER_URL` | `<MOSIP_API_INTERNAL_HOST>/v1/auditmanager/audits` | Audit-manager ingestion endpoint |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH_TOKEN_URL` | `<MOSIP_API_INTERNAL_HOST>/v1/authmanager/authenticate/clientidsecretkey` | authmanager token endpoint |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_CLIENT_ID` | `mosip-ida-client` | authmanager client id |
| `MOSIP_IDA_CLIENT_SECRET` | *(required, no default)* | authmanager client secret |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_APP_ID` | `ida` | authmanager app id |
| `MOSIP_API_INTERNAL_HOST` | *(required, no default)* | Base URL the defaults above derive from |

`mosip.LoadConfig()` does not validate the URL scheme of `AUTH_TOKEN_URL` or `AUDIT_MANAGER_URL` — both requests carry credentials (the client secret, and the resulting `Authorization` cookie), so deployments must set these to `https://` endpoints with certificate verification themselves; a misconfigured `http://` value will not be rejected.

## How to implement your own audit plugin

1. Define an `Event → <your record type>` mapping for whatever fields your audit system needs — you don't have to use every field on `Event`, and you can enrich the record with values pulled out of `Data`.
2. Implement `PublishEvent` as fire-and-forget (a goroutine, or a buffered channel to a background worker) so a slow or unavailable audit sink never blocks or fails the authentication flow that generated the event. Derive the goroutine's context from `context.Background()`, not the request context, for the same reason — but carry the trace ID forward for correlated logging.
3. Implement `IsEnabled` to report whether your sink is currently configured/reachable; return `true` unconditionally if that distinction doesn't apply to your implementation.
4. Return your type from your provider package's `Init(...)` function as the second return value.
