# Partner Onboarder

## Overview
Exchanges certificates for the eSignet MISP partner. Refer to the [mosip-onboarding repo](https://github.com/mosip/mosip-onboarding).

The `install.sh` script wraps the `mosip/partner-onboarder` Helm chart and interactively collects the configuration it needs (SSL/domain status, report storage backend, and Keycloak setup) before running the onboarding job.

## Prerequisites

### 1. Report storage (choose one)

You can store the onboarder's HTML reports either in **S3** or on an **NFS** share. The install script will ask which one you want to use.

**Option A — S3**
Have the following ready before running the script:
- S3 host
- S3 region
- S3 bucket name
- S3 access key
- S3 secret key

**Option B — NFS**
Create a directory for the onboarder on the NFS server:
```
mkdir -p /srv/nfs/mosip/<sandbox>/onboarder/
```
Ensure the directory has `777` permissions:
```
chmod 777 /srv/nfs/mosip/<sandbox>/onboarder
```
Add the following entry to `/etc/exports`:
```
/srv/nfs/mosip/<sandbox>/onboarder *(rw,sync,no_root_squash,no_all_squash,insecure,subtree_check)
```
Apply the export:
```
sudo exportfs -rav
```
Restart the NFS server:
```
sudo systemctl restart nfs-kernel-server
```
Have the **NFS server IP** and the **NFS path** (e.g. `/srv/nfs/mosip/<sandbox>/onboarder/`) ready — the script will prompt for both.

### 2. Keycloak setup (choose one)

The script also asks whether you're using an **external Keycloak instance**.

**If yes**, have these ready:
- `KEYCLOAK_EXTERNAL_URL`
- Keycloak admin username
- Keycloak admin password
- PMS domain (e.g. `api-internal.sandbox.mosip.net`)
- PMS client secret

**If no**, the script will copy the required configmaps/secrets (`keycloak-env-vars`, `keycloak`, `keycloak-client-secrets`) from the `keycloak` namespace into the `esignet` namespace automatically. No extra input is needed here, but `../deploy/copy_cm_func.sh` must be present and the source configmaps/secrets must already exist in the `keycloak` namespace.

### 3. SSL / domain

The script will also ask whether you have a public domain with a valid SSL certificate:
- **Y** — no extra flag is set.
- **n** — the script sets `onboarding.configmaps.onboarding.ENABLE_INSECURE=true` so the onboarder can talk to endpoints without valid SSL.

### 4. `values.yaml`

Set `values.yaml` to run the onboarder for the specific module(s) you need (currently `esignet`), and fill in the `propertiesOverride` block, e.g.:

```yaml
onboarding:
  modules:
    - name: esignet
      enabled: true

  propertiesOverride:
    esignet:
      POLICY_NAME: mpolicy-default-esignet
      POLICY_GROUP_NAME: mpolicygroup-default-esignet
      PARTNER_KC_USERNAME: mpartner-default-esignet
      PARTNER_ORGANIZATION_NAME: IIITB
      PARTNER_TYPE: Misp_Partner
      PARTNER_DOMAIN: MISP
      PARTNER_MANAGER_USERNAME: esignet-kc-mockusername
      PARTNER_MANAGER_PASSWORD: esignet-kc-mockpassword
      EXTERNAL_URL: https://esignet.sandbox.mosip.net
```

If you're using S3 or a custom NFS mount, or need to override configmaps/secrets, uncomment and fill in the corresponding `configmaps` / `secrets` / `volumes` blocks in `values.yaml` as needed — see the comments in the file for the exact keys.

## Install

Run the script, optionally passing a kubeconfig path:
```
./install.sh [kubeconfig]
```

You'll be walked through:
1. **Public domain / SSL check** — `Y`/`n`.
2. **`values.yaml` confirmation** — confirm it's set correctly before proceeding.
3. **Report storage** — S3 details, or NFS server + path if S3 isn't available.
4. **Keycloak** — external instance details, or automatic copy from the internal `keycloak` namespace.

The script then:
- Creates the `esignet` namespace (if it doesn't already exist).
- Disables Istio sidecar injection on that namespace.
- Runs `helm repo update`.
- Installs the `mosip/partner-onboarder` chart (`esignet-misp-onboarder`, version `0.0.2-develop`) into the `esignet` namespace, waiting for the job to complete.
- Restarts the `esignet` deployment so the new MISP license key takes effect.
- Cleans up the temporary configmaps created during the run.

## Troubleshooting

Once the onboarder job completes, a detailed HTML report is generated and stored in the S3 bucket or NFS directory you configured. Check this report to confirm the onboarding succeeded.

### Commonly found issues

1. **KER-ATH-401: Authentication Failed**
   Resolution: Provide the correct secret key for `mosip-deployment-client`.

2. **KER-KMS-021: The PARTNER Certificate validity is less than required minimum validity**
   Resolution: Check with the admin about adding a grace period in configuration.

3. **Upload of certificate will not be allowed to update other domain certificate**
   Resolution: This is expected when trying to upload the `ida-cred` certificate a second time — it should only run once, and this error can be ignored if the certificate is already present.

4. **Script exits with "'flag' was not provided"**
   Resolution: Answer the SSL/domain prompt with `Y` or `n` — the script requires a non-empty response.

5. **Script exits after S3/NFS prompt**
   Resolution: You must provide either complete S3 details or complete NFS details (server IP and path). Re-run the script with one set fully filled in.

6. **`copy_cm_func.sh` errors when using internal Keycloak**
   Resolution: Confirm `../deploy/copy_cm_func.sh` exists relative to where you run `install.sh`, and that the `keycloak-env-vars`, `keycloak`, and `keycloak-client-secrets` configmaps/secrets already exist in the `keycloak` namespace.
