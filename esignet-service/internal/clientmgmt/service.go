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
	"strings"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt/db"
	applog "github.com/mosip/esignet/internal/log"
)

// ErrClientNotFound is returned when a client ID does not exist.
var ErrClientNotFound = errors.New("client not found")

// ErrDuplicateClientID is returned when the client ID already exists.
var ErrDuplicateClientID = errors.New("duplicate client id")

// ErrDuplicatePublicKey is returned when the public key hash already exists.
var ErrDuplicatePublicKey = errors.New("public key already registered")

// ErrClientConflict is returned when a PATCH races with another update.
var ErrClientConflict = errors.New("client was modified concurrently")

// clientCacheNamespace isolates cached client rows in the shared runtime store.
const clientCacheNamespace providers.RuntimeStoreNamespace = "client:detail"

// Service handles client management business logic.
type Service struct {
	q            db.Querier
	cache        providers.RuntimeStoreProvider
	cacheTTLSecs int64
	logger       *applog.Logger
}

// NewService creates a Service backed by the given database connection. Client
// lookups are cached in cache under the given TTL and invalidated on write.
func NewService(conn *sql.DB, cache providers.RuntimeStoreProvider, cacheTTLSecs int64) *Service {
	return &Service{q: db.New(conn), cache: cache, cacheTTLSecs: cacheTTLSecs, logger: applog.GetLogger().Named("clientmgmt")}
}

// NewServiceWithQuerier creates a Service with an explicit Querier; use in tests
// to inject a mock without a real database connection.
func NewServiceWithQuerier(q db.Querier, cache providers.RuntimeStoreProvider, cacheTTLSecs int64) *Service {
	return &Service{q: q, cache: cache, cacheTTLSecs: cacheTTLSecs, logger: applog.GetLogger().Named("clientmgmt")}
}

