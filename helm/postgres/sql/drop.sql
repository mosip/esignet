-- Drop all DBs and roles
-- If session are active and db cannot be dropped use terminate_sessions.sql
-- CAUTION Dangerous!  Only for development and testing
drop database mosip_master;
drop database mosip_audit;
drop database mosip_keymgr;
drop database mosip_kernel;
drop database mosip_idmap;
drop database mosip_prereg;
drop database mosip_idrepo;
drop database mosip_ida;
drop database mosip_credential;
drop database mosip_regprc;
drop database mosip_regdevice;
drop database mosip_authdevice;
drop database mosip_pms;
drop database mosip_websub;
drop role appadmin;
drop role audituser;
drop role authdeviceuser;
drop role credentialuser;
drop role dbadmin;
drop role idauser;
drop role idmapuser;
drop role idrepouser;
drop role kerneluser;
drop role keymgruser;
drop role masteruser;
drop role pmsuser;
drop role sysadmin;
drop role prereguser;
drop role regdeviceuser;
drop role regprcuser;
drop role websubuser;
