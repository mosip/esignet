-- Add the new column with a default value
ALTER TABLE client_detail
ADD COLUMN additional_config jsonb DEFAULT '{}'::jsonb;

-- Update existing entries to set the default value for the new column
UPDATE client_detail
SET additional_config = '{}'::jsonb
WHERE additional_config IS NULL;