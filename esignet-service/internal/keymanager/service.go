// Package keymanager ports the MOSIP kernel-keymanager-service's
// KeymanagerService business logic to Go: key generation across the ROOT ->
// Component Master Key -> Component Encryption Key hierarchy, certificate
// issuance/upload/revocation, and lazy expiry-driven rotation, against the
// existing keymgr Postgres schema and a PKCS#11/PKCS#12 keystore backend.
package keymanager

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"database/sql"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"

	"github.com/mosip/esignet/internal/keymanager/db"
	"github.com/mosip/esignet/internal/keymanager/keystore"
)

// Sentinel errors for validation/lookup failures (branch via errors.Is).
var (
	ErrThumbprintMismatch = errors.New("uploaded certificate's public key does not match the existing key pair")
	ErrPrivateKeyExists   = errors.New("a private key already exists for this application/reference id")
	ErrUnsupportedCurve   = errors.New("unsupported EC curve")
	ErrKeyNotFound        = errors.New("key not found")

	// ErrKeyAlreadyGeneratedToday is returned when a key was already
	// generated (and possibly since revoked) for the same
	// (ApplicationID, ReferenceID) earlier the same calendar day. This is
	// an inherited limitation of the uni_ident scheme (SHA1 of
	// appID+refID+date, see rotation.go), not a transient error — retry
	// tomorrow, or use a different ReferenceID.
	ErrKeyAlreadyGeneratedToday = errors.New("a key for this application/reference id was already generated earlier today; uni_ident is only unique per calendar day")

	// ErrUnknownApplicationID is returned when ApplicationID has no row in
	// key_policy_def — an application must be registered (a policy row
	// added, e.g. via a "add ROOT/ESIGNET_RSA policy" migration) before any
	// key can be generated for it. This is a hard error, never a silent
	// fallback to the BASE policy — BASE is used only to govern Component
	// Encryption Key validity (see policyForKeyTier), not as a stand-in for
	// an unregistered application.
	ErrUnknownApplicationID = errors.New("application id not found in key_policy_def")

	// ErrBlankReferenceID is returned when GenerateMasterKey, GetCertificate,
	// or GenerateCSR is called with a blank ReferenceID for any
	// ApplicationID other than ROOT. A blank ref id is meaningful only for
	// ROOT (self-signed, tier 0 of the hierarchy, §6.1) — every Component
	// Master Key has RefID=RSA_2048 and every Component Encryption Key
	// needs its own distinguishing ref id; neither tier has a sensible
	// "blank" identity.
	ErrBlankReferenceID = errors.New("reference id is required for all applications other than ROOT")

	// ErrEncryptionKeyGenerationNotAllowed is returned when GenerateMasterKey
	// is called for a reference id that resolves to a Component Encryption
	// Key (i.e. isKeystoreResident == false). GenerateMasterKey is
	// restricted to the ROOT and Component Master Key tiers only —
	// Component Encryption Keys are generated (and auto-rotated) on demand
	// by GetCertificate/GenerateCSR instead. Intended operational model: an
	// administrator runs GenerateMasterKey once per application to
	// provision its Component Master Key; after that, any caller can fetch
	// certificates/CSRs (including for not-yet-generated encryption keys)
	// via GetCertificate/GenerateCSR without ever needing GenerateMasterKey.
	// This is a structural restriction based on which tier ReferenceID
	// resolves to — no caller-role check is implemented or intended here.
	ErrEncryptionKeyGenerationNotAllowed = errors.New("component encryption keys cannot be generated via GenerateMasterKey; use GetCertificate or GenerateCSR instead")

	// ErrSymmetricKeyRefIDNotAllowed is returned when GenerateSymmetricKey's
	// ReferenceID isn't in the configured allow-list
	// (Config.SymmetricKeyAllowedRefIDs / KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS).
	// Unlike ApplicationID (validated against key_policy_def, a DB table),
	// there's no natural registry for symmetric key reference ids, so the
	// allow-list is config-driven; an empty/unset list allows nothing,
	// requiring explicit configuration rather than silently accepting any
	// reference id.
	ErrSymmetricKeyRefIDNotAllowed = errors.New("reference id not in the configured symmetric key allow-list")

	// ErrReferenceIDReservedForSymmetricKey is returned when
	// GenerateMasterKey, GetCertificate, or GenerateCSR is called with a
	// ReferenceID that's in Config.SymmetricKeyAllowedRefIDs — reference ids
	// configured as valid for symmetric key generation are reserved for
	// that purpose and may not be used for any asymmetric key, including
	// ROOT, a Component Master Key, an EC sign key, or a Component
	// Encryption Key. Checked against the configured list directly, not
	// against whether a symmetric key with that reference id has actually
	// been generated — the reservation holds even if none ever is.
	ErrReferenceIDReservedForSymmetricKey = errors.New("reference id is reserved for symmetric key generation and cannot be used for an asymmetric key")

	// ErrForeignDomainAppIDNotAllowed is returned when
	// UploadOtherDomainCertificate's ApplicationID isn't in the configured
	// allow-list (Config.ForeignDomainAllowedAppIDs /
	// KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS, default "PARTNER,IDA").
	ErrForeignDomainAppIDNotAllowed = errors.New("application id is not in the configured foreign-domain allow-list")

	// ErrForeignDomainAppIDRegistered is returned when
	// UploadOtherDomainCertificate's ApplicationID already has a row in
	// key_policy_def — i.e. it's one of MOSIP's own registered applications,
	// not a genuinely foreign domain. A foreign-domain, cert-only entry must
	// never share an ApplicationID with the real key hierarchy.
	ErrForeignDomainAppIDRegistered = errors.New("application id is already registered in key_policy_def and cannot be used for a foreign-domain upload")

	// ErrCertificateAlreadyExists is returned when UploadCertificate or
	// UploadOtherDomainCertificate is called with a certificate whose
	// SHA-256 thumbprint already matches an existing key_alias row — for
	// UploadCertificate, the current alias's own certificate; for
	// UploadOtherDomainCertificate, any existing row for the same
	// (ApplicationID, ReferenceID). The same certificate has already been
	// uploaded, so this is a caller mistake, not a benign re-upload.
	ErrCertificateAlreadyExists = errors.New("a certificate with this thumbprint already exists for this application/reference id")
)

