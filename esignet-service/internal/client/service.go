package client

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/pkg/jwk"
)

// ClientRepo is the persistence contract the service consumes. Defined here
// so the repository package stays consumer-agnostic.
type ClientRepo interface {
	ExistsByID(ctx context.Context, id string) (bool, error)
	Insert(ctx context.Context, row *ClientDetailRow) error
}

// Service runs the post-validation pipeline: JWK parse → duplicate check →
// row build → persist. Schema validation runs upstream in the handler.
type Service struct {
	repo ClientRepo
	log  *applog.Logger
}

// NewService binds the service to its dependencies.
func NewService(repo ClientRepo, log *applog.Logger) *Service {
	return &Service{repo: repo, log: log}
}

// Create runs the create pipeline. Returns a populated createResponse on
// success, or a non-empty list of wire error codes on failure.
func (s *Service) Create(ctx context.Context, req *createRequest) (*createResponse, []string) {
	// 1. publicKey — parse the JWK and compute the row's public_key_hash.
	pubKey, err := jwk.Parse(req.PublicKey)
	if err != nil {
		return nil, []string{errInvalidPublicKey}
	}
	pubKeyHash, err := jwk.ComputeHash(pubKey)
	if err != nil {
		return nil, []string{errInvalidPublicKey}
	}

	// 2. Up-front duplicate check. The DB PK also enforces it; we fail
	//    here so the wire response carries the right code without an INSERT.
	exists, err := s.repo.ExistsByID(ctx, req.ClientID)
	if err != nil {
		s.log.Error("client_detail exists check failed",
			applog.String("client_id", req.ClientID),
			applog.Error(err),
		)
		return nil, []string{errUnknownError}
	}
	if exists {
		return nil, []string{errDuplicateClientID}
	}

	// 3. Build the row. Lists serialise as JSON arrays; name is a JSON map
	//    when clientNameLangMap is set, else plain string; additionalConfig
	//    is compacted JSON or nil when absent/null.
	row, errCode := buildClientDetailRow(req, string(req.PublicKey), pubKeyHash)
	if errCode != "" {
		return nil, []string{errCode}
	}

	// 4. INSERT. Map duplicate sentinels to their wire codes; log and report
	//    unknown_error for anything else so the client isn't told
	//    their input was invalid when the fault was server-side.
	if err := s.repo.Insert(ctx, row); err != nil {
		switch {
		case errors.Is(err, ErrDuplicateClientID):
			return nil, []string{errDuplicateClientID}
		case errors.Is(err, ErrDuplicatePublicKey):
			return nil, []string{errDuplicatePublicKey}
		default:
			s.log.Error("client_detail insert failed",
				applog.String("client_id", req.ClientID),
				applog.Error(err),
			)
			return nil, []string{errUnknownError}
		}
	}

	return &createResponse{ClientID: req.ClientID, Status: clientStatusActive}, nil
}

func buildClientDetailRow(req *createRequest, publicKeyJSON, publicKeyHash string) (*ClientDetailRow, string) {
	name, err := marshalNameField(req)
	if err != nil {
		return nil, errInvalidClientName
	}

	redirectURIs, err := marshalJSONArray(req.RedirectURIs)
	if err != nil {
		return nil, errInvalidRedirectURI
	}
	claims, err := marshalJSONArray(req.UserClaims)
	if err != nil {
		return nil, errInvalidClaim
	}
	acr, err := marshalJSONArray(req.AuthContextRefs)
	if err != nil {
		return nil, errInvalidACR
	}
	grants, err := marshalJSONArray(req.GrantTypes)
	if err != nil {
		return nil, errUnsupportedGrantType
	}
	auths, err := marshalJSONArray(req.ClientAuthMethods)
	if err != nil {
		return nil, errInvalidClientAuth
	}

	var addCfg *string
	if len(req.AdditionalConfig) > 0 && string(req.AdditionalConfig) != "null" {
		compact, err := compactJSON(req.AdditionalConfig)
		if err != nil {
			return nil, errInvalidAdditionalConfig
		}
		addCfg = &compact
	}

	return &ClientDetailRow{
		ID:               req.ClientID,
		Name:             name,
		RpID:             req.RelyingPartyID,
		LogoURI:          req.LogoURI,
		RedirectURIs:     redirectURIs,
		Claims:           claims,
		ACRValues:        acr,
		PublicKey:        publicKeyJSON,
		PublicKeyHash:    publicKeyHash,
		GrantTypes:       grants,
		AuthMethods:      auths,
		Status:           clientStatusActive,
		AdditionalConfig: addCfg,
		CrDtimes:         time.Now().UTC(),
	}, ""
}

func marshalNameField(req *createRequest) (string, error) {
	if len(req.ClientNameLangMap) == 0 {
		return req.ClientName, nil
	}
	buf, err := json.Marshal(req.ClientNameLangMap)
	if err != nil {
		return "", fmt.Errorf("marshal clientNameLangMap: %w", err)
	}
	return string(buf), nil
}

func marshalJSONArray(items []string) (string, error) {
	if items == nil {
		items = []string{}
	}
	buf, err := json.Marshal(items)
	if err != nil {
		return "", fmt.Errorf("marshal json array: %w", err)
	}
	return string(buf), nil
}

func compactJSON(raw json.RawMessage) (string, error) {
	var buf bytes.Buffer
	if err := json.Compact(&buf, raw); err != nil {
		return "", fmt.Errorf("compact json: %w", err)
	}
	return buf.String(), nil
}
