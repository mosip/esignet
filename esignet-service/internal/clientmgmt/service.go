/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package clientmgmt implements OIDC client registration management.
package clientmgmt

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt/db"
	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/persistence"
	"github.com/mosip/esignet/internal/reqcontext"
)

// ErrClientNotFound is returned when a client ID does not exist.
var ErrClientNotFound = errors.New("client not found")

// ErrDuplicateClientID is returned when the client ID already exists.
var ErrDuplicateClientID = errors.New("duplicate client id")

// ErrDuplicatePublicKey is returned when the public key hash already exists.
var ErrDuplicatePublicKey = errors.New("public key already registered")

// clientCacheNamespace isolates cached client rows in the shared runtime store.
const clientCacheNamespace providers.RuntimeStoreNamespace = "client:detail"

// statusActive is the canonical status of a client that may be used by the
// authentication engine. It matches the value stored by normalizeStatus.
const statusActive = "ACTIVE"

// eventWeakRSAKey is the audit event type published when an active client is
// loaded with an RSA key below minRSAKeyBits. Such clients predate the
// registration-time key size check and are still served, so the event is how
// operators trace them and notify the relying party to rotate the key.
const eventWeakRSAKey = "CLIENT_WEAK_RSA_KEY"

// Service handles client management business logic.
type Service struct {
	q                db.Querier
	txBase           *db.Queries
	db               *sqlx.DB
	driverName       string
	cache            providers.RuntimeStoreProvider
	cacheTTLSecs     int64
	logger           *applog.Logger
	supportedEncAlgs []string
	auditor          providers.ObservabilityProvider
}

// NewService creates a Service backed by the given database connection. Client
// lookups are cached in cache under the given TTL and invalidated on write.
// supportedEncAlgs restricts which "alg" values an EncPublicKey JWK may
// declare (config.AppConfig.SupportedEncAlgorithms); pass nil to leave "alg"
// unrestricted.
func NewService(conn *sqlx.DB, cache providers.RuntimeStoreProvider, cacheTTLSecs int64, supportedEncAlgs []string) *Service {
	queries := db.New(conn, conn.DriverName())
	return &Service{
		q: queries, txBase: queries, db: conn, driverName: conn.DriverName(),
		cache: cache, cacheTTLSecs: cacheTTLSecs,
		logger: applog.GetLogger().Named("clientmgmt"), supportedEncAlgs: supportedEncAlgs,
	}
}

// NewServiceWithQuerier supports tests that do not call lockClient. PUT/PATCH
// tests must use NewService with a database connection.
func NewServiceWithQuerier(q db.Querier, cache providers.RuntimeStoreProvider, cacheTTLSecs int64, supportedEncAlgs []string) *Service {
	return &Service{
		q: q, cache: cache, cacheTTLSecs: cacheTTLSecs,
		logger: applog.GetLogger().Named("clientmgmt"), supportedEncAlgs: supportedEncAlgs,
	}
}

// SetAuditor sets the observability provider that weak client key events are
// published to. The auditor is built after the Service (the ID system
// providers depend on it), so it can't be a constructor argument.
func (s *Service) SetAuditor(auditor providers.ObservabilityProvider) {
	s.auditor = auditor
}

