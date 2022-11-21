CREATE DATABASE mosip_idpbinding
	ENCODING = 'UTF8' 
	LC_COLLATE = 'en_US.UTF-8' 
	LC_CTYPE = 'en_US.UTF-8' 
	TABLESPACE = pg_default 
	OWNER = postgres
	TEMPLATE  = template0;

COMMENT ON DATABASE mosip_idpbinding IS 'IdP Binding Service related data is stored in this database';

\c mosip_idpbinding postgres

DROP SCHEMA IF EXISTS idpbinding CASCADE;
CREATE SCHEMA idpbinding;
ALTER SCHEMA idpbinding OWNER TO postgres;
ALTER DATABASE mosip_idpbinding SET search_path TO idpbinding,pg_catalog,public;

