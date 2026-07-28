package keymanager

import (
	"time"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// ObjectType values for GenerateMasterKeyRequest.ObjectType, mirroring the
// Java {objectType} path variable on /generateMasterKey and /generateECSignKey.
const (
	ObjectTypeCertificate = "certificate"
	ObjectTypeCSR         = "csr"
)

// GenerateMasterKeyRequest is the merged request for what was
// generateMasterKey + generateECSignKey in Java (see hierarchy.go /
// resolveKeyType for how ReferenceID selects RSA vs EC and, together with
// ApplicationID, the signing hierarchy tier). GenerateMasterKey is
// restricted to the ROOT and Component Master Key tiers only — see the
// method's doc comment in service.go.
type GenerateMasterKeyRequest struct {
	ApplicationID    string
	ReferenceID      string
	Force            bool
	ObjectType       string // ObjectTypeCertificate | ObjectTypeCSR
	CommonName       string
	OrganizationUnit string
	Organization     string
	Location         string
	State            string
	Country          string
}

// KeyPairResponse mirrors KeyPairGenerateResponseDto.
type KeyPairResponse struct {
	Certificate     string
	CertSignRequest string
	IssuedAt        time.Time
	ExpiryAt        time.Time
	Timestamp       time.Time
}

// CSRRequest mirrors CSRGenerateRequestDto. Note: the DN fields
// (CommonName etc.) are currently unused — GenerateCSR never originates a
// ROOT/Component Master Key from scratch (only GenerateMasterKey does; a
// request for one that doesn't exist yet returns ErrKeyNotFound), and on
// rotation reuses the previous certificate's own DN. For a Component
// Encryption Key, the DN always comes from the signing Component Master
// Key's own certificate, per your direction. GetCertificate takes no DN
// fields at all (ApplicationID/ReferenceID only) for the same reason;
// CSRRequest keeps them only because GenerateCSR's signature wasn't asked
// to change — flag if you'd rather they were dropped.
type CSRRequest struct {
	ApplicationID    string
	ReferenceID      string
	CommonName       string
	OrganizationUnit string
	Organization     string
	Location         string
	State            string
	Country          string
}

// UploadCertificateRequest mirrors UploadCertificateRequestDto — used for
// both UploadCertificate and UploadOtherDomainCertificate.
type UploadCertificateRequest struct {
	ApplicationID   string
	ReferenceID     string
	CertificateData string
}

// UploadCertificateResponse mirrors UploadCertificateResponseDto.
type UploadCertificateResponse struct {
	Status    string
	Timestamp time.Time
}

// SymmetricKeyRequest mirrors SymmetricKeyGenerateRequestDto.
type SymmetricKeyRequest struct {
	ApplicationID string
	ReferenceID   string
	Force         bool
}

// SymmetricKeyResponse mirrors SymmetricKeyGenerateResponseDto.
type SymmetricKeyResponse struct {
	Status    string
	Timestamp time.Time
}

// RevokeKeyRequest mirrors RevokeKeyRequestDto.
type RevokeKeyRequest struct {
	ApplicationID  string
	ReferenceID    string
	DisableAutoGen bool
}

// RevokeKeyResponse mirrors RevokeKeyResponseDto.
type RevokeKeyResponse struct {
	Status    string
	Timestamp time.Time
}

// CertificateData mirrors CertificateDataResponseDto.
type CertificateData struct {
	CertificateData string
	IssuedAt        time.Time
	ExpiryAt        time.Time
	KeyID           string
}

// AllCertificatesResponse mirrors AllCertificatesDataResponseDto.
type AllCertificatesResponse struct {
	AllCertificates []CertificateData
}

// CertificateChainResponse mirrors CertificateChainResponseDto.
type CertificateChainResponse struct {
	CertificatesTrustPath string
	Timestamp             time.Time
}

// SigningCertificate is returned by GetSigningCertificate — it carries the
// private key entry (not just the public cert), for use by a future signing
// component (mirrors Java's internal SignatureCertificate).
type SigningCertificate struct {
	Alias            string
	KeyPairEntry     *keystore.KeyPairEntry
	GenerationTime   time.Time
	ExpiryTime       time.Time
	ProviderName     string
	UniqueIdentifier string
}
