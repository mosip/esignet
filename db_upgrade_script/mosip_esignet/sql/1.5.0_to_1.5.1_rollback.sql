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
IF is_column_jsonb('client_detail', 'public_key') THEN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name='client_detail_migr_bkp'
    ) THEN
        DROP TABLE client_detail;
        CREATE TABLE client_detail (LIKE client_detail_migr_bkp including ALL);
        INSERT INTO client_detail SELECT * FROM client_detail_migr_bkp;
        DROP TABLE client_detail_migr_bkp;
    ELSE
        RAISE EXCEPTION 'Error: Backup doesn''t exist';
    END IF;
END IF;
END $$