// CreateClient registers a new OIDC client.
func (s *Service) CreateClient(ctx context.Context, profile Profile, req CreateClientRequest) (ClientResponse, error) {
	if err := ValidateCreate(profile, req); err != nil {
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
	claims, err := marshalStringSlice(req.Claims)
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

	encPK, encPKHash, encPKCert, err := encKeyColumns(req.EncPublicKey, req.EncPublicKeyCert)
	if err != nil {
		return ClientResponse{}, err
	}

	params := db.CreateClientParams{
		ID:               req.ClientID,
		Name:             marshalClientName(req.ClientName, req.ClientNameLangMap, profile),
		RpID:             req.RpID,
		LogoUri:          req.LogoURI,
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

	row, err := s.q.CreateClient(ctx, params)
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
	if err := ValidateUpdate(profile, req); err != nil {
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
	claims, err := marshalStringSlice(req.Claims)
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
		LogoUri:          req.LogoURI,
		RedirectUris:     redirectURIs,
		Claims:           claims,
		AcrValues:        acrValues,
		GrantTypes:       grantTypes,
		AuthMethods:      authMethods,
		Status:           status,
		AdditionalConfig: additionalConfig,
		UpdDtimes:        sql.NullTime{Time: now, Valid: true},
	}

	row, err := s.q.UpdateClient(ctx, params)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		return ClientResponse{}, fmt.Errorf("update client: %w", err)
	}
	s.invalidateCache(ctx, clientID)
	return toResponse(row)
}

// PatchClient partially updates an existing OIDC client.
func (s *Service) PatchClient(ctx context.Context, clientID string, req PatchClientRequest, fields PatchFields) (ClientResponse, error) {
	existing, err := s.q.GetClient(ctx, clientID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		return ClientResponse{}, fmt.Errorf("get client: %w", err)
	}

	merged, err := mergePatch(existing, req, fields)
	if err != nil {
		return ClientResponse{}, err
	}
	if err := ValidatePatch(ProfileClient, merged, fields, req.EncPublicKey); err != nil {
		return ClientResponse{}, err
	}

	status := existing.Status
	if fields.Status {
		status, err = normalizeStatus(merged.Status)
		if err != nil {
			return ClientResponse{}, err
		}
	}

	redirectURIs, err := marshalStringSlice(merged.RedirectURIs)
	if err != nil {
		return ClientResponse{}, err
	}
	claims, err := marshalStringSlice(merged.Claims)
	if err != nil {
		return ClientResponse{}, err
	}
	acrValues, err := marshalStringSlice(merged.AcrValues)
	if err != nil {
		return ClientResponse{}, err
	}
	grantTypes, err := marshalStringSlice(merged.GrantTypes)
	if err != nil {
		return ClientResponse{}, err
	}
	authMethods, err := marshalStringSlice(merged.AuthMethods)
	if err != nil {
		return ClientResponse{}, err
	}
	additionalConfig, err := marshalAdditionalConfigRaw(merged.AdditionalConfig)
	if err != nil {
		return ClientResponse{}, err
	}

	encPK := existing.EncPublicKey
	encPKHash := existing.EncPublicKeyHash
	encPKCert := existing.EncPublicKeyCert
	if fields.EncPublicKey {
		if req.EncPublicKey.IsNull {
			encPK = sql.NullString{}
			encPKHash = sql.NullString{}
			encPKCert = sql.NullString{}
		} else {
			if err := validateJWK(req.EncPublicKey.Value); err != nil {
				return ClientResponse{}, err
			}
			pkJSON, err := marshalJWK(req.EncPublicKey.Value)
			if err != nil {
				return ClientResponse{}, err
			}
			encPK = sql.NullString{String: pkJSON, Valid: true}
			encPKHash = sql.NullString{String: hashJWK(req.EncPublicKey.Value), Valid: true}
			encPKCert = sql.NullString{}
		}
	}

	now := time.Now().UTC()
	params := db.PatchClientParams{
		ID:                clientID,
		Name:              marshalClientName(merged.ClientName, merged.ClientNameLangMap, ProfileClient),
		LogoUri:           merged.LogoURI,
		RedirectUris:      redirectURIs,
		Claims:            claims,
		AcrValues:         acrValues,
		GrantTypes:        grantTypes,
		AuthMethods:       authMethods,
		Status:            status,
		AdditionalConfig:  additionalConfig,
		EncPublicKey:      encPK,
		EncPublicKeyHash:  encPKHash,
		EncPublicKeyCert:  encPKCert,
		ExpectedUpdDtimes: existing.UpdDtimes,
		UpdDtimes:         sql.NullTime{Time: now, Valid: true},
	}

	row, err := s.q.PatchClient(ctx, params)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientConflict
		}
		if isDuplicatePublicKeyHash(err) {
			return ClientResponse{}, &ValidationError{Code: "invalid_public_key"}
		}
		return ClientResponse{}, fmt.Errorf("patch client: %w", err)
	}
	s.invalidateCache(ctx, clientID)
	return toResponse(row)
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

// getCachedRow returns the cached row for clientID, if present. Any cache
// read/decode error is logged and treated as a miss: Postgres remains the
// source of truth, so a cache problem must never fail the request.
func (s *Service) getCachedRow(ctx context.Context, clientID string) (db.ClientDetail, bool) {
	if s.cache == nil {
		return db.ClientDetail{}, false
	}
	data, err := s.cache.Get(ctx, clientCacheNamespace, clientID)
	if err != nil {
		s.logger.Warn("client cache get failed", applog.String("client_id", clientID), applog.Error(err))
		return db.ClientDetail{}, false
	}
	if data == nil {
		return db.ClientDetail{}, false
	}
	var row db.ClientDetail
	if err := json.Unmarshal(data, &row); err != nil {
		s.logger.Warn("client cache decode failed", applog.String("client_id", clientID), applog.Error(err))
		return db.ClientDetail{}, false
	}
	s.logger.Debug("client cache hit", applog.String("client_id", clientID))
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
		s.logger.Warn("client cache encode failed", applog.String("client_id", clientID), applog.Error(err))
		return
	}
	if err := s.cache.Put(ctx, clientCacheNamespace, clientID, data, s.cacheTTLSecs); err != nil {
		s.logger.Warn("client cache put failed", applog.String("client_id", clientID), applog.Error(err))
	}
}

