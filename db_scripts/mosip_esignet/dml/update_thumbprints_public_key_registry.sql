-- this script will create a method to generate thumbprint by using given certificate
-- defines a function to generate thumbprints from certificates,
-- and updates the 'thumbprint' column for rows where 'thumbprint' is NULL.

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
