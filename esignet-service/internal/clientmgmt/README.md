# Client-management PATCH flow

This document traces `PATCH /client-mgmt/client/{client_id}` through the HTTP handler, service, validation, and database layer. It follows the current PATCH policy: **validate supplied values and affected dependencies, then update only selected database columns**.

A transaction and `SELECT ... FOR UPDATE` protect the read, validation, and write as one operation for a client row. PATCH does not build a full PUT-shaped request or run full PUT validation.

```text
PATCH /client-mgmt/client/{client_id}
  ? handler.patchClient: read envelope and record which fields are present
  ? service.PatchClient: reject empty PATCH, begin transaction, lock and read row
  ? ValidatePatch: check supplied values and affected key/config dependencies
  ? preparePatchParams: serialize selected values; read stored name only if needed
  ? db.Queries.PatchClient: UPDATE selected columns and upd_dtimes
  ? service.PatchClient: convert returned row, commit, invalidate cache
  ? handler.patchClient: write response
```

The route is registered in [handler.go](handler.go). This PATCH endpoint exists for `/client-mgmt/client/{client_id}`; the nearby OIDC and OAuth client routes use PUT.

## 1. Handler: read and decode the request

`Handler.patchClient` in [handler.go](handler.go) performs these steps:

1. Read `client_id` from the path. An empty ID produces `invalid_input`.
2. Limit the body to 1 MiB, read it, and decode the outer `PatchRequestWrapper`. Malformed input produces `invalid_input`.
3. Require a nonblank `requestTime` and a non-null `request` member. These failures produce `invalid_request`. This handler checks that `requestTime` is present; it does not itself validate the timestamp format.
4. Call `DecodePatchRequest`. This returns a `PatchClientRequest` containing values and a `PatchFields` mask recording which JSON attributes were supplied.
5. Call `Service.PatchClient`. Map validation and not-found errors to the client-management API envelope.
6. On success, log the selected fields and return HTTP 200 with `clientId` and the canonical status.

The presence mask in [model.go](model.go) distinguishes an omitted attribute from an explicitly supplied empty value. For example, an omitted `logoUri` and `"logoUri": ""` both decode to an empty Go string, but only the latter sets `fields.LogoURI`. An explicit empty value is validated; an omitted value is untouched.

`encPublicKey` has three states: omitted means retain the key, `null` means clear it, and a JWK object means replace it. Unknown request attributes are rejected. An empty `"request": {}` reaches the service with no flags and is rejected there before database access.

## 2. Service: lock, validate, prepare, write

The main method is `Service.PatchClient` in [service.go](service.go).

### Reject an empty PATCH and lock the row

The first check rejects `fields == (PatchFields{})` as `invalid_request`. This prevents a timestamp-only UPDATE for an empty request.

`lockClient` then starts a database transaction, binds the query object to it, and calls `GetClientForUpdate`. The query in [db/db.go](db/db.go) is `SELECT <client columns> FROM client_detail WHERE id = ? FOR UPDATE`, with placeholders adapted to the driver. The row lock is held through validation and the UPDATE until commit or rollback. A second PATCH of the same row waits at its locking read and then sees the first transaction's committed changes under the usual isolation settings. See the [PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html) and [MySQL InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) documentation.

If the row is absent, the service maps `sql.ErrNoRows` to `ErrClientNotFound`. The deferred rollback releases the lock on any later error. Injected fake-querier tests use a branch without a real transaction; production services use the transaction path.

The lock is taken **before validation**. A write-time database lock alone would allow two requests to validate against the same earlier state.

### Validate the requested change

`ValidatePatch` in [validate.go](validate.go) takes the supplied `PatchClientRequest`, its `PatchFields` mask, the stored key-presence flag, and the stored configuration for the key-clear dependency. It returns a validated copy of the request. If status was supplied, that copy contains canonical `ACTIVE` or `INACTIVE`; status is normalized once.

Only fields marked present are validated. Supplied lists are replacement values with these bounds:

| Supplied list | Allowed number of items |
|---|---:|
| `redirectUris` | 1?5 |
| `userClaims` | 1?30 |
| `authContextRefs` | 1?30 |
| `grantTypes` | 1?3 |
| `clientAuthMethods` | 1?3 |