const (
	statusSuccess     = "success"
	certificatePEMTag = "CERTIFICATE"
)

// Service implements the keymanager business logic.
type Service struct {
	q   db.Querier
	ks  keystore.KeyStore
	cfg Config
}

// NewService constructs a Service backed by a real DB connection.
func NewService(conn *sqlx.DB, ks keystore.KeyStore, cfg Config) *Service {
	return &Service{q: db.New(conn, cfg.DBSchema), ks: ks, cfg: cfg}
}

// NewServiceWithQuerier constructs a Service with an explicit Querier; used
// in tests to inject a fake without a real database connection.
func NewServiceWithQuerier(q db.Querier, ks keystore.KeyStore, cfg Config) *Service {
	return &Service{q: q, ks: ks, cfg: cfg}
}

// currentAlias returns the current (non-expired, past its pre-expiry
// buffer) key_alias row for (appID, refID), or nil if none exists.
func (s *Service) currentAlias(ctx context.Context, appID, refID string) (*db.KeyAlias, error) {
	aliases, err := s.q.GetKeyAliasesByAppRef(ctx, appID, refID)
	if err != nil {
		return nil, fmt.Errorf("get key aliases: %w", err)
	}
	if len(aliases) == 0 {
		return nil, nil
	}
	policy, err := s.policyForKeyTier(ctx, appID, isKeystoreResident(appID, refID))
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC()
	for i := range aliases {
		a := aliases[i]
		if a.KeyGenDtimes == nil || a.KeyExpireDtimes == nil {
			continue
		}
		if isCurrent(now, *a.KeyGenDtimes, *a.KeyExpireDtimes, policy.PreExpireDays) {
			return &a, nil
		}
	}
	return nil, nil
}

// mostRecentAlias returns the most recently generated key_alias row for
// (appID, refID) regardless of whether it is still current, or nil if no
// key has ever been generated. Used by ensureCurrentKey to preserve
// certificate-DN continuity when GetCertificate/GenerateCSR auto-rotate an
// expired ROOT/Component Master Key — those are only ever rotated here,
// never originated (that's GenerateMasterKey's job), so there is always a
// previous certificate to carry the DN forward from, or this method's nil
// result signals that assumption doesn't hold.
func (s *Service) mostRecentAlias(ctx context.Context, appID, refID string) (*db.KeyAlias, error) {
	aliases, err := s.q.GetKeyAliasesByAppRef(ctx, appID, refID)
	if err != nil {
		return nil, fmt.Errorf("get key aliases: %w", err)
	}
	if len(aliases) == 0 {
		return nil, nil
	}
	return &aliases[0], nil // GetKeyAliasesByAppRef orders by key_gen_dtimes DESC
}

// validateAppID confirms appID is a registered application — i.e. has its
// own row in key_policy_def — before any key material is generated for it.
// Returns ErrUnknownApplicationID (not a silent BASE fallback) when it
// isn't; see policyForKeyTier for why BASE is a separate concern.
func (s *Service) validateAppID(ctx context.Context, appID string) error {
	if _, err := s.q.GetKeyPolicy(ctx, appID); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return fmt.Errorf("%w: %q", ErrUnknownApplicationID, appID)
		}
		return fmt.Errorf("validate application id %q: %w", appID, err)
	}
	return nil
}

// policyForKeyTier returns the key_policy_def row that governs expiry for a
// key generation, based on which hierarchy tier it belongs to (§6.1): ROOT,
// Component Master Keys, and EC/sign keys (keystoreResident == true) use
// their own application's policy row. Component Encryption Keys
// (DB-resident, keystoreResident == false) always use the shared "BASE"
// policy row instead — an application's own master-key validity period
// doesn't have to match the rotation cadence of its individual encryption
// keys, so encryption keys are governed by one common policy regardless of
// which application owns them.
//
// This assumes validateAppID has already confirmed appID itself is
// registered; policyForKeyTier does not fall back to BASE for a missing
// appID row — call validateAppID first if that hasn't already happened.
func (s *Service) policyForKeyTier(ctx context.Context, appID string, keystoreResident bool) (db.KeyPolicy, error) {
	policyAppID := appID
	if !keystoreResident {
		policyAppID = "BASE"
	}
	p, err := s.q.GetKeyPolicy(ctx, policyAppID)
	if err != nil {
		return db.KeyPolicy{}, fmt.Errorf("get key policy for %q: %w", policyAppID, err)
	}
	return p, nil
}

// isKeystoreResident reports whether a key lives directly in the
// PKCS#11/PKCS#12 keystore backend (ROOT, Component Master Keys, and
// EC/sign keys) as opposed to being DB-resident — an RSA key generated
// in-process and stored encrypted (under its Component Master Key) in
// keymgr.key_store (Component Encryption Keys).
func isKeystoreResident(appID, refID string) bool {
	if appID == AppIDRoot || refID == RefIDRSA2048 {
		return true
	}
	switch refID {
	case RefIDECSECP256K1Sign, RefIDECSECP256R1Sign, RefIDED25519Sign:
		return true
	}
	return false
}

