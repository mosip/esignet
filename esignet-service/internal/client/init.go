// Package client implements the client-management feature module
// (POST /v1/esignet/client-mgmt/client).
package client

import (
	"context"
	"fmt"
	"net/http"

	"github.com/jackc/pgx/v5/pgxpool"

	applog "github.com/mosip/esignet/internal/log"
)

// Module bundles the constructed validator + service, ready to mount.
type Module struct {
	service   *Service
	validator *Validator
	log       *applog.Logger
}

// NewModule wires the validator and service against a repository built
// from the supplied pool. Fails fast on validator-load errors.
func NewModule(
	ctx context.Context,
	cfg Config,
	pool *pgxpool.Pool,
	log *applog.Logger,
) (*Module, error) {
	validatorCfg := ValidatorConfig{
		SupportedUserClaims:        cfg.SupportedUserClaims,
		SupportedACRValues:         cfg.SupportedACRValues,
		SupportedGrantTypes:        cfg.SupportedGrantTypes,
		SupportedClientAuthMethods: cfg.SupportedClientAuthMethods,
		SupportedIDRegex:           cfg.SupportedIDRegex,
	}

	val, err := buildValidatorForConfig(ctx, validatorCfg, cfg.AdditionalConfigSchemaURL, log)
	if err != nil {
		return nil, fmt.Errorf("client module: %w", err)
	}
	repo := NewRepository(pool)
	return &Module{
		service:   NewService(repo, log),
		validator: val,
		log:       log,
	}, nil
}

// Initialize registers the client-management routes on the supplied mux.
func (m *Module) Initialize(mux *http.ServeMux) {
	create := MgmtCreate(m.service, m.validator, m.log)
	mux.HandleFunc("POST /v1/esignet/client-mgmt/client", create)
}

// buildValidatorForConfig picks the embedded additionalConfig schema or
// the override URL.
func buildValidatorForConfig(
	ctx context.Context,
	vcfg ValidatorConfig,
	additionalConfigSchemaURL string,
	log *applog.Logger,
) (*Validator, error) {
	if additionalConfigSchemaURL == "" {
		val, err := NewValidator(vcfg)
		if err != nil {
			return nil, err
		}
		log.Info("additionalConfig schema loaded (embedded)")
		return val, nil
	}
	val, err := NewValidatorWithSchema(ctx, vcfg, additionalConfigSchemaURL)
	if err != nil {
		return nil, err
	}
	log.Info("additionalConfig schema loaded (override)",
		applog.String("url", additionalConfigSchemaURL))
	return val, nil
}
