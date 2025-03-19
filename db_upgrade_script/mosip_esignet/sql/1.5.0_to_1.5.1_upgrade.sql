\c mosip_esignet

CREATE OR REPLACE FUNCTION is_column_jsonb(
    p_table_name text,
    p_column_name text,
    p_schema_name text DEFAULT current_schema()
) RETURNS boolean AS $$
DECLARE
    v_column_type text;
BEGIN
    -- Get the column data type
    SELECT data_type INTO v_column_type
    FROM information_schema.columns
    WHERE table_schema = p_schema_name
    AND table_name = p_table_name
    AND column_name = p_column_name;

    -- Handle case when column doesn't exist
    IF v_column_type IS NULL THEN
        RAISE EXCEPTION 'Column %.% does not exist', p_table_name, p_column_name;
    END IF;

    -- Return true if jsonb, false otherwise
    RETURN v_column_type = 'jsonb';

EXCEPTION
    WHEN undefined_table THEN
        RAISE EXCEPTION 'Table %.% does not exist', p_schema_name, p_table_name;
    WHEN OTHERS THEN
        RAISE EXCEPTION 'Error checking column type: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;


DO $$
BEGIN

IF NOT is_column_jsonb('client_detail', 'public_key') THEN

    -- create backup
    DROP TABLE IF EXISTS client_detail_migr_bkp;
    CREATE TABLE client_detail_migr_bkp (LIKE client_detail including ALL);
    INSERT into client_detail_migr_bkp SELECT * from client_detail;
    ----

    ALTER TABLE client_detail ADD COLUMN public_key_new jsonb;
    UPDATE client_detail SET public_key_new = public_key::jsonb;
    ALTER TABLE client_detail DROP COLUMN public_key;
    ALTER TABLE client_detail RENAME COLUMN public_key_new TO public_key;

    -- inactivating clients with same modulus in public key
    WITH duplicates AS (
        SELECT public_key->>'n' as modulus
        FROM client_detail
        WHERE public_key->>'n' IS NOT NULL
        GROUP BY public_key->>'n'
        HAVING COUNT(*) > 1
    )
    UPDATE client_detail SET status='INACTIVE', public_key='{}'::jsonb where id IN (
    SELECT
        client_detail.id
    FROM client_detail
    JOIN duplicates ON client_detail.public_key->>'n' = duplicates.modulus);
    ----

    ALTER TABLE client_detail ALTER COLUMN public_key SET NOT NULL;
    CREATE UNIQUE INDEX unique_n_value ON client_detail ((public_key->>'n'));
    RAISE NOTICE 'Upgrade Successful';
ELSE
    RAISE NOTICE 'Database already uptodate';
END IF;
END $$