// ensureCurrentKey returns the current key_alias for (appID, refID),
// generating — and, if the existing one has passed its pre-expiry cutoff,
// transparently rotating — a new key when necessary. This is the single
// shared implementation behind GenerateMasterKey, GetCertificate, and
// GenerateCSR: the only difference between them is onlyMasterTiers, which
// restricts generation to the ROOT/Component Master Key tiers
// (GenerateMasterKey) versus allowing any tier including Component
// Encryption Keys (GetCertificate/GenerateCSR) — see each method's doc
// comment for the operational rationale.
func (s *Service) ensureCurrentKey(ctx context.Context, appID, refID string, params keystore.CertificateParameters, force, onlyMasterTiers bool) (*db.KeyAlias, bool, error) {
	if appID == AppIDRoot {
		refID = ""
	} else if refID == "" {
		return nil, false, ErrBlankReferenceID
	}
	if err := s.validateAsymmetricRefIDNotReserved(refID); err != nil {
		return nil, false, err
	}
	if err := s.validateAppID(ctx, appID); err != nil {
		return nil, false, err
	}
	resident := isKeystoreResident(appID, refID)
	if onlyMasterTiers && !resident {
		return nil, false, fmt.Errorf("%w: application %q, reference %q", ErrEncryptionKeyGenerationNotAllowed, appID, refID)
	}

	algoName, curveName := resolveKeyType(refID)
	signKeyAlias, err := s.resolveSignKeyAlias(ctx, appID, refID)
	if err != nil {
		return nil, resident, err
	}

	current, err := s.currentAlias(ctx, appID, refID)
	if err != nil {
		return nil, resident, err
	}
	if current != nil && !force {
		return current, resident, nil
	}

	// GenerateMasterKey (onlyMasterTiers) uses the caller-supplied params,
	// filling any blank field from configured defaults — an administrator
	// may name ROOT/Component Master Keys explicitly, or just take the
	// defaults. GetCertificate/GenerateCSR never take DN input; the
	// certificate subject is always derived structurally instead:
	if onlyMasterTiers {
		params = s.applyCertSubjectDefaults(params, appID, refID)
	} else if resident {
		// ROOT / Component Master Key: GetCertificate/GenerateCSR only ever
		// *rotate* these, never originate them (that's GenerateMasterKey's
		// job) — reuse the whole DN, CommonName included, from the most
		// recently generated certificate, even if it's the one now
		// expiring, for identity continuity across rotations.
		prev, err := s.mostRecentAlias(ctx, appID, refID)
		if err != nil {
			return nil, resident, err
		}
		if prev == nil {
			return nil, resident, fmt.Errorf("%w: application %q, reference %q — generate it via GenerateMasterKey first", ErrKeyNotFound, appID, refID)
		}
		prevCert, err := s.certificateForAlias(prev.ID, resident)
		if err != nil {
			return nil, resident, err
		}
		params = certParamsFromSubject(prevCert.Subject)
	} else {
		// Component Encryption Key: the O/OU/L/ST/C fields always come
		// from the signing Component Master Key's current certificate, per
		// your direction — both on first generation and on every rotation.
		// CommonName is rebuilt from the configured default plus the
		// "ENC_<appID>_<refID>" suffix rather than inheriting the master's
		// own component-identifying suffix — each encryption key carries
		// its own reference id so it can be told apart from its master and
		// from other encryption keys under the same component.
		// signKeyAlias is always non-empty here: only ROOT self-signs
		// (signKeyAlias == ""), and ROOT is always resident.
		masterCert, err := s.ks.GetCertificate(signKeyAlias)
		if err != nil {
			return nil, resident, fmt.Errorf("get signing certificate for DN: %w", err)
		}
		params = certParamsFromSubject(masterCert.Subject)
		params.CommonName = fmt.Sprintf("%s (%s)", s.cfg.CertCommonName, certSubjectSuffix(appID, refID, false))
	}

	alias := uuid.NewString()
	signAlias := signKeyAlias
	if signAlias == "" {
		signAlias = alias // ROOT: self-signed
	}
	now := time.Now().UTC()
	policy, err := s.policyForKeyTier(ctx, appID, resident)
	if err != nil {
		return nil, resident, err
	}
	expiry := expiryFor(now, policy)
	params.NotBefore = now
	params.NotAfter = expiry

	if !resident {
		if err := s.generateDBResidentKey(ctx, alias, signAlias, params); err != nil {
			return nil, resident, err
		}
	} else {
		if err := s.ks.GenerateAndStoreAsymmetricKey(alias, signAlias, params, algoName, curveName); err != nil {
			return nil, resident, fmt.Errorf("generate asymmetric key: %w", err)
		}
	}

	newAlias, err := s.persistNewAlias(ctx, appID, refID, alias, now, expiry)
	if err != nil {
		return nil, resident, err
	}
	return newAlias, resident, nil
}

// GenerateMasterKey provisions the ROOT key or a Component Master Key
// (RefID=RSA_2048) — an administrative, essentially one-time-per-application
// operation. It refuses to generate a Component Encryption Key
// (ErrEncryptionKeyGenerationNotAllowed): those are generated (and
// auto-rotated) on demand by GetCertificate/GenerateCSR instead, the first
// time either is called for a not-yet-generated reference id. The intended
// flow: an administrator runs GenerateMasterKey once per application to
// provision ROOT/its Component Master Key; every other caller only ever
// uses GetCertificate/GenerateCSR, including to obtain the ROOT/Component
// Master Key's own certificate or CSR (no separate access to
// GenerateMasterKey is needed for that — see GetCertificate). This is a
// structural restriction on which tier ReferenceID resolves to, not a
// caller-role check — none is implemented here.
func (s *Service) GenerateMasterKey(ctx context.Context, req GenerateMasterKeyRequest) (KeyPairResponse, error) {
	params := keystore.CertificateParameters{
		CommonName: req.CommonName, OrganizationUnit: req.OrganizationUnit,
		Organization: req.Organization, Location: req.Location, State: req.State, Country: req.Country,
	}
	alias, resident, err := s.ensureCurrentKey(ctx, req.ApplicationID, req.ReferenceID, params, req.Force, true)
	if err != nil {
		return KeyPairResponse{}, err
	}
	return s.buildKeyPairResponse(*alias, req.ObjectType, resident)
}

