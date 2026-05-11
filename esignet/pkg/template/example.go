package template

import (
	"context"

	http "github.com/aarock1234/fphttp"
)

type exampleRequest struct {
	Example string `json:"example"`
}

// ExampleResponse holds the parsed TLS fingerprint from tls.peet.ws.
type ExampleResponse struct {
	TLS TLSFingerprint `json:"tls"`
}

// TLSFingerprint contains the PeetPrint fingerprint and its hash.
type TLSFingerprint struct {
	PeetPrint     string `json:"peetprint"`
	PeetPrintHash string `json:"peetprint_hash"`
}

// Example demonstrates a POST request with JSON payload and typed response.
func (s *Service) Example(ctx context.Context) (*ExampleResponse, error) {
	payload := exampleRequest{
		Example: "example",
	}

	resp, err := doJSON[ExampleResponse](ctx, s, http.MethodPost, "/api/all", payload)
	if err != nil {
		return nil, err
	}

	return resp, nil
}
