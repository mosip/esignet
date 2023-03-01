-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : key_alias
-- Purpose    : Key Alias table
--
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
-- ------------------------------------------------------------------------------------------
CREATE TABLE key_alias(
    id character varying(36) NOT NULL,
    app_id character varying(36) NOT NULL,
    ref_id character varying(128),
    key_gen_dtimes timestamp,
    key_expire_dtimes timestamp,
    status_code character varying(36),
    lang_code character varying(3),
    cr_by character varying(256) NOT NULL,
    cr_dtimes timestamp NOT NULL,
    upd_by character varying(256),
    upd_dtimes timestamp,
    is_deleted boolean DEFAULT FALSE,
    del_dtimes timestamp,
    cert_thumbprint character varying(100),
    uni_ident character varying(50),
    CONSTRAINT pk_keymals_id PRIMARY KEY (id),
    CONSTRAINT uni_ident_const UNIQUE (uni_ident)
);

COMMENT ON TABLE key_alias IS 'Contains key alias and  metadata of all the keys used in MOSIP system.';

COMMENT ON COLUMN key_alias.id IS 'Unique identifier (UUID) used for referencing keys in key_store table and HSM';
COMMENT ON COLUMN key_alias.app_id IS 'To reference a Module key';
COMMENT ON COLUMN key_alias.ref_id IS 'To reference a Encryption key ';
COMMENT ON COLUMN key_alias.key_gen_dtimes IS 'Date and time when the key was generated.';
COMMENT ON COLUMN key_alias.key_expire_dtimes IS 'Date and time when the key will be expired. This will be derived based on the configuration / policy defined in Key policy definition.';
COMMENT ON COLUMN key_alias.status_code IS 'Status of the key, whether it is active or expired.';
COMMENT ON COLUMN key_alias.lang_code IS 'For multilanguage implementation this attribute Refers master.language.code. The value of some of the attributes in current record is stored in this respective language. ';
COMMENT ON COLUMN key_alias.cr_by IS 'ID or name of the user who create / insert record';
COMMENT ON COLUMN key_alias.cr_dtimes IS 'Date and Timestamp when the record is created/inserted';
COMMENT ON COLUMN key_alias.upd_by IS 'ID or name of the user who update the record with new values';
COMMENT ON COLUMN key_alias.upd_dtimes IS 'Date and Timestamp when any of the fields in the record is updated with new values.';
COMMENT ON COLUMN key_alias.is_deleted IS 'Flag to mark whether the record is Soft deleted.';
COMMENT ON COLUMN key_alias.del_dtimes IS 'Date and Timestamp when the record is soft deleted with is_deleted=TRUE';