// persistNewAlias inserts the key_alias row for a freshly generated key,
// self-healing on a uni_ident unique-violation (§5 of the implementation
// plan) by re-querying and returning the row the other concurrent request
// just inserted, rather than propagating the error.
func (s *Service) persistNewAlias(ctx context.Context, appID, refID, alias string, genTime, expiry time.Time) (*db.KeyAlias, error) {
	certThumbprint, err := s.certThumbprint(alias, isKeystoreResident(appID, refID))
	if err != nil {
		return nil, err
	}
	uniIdent := uniqueIdentifier(appID, refID, genTime)
	ka := db.KeyAlias{
		ID: alias, AppID: appID, RefID: &refID,
		KeyGenDtimes: &genTime, KeyExpireDtimes: &expiry,
		CertThumbprint: &certThumbprint, UniIdent: &uniIdent,
	}
	ka.CrBy = "keymanager"
	ka.CrDtimes = genTime

	if err := s.q.InsertKeyAlias(ctx, ka); err != nil {
		if IsDuplicateUniIdent(err) {
			existing, rerr := s.currentAlias(ctx, appID, refID)
			if rerr != nil {
				return nil, rerr
			}
			if existing != nil {
				// Another concurrent request generated (appID, refID)'s key
				// first, on the same calendar day — self-heal (§5): return
				// its row rather than erroring.
				return existing, nil
			}
			// uni_ident is only unique per (appID, refID, calendar day) —
			// inherited as-is from the Java service (KeymanagerServiceImpl's
			// uniqueValue formatter). No *current* alias exists (e.g. it was
			// just revoked), yet the insert still collided: a key for this
			// (appID, refID) was already generated earlier today and the
			// hash landed on the same row. This isn't a concurrency race;
			// it's a same-day regeneration limit inherited from the source
			// system, so it gets its own clear error instead of a raw DB
			// constraint message.
			return nil, fmt.Errorf("%w: application %q, reference %q", ErrKeyAlreadyGeneratedToday, appID, refID)
		}
		return nil, fmt.Errorf("insert key alias: %w", err)
	}
	return &ka, nil
}

func (s *Service) certThumbprint(alias string, keystoreResident bool) (string, error) {
	cert, err := s.certificateForAlias(alias, keystoreResident)
	if err != nil {
		return "", err
	}
	return thumbprintForCert(cert), nil
}

// thumbprintForCert is the SHA-256 hex digest of a certificate's raw DER —
// the same value stored in key_alias.cert_thumbprint.
func thumbprintForCert(cert *x509.Certificate) string {
	sum := sha256.Sum256(cert.Raw)
	return hex.EncodeToString(sum[:])
}

func (s *Service) certificateForAlias(alias string, keystoreResident bool) (*x509.Certificate, error) {
	if keystoreResident {
		return s.ks.GetCertificate(alias)
	}
	rec, err := s.q.GetKeyStoreRecord(context.Background(), alias)
	if err != nil {
		return nil, fmt.Errorf("get key_store record: %w", err)
	}
	return parseCertPEM(rec.CertificateData)
}

// generateDBResidentKey generates a Component Encryption Key in-process
// (RSA, per this system's convention — application encryption keys are
// always RSA), signs its certificate with the Component Master Key
// (signAlias), and stores the private key envelope-encrypted (AES-256-GCM
// under a random DEK, itself RSA-OAEP-wrapped by the Component Master Key's
// public key — see envelope.go) in keymgr.key_store.
func (s *Service) generateDBResidentKey(ctx context.Context, alias, masterAlias string, params keystore.CertificateParameters) error {
	priv, err := rsa.GenerateKey(rand.Reader, s.cfg.AsymmetricKeyLength)
	if err != nil {
		return fmt.Errorf("generate RSA key: %w", err)
	}

	masterCert, err := s.ks.GetCertificate(masterAlias)
	if err != nil {
		return fmt.Errorf("get component master certificate %q: %w", masterAlias, err)
	}
	masterPriv, err := s.ks.GetPrivateKey(masterAlias)
	if err != nil {
		return fmt.Errorf("get component master private key %q: %w", masterAlias, err)
	}
	masterSigner, ok := masterPriv.(crypto.Signer)
	if !ok {
		return fmt.Errorf("component master key %q does not support signing", masterAlias)
	}

	template, err := buildCertTemplate(params)
	if err != nil {
		return err
	}
	certDER, err := x509.CreateCertificate(rand.Reader, template, masterCert, &priv.PublicKey, masterSigner)
	if err != nil {
		return fmt.Errorf("create certificate: %w", err)
	}

	masterPub, err := s.ks.GetPublicKey(masterAlias)
	if err != nil {
		return fmt.Errorf("get component master public key %q: %w", masterAlias, err)
	}
	masterRSAPub, ok := masterPub.(*rsa.PublicKey)
	if !ok {
		return fmt.Errorf("component master key %q is not RSA", masterAlias)
	}
	privDER, err := x509.MarshalPKCS8PrivateKey(priv)
	if err != nil {
		return fmt.Errorf("marshal private key: %w", err)
	}
	encPriv, err := envelopeEncrypt(masterRSAPub, privDER)
	if err != nil {
		return fmt.Errorf("encrypt private key: %w", err)
	}

	rec := db.KeyStoreRecord{
		ID:              alias,
		CertificateData: encodeCertPEM(certDER),
		PrivateKey:      encPriv,
		MasterKey:       &masterAlias,
	}
	rec.CrBy = "keymanager"
	rec.CrDtimes = time.Now().UTC()
	if err := s.q.InsertKeyStoreRecord(ctx, rec); err != nil {
		return fmt.Errorf("insert key_store record: %w", err)
	}
	return nil
}