// runWrite executes CreateClient with a consistent read-back. PostgreSQL uses
// RETURNING; MySQL runs the write and follow-up SELECT in one transaction.
// Mock-backed services (db == nil) run directly against their injected Querier.
func (s *Service) runWrite(ctx context.Context, fn func(q db.Querier) (db.ClientDetail, error)) (db.ClientDetail, error) {
	if s.db == nil || persistence.SupportsReturning(s.driverName) {
		return fn(s.q)
	}
	tx, err := s.db.BeginTxx(ctx, nil)
	if err != nil {
		return db.ClientDetail{}, fmt.Errorf("begin client tx: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	row, err := fn(s.txBase.WithTx(tx))
	if err != nil {
		return db.ClientDetail{}, err
	}
	if err := tx.Commit(); err != nil {
		return db.ClientDetail{}, fmt.Errorf("commit client tx: %w", err)
	}
	return row, nil
}

// lockClient starts a transaction before reading the row and holds its lock
// through validation and the subsequent PUT or PATCH.
func (s *Service) lockClient(ctx context.Context, id string) (db.Querier, *sqlx.Tx, db.ClientDetail, error) {
	tx, err := s.db.BeginTxx(ctx, nil)
	if err != nil {
		return nil, nil, db.ClientDetail{}, fmt.Errorf("begin client tx: %w", err)
	}
	q := s.txBase.WithTx(tx)
	row, err := q.GetClientForUpdate(ctx, id)
	if err != nil {
		_ = tx.Rollback()
		return nil, nil, db.ClientDetail{}, err
	}
	return q, tx, row, nil
}

// CreateClient registers a new OIDC client.
func (s *Service) CreateClient(ctx context.Context, profile Profile, req CreateClientRequest) (ClientResponse, error) {
	if err := ValidateCreate(profile, req, s.supportedEncAlgs); err != nil {
		return ClientResponse{}, err
	}

	publicKey, err := marshalJWK(req.PublicKey)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("public_key: %w", err)
	}
	pkHash := hashJWK(req.PublicKey)

	redirectURIs, err := marshalStringSlice(req.RedirectURIs)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("redirect_uris: %w", err)
	}
	claims, err := marshalStringSlice(uniqueStrings(req.Claims))
	if err != nil {
		return ClientResponse{}, fmt.Errorf("claims: %w", err)
	}
	acrValues, err := marshalStringSlice(req.AcrValues)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("acr_values: %w", err)
	}
	grantTypes, err := marshalStringSlice(req.GrantTypes)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("grant_types: %w", err)
	}
	authMethods, err := marshalStringSlice(req.AuthMethods)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client_auth_methods: %w", err)
	}
	additionalConfig, err := marshalAdditionalConfigRaw(req.AdditionalConfig)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("additional_config: %w", err)
	}

	encPK, encPKHash, encPKCert, err := encKeyColumns(req.EncPublicKey, req.EncPublicKeyCert, s.supportedEncAlgs)
	if err != nil {
		return ClientResponse{}, err
	}

	params := db.CreateClientParams{
		ID:               req.ClientID,
		Name:             marshalClientName(req.ClientName, req.ClientNameLangMap, profile),
		RpID:             req.RpID,
		LogoURI:          req.LogoURI,
		RedirectUris:     redirectURIs,
		Claims:           claims,
		AcrValues:        acrValues,
		PublicKey:        publicKey,
		PublicKeyHash:    pkHash,
		EncPublicKey:     encPK,
		EncPublicKeyHash: encPKHash,
		EncPublicKeyCert: encPKCert,
		GrantTypes:       grantTypes,
		AuthMethods:      authMethods,
		Status:           "ACTIVE",
		AdditionalConfig: additionalConfig,
		CrDtimes:         time.Now().UTC(),
	}

	row, err := s.runWrite(ctx, func(q db.Querier) (db.ClientDetail, error) {
		return q.CreateClient(ctx, params)
	})
	if err != nil {
		if isDuplicateClientID(err) {
			return ClientResponse{}, ErrDuplicateClientID
		}
		if isDuplicatePublicKeyHash(err) {
			return ClientResponse{}, &ValidationError{Code: "invalid_public_key"}
		}
		return ClientResponse{}, fmt.Errorf("create client: %w", err)
	}
	return toResponse(row)
}

