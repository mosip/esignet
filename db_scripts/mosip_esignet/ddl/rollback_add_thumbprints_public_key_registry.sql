-- This script removes the 'thumbprint' column from the 'public_key_registry' table
-- and drops the 'generate_thumbprint' function, effectively undoing the changes made by the previous script.

--  Drop the thumbprint column from the public_key_registry table
ALTER TABLE public_key_registry
DROP COLUMN thumbprint;

