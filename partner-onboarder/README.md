# Partner Onboarder

## Overview
Exchanges certificates for eSignet MISP partner. Refer [mosip-onboarding repo](https://github.com/mosip/mosip-onboarding).

## Install 
* Create a directory for onboarder on the NFS server at `/srv/nfs/<sandbox>/onboarder/`:
```
mkdir -p /srv/nfs/mosip/<sandbox>/onboarder/
```
* Ensure the directory has 777 permissions:
```
chmod 777 /srv/nfs/mosip/<sandbox>/onboarder
```
* Add the following entry to the /etc/exports file:
```
/srv/nfs/mosip/<sandbox>/onboarder **(rw,sync,no_root_squash,no_all_squash,insecure,subtree_check)
```
* Apply export command
```
sudo exportfs -rav
```
* Restart the nfs-server
```
sudo systemctl restart nfs-kernel-server
```
* Set `values.yaml` to run onboarder for specific modules.
* run `./install.sh`.
```
./install.sh
```
# Troubleshootings
* Once onboarder job is completed, detailed `html report` is prepared and stored at provided S3 bucket / NFS directory. 
* Once onboarder helm installation is complted, please check the reports to confirm sucessfull onboarding.

### Commonly found issues 

 1. KER-ATH-401: Authentication Failed
 
    Resolution: You need to provide correct secretkey for mosip-deployment-client.
 
 2. Certificate dates are not valid

    Resolution: Check with admin regarding adding grace period in configuration.
 
 3. Upload of certificate will not be allowed to update other domain certificate
 
    Resolution: This is expected when you try to upload `ida-cred` certificate twice. It should only run once and if you see this error while uploading a second      time it can be ignored as the cert is already present.