// decryptDBResidentPrivateKey reverses generateDBResidentKey's envelope
// encryption, using the Component Master Key's private key to unwrap the DEK.
func (s *Service) decryptDBResidentPrivateKey(rec db.KeyStoreRecord) (*rsa.PrivateKey, error) {
	if rec.MasterKey == nil {
		return nil, fmt.Errorf("key_store record %q has no master_key reference", rec.ID)
	}
	masterPriv, err := s.ks.GetPrivateKey(*rec.MasterKey)
	if err != nil {
		return nil, fmt.Errorf("get component master private key %q: %w", *rec.MasterKey, err)
	}
	decrypter, ok := masterPriv.(crypto.Decrypter)
	if !ok {
		return nil, fmt.Errorf("component master key %q does not support decryption", *rec.MasterKey)
	}
	privDER, err := envelopeDecrypt(decrypter, rec.PrivateKey)
	if err != nil {
		return nil, fmt.Errorf("unwrap private key: %w", err)
	}
	key, err := x509.ParsePKCS8PrivateKey(privDER)
	if err != nil {
		return nil, fmt.Errorf("parse private key: %w", err)
	}
	rsaKey, ok := key.(*rsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("unwrapped key is not RSA")
	}
	return rsaKey, nil
}

func (s *Service) buildKeyPairResponse(alias db.KeyAlias, objectType string, keystoreResident bool) (KeyPairResponse, error) {
	cert, err := s.certificateForAlias(alias.ID, keystoreResident)
	if err != nil {
		return KeyPairResponse{}, err
	}
	resp := KeyPairResponse{Timestamp: time.Now().UTC()}
	if alias.KeyGenDtimes != nil {
		resp.IssuedAt = *alias.KeyGenDtimes
	}
	if alias.KeyExpireDtimes != nil {
		resp.ExpiryAt = *alias.KeyExpireDtimes
	}
	if objectType == ObjectTypeCSR {
		priv, err := s.privateKeyForAlias(alias.ID, keystoreResident)
		if err != nil {
			return KeyPairResponse{}, err
		}
		signer, ok := priv.(crypto.Signer)
		if !ok {
			return KeyPairResponse{}, fmt.Errorf("key %q does not support signing", alias.ID)
		}
		csrDER, err := x509.CreateCertificateRequest(rand.Reader, &x509.CertificateRequest{Subject: cert.Subject}, signer)
		if err != nil {
			return KeyPairResponse{}, fmt.Errorf("create CSR: %w", err)
		}
		resp.CertSignRequest = encodePEM("CERTIFICATE REQUEST", csrDER)
		return resp, nil
	}
	resp.Certificate = encodeCertPEM(cert.Raw)
	return resp, nil
}

func (s *Service) privateKeyForAlias(alias string, keystoreResident bool) (crypto.PrivateKey, error) {
	if keystoreResident {
		return s.ks.GetPrivateKey(alias)
	}
	rec, err := s.q.GetKeyStoreRecord(context.Background(), alias)
	if err != nil {
		return nil, fmt.Errorf("get key_store record: %w", err)
	}
	return s.decryptDBResidentPrivateKey(rec)
}

// GetCertificate returns the current certificate for (appID, refID) — ROOT,
// a Component Master Key, or a Component Encryption Key. Unlike
// GenerateMasterKey, it generates the key on first request when none exists
// yet (this is the only way a Component Encryption Key ever gets created —
// its certificate DN is derived from the signing Component Master Key's own
// certificate, never from caller input; GetCertificate takes no DN fields),
// and always performs lazy rotation: if the current key has passed its
// pre-expiry cutoff, a new one is generated transparently before the
// certificate is returned — for every tier, including ROOT and Component
// Master Keys (reusing the previous certificate's DN on rotation, since
// GetCertificate never originates ROOT/Component Master Key identity —
// that's GenerateMasterKey's job; see ensureCurrentKey).
func (s *Service) GetCertificate(ctx context.Context, appID, refID string) (KeyPairResponse, error) {
	alias, resident, err := s.ensureCurrentKey(ctx, appID, refID, keystore.CertificateParameters{}, false, false)
	if err != nil {
		return KeyPairResponse{}, err
	}
	return s.buildKeyPairResponse(*alias, ObjectTypeCertificate, resident)
}

// GenerateCSR builds a PKCS#10 CSR for the current (or, on first request or
// after rotation, newly generated) key — same generation/auto-rotation
// scope as GetCertificate, across every tier including Component
// Encryption Keys.
func (s *Service) GenerateCSR(ctx context.Context, req CSRRequest) (KeyPairResponse, error) {
	params := keystore.CertificateParameters{
		CommonName: req.CommonName, OrganizationUnit: req.OrganizationUnit,
		Organization: req.Organization, Location: req.Location, State: req.State, Country: req.Country,
	}
	alias, resident, err := s.ensureCurrentKey(ctx, req.ApplicationID, req.ReferenceID, params, false, false)
	if err != nil {
		return KeyPairResponse{}, err
	}
	return s.buildKeyPairResponse(*alias, ObjectTypeCSR, resident)
}