// invalidateCache clears the cached row for clientID after a successful
// write so the next GetClient re-reads Postgres. Failures are logged, not
// returned: the write already succeeded and must not be reported as failed.
func (s *Service) invalidateCache(ctx context.Context, clientID string) {
	if s.cache == nil {
		return
	}
	if err := s.cache.Delete(ctx, clientCacheNamespace, clientID); err != nil {
		s.logger.Warn("client cache invalidate failed", applog.String("client_id", clientID), applog.Error(err))
	}
}

func mergePatch(existing db.ClientDetail, req PatchClientRequest, fields PatchFields) (UpdateClientRequest, error) {
	clientName, langMap, err := parseClientName(existing.Name)
	if err != nil {
		return UpdateClientRequest{}, err
	}
	redirectURIs, err := unmarshalStringSlice(existing.RedirectUris)
	if err != nil {
		return UpdateClientRequest{}, err
	}
	claims, err := unmarshalStringSlice(existing.Claims)
	if err != nil {
		return UpdateClientRequest{}, err
	}
	acrValues, err := unmarshalStringSlice(existing.AcrValues)
	if err != nil {
		return UpdateClientRequest{}, err
	}
	grantTypes, err := unmarshalStringSlice(existing.GrantTypes)
	if err != nil {
		return UpdateClientRequest{}, err
	}
	authMethods, err := unmarshalStringSlice(existing.AuthMethods)
	if err != nil {
		return UpdateClientRequest{}, err
	}
	var additionalConfig json.RawMessage
	if existing.AdditionalConfig.Valid && existing.AdditionalConfig.String != "" {
		additionalConfig = json.RawMessage(existing.AdditionalConfig.String)
	}

	merged := UpdateClientRequest{
		ClientName:        clientName,
		ClientNameLangMap: langMap,
		Status:            existing.Status,
		LogoURI:           existing.LogoUri,
		RedirectURIs:      redirectURIs,
		Claims:            claims,
		AcrValues:         acrValues,
		GrantTypes:        grantTypes,
		AuthMethods:       authMethods,
		AdditionalConfig:  additionalConfig,
	}

	if fields.ClientName {
		merged.ClientName = req.ClientName
	}
	if fields.ClientNameLangMap {
		merged.ClientNameLangMap = req.ClientNameLangMap
	}
	if fields.Status {
		merged.Status = req.Status
	}
	if fields.LogoURI {
		merged.LogoURI = req.LogoURI
	}
	if fields.RedirectURIs {
		merged.RedirectURIs = req.RedirectURIs
	}
	if fields.Claims {
		merged.Claims = req.Claims
	}
	if fields.AcrValues {
		merged.AcrValues = req.AcrValues
	}
	if fields.GrantTypes {
		merged.GrantTypes = req.GrantTypes
	}
	if fields.AuthMethods {
		merged.AuthMethods = req.AuthMethods
	}
	if fields.AdditionalConfig {
		merged.AdditionalConfig = req.AdditionalConfig
	}
	return merged, nil
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

func encKeyColumns(encKey map[string]string, cert string) (sql.NullString, sql.NullString, sql.NullString, error) {
	if len(encKey) == 0 {
		return sql.NullString{}, sql.NullString{}, sql.NullString{}, nil
	}
	if err := validateJWK(encKey); err != nil {
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

func isDuplicateClientID(err error) bool {
	msg := err.Error()
	return strings.Contains(msg, "23505") &&
		(strings.Contains(msg, "pk_clntdtl_id") || strings.Contains(msg, "client_detail_pkey"))
}

func isDuplicatePublicKeyHash(err error) bool {
	return strings.Contains(err.Error(), "uk_clntdtl_public_key_hash")
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
		LogoURI:           row.LogoUri,
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
