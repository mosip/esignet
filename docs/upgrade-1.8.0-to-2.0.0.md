# eSignet Upgrade Guide: 1.8.0 → 2.0.0

## Overview

Upgrade path: build the authn provider (if needed) → bring down 1.8.0 → run DB upgrade scripts → bring up 2.0.0 on the same DB → update configuration → verify.

**Services involved:** `esignet`, `esignet-ui` (oidc-ui), and any dependent mock/partner services (e.g. `mock-identity-system`). The `database` service is retained throughout — do not tear it down or recreate its volume.

---

## 1. Build the authn provider (if not using an existing authn provider)

- If your deployment uses one of the existing/bundled authn providers (`mosip`, `mock`, `sunbird`), no authn provider build is required — skip to step 2.
- If your deployment uses a custom authn provider, build the authn provider for 2.0.0 first, to be ready for the migration.
- Refer to the **[authn provider integration documentation](./authn-provider-integration.md)** for the authn-provider build/packaging steps.

> **Note:** If you are using the `mosip` authn provider (mosip plugin, 1.8.0), you will need to redo MISP onboarding after the upgrade — the key alias table is truncated as part of the DB upgrade (step 3).

## 2. Bring down existing 1.8.0 services (except DB)

- Stop the `esignet` and `oidc-ui` services (container/pods).
- Stop any dependent mock/adapter services if part of this environment (e.g. `mock-identity-system`).
- **Do not stop or remove the `database` service/volume** — the existing schema and data must be preserved for the upgrade scripts and reused by 2.0.0.
- Take a full backup of the `mosip_esignet` database before proceeding (mandatory rollback safety net).

## 3. Run upgrade scripts on the DB

Location: `db_upgrade_script/mosip_esignet/`

- Upgrade SQL: `sql/1.8.0_to_2.0.0_upgrade.sql`
- Rollback SQL (if needed): `sql/1.8.0_to_2.0.0_rollback.sql`
- Runner: `upgrade.sh`, driven by `upgrade.properties`

Steps:
1. Copy `upgrade.properties` and fill in the environment-specific values actually read by `upgrade.sh`:
   - `MOSIP_DB_NAME`, `DB_SERVERIP`, `DB_PORT`
   - `SU_USER`, `SU_USER_PWD` (superuser, default `postgres`)
   - `DEFAULT_DB_NAME` (default `postgres`)
   - `CURRENT_VERSION=1.8.0`
   - `UPGRADE_VERSION=2.0.0`
   - `ACTION=upgrade`
2. Run the script from `db_upgrade_script/mosip_esignet/`:
   ```bash
   ./upgrade.sh upgrade.properties
   ```
3. Confirm it executed `sql/1.8.0_to_2.0.0_upgrade.sql` successfully (script exits non-zero and logs an error if the file is missing or a statement fails — it runs with `ON_ERROR_STOP=1`).

If the upgrade needs to be reverted, use `ACTION=rollback` with the same properties file, which runs `sql/1.8.0_to_2.0.0_rollback.sql`.

## 4. Bring up 2.0.0 services (reuse existing DB)

- Deploy `esignet` 2.0.0 and `esignet-ui` 2.0.0 images/artifacts, pointed at the **same** database instance/schema used in 1.8.0 (no new DB, no re-init).
- Refer to the **[deployment documentation](../deploy/README.md)** for how to deploy `esignet` and its related services.
- Keep the `database` service as-is; only `esignet` and `esignet-ui` (and any other app-tier services) are replaced with 2.0.0 builds.
- Do not start application traffic yet — configuration changes (step 5) must be applied first.

## 5. Apply 1.8.0 → 2.0.0 configuration changes

The default 2.0.0 configuration is applied automatically when the new services are deployed via eSignet's deployment script.

The only thing that needs manual attention is **environment-specific overrides**: if your 1.8.0 deployment used non-default values for any property (custom key-policy settings, custom captcha/auth-provider config, custom hostnames, etc.), those custom values do **not** carry forward automatically and must be re-applied on top of the new 2.0.0 defaults.

- Refer to the **[configuration documentation](./configuration.md)** for the full list of 2.0.0 properties and their defaults.
- Refer to the **[configuration-delta documentation](./configuration-delta.md)** for the 1.8.0 → 2.0.0 release to see exactly which properties were added, removed, renamed, or changed in default value.
- For each property in that delta that you had overridden in 1.8.0, decide whether the override is still needed against the new 2.0.0 default, and port it forward into your environment's override file accordingly.
- Do **not** re-apply overrides for properties the delta shows as removed/replaced.

> Record which overrides were ported forward, per environment — needed for the release notes / change record and for future rollback.

## 6. Verify the new services

- **Automation:** `api-test/`, `ui-test/`, `postman-collection/` suites pass against the upgraded environment.
- **Manual:** services healthy, `.well-known` endpoints resolve, full OIDC login flow succeeds end-to-end, **re-consent is prompted** (expected — `consent_detail` migration in step 3), key policy operations work, and partner integrations still authenticate.
- Only route production traffic to 2.0.0 once both pass.

---

## Rollback

If verification fails:
1. Stop 2.0.0 `esignet` / `esignet-ui` services.
2. Run `upgrade.sh` with `ACTION=rollback` (`sql/1.8.0_to_2.0.0_rollback.sql`) against the DB.
3. Redeploy the 1.8.0 services and configuration.

> The rollback script restores `consent_detail` from the pre-upgrade snapshot (`consent_detail_bkp_1_8_0`) taken in step 3, then drops it — this requires that snapshot to still be present and unmodified. **Any consents recorded while running on 2.0.0 are lost**, since the snapshot only has pre-upgrade data. Export those rows first if they need to be retained. If the snapshot is missing/altered, restore the full DB backup from step 2 instead.
