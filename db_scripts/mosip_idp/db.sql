CREATE DATABASE mosip_idp
	ENCODING = 'UTF8' 
	LC_COLLATE = 'en_US.UTF-8' 
	LC_CTYPE = 'en_US.UTF-8' 
	TABLESPACE = pg_default 
	OWNER = postgres
	TEMPLATE  = template0;

COMMENT ON DATABASE mosip_idp IS 'IdP related data is stored in this database';

\c mosip_idp postgres

DROP SCHEMA IF EXISTS idp CASCADE;
CREATE SCHEMA idp;
ALTER SCHEMA idp OWNER TO postgres;
ALTER DATABASE mosip_idp SET search_path TO idp,pg_catalog,public;