// UpdateClient updates an existing OIDC client.
func (s *Service) UpdateClient(ctx context.Context, profile Profile, clientID string, req UpdateClientRequest) (ClientResponse, error) {
	q, tx, existing, err := s.lockClient(ctx, clientID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		return ClientResponse{}, fmt.Errorf("get client: %w", err)
	}
	defer func() { _ = tx.Rollback() }()
	// PUT cannot change encPublicKey, so its presence is carried over as-is.
	if err := ValidateUpdate(profile, req, existing.EncPublicKey.Valid); err != nil {
		return ClientResponse{}, err
	}

	status, err := normalizeStatus(req.Status)
	if err != nil {
		return ClientResponse{}, err
	}

	redirectURIs, err := marshalStringSlice(req.RedirectURIs)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("redirect_uris: %w", err)
	}
	claims, err := marshalStringSlice(uniqueStrings(req.Claims))
	if err != nil {
		return ClientResponse{}, fmt.Errorf("claims: %w", err)
	}
	acrValues, err := marshalStringSlice(req.AcrValues)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("acr_values: %w", err)
	}
	grantTypes, err := marshalStringSlice(req.GrantTypes)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("grant_types: %w", err)
	}
	authMethods, err := marshalStringSlice(req.AuthMethods)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client_auth_methods: %w", err)
	}
	additionalConfig, err := marshalAdditionalConfigRaw(req.AdditionalConfig)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("additional_config: %w", err)
	}

	now := time.Now().UTC()
	params := db.UpdateClientParams{
		ID:               clientID,
		Name:             marshalClientName(req.ClientName, req.ClientNameLangMap, profile),
		LogoURI:          req.LogoURI,
		RedirectUris:     redirectURIs,
		Claims:           claims,
		AcrValues:        acrValues,
		GrantTypes:       grantTypes,
		AuthMethods:      authMethods,
		Status:           status,
		AdditionalConfig: additionalConfig,
		UpdDtimes:        sql.NullTime{Time: now, Valid: true},
	}

	row, err := q.UpdateClient(ctx, params)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		return ClientResponse{}, fmt.Errorf("update client: %w", err)
	}
	resp, err := toResponse(row)
	if err != nil {
		return ClientResponse{}, err
	}
	if err := tx.Commit(); err != nil {
		return ClientResponse{}, fmt.Errorf("commit client tx: %w", err)
	}
	if err := s.invalidateCache(ctx, clientID); err != nil {
		return ClientResponse{}, err
	}
	return resp, nil
}

// PatchClient partially updates an existing OIDC client.
func (s *Service) PatchClient(ctx context.Context, clientID string, req PatchClientRequest, fields PatchFields) (ClientResponse, error) {
	if fields == (PatchFields{}) {
		return ClientResponse{}, &ValidationError{Code: "invalid_request", Message: "request must contain at least one field"}
	}

	q, tx, existing, err := s.lockClient(ctx, clientID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		return ClientResponse{}, fmt.Errorf("get client: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	// Validation stays inside the transaction, using the row locked above.
	req, err = ValidatePatch(req, fields, existing, s.supportedEncAlgs)
	if err != nil {
		return ClientResponse{}, err
	}
	params, err := preparePatchParams(clientID, req, fields, existing.Name)
	if err != nil {
		return ClientResponse{}, err
	}
	params.UpdDtimes = sql.NullTime{Time: time.Now().UTC(), Valid: true}

	row, err := q.PatchClient(ctx, params)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		if isDuplicatePublicKeyHash(err) {
			return ClientResponse{}, &ValidationError{Code: "invalid_public_key"}
		}
		return ClientResponse{}, fmt.Errorf("patch client: %w", err)
	}
	resp, err := toResponse(row)
	if err != nil {
		return ClientResponse{}, err
	}
	if err := tx.Commit(); err != nil {
		return ClientResponse{}, fmt.Errorf("commit client tx: %w", err)
	}
	if err := s.invalidateCache(ctx, clientID); err != nil {
		return ClientResponse{}, err
	}
	return resp, nil
}

// GetClient retrieves a client by ID, serving from cache when possible.
func (s *Service) GetClient(ctx context.Context, clientID string) (ClientResponse, error) {
	if row, ok := s.getCachedRow(ctx, clientID); ok {
		return toResponse(row)
	}

	row, err := s.q.GetClient(ctx, clientID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		return ClientResponse{}, fmt.Errorf("get client: %w", err)
	}
	s.cacheRow(ctx, clientID, row)
	return toResponse(row)
}

// GetActiveClient retrieves a client by ID only when its status is ACTIVE,
// serving from cache when possible. The authentication engine (actor, consent
// and i18n providers, executors, and authenticators) must never operate on a
// deactivated client, so a missing row and a non-ACTIVE row are both reported
// as ErrClientNotFound.
func (s *Service) GetActiveClient(ctx context.Context, clientID string) (ClientResponse, error) {
	row, err := s.getActiveRow(ctx, clientID)
	if err != nil {
		return ClientResponse{}, err
	}
	s.auditWeakRSAKeys(ctx, row)
	return toResponse(row)
}

// getActiveRow returns the ACTIVE row for clientID from cache, falling back to
// Postgres and populating the cache on a miss.
func (s *Service) getActiveRow(ctx context.Context, clientID string) (db.ClientDetail, error) {
	if row, ok := s.getCachedRow(ctx, clientID); ok {
		if row.Status != statusActive {
			return db.ClientDetail{}, ErrClientNotFound
		}
		return row, nil
	}

	row, err := s.q.GetActiveClient(ctx, clientID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return db.ClientDetail{}, ErrClientNotFound
		}
		return db.ClientDetail{}, fmt.Errorf("get active client: %w", err)
	}
	s.cacheRow(ctx, clientID, row)
	return row, nil
}

