// Package clientmgmt implements OIDC client registration management.
package clientmgmt

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/clientmgmt/db"
)

// ErrClientNotFound is returned when a client ID does not exist.
var ErrClientNotFound = errors.New("client not found")

// ErrDuplicatePublicKey is returned when the public key hash already exists.
var ErrDuplicatePublicKey = errors.New("public key already registered")

// CreateClientRequest is the payload for registering a new OIDC client.
type CreateClientRequest struct {
	ClientID         string            `json:"client_id"`
	ClientName       string            `json:"client_name"`
	RpID             string            `json:"rp_id"`
	LogoURI          string            `json:"logo_uri"`
	RedirectURIs     []string          `json:"redirect_uris"`
	Claims           []string          `json:"claims"`
	AcrValues        []string          `json:"acr_values"`
	PublicKey        string            `json:"public_key"`
	EncPublicKey     string            `json:"enc_public_key,omitempty"`
	EncPublicKeyCert string            `json:"enc_public_key_cert,omitempty"`
	GrantTypes       []string          `json:"grant_types"`
	AuthMethods      []string          `json:"client_auth_methods"`
	AdditionalConfig map[string]string `json:"additional_config,omitempty"`
}

// UpdateClientRequest is the payload for updating an existing OIDC client.
type UpdateClientRequest struct {
	ClientName       string            `json:"client_name"`
	LogoURI          string            `json:"logo_uri"`
	RedirectURIs     []string          `json:"redirect_uris"`
	Claims           []string          `json:"claims"`
	AcrValues        []string          `json:"acr_values"`
	GrantTypes       []string          `json:"grant_types"`
	AuthMethods      []string          `json:"client_auth_methods"`
	Status           string            `json:"status"`
	AdditionalConfig map[string]string `json:"additional_config,omitempty"`
}

// ClientResponse is returned from create/update/get operations.
type ClientResponse struct {
	ClientID         string            `json:"client_id"`
	ClientName       string            `json:"client_name"`
	RpID             string            `json:"rp_id"`
	LogoURI          string            `json:"logo_uri"`
	RedirectURIs     []string          `json:"redirect_uris"`
	Claims           []string          `json:"claims"`
	AcrValues        []string          `json:"acr_values"`
	GrantTypes       []string          `json:"grant_types"`
	AuthMethods      []string          `json:"client_auth_methods"`
	Status           string            `json:"status"`
	AdditionalConfig map[string]string `json:"additional_config,omitempty"`
	PublicKey        string            `json:"public_key"`
	EncPublicKey     string            `json:"enc_public_key,omitempty"`
	CreatedAt        time.Time         `json:"created_at"`
	UpdatedAt        *time.Time        `json:"updated_at,omitempty"`
}

// Service handles client management business logic.
type Service struct {
	q db.Querier
}

// NewService creates a Service backed by the given database connection.
func NewService(conn *sql.DB) *Service {
	return &Service{q: db.New(conn)}
}

// NewServiceWithQuerier creates a Service with an explicit Querier; use in tests
// to inject a mock without a real database connection.
func NewServiceWithQuerier(q db.Querier) *Service {
	return &Service{q: q}
}

// CreateClient registers a new OIDC client.
func (s *Service) CreateClient(ctx context.Context, req CreateClientRequest) (ClientResponse, error) {
	if err := validateCreate(req); err != nil {
		return ClientResponse{}, err
	}

	pkHash := hashKey(req.PublicKey)

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
	additionalConfig, err := marshalAdditionalConfig(req.AdditionalConfig)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("additional_config: %w", err)
	}

	params := db.CreateClientParams{
		ID:               req.ClientID,
		Name:             req.ClientName,
		RpID:             req.RpID,
		LogoURI:          req.LogoURI,
		RedirectUris:     redirectURIs,
		Claims:           claims,
		AcrValues:        acrValues,
		PublicKey:        req.PublicKey,
		PublicKeyHash:    pkHash,
		EncPublicKey:     nullableString(req.EncPublicKey),
		EncPublicKeyHash: nullableString(hashKeyIfNonEmpty(req.EncPublicKey)),
		EncPublicKeyCert: nullableString(req.EncPublicKeyCert),
		GrantTypes:       grantTypes,
		AuthMethods:      authMethods,
		Status:           "ACTIVE",
		AdditionalConfig: additionalConfig,
		CrDtimes:         time.Now().UTC(),
	}

	row, err := s.q.CreateClient(ctx, params)
	if err != nil {
		if isUniqueViolation(err) {
			return ClientResponse{}, ErrDuplicatePublicKey
		}
		return ClientResponse{}, fmt.Errorf("create client: %w", err)
	}
	return toResponse(row)
}