// UploadCertificate replaces the certificate for the current alias after
// verifying the uploaded certificate's public key matches the existing key
// pair (public-key match — mirrors Java's uploadCertificate) and rejecting
// it outright if it's the exact same certificate already on file (thumbprint
// match against the current alias's own cert_thumbprint) — a public-key
// match alone doesn't rule out a byte-identical re-upload, since a renewed
// certificate can legitimately reuse the same key pair with a different
// thumbprint.
func (s *Service) UploadCertificate(ctx context.Context, req UploadCertificateRequest) (UploadCertificateResponse, error) {
	current, err := s.currentAlias(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return UploadCertificateResponse{}, err
	}
	if current == nil {
		return UploadCertificateResponse{}, ErrKeyNotFound
	}
	newCert, err := parseCertPEM(req.CertificateData)
	if err != nil {
		return UploadCertificateResponse{}, fmt.Errorf("parse uploaded certificate: %w", err)
	}
	newThumbprint := thumbprintForCert(newCert)
	if current.CertThumbprint != nil && *current.CertThumbprint == newThumbprint {
		return UploadCertificateResponse{}, ErrCertificateAlreadyExists
	}

	resident := isKeystoreResident(req.ApplicationID, req.ReferenceID)
	existingPub, err := s.publicKeyForAlias(current.ID, resident)
	if err != nil {
		return UploadCertificateResponse{}, err
	}
	if !publicKeysEqual(existingPub, newCert.PublicKey) {
		return UploadCertificateResponse{}, ErrThumbprintMismatch
	}

	if resident {
		priv, err := s.ks.GetPrivateKey(current.ID)
		if err != nil {
			return UploadCertificateResponse{}, err
		}
		if err := s.ks.StoreCertificate(current.ID, priv, newCert); err != nil {
			return UploadCertificateResponse{}, fmt.Errorf("store certificate: %w", err)
		}
	} else {
		rec, err := s.q.GetKeyStoreRecord(ctx, current.ID)
		if err != nil {
			return UploadCertificateResponse{}, fmt.Errorf("get key_store record: %w", err)
		}
		rec.CertificateData = encodeCertPEM(newCert.Raw)
		now := time.Now().UTC()
		rec.UpdDtimes = &now
		if err := s.q.UpdateKeyStoreRecord(ctx, rec); err != nil {
			return UploadCertificateResponse{}, fmt.Errorf("update key_store record: %w", err)
		}
	}

	// key_gen_dtimes/key_expire_dtimes must track the uploaded certificate's
	// own validity window (NotBefore/NotAfter), not the previous
	// certificate's — otherwise isCurrent (§5) would keep judging currency
	// against a window the new certificate doesn't actually have, same as
	// Java's uploadCertificate (storeKeyInAlias(appId, notBeforeDate, ...,
	// notAfterDate, ...)).
	genTime := newCert.NotBefore.UTC()
	expiry := newCert.NotAfter.UTC()
	current.KeyGenDtimes = &genTime
	current.KeyExpireDtimes = &expiry
	current.CertThumbprint = &newThumbprint
	now := time.Now().UTC()
	current.UpdDtimes = &now
	if err := s.q.UpdateKeyAlias(ctx, *current); err != nil {
		return UploadCertificateResponse{}, fmt.Errorf("update key alias: %w", err)
	}
	return UploadCertificateResponse{Status: statusSuccess, Timestamp: now}, nil
}

// UploadOtherDomainCertificate stores a foreign-domain, cert-only entry
// (private_key = "NA" in key_store) — MOSIP holds no private key for this
// alias. ApplicationID must be a configured foreign domain
// (Config.ForeignDomainAllowedAppIDs) that is NOT itself a registered MOSIP
// application (validateForeignDomainAppID); ReferenceID may freely reuse any
// ref id already used for asymmetric key generation (RSA_2048,
// EC_SECP256K1_SIGN, etc.) since the appID is guaranteed foreign. Every
// existing key_alias row for (ApplicationID, ReferenceID) is checked: one
// backed by a real private key blocks the upload (ErrPrivateKeyExists), and
// one whose thumbprint already matches the uploaded certificate blocks it as
// a duplicate (ErrCertificateAlreadyExists). Deliberately bypasses
// currentAlias/persistNewAlias/isKeystoreResident — those assume residency
// tied to MOSIP's own key hierarchy, which doesn't apply here: a
// foreign-domain cert is never keystore-resident, regardless of which ref id
// it reuses.
func (s *Service) UploadOtherDomainCertificate(ctx context.Context, req UploadCertificateRequest) (UploadCertificateResponse, error) {
	if err := s.validateForeignDomainAppID(ctx, req.ApplicationID); err != nil {
		return UploadCertificateResponse{}, err
	}

	cert, err := parseCertPEM(req.CertificateData)
	if err != nil {
		return UploadCertificateResponse{}, fmt.Errorf("parse uploaded certificate: %w", err)
	}
	thumbprint := thumbprintForCert(cert)

	existing, err := s.q.GetKeyAliasesByAppRef(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return UploadCertificateResponse{}, fmt.Errorf("get key aliases: %w", err)
	}
	for _, a := range existing {
		if a.CertThumbprint != nil && *a.CertThumbprint == thumbprint {
			return UploadCertificateResponse{}, ErrCertificateAlreadyExists
		}
		if rec, err := s.q.GetKeyStoreRecord(ctx, a.ID); err == nil && rec.PrivateKey != "NA" {
			return UploadCertificateResponse{}, ErrPrivateKeyExists
		}
	}

	alias := uuid.NewString()
	now := time.Now().UTC()
	// master_key is NOT NULL in key_store; Java's storeKeyInDBStore sets it
	// self-referentially (masterAlias == alias) for foreign-domain, cert-only
	// entries — there is no real Component Master Key wrapping anything here.
	rec := db.KeyStoreRecord{ID: alias, CertificateData: encodeCertPEM(cert.Raw), PrivateKey: "NA", MasterKey: &alias}
	rec.CrBy = "keymanager"
	rec.CrDtimes = now
	if err := s.q.InsertKeyStoreRecord(ctx, rec); err != nil {
		return UploadCertificateResponse{}, fmt.Errorf("insert key_store record: %w", err)
	}

	// key_gen_dtimes/key_expire_dtimes track the certificate's own validity
	// window (NotBefore/NotAfter), not the upload timestamp — same as Java's
	// storeAndBuildResponse (storeKeyInAlias(appId, notBeforeDate, ...,
	// notAfterDate, ...)), and consistent with UploadCertificate (above).
	uniIdent := uniqueIdentifier(req.ApplicationID, req.ReferenceID, now)
	refID := req.ReferenceID
	genTime := cert.NotBefore.UTC()
	expiry := cert.NotAfter.UTC()
	ka := db.KeyAlias{
		ID: alias, AppID: req.ApplicationID, RefID: &refID,
		KeyGenDtimes: &genTime, KeyExpireDtimes: &expiry,
		CertThumbprint: &thumbprint, UniIdent: &uniIdent,
	}
	ka.CrBy = "keymanager"
	ka.CrDtimes = now
	if err := s.q.InsertKeyAlias(ctx, ka); err != nil {
		if IsDuplicateUniIdent(err) {
			return UploadCertificateResponse{}, fmt.Errorf("%w: application %q, reference %q", ErrKeyAlreadyGeneratedToday, req.ApplicationID, req.ReferenceID)
		}
		return UploadCertificateResponse{}, fmt.Errorf("insert key alias: %w", err)
	}
	return UploadCertificateResponse{Status: statusSuccess, Timestamp: now}, nil
}