// auditWeakRSAKeys logs and publishes an eventWeakRSAKey event for each of
// row's RSA keys (signing and encryption) whose modulus is below
// minRSAKeyBits. It never fails the lookup.
func (s *Service) auditWeakRSAKeys(ctx context.Context, row db.ClientDetail) {
	keys := []struct{ field, jwk string }{{"public_key", row.PublicKey}}
	if row.EncPublicKey.Valid {
		keys = append(keys, struct{ field, jwk string }{"enc_public_key", row.EncPublicKey.String})
	}
	for _, k := range keys {
		bits, ok := rsaKeyBits(k.jwk)
		if !ok || bits >= minRSAKeyBits {
			continue
		}
		s.logger.Warn(ctx, "client registered with weak RSA key",
			applog.String("client_id", row.ID),
			applog.String("rp_id", row.RpID),
			applog.String("key_field", k.field),
			applog.Int("key_bits", bits))
		s.auditor.PublishEvent(ctx, &providers.Event{
			TraceID:   reqcontext.TraceIDFromContext(ctx),
			EventID:   uuid.NewString(),
			Type:      eventWeakRSAKey,
			Timestamp: time.Now().UTC(),
			Component: "clientmgmt",
			Status:    providers.StatusFailure,
			Data: map[string]any{
				"client_id":    row.ID,
				"rp_id":        row.RpID,
				"key_field":    k.field,
				"key_bits":     bits,
				"min_key_bits": minRSAKeyBits,
				"error":        fmt.Sprintf("%s RSA modulus is %d bits, minimum is %d", k.field, bits, minRSAKeyBits),
			},
		})
	}
}

// getCachedRow returns the cached row for clientID, if present. Any cache
// read/decode error is logged and treated as a miss: Postgres remains the
// source of truth, so a cache problem must never fail the request.
func (s *Service) getCachedRow(ctx context.Context, clientID string) (db.ClientDetail, bool) {
	if s.cache == nil {
		return db.ClientDetail{}, false
	}
	data, err := s.cache.Get(ctx, clientCacheNamespace, clientID)
	if err != nil {
		s.logger.Warn(ctx, "client cache get failed", applog.String("client_id", clientID), applog.Error(err))
		return db.ClientDetail{}, false
	}
	if data == nil {
		return db.ClientDetail{}, false
	}
	var row db.ClientDetail
	if err := json.Unmarshal(data, &row); err != nil {
		s.logger.Warn(ctx, "client cache decode failed", applog.String("client_id", clientID), applog.Error(err))
		return db.ClientDetail{}, false
	}
	s.logger.Debug(ctx, "client cache hit", applog.String("client_id", clientID))
	return row, true
}

// cacheRow populates the cache with row. Failures are logged, not returned:
// caching is a pure optimization on top of the successful DB read.
func (s *Service) cacheRow(ctx context.Context, clientID string, row db.ClientDetail) {
	if s.cache == nil {
		return
	}
	data, err := json.Marshal(row)
	if err != nil {
		s.logger.Warn(ctx, "client cache encode failed", applog.String("client_id", clientID), applog.Error(err))
		return
	}
	if err := s.cache.Put(ctx, clientCacheNamespace, clientID, data, s.cacheTTLSecs); err != nil {
		s.logger.Warn(ctx, "client cache put failed", applog.String("client_id", clientID), applog.Error(err))
	}
}

// invalidateCache clears the cached row for clientID after a successful
// write so the next GetClient re-reads Postgres. A failure here leaves the
// cache stale until it expires (up to cacheTTLSecs), which can allow reads
// of a deactivated or rotated client, so it is returned rather than only
// logged: the caller must be able to signal the partial failure.
func (s *Service) invalidateCache(ctx context.Context, clientID string) error {
	if s.cache == nil {
		return nil
	}
	if err := s.cache.Delete(ctx, clientCacheNamespace, clientID); err != nil {
		s.logger.Warn(ctx, "client cache invalidate failed", applog.String("client_id", clientID), applog.Error(err))
		return fmt.Errorf("invalidate client cache: %w", err)
	}
	return nil
}

