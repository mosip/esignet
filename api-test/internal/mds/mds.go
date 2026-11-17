// Package mds captures biometrics from the mock MDS (MOSIP SBI device service) the way the login UI's SBI widget does, so a scripted login can submit a real, IDA-verifiable capture.
package mds

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/mosip/esignet/api-test/internal/esignet"
	"github.com/mosip/esignet/api-test/internal/result"
	"github.com/mosip/esignet/api-test/internal/textx"
)

// AuthTransactionIDLength is the IDA transaction id length the login UI hardcodes when it derives one for the SBI widget (oidc-ui SbiComponent); the server derives the same one from the execution id, and IDA refuses a capture whose transaction id differs.
const AuthTransactionIDLength = 10

// DefaultProfile is the mock MDS profile it starts on and is returned to after a capture from any other.
const DefaultProfile = "Default"

// Client talks to one mock MDS over the SBI loopback API.
type Client struct {
	BaseURL string // e.g. http://127.0.0.1:4501
	// Env and DomainURI are signed into every capture and must match what the authn provider sends IDA (its env and domain URI config), or IDA refuses the capture.
	Env       string
	DomainURI string
	HTTP      *http.Client

	mu      sync.Mutex
	devices map[string]device // SBI device type -> discovered device
}

// New returns a client for the MDS at baseURL.
func New(baseURL, env, domainURI string) *Client {
	return &Client{
		BaseURL:   strings.TrimRight(baseURL, "/"),
		Env:       env,
		DomainURI: domainURI,
		// A capture waits out the MDS's own capture loop, so allow well past the capture timeout sent with it.
		HTTP: &http.Client{Timeout: 60 * time.Second},
	}
}

// captureTimeoutMs is the SBI capture timeout sent to the MDS, in milliseconds.
const captureTimeoutMs = 10000

type device struct {
	ID          string
	SpecVersion string
}

// DeriveTransactionID derives the IDA transaction id from a flow execution id exactly as the login UI and esignet-service do: hyphens and underscores dropped, then characters read from the end, cyclically, until n are collected.
func DeriveTransactionID(executionID string, n int) (string, error) {
	cleaned := strings.NewReplacer("_", "", "-", "").Replace(executionID)
	if cleaned == "" {
		return "", fmt.Errorf("no execution id to derive a transaction id from")
	}
	out := make([]byte, n)
	i := len(cleaned) - 1
	for j := range n {
		out[j] = cleaned[i]
		i--
		if i < 0 {
			i = len(cleaned) - 1
		}
	}
	return string(out), nil
}

// Capture takes one capture for executionID and returns it encoded as the UI submits it: base64 of the JSON array of the MDS's biometrics records, each with its error member removed.
func (c *Client) Capture(ctx context.Context, executionID string, spec esignet.CaptureSpec) (_ string, calls []result.HTTPCall, err error) {
	txn, err := DeriveTransactionID(executionID, AuthTransactionIDLength)
	if err != nil {
		return "", calls, err
	}
	modality := esignet.CaptureModality(spec)
	dev, err := c.device(ctx, &calls, modality)
	if err != nil {
		return "", calls, err
	}

	if spec.Profile != "" && !strings.EqualFold(spec.Profile, DefaultProfile) {
		if err := c.setProfile(ctx, &calls, spec.Profile); err != nil {
			return "", calls, err
		}
		// Back to Default whatever happens, so one scenario's profile never leaks into the next scenario's capture. A failed reset fails the capture: the next one would silently use the wrong profile.
		defer func() {
			if rerr := c.setProfile(context.WithoutCancel(ctx), &calls, DefaultProfile); rerr != nil && err == nil {
				err = fmt.Errorf("reset to %s profile: %w", DefaultProfile, rerr)
			}
		}()
	}

	bio := map[string]any{
		"type":           modality,
		"count":          "1",
		"requestedScore": "80",
		"deviceId":       dev.ID,
		"deviceSubId":    "0",
		// The SBI widget chains each capture to the previous one's hash; a first capture chains to the hash of "".
		"previousHash": emptyHash,
	}
	// Face takes no sub type; Finger and Iris ask for UNKNOWN, as the widget does.
	if modality != "Face" {
		bio["bioSubType"] = []string{"UNKNOWN"}
	}
	req := map[string]any{
		"env":           c.Env,
		"purpose":       "Auth",
		"specVersion":   dev.SpecVersion,
		"timeout":       fmt.Sprint(captureTimeoutMs),
		"captureTime":   time.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"domainUri":     c.DomainURI,
		"transactionId": txn,
		"bio":           []any{bio},
		"customOpts":    nil,
	}
	body, err := c.call(ctx, &calls, "mock MDS capture ("+modality+")", "CAPTURE", "/capture", req)
	if err != nil {
		return "", calls, err
	}
	var resp struct {
		Biometrics []map[string]json.RawMessage `json:"biometrics"`
	}
	if err := json.Unmarshal(body, &resp); err != nil {
		return "", calls, fmt.Errorf("capture response: %w", err)
	}
	if len(resp.Biometrics) == 0 {
		return "", calls, fmt.Errorf("capture returned no biometrics: %s", snippet(body))
	}
	for _, rec := range resp.Biometrics {
		var e struct {
			ErrorCode string `json:"errorCode"`
			ErrorInfo string `json:"errorInfo"`
		}
		if raw, ok := rec["error"]; ok && json.Unmarshal(raw, &e) == nil && e.ErrorCode != "" && e.ErrorCode != "0" {
			return "", calls, fmt.Errorf("capture failed: MDS error %s (%s)", e.ErrorCode, e.ErrorInfo)
		}
		delete(rec, "error")
	}
	if spec.Tamper {
		if err := tamper(resp.Biometrics); err != nil {
			return "", calls, err
		}
	}
	records, err := json.Marshal(resp.Biometrics)
	if err != nil {
		return "", calls, err
	}
	return base64.StdEncoding.EncodeToString(records), calls, nil
}

