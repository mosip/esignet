\c mosip_esignet

--adding new column thumbprint to public_key_registry
ALTER TABLE public_key_registry
ADD COLUMN thumbprint varchar;

-- adding pgcrypto extension if not exist for hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

--Create a new function to generate thumbprints
-- This function uses the provided script to generate thumbprints from the certificate column

CREATE OR REPLACE FUNCTION generate_thumbprint(cert text)
  RETURNS text AS
$$
BEGIN
  RETURN regexp_replace(  -- Deal with '+', replace them with '-'
        rtrim(          -- Deal with trailing = signs
         encode(                -- Base64 of SHA256 hash
           digest(              -- Create SHA256 hash
             decode(            -- Get cert bytes from base64
               regexp_replace(  -- Remove BEGIN and END lines
                 cert, '(-.*?-)', '', 'g'
               ),
               'base64'
             ),
             'sha256'
           ),
           'base64'
         ),
         '='
       ),'\+','-','g');
END;
$$
LANGUAGE 'plpgsql';

-- Update the thumbprints for the rows with null thumbprints
UPDATE public_key_registry
SET thumbprint = generate_thumbprint(certificate)
WHERE thumbprint IS NULL;

--  Drop the generate_thumbprint function
DROP FUNCTION IF EXISTS generate_thumbprint(text);
---Drop the Extension pgcrypto
DROP EXTENSION IF EXISTS pgcrypto;