// GenerateSymmetricKey generates and stores an AES key; expiry uses the
// fixed SymmetricKeyValidity window — no certificate is involved and it is
// not part of the signing hierarchy.
func (s *Service) GenerateSymmetricKey(ctx context.Context, req SymmetricKeyRequest) (SymmetricKeyResponse, error) {
	if err := s.validateAppID(ctx, req.ApplicationID); err != nil {
		return SymmetricKeyResponse{}, err
	}
	if err := s.validateSymmetricKeyRefID(req.ReferenceID); err != nil {
		return SymmetricKeyResponse{}, err
	}
	current, err := s.currentAlias(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return SymmetricKeyResponse{}, err
	}
	now := time.Now().UTC()
	if current == nil || req.Force {
		alias := uuid.NewString()
		if err := s.ks.GenerateAndStoreSymmetricKey(alias); err != nil {
			return SymmetricKeyResponse{}, fmt.Errorf("generate symmetric key: %w", err)
		}
		expiry := now.Add(s.cfg.SymmetricKeyValidity)
		uniIdent := uniqueIdentifier(req.ApplicationID, req.ReferenceID, now)
		refID := req.ReferenceID
		ka := db.KeyAlias{ID: alias, AppID: req.ApplicationID, RefID: &refID, KeyGenDtimes: &now, KeyExpireDtimes: &expiry, UniIdent: &uniIdent}
		ka.CrBy = "keymanager"
		ka.CrDtimes = now
		if err := s.q.InsertKeyAlias(ctx, ka); err != nil && !IsDuplicateUniIdent(err) {
			return SymmetricKeyResponse{}, fmt.Errorf("insert key alias: %w", err)
		}
	}
	return SymmetricKeyResponse{Status: statusSuccess, Timestamp: now}, nil
}

// RevokeKey immediately invalidates the current key by moving its expiry
// into the past — no deletion from the keystore.
func (s *Service) RevokeKey(ctx context.Context, req RevokeKeyRequest) (RevokeKeyResponse, error) {
	current, err := s.currentAlias(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return RevokeKeyResponse{}, err
	}
	if current == nil {
		return RevokeKeyResponse{}, ErrKeyNotFound
	}
	now := time.Now().UTC()
	expired := now.Add(-1 * time.Minute)
	current.KeyExpireDtimes = &expired
	current.UpdDtimes = &now
	if err := s.q.UpdateKeyAlias(ctx, *current); err != nil {
		return RevokeKeyResponse{}, fmt.Errorf("update key alias: %w", err)
	}
	return RevokeKeyResponse{Status: statusSuccess, Timestamp: now}, nil
}

// GetAllCertificates returns the full alias history for (appID, refID).
func (s *Service) GetAllCertificates(ctx context.Context, appID, refID string) (AllCertificatesResponse, error) {
	aliases, err := s.q.GetKeyAliasesByAppRef(ctx, appID, refID)
	if err != nil {
		return AllCertificatesResponse{}, fmt.Errorf("get key aliases: %w", err)
	}
	resident := isKeystoreResident(appID, refID)
	resp := AllCertificatesResponse{AllCertificates: make([]CertificateData, 0, len(aliases))}
	for _, a := range aliases {
		cert, err := s.certificateForAlias(a.ID, resident)
		if err != nil {
			continue
		}
		cd := CertificateData{CertificateData: encodeCertPEM(cert.Raw), KeyID: a.ID}
		if a.KeyGenDtimes != nil {
			cd.IssuedAt = *a.KeyGenDtimes
		}
		if a.KeyExpireDtimes != nil {
			cd.ExpiryAt = *a.KeyExpireDtimes
		}
		resp.AllCertificates = append(resp.AllCertificates, cd)
	}
	return resp, nil
}

// GetCertificateChain builds a PKCS#7 (p7b) trust chain by walking the
// signing hierarchy up to ROOT.
func (s *Service) GetCertificateChain(ctx context.Context, appID, refID string) (CertificateChainResponse, error) {
	resident := isKeystoreResident(appID, refID)
	leaf, err := s.certificateForAliasByAppRef(ctx, appID, refID, resident)
	if err != nil {
		return CertificateChainResponse{}, err
	}
	chain := []*x509.Certificate{leaf}
	curAppID, curRefID := appID, refID
	for {
		if curAppID == AppIDRoot {
			break
		}
		var parentAppID, parentRefID string
		if curRefID == RefIDRSA2048 {
			parentAppID, parentRefID = AppIDRoot, ""
		} else {
			parentAppID, parentRefID = curAppID, RefIDRSA2048
		}
		parentAlias, err := s.currentAlias(ctx, parentAppID, parentRefID)
		if err != nil {
			return CertificateChainResponse{}, err
		}
		if parentAlias == nil {
			break
		}
		parentCert, err := s.certificateForAlias(parentAlias.ID, isKeystoreResident(parentAppID, parentRefID))
		if err != nil {
			break
		}
		chain = append(chain, parentCert)
		curAppID, curRefID = parentAppID, parentRefID
	}
	return CertificateChainResponse{CertificatesTrustPath: buildPKCS7TrustPath(chain), Timestamp: time.Now().UTC()}, nil
}