// preparePatchParams serializes only supplied attributes for the UPDATE.
// It reads the stored name only when one of its two components must be retained.
func preparePatchParams(clientID string, req PatchClientRequest, fields PatchFields, storedName string) (db.PatchClientParams, error) {
	params := db.PatchClientParams{ID: clientID}
	if fields.ClientName || fields.ClientNameLangMap {
		clientName, langMap := req.ClientName, req.ClientNameLangMap
		if !fields.ClientName || !fields.ClientNameLangMap {
			storedClientName, storedLangMap, err := parseClientName(storedName)
			if err != nil {
				return db.PatchClientParams{}, err
			}
			if !fields.ClientName {
				clientName = storedClientName
			}
			if !fields.ClientNameLangMap {
				langMap = storedLangMap
			}
		}
		name := make(map[string]string, len(langMap)+1)
		for code, value := range langMap {
			name[code] = value
		}
		name["@none"] = clientName
		encoded, err := json.Marshal(name)
		if err != nil {
			return db.PatchClientParams{}, fmt.Errorf("marshal client name: %w", err)
		}
		params.Fields.Name = true
		params.Name = string(encoded)
	}
	if fields.LogoURI {
		params.Fields.LogoURI = true
		params.LogoURI = req.LogoURI
	}
	// These list fields share the same replacement and JSON storage behavior.
	for _, field := range []struct {
		selected bool
		label    string
		request  []string
		value    *string
		mask     *bool
	}{
		{fields.RedirectURIs, "redirect_uris", req.RedirectURIs, &params.RedirectUris, &params.Fields.RedirectURIs},
		{fields.Claims, "claims", req.Claims, &params.Claims, &params.Fields.Claims},
		{fields.AcrValues, "acr_values", req.AcrValues, &params.AcrValues, &params.Fields.AcrValues},
		{fields.GrantTypes, "grant_types", req.GrantTypes, &params.GrantTypes, &params.Fields.GrantTypes},
		{fields.AuthMethods, "auth_methods", req.AuthMethods, &params.AuthMethods, &params.Fields.AuthMethods},
	} {
		if !field.selected {
			continue
		}
		encoded, err := marshalStringSlice(field.request)
		if err != nil {
			return db.PatchClientParams{}, fmt.Errorf("marshal %s: %w", field.label, err)
		}
		*field.value = encoded
		*field.mask = true
	}
	if fields.Status {
		params.Fields.Status = true
		params.Status = req.Status
	}
	if fields.AdditionalConfig {
		value, err := marshalAdditionalConfigRaw(req.AdditionalConfig)
		if err != nil {
			return db.PatchClientParams{}, fmt.Errorf("marshal additional_config: %w", err)
		}
		params.Fields.AdditionalConfig = true
		params.AdditionalConfig = value
	}
	if fields.EncPublicKey {
		params.Fields.EncPublicKey = true
		if !req.EncPublicKey.IsNull {
			pkJSON, err := marshalJWK(req.EncPublicKey.Value)
			if err != nil {
				return db.PatchClientParams{}, fmt.Errorf("marshal enc_public_key: %w", err)
			}
			params.EncPublicKey = sql.NullString{String: pkJSON, Valid: true}
			params.EncPublicKeyHash = sql.NullString{String: hashJWK(req.EncPublicKey.Value), Valid: true}
		}
	}
	return params, nil
}

func marshalClientName(clientName string, langMap map[string]string, profile Profile) string {
	if profile == ProfileOIDC {
		return clientName
	}
	m := make(map[string]string, len(langMap)+1)
	for k, v := range langMap {
		m[k] = v
	}
	m["@none"] = clientName
	b, err := json.Marshal(m)
	if err != nil {
		return clientName
	}
	return string(b)
}

func parseClientName(name string) (string, map[string]string, error) {
	var langMap map[string]string
	if err := json.Unmarshal([]byte(name), &langMap); err != nil {
		return name, nil, nil
	}
	clientName := langMap["@none"]
	withoutNone := make(map[string]string, len(langMap))
	for k, v := range langMap {
		if k != "@none" {
			withoutNone[k] = v
		}
	}
	return clientName, withoutNone, nil
}

