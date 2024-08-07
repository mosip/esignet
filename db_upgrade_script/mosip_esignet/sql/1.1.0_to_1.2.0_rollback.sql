\c mosip_esignet

--  Drop the thumbprint column from the public_key_registry table
ALTER TABLE public_key_registry
DROP COLUMN thumbprint;
--  Drop the generate_thumbprint function
DROP FUNCTION IF EXISTS generate_thumbprint(text);
---Drop the Extension pgcrypto
DROP EXTENSION IF EXISTS pgcrypto;