Existing URI, name, element, uniqueness, and encryption-algorithm validators still apply. A supplied empty required list fails. An omitted list is neither decoded for validation nor rewritten. An unrelated retained value that fails a current business rule does not by itself block a valid PATCH.

The key/configuration dependency is checked only when it can change:

- If `encPublicKey` is omitted, effective key presence comes from the locked row.
- If it is `null`, the resulting client has no key.
- If a JWK is supplied, the JWK is validated and the resulting client has a key.
- If `additionalConfig` is supplied, its replacement value is validated against that effective key state.
- If the key is cleared and configuration is omitted, the retained configuration is validated against the absence of a key.
- An unrelated logo, status, or list change does not revalidate retained configuration. A valid key replacement alone likewise does not require full validation of retained configuration under the current key-presence rule.

A resulting configuration requesting JWE without an encryption key is rejected. Supplying `additionalConfig` replaces the entire column value; individual JSON properties are not recursively merged. The low-level PUT and Create validators and their callers remain unchanged.

Validation runs **after the locking read and inside the transaction**. This preserves the not-found-before-field-validation order for nonempty PATCH requests.

### Prepare only selected database parameters

`preparePatchParams` in [service.go](service.go) receives the validated request and the stored `name` string. It sets only the values and `db.PatchClientFields` flags selected by the request.

| PATCH attribute | Selected DB column(s) |
|---|---|
| `clientName`, `clientNameLangMap` | `name` |
| `logoUri` | `logo_uri` |
| `redirectUris` | `redirect_uris` |
| `userClaims` | `claims` |
| `authContextRefs` | `acr_values` |
| `grantTypes` | `grant_types` |
| `clientAuthMethods` | `auth_methods` |
| `status` | `status` |
| `additionalConfig` | `additional_config` |
| `encPublicKey` | `enc_public_key`, `enc_public_key_hash`, `enc_public_key_cert` |

`clientName` and `clientNameLangMap` share one stored JSON column. If only one is supplied, preparation parses the stored name to retain the omitted component. If both are supplied, it serializes both submitted components without parsing the stored name. The submitted language map replaces the old map; it is not recursively merged. If neither is supplied, the stored name is not parsed or serialized during preparation.

Only supplied lists and configuration are serialized. A supplied non-null encryption key is stored with its computed hash; its certificate column is cleared. Explicit `null` selects all three encryption-key columns with SQL `NULL` values. Serialization errors are returned before UPDATE.

The service also sets `upd_dtimes` to the current UTC time for every accepted PATCH.

### Write, convert response, commit, invalidate cache

The transaction-bound `q.PatchClient` performs the UPDATE. The service maps missing rows to `ErrClientNotFound` and duplicate encryption-key hashes to `invalid_public_key`.

`toResponse` converts the returned database row **before commit**. It still decodes the full returned row, including stored JSON lists. If that conversion fails, the service rolls back. This response-conversion behavior is separate from PATCH's targeted business validation.

The transaction commits next, releasing the lock. Cache invalidation follows commit. If cache invalidation fails, the database write has already committed, but the service returns an error so the caller sees the partial failure. Finally the handler exposes the minimal API response containing `clientId` and `status`.

## 3. Database layer: the selected-column UPDATE

`db.Queries.PatchClient` in [db/db.go](db/db.go) has a fixed, hardcoded mapping from field masks to columns. It appends `column = ?` and its value only when the corresponding mask is true. It always appends `upd_dtimes = ?`, followed by `WHERE id = ?`.

For example, status alone generates:

```sql
UPDATE client_detail SET status = ?, upd_dtimes = ? WHERE id = ?
```

A request for logo and status generates:

```sql
UPDATE client_detail SET logo_uri = ?, status = ?, upd_dtimes = ? WHERE id = ?
```

Absent request attributes do not appear in `SET`. `upd_dtimes` is updated but is not an optimistic-version predicate in `WHERE`; the transaction's row lock protects the read/validate/write sequence.

PostgreSQL rebinds `?` to `$1`, `$2`, and so on, appends `RETURNING`, and obtains the row from the UPDATE. MySQL runs the UPDATE, then reads the row back in the same transaction. The MySQL path does not use `RowsAffected()` to decide whether a client exists, so a PATCH of an already-stored value is not mistaken for a missing row.

