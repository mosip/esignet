// Package template is a skeleton service that demonstrates how to wire an HTTP
// client and database together. Replace it with your own domain logic.
package template

import (
	"fmt"

	"github.com/mosip/esignet/pkg/client"
	"github.com/mosip/esignet/pkg/db"
)

// Service orchestrates HTTP requests and database access for a single domain.
type Service struct {
	db     *db.DB
	client *client.Client
}

// New creates a Service with an HTTP client configured for the given proxy.
func New(db *db.DB, proxy *client.Proxy) (*Service, error) {
	var opts []client.Option
	if proxy != nil {
		opts = append(opts, client.WithProxy(proxy))
	}

	c, err := client.New(opts...)
	if err != nil {
		return nil, fmt.Errorf("create client: %w", err)
	}

	return &Service{
		db:     db,
		client: c,
	}, nil
}
