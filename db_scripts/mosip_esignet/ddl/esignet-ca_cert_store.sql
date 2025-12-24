-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Database Name: mosip_esignet
-- Table Name : ca_cert_store
-- Purpose    : CA Certificate Store Table

CREATE TABLE ca_cert_store(
	cert_id varchar(36) NOT NULL,
	cert_subject varchar(500) NOT NULL,
	cert_issuer varchar(500) NOT NULL,
	issuer_id varchar(36) NOT NULL,
	cert_not_before timestamp,
	cert_not_after timestamp,
	crl_uri varchar(120),
	cert_data varchar(4000),
	cert_thumbprint varchar(100),
	cert_serial_no varchar(50),
	partner_domain varchar(36),
	cr_by varchar(256),
	cr_dtimes timestamp,
	upd_by varchar(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp,
	ca_cert_type varchar(25),
	CONSTRAINT pk_cacs_id PRIMARY KEY (cert_id),
	CONSTRAINT cert_thumbprint_unique UNIQUE (cert_thumbprint,partner_domain)
);

-- COMMENT ON TABLE ca_cert_store IS 'Certificate Authority Certificate Store: Store details of all the certificate provided by certificate authority which will be used by MOSIP';
-- COMMENT ON COLUMN ca_cert_store.cert_id IS 'Certificate ID: Unique ID (UUID) will be generated and assigned to the uploaded CA/Sub-CA certificate';
-- COMMENT ON COLUMN ca_cert_store.cert_subject IS 'Certificate Subject: Subject DN of the certificate';
-- COMMENT ON COLUMN ca_cert_store.cert_issuer IS 'Certificate Issuer: Issuer DN of the certificate';
-- COMMENT ON COLUMN ca_cert_store.issuer_id IS 'Issuer UUID of the certificate. (Issuer certificate should be available in the DB)';
-- COMMENT ON COLUMN ca_cert_store.cert_not_before IS 'Certificate Start Date: Certificate Interval - Validity Start Date & Time';
-- COMMENT ON COLUMN ca_cert_store.cert_not_after IS 'Certificate Validity end Date: Certificate Interval - Validity End Date & Time';
-- COMMENT ON COLUMN ca_cert_store.crl_uri IS 'CRL URL: CRL URI of the issuer.';
-- COMMENT ON COLUMN ca_cert_store.cert_data IS 'Certificate Data: PEM Encoded actual certificate data.';
-- COMMENT ON COLUMN ca_cert_store.cert_thumbprint IS 'Certificate Thumb Print: SHA1 generated certificate thumbprint.';
-- COMMENT ON COLUMN ca_cert_store.cert_serial_no IS 'Certificate Serial No: Serial Number of the certificate.';
-- COMMENT ON COLUMN ca_cert_store.partner_domain IS 'Partner Domain : To add Partner Domain in CA/Sub-CA certificate chain';
-- COMMENT ON COLUMN ca_cert_store.cr_by IS 'Created By : ID or name of the user who create / insert record';
-- COMMENT ON COLUMN ca_cert_store.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
-- COMMENT ON COLUMN ca_cert_store.upd_by IS 'Updated By : ID or name of the user who update the record with new values';
-- COMMENT ON COLUMN ca_cert_store.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- COMMENT ON COLUMN ca_cert_store.is_deleted IS 'IS_Deleted : Flag to mark whether the record is Soft deleted.';
-- COMMENT ON COLUMN ca_cert_store.del_dtimes IS 'Deleted DateTimestamp : Date and Timestamp when the record is soft deleted with is_deleted=TRUE';
-- COMMENT ON COLUMN ca_cert_store.ca_cert_type IS 'CA Certificate Type : Indicates if the certificate is a ROOT or INTERMEDIATE CA certificate';
