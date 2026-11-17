# Client-management PATCH flow

This document traces the current `PATCH /client-mgmt/client/{client_id}` implementation from the HTTP handler through the service and database layer. It also walks through two requests in detail, including what happens when each races with another PATCH on the same client.

The central idea is:

> PATCH builds a complete **in-memory client state for validation**, but builds an SQL `UPDATE` containing **only the requested columns**. A transaction and `SELECT ... FOR UPDATE` keep the row locked from the initial read through validation and writing.

```text
PATCH /client-mgmt/client/{client_id}
        │
        ▼
handler.patchClient
  Read envelope → decode request + field-presence flags
        │
        ▼
service.PatchClient
  Reject empty patch
  Begin transaction → SELECT row FOR UPDATE
  Build proposed complete client state
  Validate proposed state
  Build values for requested columns only
        │
        ▼
db.Queries.PatchClient
  Construct UPDATE ... SET selected columns, upd_dtimes
  PostgreSQL: UPDATE ... RETURNING
  MySQL: UPDATE, then SELECT row in the same transaction
        │
        ▼
service.PatchClient
  Convert row to response → commit → invalidate cache
        │
        ▼
handler.patchClient
  Write API response
```

The route is registered in [handler.go](handler.go#L49). It is specifically the `/client-mgmt/client/{client_id}` PATCH route. The OIDC and OAuth client routes shown nearby have PUT handlers, but no PATCH route there.

## 1. Handler: accept and decode the HTTP request

The handler starts at [handler.go:141](handler.go#L141). In execution order:

| Lines | What happens |
|---|---|
| 142–146 | Read `client_id` from the URL. An empty value produces `invalid_input`. |
| 148–153 | Limit the body to 1 MiB and read it. A read failure produces `invalid_input`. |
| 154–158 | Decode the outer JSON envelope into `PatchRequestWrapper`. Malformed JSON produces `invalid_input`. |
| 159–162 | Require a nonblank `requestTime`. This handler checks presence, not the timestamp's format. |
| 163–166 | Require a non-null `request` member. |
| 167–175 | Call `DecodePatchRequest`, which gives back both request values and flags saying which fields appeared in JSON. |
| 177–181 | Call `Service.PatchClient`; pass service errors to `handleServiceError`. |
| 183–188 | On success, log the selected fields and write the API response. |

The decoder matters because a Go zero value cannot tell us whether a field was **omitted** or explicitly supplied with an empty value. For example, omitted `logoUri` and `"logoUri": ""` would both produce an empty Go string. [DecodePatchRequest](model.go#L184) iterates over the JSON keys and sets a corresponding `PatchFields` boolean whenever a key appears.

For `encPublicKey`, the decoder records three meaningful states: omitted, explicit `null`, or a JWK object. Explicit `null` means “clear the key”; omitted means “leave it alone” ([model.go:252](model.go#L252)). Unknown request fields are rejected rather than silently ignored.

An empty `"request": {}` passes the handler's “request exists” check. The service rejects it because all presence flags are false. No SQL update is attempted.

## 2. Service: lock, propose, validate, prepare, write

The main method is [Service.PatchClient](service.go#L295).

### Lines 296–309: reject an empty PATCH and obtain the locked row

At lines 296–298, `fields == (PatchFields{})` rejects a request with no recognized PATCH fields as `invalid_request`.

At line 300, `lockClient` is called. Its implementation begins at [service.go:119](service.go#L119):

1. For a real database service, `BeginTxx(ctx, nil)` opens a transaction.
2. `s.txBase.WithTx(tx)` binds the query object to **that transaction**.
3. `GetClientForUpdate` reads the client row with `SELECT ... WHERE id = ? FOR UPDATE`.
4. The method returns the row, transaction, and transaction-bound query object.

The SQL is built at [db.go:71–72](db/db.go#L71). The row lock stays held until the transaction commits or rolls back. Another transaction asking for `FOR UPDATE` on the same row waits. Once it proceeds, its locking read sees the committed row from the earlier transaction under the usual isolation settings. This behavior is documented by [PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html) and [MySQL InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html).

The placement of the lock matters: it happens **before validation**, not merely when the eventual `UPDATE` runs.

If the client does not exist, `GetClientForUpdate` yields `sql.ErrNoRows`; lines 301–305 turn that into `ErrClientNotFound`. Lines 307–309 set up a deferred rollback, so an error later in validation or writing releases the lock without persisting the PATCH. After a successful commit, the deferred rollback has no effect and its error is ignored.

The `s.db == nil` branch in `lockClient` uses an injected `Querier` without a transaction. That is for fake-backed unit-test services, not the real database path ([service.go:120–123](service.go#L120)).

### Lines 311–318: build two different things

At line 311, `clientStateForPatchValidation(existing)` decodes stored mutable values into an `UpdateClientRequest` shape. It reads the stored name, list fields, status, logo, and additional config ([service.go:514–555](service.go#L514)). This is the **validation candidate**. It begins as the state currently held under the lock.

At line 315, `preparePatchParams(clientID, req, fields, &merged)` does two jobs together ([service.go:560–618](service.go#L560)):

- It overlays supplied request values onto that validation candidate.
- It fills `db.PatchClientParams` and its column mask **only for supplied fields**.

This keeps “validate the whole resulting client” separate from “write only selected columns.” If only `status` is supplied, the candidate still contains all stored redirect URIs, claims, grant types, and other values for validation. Yet the DB parameter mask has only `Status: true`. Those other values are **not** placed in the SQL `SET` list.

The field-to-column mapping is explicit:

| PATCH JSON field | DB column selected |
|---|---|
| `clientName`, `clientNameLangMap` | `name` — these share one stored JSON value |
| `logoUri` | `logo_uri` |
| `redirectUris` | `redirect_uris` |
| `userClaims` | `claims` |
| `authContextRefs` | `acr_values` |
| `grantTypes` | `grant_types` |
| `clientAuthMethods` | `auth_methods` |
| `status` | `status` |
| `additionalConfig` | `additional_config` |
| `encPublicKey` | `enc_public_key`, `enc_public_key_hash`, and `enc_public_key_cert` together |

The five list fields share the loop at [service.go:577–601](service.go#L577). The loop skips an absent field; for a present one it JSON-encodes the new list, replaces that list in the validation candidate, and sets its DB mask.

There are two useful exceptions to a simple one-field/one-column mapping:

- `clientName` and `clientNameLangMap` both live in `name`. Supplying either causes the service to combine the supplied part with the retained part and serialize the one `name` column ([service.go:562–570](service.go#L562)).
- `encPublicKey` controls three DB columns. A key cannot be changed without updating its hash and certificate columns at the same time.

`additionalConfig` is replaced as a **whole field** when supplied. The code does not recursively merge individual properties inside its JSON object.

### Lines 319–340: validate the resulting state and finish selected values

At line 321, `ValidatePatch` receives the proposed full client state, the presence flags, the requested key state, and whether a key exists in the locked row ([validate.go:209](validate.go#L209)). Its flow is:

1. If `encPublicKey` was supplied, determine whether the client **will have** a key after this PATCH (lines 210–212).
2. Validate each supplied field using its PATCH-specific rules (lines 213–257).
3. If `additionalConfig` changes, or the key is being cleared, check the resulting config against the resulting key state (lines 258–261).
4. If a new key was supplied, validate that JWK, including its `alg` (lines 263–267).
5. Call `ValidateUpdate` on the **entire resulting mutable client** (line 269).

That last call matters. PATCH-specific list checks allow the list's lower bound to be zero while checking a supplied list, but the complete-client check requires the required lists to remain nonempty ([validate.go:159–190](validate.go#L159)). A PATCH that clears a required list does not slip through.

This validates the complete **mutable client state represented by `UpdateClientRequest`**. It does not revalidate every column in `client_detail`, such as immutable registration fields.

After validation:

- Lines 324–329 normalize a supplied status to stored `ACTIVE` or `INACTIVE` ([validate.go:615](validate.go#L615)).
- Lines 330–339 serialize a supplied non-null encryption key and compute its hash. If `encPublicKey` was explicitly `null`, those three `sql.NullString` parameters remain invalid, which writes SQL `NULL` to all three selected columns.
- Line 340 sets `upd_dtimes` to the current UTC time. It is an output column in this design; there is no timestamp comparison in the `WHERE` clause.

### Lines 342–364: write, convert response, commit, clear cache

Line 342 calls `q.PatchClient(ctx, params)`. On the real service path, `q` is transaction-bound.

Lines 343–350 classify a missing row or duplicate encryption-key hash and wrap other DB errors. A missing row at this point is a defensive path; the earlier locked read normally established and protected the row.

Lines 352–355 convert the written row into a `ClientResponse` **before commit**. `toResponse` decodes the stored JSON lists and additional config ([service.go:723–771](service.go#L723)). If this conversion fails, the transaction rolls back.

Line 357 commits. The lock is released then. Lines 361–363 invalidate the cached client row after commit, and line 364 returns the response. If cache invalidation fails, the DB transaction has already committed, but the service returns an error so the caller is told about that partial failure ([service.go:501–509](service.go#L501)).

## 3. DB layer: construct an actual partial UPDATE

The DB method is [db.Queries.PatchClient](db/db.go#L264).

Lines 265–272 create a `set` list, positional argument list, and a small `add` helper. `add` appends a column assignment **only when its mask is true**.

Lines 273–284 call `add` in a fixed order for the possible PATCH columns. Line 285 always adds `upd_dtimes`. Lines 287–288 construct:

```sql
UPDATE client_detail SET <selected assignments>, upd_dtimes = ? WHERE id = ?
```

There is no `SET` assignment for an absent request field. There is also no `upd_dtimes = <old value>` guard in the predicate; the concurrency mechanism here is the row lock held across read, validation, and write.

For PostgreSQL, lines 289–299 append `RETURNING` for the full row, rebind placeholders as `$1`, `$2`, etc., and retrieve the written row from that same update statement.

For MySQL, lines 302–305 execute the update and then call `GetClient` **through the same transaction-bound `q`**. This readback is why an existing client can succeed even when MySQL reports zero *changed* rows for a no-op value update. The code does not decide “missing client” from `RowsAffected()`.

## Scenario 1: a one-field status PATCH

Assume client `client-1` is currently `ACTIVE`, has `logo_uri = https://example.org/old.png`, and its other required fields are valid. The request is:

```json
{
  "requestTime": "2026-11-17T10:00:00Z",
  "request": {
    "status": "inactive"
  }
}
```

1. **Handler:** Decoding produces `req.Status = "inactive"` and `fields.Status = true`. Every other `PatchFields` flag is false.
2. **Service empty check:** Passes, because one flag is true.
3. **Lock and read:** A transaction starts; `SELECT ... FOR UPDATE` returns the `ACTIVE` row and locks it.
4. **Validation candidate:** The service decodes the stored row. Its candidate includes the old logo, stored lists, stored config, and `Status = "ACTIVE"`.
5. **Prepare parameters:** Only the status branch runs. It changes the candidate's status to `"inactive"` and sets `params.Fields.Status = true`. It does **not** mark `LogoURI`, `Claims`, or any other column.
6. **Validate:** `ValidatePatch` checks the supplied status and then checks the complete proposed client. Assuming the rest of the row is valid, it passes.
7. **Normalize:** `"inactive"` becomes `"INACTIVE"` for storage.
8. **DB write:** The generated SQL is effectively:

   ```sql
   UPDATE client_detail
   SET status = ?, upd_dtimes = ?
   WHERE id = ?
   ```

   The arguments are, in order, `INACTIVE`, the new UTC timestamp, and `client-1`. PostgreSQL uses `$1`, `$2`, `$3` and adds `RETURNING`. MySQL executes this statement and reads the row back within the transaction.

9. **Finish:** The service converts the row, commits, invalidates the cache, and returns. The handler writes HTTP 200 with a response containing `clientId` and canonical status `INACTIVE`. `APIResponse()` deliberately exposes only those two values ([model.go:163–166](model.go#L163)).

The complete client was used to decide whether the PATCH is valid; only `status` and `upd_dtimes` were written.

### Scenario 1 with a parallel PATCH

Now request A is that status PATCH. At nearly the same time, request B sends:

```json
{
  "requestTime": "2026-11-17T10:00:01Z",
  "request": {
    "logoUri": "https://example.org/new.png"
  }
}
```

Suppose A obtains the row lock first:

| Time | Request A: status | Request B: logo |
|---|---|---|
| 1 | Begins transaction and locks `client-1`. Reads `ACTIVE`, old logo. | Begins transaction. |
| 2 | Builds and validates `INACTIVE` with old logo. | Reaches `SELECT ... FOR UPDATE` and waits for A's lock. |
| 3 | Updates only `status` and `upd_dtimes`; commits. | Still waiting. |
| 4 | Invalidates cache. | Obtains lock and reads the now-committed row: `INACTIVE`, old logo. |
| 5 | Returns `INACTIVE`. | Builds and validates `INACTIVE` with new logo. Updates only `logo_uri` and `upd_dtimes`; commits. |

The final row has **`INACTIVE` and the new logo**. B did not put an old status back into the database, because its SQL did not include `status`.

If B gets the lock first, the final row still has both changes. B's own response may show `ACTIVE` because that was the status at the time *its* transaction completed; A's later response shows `INACTIVE`. Each response reflects its own completed operation.

The transaction means each request validates a row that was locked and current for its turn. The partial SQL prevents an unrelated field from being overwritten.

## Scenario 2: JWE config and encryption-key dependency

Assume `client-1` starts with:

```text
additional_config = {"userinfo_response_type":"JWS"}
enc_public_key    = a valid encryption JWK
```

Request A changes the response type to JWE:

```json
{
  "requestTime": "2026-11-17T10:05:00Z",
  "request": {
    "additionalConfig": {
      "userinfo_response_type": "JWE"
    }
  }
}
```

Its path is the same handler → service → DB route, but the dependency check matters:

1. Decoder sets only `fields.AdditionalConfig = true`.
2. Locked read returns the stored JWS config **and** `existing.EncPublicKey.Valid = true`.
3. `preparePatchParams` replaces the candidate's entire `AdditionalConfig` value with the requested JWE object and selects only the `additional_config` DB column.
4. `ValidatePatch` checks the new config using the *post-PATCH* key state. The key was not patched, so it remains present.
5. `validateAdditionalConfig` allows `userinfo_response_type = "JWE"` only when that key state is present ([validate.go:522–529](validate.go#L522)). The full proposed client is then validated.
6. The SQL selects just:

   ```sql
   UPDATE client_detail
   SET additional_config = ?, upd_dtimes = ?
   WHERE id = ?
   ```

7. The service commits and returns success.

### Scenario 2 with a parallel key-clear PATCH

At the same time, request B sends:

```json
{
  "requestTime": "2026-11-17T10:05:01Z",
  "request": {
    "encPublicKey": null
  }
}
```

The decoder marks `EncPublicKey` as **present and null**, which means “remove it.” The two possible lock orders are worth examining separately.

**If A locks first:**

1. A reads JWS + key, validates JWE + key, writes JWE, and commits.
2. B has been waiting at `SELECT ... FOR UPDATE`. After A commits, B reads **JWE + key**.
3. B's proposed state retains JWE config but has **no key**.
4. `ValidatePatch` sees that the key is being cleared and rechecks the retained config. JWE without a key produces `invalid_additional_config`.
5. B returns that validation error and rolls back. It never reaches `db.PatchClient`.

Final state: **JWE + key**. A succeeds; B is rejected.

**If B locks first:**

1. B reads JWS + key. Its proposed state is JWS + no key, which is valid.
2. Its `EncPublicKey` mask causes the DB layer to set all three related columns to `NULL` together: `enc_public_key`, `enc_public_key_hash`, and `enc_public_key_cert`, plus a new `upd_dtimes`.
3. B commits. A then obtains the lock and reads **JWS + no key**.
4. A proposes JWE + no key. `validateAdditionalConfig` rejects it before any update.

Final state: **JWS + no key**. B succeeds; A is rejected.

Neither order persists the invalid combination **JWE + no encryption key**. The database's lock on the eventual write alone would not establish this guarantee: both requests could otherwise validate against the same old row before either write. Here, the second request cannot even **read for validation** until the first request has finished.

## What the caller sees on errors and no-op updates

- A missing client is mapped from `sql.ErrNoRows` to `ErrClientNotFound`, then to the API error code `invalid_client_id` ([service.go:300–305](service.go#L300), [handler.go:216–218](handler.go#L216)).
- Validation failures, such as empty PATCH or JWE without a key, use their `ValidationError` code ([handler.go:210–215](handler.go#L210)).
- These documented client-management errors are written in an HTTP 200 envelope with an `errors` entry. Unexpected service errors use HTTP 500 with `server_error` ([handler.go:225–245](handler.go#L225)).
- Sending an already-stored value is still a valid, nonempty PATCH. The DB method does not rely on MySQL's changed-row count; it reads the existing row back after the update. That prevents a no-op value update from being misreported as a nonexistent client ([db.go:302–305](db/db.go#L302)).

The distinction to keep in mind when reading this code is: **`merged` answers “would the client be valid after this request?”; `params.Fields` answers “which columns may this request write?”** Those questions require different data, and the row lock makes the validation answer remain relevant until the write commits.
