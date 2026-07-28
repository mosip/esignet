package db

import "time"

// auditColumns holds the audit fields shared by every keymgr table
// (mirrors the Java @MappedSuperclass BaseEntity).
type auditColumns struct {
	CrBy      string     `db:"cr_by"`
	CrDtimes  time.Time  `db:"cr_dtimes"`
	UpdBy     *string    `db:"upd_by"`
	UpdDtimes *time.Time `db:"upd_dtimes"`
	IsDeleted *bool      `db:"is_deleted"`
	DelDtimes *time.Time `db:"del_dtimes"`
}

// KeyAlias mirrors table keymgr.key_alias.
type KeyAlias struct {
	ID              string     `db:"id"`
	AppID           string     `db:"app_id"`
	RefID           *string    `db:"ref_id"`
	KeyGenDtimes    *time.Time `db:"key_gen_dtimes"`
	KeyExpireDtimes *time.Time `db:"key_expire_dtimes"`
	StatusCode      *string    `db:"status_code"`
	CertThumbprint  *string    `db:"cert_thumbprint"`
	UniIdent        *string    `db:"uni_ident"`
	auditColumns
}

// KeyPolicy mirrors table keymgr.key_policy_def.
type KeyPolicy struct {
	AppID               string `db:"app_id"`
	KeyValidityDuration int    `db:"key_validity_duration"`
	IsActive            bool   `db:"is_active"`
	PreExpireDays       int    `db:"pre_expire_days"`
	AccessAllowed       string `db:"access_allowed"`
	auditColumns
}

// KeyStoreRecord mirrors table keymgr.key_store (named to avoid clashing
// with the keystore.KeyStore port).
type KeyStoreRecord struct {
	ID              string  `db:"id"`
	CertificateData string  `db:"certificate_data"`
	PrivateKey      string  `db:"private_key"`
	MasterKey       *string `db:"master_key"`
	auditColumns
}