// UpdateClient updates an existing OIDC client.
func (s *Service) UpdateClient(ctx context.Context, clientID string, req UpdateClientRequest) (ClientResponse, error) {
	if err := validateUpdate(req); err != nil {
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
	additionalConfig, err := marshalAdditionalConfig(req.AdditionalConfig)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("additional_config: %w", err)
	}

	now := time.Now().UTC()
	params := db.UpdateClientParams{
		ID:               clientID,
		Name:             req.ClientName,
		LogoURI:          req.LogoURI,
		RedirectUris:     redirectURIs,
		Claims:           claims,
		AcrValues:        acrValues,
		GrantTypes:       grantTypes,
		AuthMethods:      authMethods,
		Status:           req.Status,
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
	return toResponse(row)
}

// GetClient retrieves a client by ID.
func (s *Service) GetClient(ctx context.Context, clientID string) (ClientResponse, error) {
	row, err := s.q.GetClient(ctx, clientID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ClientResponse{}, ErrClientNotFound
		}
		return ClientResponse{}, fmt.Errorf("get client: %w", err)
	}
	return toResponse(row)
}

// --- helpers ---

func validateCreate(req CreateClientRequest) error {
	var missing []string
	if req.ClientID == "" {
		missing = append(missing, "client_id")
	}
	if req.ClientName == "" {
		missing = append(missing, "client_name")
	}
	if req.RpID == "" {
		missing = append(missing, "rp_id")
	}
	if req.LogoURI == "" {
		missing = append(missing, "logo_uri")
	}
	if len(req.RedirectURIs) == 0 {
		missing = append(missing, "redirect_uris")
	}
	if req.PublicKey == "" {
		missing = append(missing, "public_key")
	}
	if len(req.GrantTypes) == 0 {
		missing = append(missing, "grant_types")
	}
	if len(req.AuthMethods) == 0 {
		missing = append(missing, "client_auth_methods")
	}
	if len(missing) > 0 {
		return fmt.Errorf("missing required fields: %s", strings.Join(missing, ", "))
	}
	return nil
}

func validateUpdate(req UpdateClientRequest) error {
	var missing []string
	if req.ClientName == "" {
		missing = append(missing, "client_name")
	}
	if req.Status == "" {
		missing = append(missing, "status")
	}
	if len(missing) > 0 {
		return fmt.Errorf("missing required fields: %s", strings.Join(missing, ", "))
	}
	return nil
}

func hashKey(key string) string {
	sum := sha256.Sum256([]byte(key))
	return fmt.Sprintf("%x", sum)
}

func hashKeyIfNonEmpty(key string) string {
	if key == "" {
		return ""
	}
	return hashKey(key)
}

func nullableString(s string) sql.NullString {
	return sql.NullString{String: s, Valid: s != ""}
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

func marshalAdditionalConfig(m map[string]string) (sql.NullString, error) {
	if len(m) == 0 {
		return sql.NullString{}, nil
	}
	b, err := json.Marshal(m)
	if err != nil {
		return sql.NullString{}, err
	}
	return sql.NullString{String: string(b), Valid: true}, nil
}

func unmarshalStringSlice(s string) ([]string, error) {
	var out []string
	if err := json.Unmarshal([]byte(s), &out); err != nil {
		return nil, fmt.Errorf("unmarshal string slice: %w", err)
	}
	return out, nil
}

func unmarshalAdditionalConfig(s sql.NullString) (map[string]string, error) {
	if !s.Valid || s.String == "" {
		return nil, nil
	}
	var out map[string]string
	if err := json.Unmarshal([]byte(s.String), &out); err != nil {
		return nil, fmt.Errorf("unmarshal additional config: %w", err)
	}
	return out, nil
}

func isUniqueViolation(err error) bool {
	// lib/pq error code 23505
	return strings.Contains(err.Error(), "23505") ||
		strings.Contains(err.Error(), "unique_violation") ||
		strings.Contains(err.Error(), "uk_clntdtl_public_key_hash")
}

func toResponse(row db.ClientDetail) (ClientResponse, error) {
	redirectURIs, err := unmarshalStringSlice(row.RedirectUris)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client %s: %w", row.ID, err)
	}
	claims, err := unmarshalStringSlice(row.Claims)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client %s: %w", row.ID, err)
	}
	acrValues, err := unmarshalStringSlice(row.AcrValues)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client %s: %w", row.ID, err)
	}
	grantTypes, err := unmarshalStringSlice(row.GrantTypes)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client %s: %w", row.ID, err)
	}
	authMethods, err := unmarshalStringSlice(row.AuthMethods)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client %s: %w", row.ID, err)
	}
	additionalConfig, err := unmarshalAdditionalConfig(row.AdditionalConfig)
	if err != nil {
		return ClientResponse{}, fmt.Errorf("client %s: %w", row.ID, err)
	}
	publicKey := row.PublicKey

	resp := ClientResponse{
		ClientID:         row.ID,
		ClientName:       row.Name,
		RpID:             row.RpID,
		LogoURI:          row.LogoURI,
		RedirectURIs:     redirectURIs,
		Claims:           claims,
		AcrValues:        acrValues,
		GrantTypes:       grantTypes,
		AuthMethods:      authMethods,
		Status:           row.Status,
		AdditionalConfig: additionalConfig,
		CreatedAt:        row.CrDtimes,
		PublicKey:        publicKey,
	}
	if row.EncPublicKey.Valid {
		resp.EncPublicKey = row.EncPublicKey.String
	}
	if row.UpdDtimes.Valid {
		t := row.UpdDtimes.Time
		resp.UpdatedAt = &t
	}
	return resp, nil
}
