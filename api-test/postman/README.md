# Postman collection — mosipid PMS + e2e login

Drives by hand the path the `e2e` surface drives: admin token → register an OIDC
client through PMS → `authorize` → `flow/execute` → OTP → consent.

Use it to check an environment before spending a full harness run on it, or to
isolate a failure the report only shows as one red row.

## Import

1. Import `esignet-mosipid.postman_collection.json`.
2. Copy `esdev-mosipid.postman_environment.example.json`, fill it in, import it.
   (`*.postman_environment.json` is gitignored — it holds the Keycloak secret.)
3. Select the environment, then paste `keycloak_client_secret` into it in Postman.
   It is declared as a `secret` variable and is deliberately left empty on disk.

## Running

Folder 0 once, then folder 2 as often as you like — each request stores what the
next one needs. Only two things need typing:

| Variable | When |
|---|---|
| `otp` | before `2.5` — read it from `https://smtp.<env>.mosip.net`, 3 min validity |
| `client_public_key_n` | before creating a client — must be unique per client |

Fresh modulus:

```bash
openssl genrsa -out k.pem 2048
openssl rsa -in k.pem -noout -modulus | sed 's/Modulus=//' | xxd -r -p \
  | base64 -w0 | tr '+/' '-_' | tr -d '='
```

## The trap that costs the most time

**A client you just created will not work.** IDA does not recognise a new OIDC
client for somewhere between 20 minutes and 3 hours; until then every login fails
at `2.4 Send OTP` with `IDA-MLC-007`. Its PMS and eSignet records look completely
normal the whole time — `ACTIVE`, right partner, right policy, right ACRs — so
nothing but driving a login reveals it.

Register a client once, leave it, and reuse its id. `client_id` in the example
environment points at one that has already aged.

This is also why the harness's `e2e` surface cannot pass here: it registers a
throwaway client per run and uses it immediately.

## Error codes you will actually hit

| Code | At | Means |
|---|---|---|
| `KER-ATH-401` | any PMS call | Token sent as a Bearer header. PMS wants a cookie named `Authorization` |
| `KER-ATH-403` | create client | Token lacks the `AUTH_PARTNER` realm role (`/oidc-clients` needs it; `/oauth/client` does not) |
| `PMS_ESI_001` | create client | Public key already registered — generate a new modulus |
| `PMS_ESI_008` | update client | Deactivation is terminal; a client cannot be reactivated |
| `IDA-MLC-007` | send OTP | Client not yet known to IDA (see above), or the OTP request rate limit |
| `IDA-MLC-009` | send OTP | IDA cannot send to that identifier — use the UIN, not phone/email |
| `FET-1005` | submit OTP | IDA refused the credentials: wrong/expired OTP, or a client IDA has not picked up |
| `server_error` | authorize | Often `MOSIP_ESIGNET_CAPTCHA_SITE_KEY`/`_SITE_PROVIDER` unset — the flow definition substitutes them, so they must stay set even when the captcha *validator* is off |

## Two things worth knowing about the flow

**Inputs accumulate.** Every step re-sends every input answered so far. Each view
declares only the field it newly adds, so the OTP view declares `otp` alone — but
submitting just that leaves the auth node with no identity and it answers
`FET-1005` as though the code were wrong.

**Login-id tabs all submit under `submit_uin`.** Only `nextNode` distinguishes
them (`send_mosip_otp_uin` vs `send_mosip_otp_mobile`). For a phone or email
identity, POST `login_id_mobile` / `login_id_email` first to switch tabs.