## Scenario 1: status PATCH, then a parallel logo PATCH

Assume `client-1` is `ACTIVE`, has `logo_uri = https://example.org/old.png`, and has stored `redirect_uris = []`. The stored list is well-formed JSON but fails today's minimum-item business rule. Request A sends:

```json
{
  "requestTime": "2026-11-17T10:00:00Z",
  "request": { "status": "inactive" }
}
```

1. The handler decodes `Status = "inactive"` and `fields.Status = true`.
2. The request passes the empty-PATCH check. The service opens a transaction and locks `client-1`.
3. `ValidatePatch` validates the supplied status and returns a request copy with `Status = "INACTIVE"`. It does not decode or validate the retained redirect list.
4. Preparation selects only `status` and sets its value to `INACTIVE`.
5. The DB layer updates `status` and `upd_dtimes`, returning the row. `toResponse` can decode the well-formed stored `[]`.
6. The service commits, invalidates the cache, and returns `INACTIVE`.

The row's unrelated redirect list remains `[]`. This is the intended policy change: PATCH does not make an unrelated stored business-rule failure the caller's responsibility. If the stored list were malformed JSON instead, `toResponse` could still fail before commit; this refactor does not change response conversion.

Now request B arrives at nearly the same time with `{"logoUri":"https://example.org/new.png"}`. If A obtains the lock first:

| Step | Request A: status | Request B: logo |
|---|---|---|
| 1 | Locks and reads the row. | Begins a transaction. |
| 2 | Validates status and prepares only `status`. | Waits at `SELECT ... FOR UPDATE`. |
| 3 | Updates status and commits. | Obtains the lock after A commits; reads the updated status. |
| 4 | Invalidates cache and returns. | Validates logo and prepares only `logo_uri`. |
| 5 | Done. | Updates logo, commits, and invalidates cache. |

The final row contains both `INACTIVE` and the new logo. B never rewrites status. If B locks first, the final row still contains both changes; B's response may report the status that existed when B completed.

## Scenario 2: JWE configuration races with clearing the key

Assume `client-1` starts with a valid encryption key and `additional_config = {"userinfo_response_type":"JWS"}`.

Request A submits `{"additionalConfig":{"userinfo_response_type":"JWE"}}`. Its locked read sees the key. `ValidatePatch` checks the replacement configuration against effective key presence and accepts it. Preparation selects only `additional_config`; the DB updates that column and `upd_dtimes`.

Request B concurrently submits `{"encPublicKey":null}`.

**If A locks first:** A commits JWE with the retained key. B then reads JWE plus key. Clearing the key would leave JWE without a key, so `ValidatePatch` returns `invalid_additional_config`; B rolls back without UPDATE. Final state: **JWE plus key**.

**If B locks first:** B reads JWS plus key. It can clear all three key-related columns while JWS remains. After B commits, A reads JWS without a key. Switching to JWE would leave JWE without a key, so A is rejected before UPDATE. Final state: **JWS without key**.

In either order, the second request validates against the first request's committed row. The invalid JWE-without-key combination is never written by these PATCH operations. Supplying a valid key and JWE configuration together in one PATCH is accepted, because validation evaluates their combined resulting state.

## Errors and boundaries

- A missing client becomes API error `invalid_client_id`.
- Empty PATCH becomes `invalid_request` before any database read.
- Invalid supplied values return their existing field-specific validation codes. An invalid change never reaches UPDATE.
- Expected client-management errors use the HTTP 200 response envelope with `errors`; unexpected failures use HTTP 500 with `server_error`.
- A successful no-op value PATCH still selects the supplied column and updates `upd_dtimes`; MySQL reads the row back instead of interpreting zero changed rows as ?not found.?
- Direct database writes and other code paths have their own validation behavior. The row-lock sequence described here applies to this service's transactional PATCH path.

The two questions to keep separate are: **Which supplied values and resulting dependencies must be valid?** and **Which columns may this PATCH write?** `ValidatePatch` answers the first; `preparePatchParams` and `db.Queries.PatchClient` answer the second.