func encKeyColumns(encKey map[string]string, cert string, supportedEncAlgs []string) (sql.NullString, sql.NullString, sql.NullString, error) {
	if len(encKey) == 0 {
		return sql.NullString{}, sql.NullString{}, sql.NullString{}, nil
	}
	if err := validateEncJWK(encKey, supportedEncAlgs); err != nil {
		return sql.NullString{}, sql.NullString{}, sql.NullString{}, err
	}
	pkJSON, err := marshalJWK(encKey)
	if err != nil {
		return sql.NullString{}, sql.NullString{}, sql.NullString{}, err
	}
	encPK := sql.NullString{String: pkJSON, Valid: true}
	encHash := sql.NullString{String: hashJWK(encKey), Valid: true}
	var encCert sql.NullString
	if cert != "" {
		encCert = sql.NullString{String: cert, Valid: true}
	}
	return encPK, encHash, encCert, nil
}

// uniqueStrings returns a new slice with duplicate entries removed, preserving order.
func uniqueStrings(s []string) []string {
	seen := make(map[string]bool, len(s))
	out := make([]string, 0, len(s))
	for _, v := range s {
		if !seen[v] {
			seen[v] = true
			out = append(out, v)
		}
	}
	return out
}

func marshalStringSlice(s []string) (string, error) {
	if len(s) == 0 {
		return "[]", nil
	}
	b, err := json.Marshal(s)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func marshalAdditionalConfigRaw(raw json.RawMessage) (sql.NullString, error) {
	if len(raw) == 0 {
		return sql.NullString{}, nil
	}
	return sql.NullString{String: string(raw), Valid: true}, nil
}

func unmarshalStringSlice(s string) ([]string, error) {
	var out []string
	if err := json.Unmarshal([]byte(s), &out); err != nil {
		return nil, fmt.Errorf("unmarshal string slice: %w", err)
	}
	return out, nil
}

func unmarshalAdditionalConfig(s sql.NullString) (map[string]any, error) {
	if !s.Valid || s.String == "" {
		return nil, nil
	}
	var out map[string]any
	if err := json.Unmarshal([]byte(s.String), &out); err != nil {
		return nil, fmt.Errorf("unmarshal additional config: %w", err)
	}
	return out, nil
}

// isDuplicateClientID reports a primary-key violation on client_detail.id.
// "PRIMARY" covers MySQL, where the named PK constraint is discarded and the
// index is always reported as PRIMARY.
func isDuplicateClientID(err error) bool {
	e := persistence.GetErrorCode(err)
	return e.Code == persistence.ErrDuplicateKey &&
		(e.Constraint == "pk_clntdtl_id" || e.Constraint == "client_detail_pkey" || e.Constraint == "PRIMARY")
}

func isDuplicatePublicKeyHash(err error) bool {
	e := persistence.GetErrorCode(err)
	return e.Code == persistence.ErrDuplicateKey && e.Constraint == "uk_clntdtl_public_key_hash"
}

func toResponse(row db.ClientDetail) (ClientResponse, error) {
	clientName, langMap, err := parseClientName(row.Name)
	if err != nil {
		return ClientResponse{}, err
	}
	redirectURIs, err := unmarshalStringSlice(row.RedirectUris)
	if err != nil {
		return ClientResponse{}, err
	}
	claims, err := unmarshalStringSlice(row.Claims)
	if err != nil {
		return ClientResponse{}, err
	}
	acrValues, err := unmarshalStringSlice(row.AcrValues)
	if err != nil {
		return ClientResponse{}, err
	}
	grantTypes, err := unmarshalStringSlice(row.GrantTypes)
	if err != nil {
		return ClientResponse{}, err
	}
	authMethods, err := unmarshalStringSlice(row.AuthMethods)
	if err != nil {
		return ClientResponse{}, err
	}
	additionalConfig, err := unmarshalAdditionalConfig(row.AdditionalConfig)
	if err != nil {
		return ClientResponse{}, err
	}
	encPK := ""
	if row.EncPublicKey.Valid {
		encPK = row.EncPublicKey.String
	}
	return ClientResponse{
		ClientID:          row.ID,
		Status:            row.Status,
		ClientName:        clientName,
		ClientNameLangMap: langMap,
		RpID:              row.RpID,
		LogoURI:           row.LogoURI,
		RedirectURIs:      redirectURIs,
		Claims:            claims,
		AcrValues:         acrValues,
		PublicKey:         row.PublicKey,
		EncPublicKey:      encPK,
		GrantTypes:        grantTypes,
		AuthMethods:       authMethods,
		AdditionalConfig:  additionalConfig,
	}, nil
}
