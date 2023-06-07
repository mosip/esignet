# Partner Onboarder

## Overview
Loads certs for default partners for sandbox. Refer [mosip-onboarding repo](https://github.com/mosip/mosip-onboarding).

## Install 
* Set `values.yaml` to run onboarder for specific modules.
* run `./install.sh`.
```
./install.sh
```
## Automating MISP Partner License key for e-Signet module
* Added `misp_key.sh` script through which the MISP license key is obtained with the following endpoint:
  `v1/partnermanager/misps/$MISP_PARTNER_ID/licenseKey`
* The above license key is passed through the `config-server` as placeholder named `mosip.esignet.misp.key` in `esignet-default.properties` file and then saved as a secret called `onboarder-keys` in the kubernetes environment.
* This change is a part of the `install.sh` script of partner-onboarder.

# Troubleshootings

* After completion of the job, a very detailed `html report` is prepared and stored at https://onboarder.{sandbox_base_url}.mosip.net

* The user can go and view the same for more information or response messages.

### Commonly found issues 

 1. KER-ATH-401: Authentication Failed
 
    Resolution: You need to provide correct secretkey for mosip-deployment-client.
 
 2. Certificate dates are not valid

    Resolution: Check with admin regarding adding grace period in configuration.
 
 3. Upload of certificate will not be allowed to update other domain certificate
 
    Resolution: This is expected when you try to upload `ida-cred` certificate twice. It should only run once and if you see this error while uploading a second      time it can be ignored as the cert is already present.