func (s *Service) certificateForAliasByAppRef(ctx context.Context, appID, refID string, resident bool) (*x509.Certificate, error) {
	alias, err := s.currentAlias(ctx, appID, refID)
	if err != nil {
		return nil, err
	}
	if alias == nil {
		return nil, ErrKeyNotFound
	}
	return s.certificateForAlias(alias.ID, resident)
}

// GetSigningCertificate resolves the current signing key for a sign ref id,
// generating on-the-fly via the same lazy-rotation path as
// GetCertificate/GenerateCSR (not GenerateMasterKey, which is restricted to
// ROOT/Component Master Key tiers only) if expired/absent, and returns the
// private key entry for use by a future signing component.
func (s *Service) GetSigningCertificate(ctx context.Context, appID, refID string) (SigningCertificate, error) {
	alias, _, err := s.ensureCurrentKey(ctx, appID, refID, keystore.CertificateParameters{}, false, false)
	if err != nil {
		return SigningCertificate{}, err
	}
	entry, err := s.ks.GetAsymmetricKey(alias.ID)
	if err != nil {
		return SigningCertificate{}, fmt.Errorf("get asymmetric key: %w", err)
	}
	sc := SigningCertificate{Alias: alias.ID, KeyPairEntry: entry, ProviderName: s.ks.ProviderName()}
	if alias.KeyGenDtimes != nil {
		sc.GenerationTime = *alias.KeyGenDtimes
	}
	if alias.KeyExpireDtimes != nil {
		sc.ExpiryTime = *alias.KeyExpireDtimes
	}
	if alias.UniIdent != nil {
		sc.UniqueIdentifier = *alias.UniIdent
	}
	return sc, nil
}

func (s *Service) publicKeyForAlias(alias string, keystoreResident bool) (crypto.PublicKey, error) {
	if keystoreResident {
		return s.ks.GetPublicKey(alias)
	}
	rec, err := s.q.GetKeyStoreRecord(context.Background(), alias)
	if err != nil {
		return nil, fmt.Errorf("get key_store record: %w", err)
	}
	cert, err := parseCertPEM(rec.CertificateData)
	if err != nil {
		return nil, err
	}
	return cert.PublicKey, nil
}

// certParamsFromSubject copies a certificate's Subject DN into
// CertificateParameters — used by ensureCurrentKey to derive a new key's
// identity from an existing certificate (the signing Component Master
// Key's, for a Component Encryption Key; or the previous certificate's
// own, when GetCertificate/GenerateCSR auto-rotate ROOT/a Component Master
// Key) instead of from caller input, which GetCertificate/GenerateCSR don't
// accept for this purpose.
func certParamsFromSubject(subject pkix.Name) keystore.CertificateParameters {
	return keystore.CertificateParameters{
		CommonName:       subject.CommonName,
		OrganizationUnit: firstOrEmpty(subject.OrganizationalUnit),
		Organization:     firstOrEmpty(subject.Organization),
		Location:         firstOrEmpty(subject.Locality),
		State:            firstOrEmpty(subject.Province),
		Country:          firstOrEmpty(subject.Country),
	}
}

func firstOrEmpty(vals []string) string {
	if len(vals) == 0 {
		return ""
	}
	return vals[0]
}

// certSubjectSuffix returns the "(...)" label appended to a certificate's
// CommonName, identifying its tier/component/key precisely enough to tell
// apart, at a glance, an RSA Component Master Key from an EC sign key from
// a specific Component Encryption Key:
//
//   - ROOT certificate: the literal "ROOT"
//   - Component Master Key or EC sign key (signed directly by ROOT,
//     resident=true): "<appID>_<refID>", e.g. "THUNDER_ID_RSA_2048" or
//     "THUNDER_ID_EC_SECP256R1_SIGN"
//   - Component Encryption Key (resident=false): "ENC_<appID>_<refID>",
//     e.g. "ENC_THUNDER_ID_SOME_ENCRYPT_KEY"
func certSubjectSuffix(appID, refID string, resident bool) string {
	if appID == AppIDRoot {
		return AppIDRoot
	}
	if !resident {
		return fmt.Sprintf("ENC_%s_%s", appID, refID)
	}
	return fmt.Sprintf("%s_%s", appID, refID)
}

// applyCertSubjectDefaults fills any blank DN field in params from the
// service's configured certificate-subject defaults (Config.CertCommonName
// etc.) — used only for GenerateMasterKey (ROOT/Component Master Key/EC
// sign key), where an administrator may supply an explicit DN or take the
// defaults. GetCertificate/GenerateCSR never call this; they derive DN
// structurally instead (see ensureCurrentKey). Always appends the
// tier/component suffix to CommonName, whether it came from params or from
// the default.
func (s *Service) applyCertSubjectDefaults(params keystore.CertificateParameters, appID, refID string) keystore.CertificateParameters {
	if params.CommonName == "" {
		params.CommonName = s.cfg.CertCommonName
	}
	if params.OrganizationUnit == "" {
		params.OrganizationUnit = s.cfg.CertOrganizationUnit
	}
	if params.Organization == "" {
		params.Organization = s.cfg.CertOrganization
	}
	if params.Location == "" {
		params.Location = s.cfg.CertLocation
	}
	if params.State == "" {
		params.State = s.cfg.CertState
	}
	if params.Country == "" {
		params.Country = s.cfg.CertCountry
	}
	params.CommonName = fmt.Sprintf("%s (%s)", params.CommonName, certSubjectSuffix(appID, refID, true))
	return params
}

// --- PEM / certificate helpers ---

func encodeCertPEM(der []byte) string {
	return encodePEM(certificatePEMTag, der)
}

func encodePEM(tag string, der []byte) string {
	block := &pem.Block{Type: tag, Bytes: der}
	return string(pem.EncodeToMemory(block))
}

func parseCertPEM(data string) (*x509.Certificate, error) {
	block, _ := pem.Decode([]byte(data))
	if block == nil {
		return x509.ParseCertificate([]byte(data)) // tolerate raw DER too
	}
	return x509.ParseCertificate(block.Bytes)
}
