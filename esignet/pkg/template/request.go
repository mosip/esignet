package template

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/url"
	"strings"

	http "github.com/aarock1234/fphttp"
)

const baseURL = "https://tls.peet.ws"

var defaultHeaders = http.Header{
	"Accept":          {"*/*"},
	"Accept-Language": {"en-US,en;q=0.9"},
	"Accept-Encoding": {"gzip, deflate, br"},
}

// do executes an HTTP request and returns the response. The caller is
// responsible for closing the response body.
func (s *Service) do(ctx context.Context, method, path string, body io.Reader, contentType string, headers http.Header) (*http.Response, error) {
	fullURL := baseURL + path
	if _, err := url.Parse(fullURL); err != nil {
		return nil, fmt.Errorf("invalid url %q: %w", fullURL, err)
	}

	req, err := http.NewRequestWithContext(ctx, method, fullURL, body)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}
	req.Header = defaultHeaders.Clone()

	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}

	for k, v := range headers {
		req.Header.Set(k, strings.Join(v, ", "))
	}

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("executing request: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		_ = resp.Body.Close()

		return nil, fmt.Errorf("unexpected status: %d", resp.StatusCode)
	}

	return resp, nil
}

// doJSON sends an HTTP request with an optional JSON payload and decodes
// the JSON response into T. doJSON is a top-level function because Go
// does not support generic methods.
func doJSON[T any](ctx context.Context, s *Service, method, path string, payload any) (*T, error) {
	body, contentType, err := marshalPayload(payload)
	if err != nil {
		return nil, err
	}

	resp, err := s.do(ctx, method, path, body, contentType, nil)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()

	var result T
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("decoding response: %w", err)
	}

	return &result, nil
}

// marshalPayload encodes a payload as JSON and returns a reader and
// content type. A nil payload produces a nil reader and empty content type.
func marshalPayload(payload any) (io.Reader, string, error) {
	if payload == nil {
		return nil, "", nil
	}

	data, err := json.Marshal(payload)
	if err != nil {
		return nil, "", fmt.Errorf("marshaling request: %w", err)
	}

	return bytes.NewReader(data), "application/json", nil
}