var emptyHash = func() string { h := sha256.Sum256(nil); return hex.EncodeToString(h[:]) }()

// tamper alters one character in the payload of the first record's signed data, leaving a well-formed capture whose signature no longer verifies.
func tamper(records []map[string]json.RawMessage) error {
	var data string
	if err := json.Unmarshal(records[0]["data"], &data); err != nil {
		return fmt.Errorf("tamper: record has no string data: %w", err)
	}
	parts := strings.Split(data, ".")
	if len(parts) != 3 || len(parts[1]) < 2 {
		return fmt.Errorf("tamper: record data is not a JWS")
	}
	p := []byte(parts[1])
	mid := len(p) / 2
	if p[mid] == 'A' {
		p[mid] = 'B'
	} else {
		p[mid] = 'A'
	}
	parts[1] = string(p)
	raw, err := json.Marshal(strings.Join(parts, "."))
	if err != nil {
		return err
	}
	records[0]["data"] = raw
	return nil
}

// device discovers, once per run, the device the MDS offers for an SBI device type.
func (c *Client) device(ctx context.Context, calls *[]result.HTTPCall, modality string) (device, error) {
	c.mu.Lock()
	dev, ok := c.devices[modality]
	c.mu.Unlock()
	if ok {
		return dev, nil
	}
	body, err := c.call(ctx, calls, "mock MDS discover ("+modality+")", "MOSIPDISC", "/device", map[string]string{"type": modality})
	if err != nil {
		return device{}, err
	}
	var found []struct {
		DeviceID    string   `json:"deviceId"`
		SpecVersion []string `json:"specVersion"`
	}
	if err := json.Unmarshal(body, &found); err != nil {
		return device{}, fmt.Errorf("discover %s: %w", modality, err)
	}
	for _, f := range found {
		if f.DeviceID != "" && len(f.SpecVersion) > 0 {
			dev = device{ID: f.DeviceID, SpecVersion: f.SpecVersion[0]}
			c.mu.Lock()
			if c.devices == nil {
				c.devices = map[string]device{}
			}
			c.devices[modality] = dev
			c.mu.Unlock()
			return dev, nil
		}
	}
	return device{}, fmt.Errorf("mock MDS offers no %s device (check MOCK_MDS_BIOMETRIC_TYPE): %s", modality, snippet(body))
}

// setProfile switches the biometrics the MDS returns, for every device, to one of its Profile/ folders.
func (c *Client) setProfile(ctx context.Context, calls *[]result.HTTPCall, profile string) error {
	_, err := c.call(ctx, calls, "mock MDS profile "+profile, http.MethodPost, "/admin/profile",
		map[string]string{"type": "Biometric Device", "profileId": profile})
	return err
}

// call sends one SBI request and records it; SBI verbs are custom HTTP methods (MOSIPDISC, CAPTURE).
func (c *Client) call(ctx context.Context, calls *[]result.HTTPCall, label, method, path string, payload any) ([]byte, error) {
	if c.BaseURL == "" {
		return nil, fmt.Errorf("mock MDS base URL is not set")
	}
	u, err := url.JoinPath(c.BaseURL, path)
	if err != nil {
		return nil, err
	}
	reqBody, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, method, u, bytes.NewReader(reqBody))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	rec := result.HTTPCall{At: time.Now().UnixNano(), Label: label, Method: method, URL: u, ReqBody: string(reqBody),
		ReqHeaders: map[string][]string{"Content-Type": {"application/json"}}}
	resp, err := c.HTTP.Do(req)
	if err != nil {
		rec.RespBody = "ERROR: " + err.Error()
		*calls = append(*calls, rec)
		return nil, fmt.Errorf("%s: %w", label, err)
	}
	defer func() { _ = resp.Body.Close() }()
	body, _ := io.ReadAll(resp.Body)
	rec.Status = resp.StatusCode
	rec.RespBody = string(body)
	*calls = append(*calls, rec)
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("%s: HTTP %d: %s", label, resp.StatusCode, snippet(body))
	}
	return body, nil
}

func snippet(b []byte) string { return textx.Truncate(strings.TrimSpace(string(b)), 300, "…") }
