-- This script removes the 'thumbprint' column from the 'public_key_registry' table
--  Drop the thumbprint column from the public_key_registry table
ALTER TABLE public_key_registry
DROP COLUMN thumbprint;

