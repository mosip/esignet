--adding new column thumbprint to public_key_registry
ALTER TABLE public_key_registry
ADD COLUMN thumbprint varchar;
