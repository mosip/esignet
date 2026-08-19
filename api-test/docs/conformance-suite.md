# Conformance suite setup

The `conformance` surface drives the [OpenID Conformance Suite](https://gitlab.com/openid/conformance-suite),
which acts as the OAuth client and grades eSignet's compliance. Two things must exist before it can
run: the **suite itself**, and a **plan config** telling the suite which eSignet to test and which
OAuth client credentials to use.

The plan config holds a **private JWKS**. It is never in git, and no script can generate it for
you — it has to be created once per target environment.

**Contents:** [Starting the suite](#starting-the-suite) · [Creating the plan config](#creating-the-plan-config) ·
[Providing the plan config securely](#providing-the-plan-config-securely) · [Running several plans](#running-several-plans)

---

## Starting the suite

The simplest option is `docker compose up` from `api-test/`, which brings the suite up alongside
the harness — see [Running, option D](../README.md#d-docker-compose--conformance-suite-and-harness-together).

To run it yourself instead:

```bash
cd /path/to/conformance-suite
docker compose -f docker-compose-prebuilt.yml up -d

# 200 when ready (the nginx front end returns 502 until the Java server finishes booting)
curl -sk -o /dev/null -w "%{http_code}\n" \
  https://localhost.emobix.co.uk:8443/api/runner/available
```

Then point the harness at it with `conformance.base_url`. The suite ships a self-signed certificate
for `localhost.emobix.co.uk`, which is why every shipped config sets `conformance.tls_verify:
false`. That setting governs **only** the connection to the suite — how eSignet itself is reached
is `esignet.tls_verify`, which stays on.

> The suite image tag can go stale: GitLab prunes old `conformance-suite` tags, so a pull can 404
> on a tag that worked last month. If the pull fails, pick a current one from the
> [releases page](https://gitlab.com/openid/conformance-suite/-/releases) and set `SUITE_IMAGE_TAG`.

---

## Creating the plan config

Each plan needs one config file. The *shape* is tracked as a placeholder-only template; the filled-in
copies are git-ignored.

```bash
cd api-test
mkdir -p conformance-suite-private
cp data/conformance/conformance-config.example.json conformance-suite-private/esignet-config.json
cp data/conformance/conformance-config.example.json conformance-suite-private/esignet-fapi2-config.json
# then replace every REPLACE-WITH-* value in each copy
```

Both files are needed as shipped: each plugin config declares two plans (`oidcc-test-plan` and
`fapi2-security-profile-final-test-plan`). To run only the first, drop the second `plans[]` entry.

### What to fill in

This is the exact JSON the harness reads from `plans[].config_file` and posts unchanged to the
suite's create-plan API — the suite's own static-client plan-registration format.

| Field | Value |
|---|---|
| `alias` | The suite's callback path segment: `<conformance.base_url>/test/a/<alias>/callback`, i.e. `https://localhost.emobix.co.uk:8443/test/a/esignet-test/callback` for the template's `esignet-test` against the bundled suite. Keep that value unless you have a reason to change it: whatever it says has to appear verbatim in the redirect URI you register in eSignet (step 2 below), and changing it means re-registering every client. |
| `server.discoveryUrl` | `<esignet.base_url>/.well-known/openid-configuration` |
| `client.client_id`, `client2.client_id` | The client ids you register in eSignet (step 2 below) |
| `client.jwks`, `client2.jwks` | The **full private** RSA JWK — `n`, `e`, `d`, `p`, `q`, `dp`, `dq`, `qi` — with `alg: PS256`, one per client |

`oidcc-test-plan` needs only `client`; the FAPI plan uses two, hence `client2`. The template carries
both, so one template serves both files.

### Producing the values, once per environment

1. **Generate an RSA keypair per client as a JWK.** Two keypairs for the FAPI plan, one for an
   oidcc-only run. Whatever generator you use, these are the settings that matter:

   | Setting | Value |
   |---|---|
   | Key type | RSA |
   | Key size | 2048 |
   | Key use | Signature (`use: "sig"`) |
   | Algorithm | **PS256** — what eSignet expects for `private_key_jwt` |
   | Key ID | any stable value; SHA-256 thumbprint is a fine default |
   | Show X.509 | optional — the extra `x5c`/`x5t` fields are ignored here |

   [mkjwk.org](https://mkjwk.org) is the usual tool and emits both halves side by side. It is a
   third-party site, so treat anything generated there as test-only and never reuse such a key
   outside a test environment. To stay local instead, `openssl genrsa 2048` plus any PEM-to-JWK
   converter produces the same thing.

   Keep the **private** JWK (`n`, `e`, `d`, `p`, `q`, `dp`, `dq`, `qi`) for the plan config, and the
   **public** half (`n`, `e` only) for step 2. Note the template wraps the key in a **set** —
   `"jwks": { "keys": [ { … } ] }` — so paste the keypair *set* form, or wrap the bare JWK in
   `keys: []` yourself. A bare JWK dropped straight into `jwks` is the most common way this file
   ends up rejected by the suite.
2. **Register each `client_id` in eSignet** via client management, as `private_key_jwt`, with the
   **public** JWK from step 1 and a redirect URI whose path segment is the `alias` value from this
   same file. With the template's `alias: "esignet-test"` and the bundled suite, that is exactly:

   ```
   https://localhost.emobix.co.uk:8443/test/a/esignet-test/callback
   ```

   The suite builds its callback as `<conformance.base_url>/test/a/<alias>/callback` and sends the
   client there, so a redirect URI that does not match this — including a different `alias` — makes
   eSignet reject the authorize request before any test module runs.
3. **Fill in the copies** you made with the `cp` commands.

The suite signs the `private_key_jwt` client assertion itself when it drives the flow, which is why
it — not the harness — needs the private key.

> `conformance-config.example.json` is tracked precisely because every field is a `REPLACE-WITH-*`
> placeholder. It is named so it matches neither `.gitignore` pattern (`*esignet-config*.json`,
> `conformance-suite-private/`). Keep it that way, or the template starts being ignored and the next
> person is back to hand-writing it.

---

## Providing the plan config securely

The plan config is a **long-lived infrastructure credential**, on a par with
`KEYCLOAK_CLIENT_SECRET`. Treat it as one: never commit it, never bake it into the image, never
pass it as an environment variable (environment variables show up in `kubectl describe pod`, in the
Rancher UI's env tab, and in process listings).

The harness reads it from a **file path**, so the correct pattern everywhere is: provision the file
out of band, mount it read-only, and point the config at the mount.

### What you must supply

| | |
|---|---|
| **Files** | One JSON file per plan. As shipped: `esignet-config.json` (oidcc) and `esignet-fapi2-config.json` (FAPI). |
| **Contents** | As per [Creating the plan config](#creating-the-plan-config) — discovery URL, client ids, private JWKS. |
| **Where they are read from** | Whatever `plans[].config_file` says. All shipped configs use `conformance-suite-private/<name>.json`, relative to the harness working directory (`/app` in the image), so mounting at `/app/conformance-suite-private` needs no config change at all. |

### Kubernetes / Rancher — a Secret volume

Secret volumes are mounted as tmpfs and never appear in pod descriptions or the Rancher UI.

```bash
kubectl create secret generic esignet-conformance-config \
  --from-file=esignet-config.json=./conformance-suite-private/esignet-config.json \
  --from-file=esignet-fapi2-config.json=./conformance-suite-private/esignet-fapi2-config.json \
  -n <namespace>
```

```yaml
spec:
  volumes:
    - name: plan-config
      secret:
        secretName: esignet-conformance-config
        defaultMode: 0400
    - name: harness-config
      configMap: { name: esignet-harness-config }     # holds config.mosip.json
  containers:
    - name: harness
      args: ["-c", "/app/config.json"]
      env:
        - name: KEYCLOAK_CLIENT_SECRET
          valueFrom:
            secretKeyRef: { name: esignet-harness-secrets, key: keycloak-client-secret }
      volumeMounts:
        # Mounted at the path plans[].config_file already names — no override needed.
        - { name: plan-config,    mountPath: /app/conformance-suite-private, readOnly: true }
        - { name: harness-config, mountPath: /app/config.json, subPath: config.mosip.json, readOnly: true }
```

Non-secret configuration (which plugin, which surfaces, which modules) belongs in a ConfigMap
alongside it, as above. The split matters: the ConfigMap is readable by anyone with namespace
access, the Secret is not.

### Docker Swarm — a Docker secret

```bash
docker secret create esignet_conformance_config ./conformance-suite-private/esignet-config.json
```

```yaml
services:
  harness:
    image: apitest-esignet:<tag>
    command: ["-c", "/app/config.json"]
    secrets:
      - source: esignet_conformance_config
        target: /app/conformance-suite-private/esignet-config.json
        mode: 0400
secrets:
  esignet_conformance_config:
    external: true
```

### Plain `docker run` / local Compose — a read-only bind mount

Acceptable on a machine you control, where the file is already on disk with restrictive permissions:

```bash
docker run --rm \
  -v /opt/esignet-harness/secrets:/app/conformance-suite-private:ro \
  …
```

Local `docker-compose.yml` mounts `./conformance-suite-private` at the *same relative path* it has
on the host, so one config file drives both the native and containerised run.

### If you cannot mount at the default path

Point the harness elsewhere with the indexed environment override — `<n>` is 1-based and matches
the plan's position in `plans[]`:

```bash
PLAN_1_CONFIG_PATH=/var/run/secrets/oidcc-plan.json
PLAN_2_CONFIG_PATH=/var/run/secrets/fapi2-plan.json
```

The un-indexed `PLAN_CONFIG_PATH` addresses a single-plan config only. With several plans
configured it is rejected rather than guessed at — silently applying one mounted file to `plans[0]`
would run the FAPI plan against the OIDC client's keys.

---

## Running several plans

`plans` is a list and one run executes every entry in order. Each entry brings its own variant and
its own `config_file`, so two plans mean two client/key sets. The `api` and `e2e` surfaces still run
once, and every plan's modules land in the same report under a section of its own.

```jsonc
"plans": [
  { "name": "oidcc-test-plan",
    "variant": { "client_auth_type": "private_key_jwt", "response_type": "code",
                 "response_mode": "default", "client_registration": "static_client" },
    "config_file": "conformance-suite-private/esignet-config.json" },

  { "name": "fapi2-security-profile-final-test-plan",
    "variant": { "client_auth_type": "private_key_jwt", "sender_constrain": "dpop",
                 "authorization_request_type": "simple", "openid": "openid_connect",
                 "fapi_profile": "plain_fapi" },
    "config_file": "conformance-suite-private/esignet-fapi2-config.json",
    "profile": "full" }
]
```

- `profile`, `modules`, `filter`, `skip` and `known_issues` are per plan and fall back to `run.*`.
- `profile: "smoke"` reads `data/conformance/<plan name>.smoke.json`. Only
  `oidcc-test-plan.smoke.json` ships, so any other plan needs `"profile": "full"` — otherwise it
  looks for a curated list that is not there and becomes one errored `(plan setup)` row.
- Plan names must be unique: they key the report sections and the profile files.
- A plan the suite refuses to create becomes one errored row and the next plan still runs, so a
  broken FAPI variant does not throw away the oidcc results.
- `variant` must **not** include `server_metadata` — the plan sets it, and passing it yourself
  returns HTTP 400.
- `run.fail_fast` stops the whole run, remaining plans included.

`./run-all.sh -c <config> --check` prints the plans in run order with their resolved variants —
worth a look before a two-plan run, which takes twice as long.
