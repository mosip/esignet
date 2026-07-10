package mosip

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	applog "github.com/mosip/esignet/internal/log"
)

const (
	applicationName = "eSignet"
	createdBy       = "esignet-service"
	defaultHost     = "localhost"
	noUser          = "no-user"
	// publishTimeout bounds each fire-and-forget audit POST.
	publishTimeout = 15 * time.Second
)

// descriptionDataKeys are the event data fields serialized into the audit
// record description.
var descriptionDataKeys = []string{
	"client_id", "flow_type", "app_id", "error",
	"duration_ms", "redirect_to", "failed_step", "node_id",
}

// auditor maps ThunderID flow lifecycle events to MOSIP audit records and
// posts them to mosip-audit-manager.
type auditor struct {
	client   *Client
	hostName string
	log      *applog.Logger
}

// NewAuditor builds an audit-manager observability provider. It fails if no
// audit manager endpoint is configured.
func NewAuditor(client *http.Client) (providers.ObservabilityProvider, error) {
	auditCfg, err := LoadAuditConfig()
	if err != nil {
		return nil, err
	}

	hostName, err := os.Hostname()
	if err != nil || hostName == "" {
		hostName = defaultHost
	}

	return &auditor{
		client:   NewClient(auditCfg, client),
		hostName: hostName,
		log:      applog.GetLogger(),
	}, nil
}

// IsEnabled reports whether audit publishing is active.
func (a *auditor) IsEnabled() bool {
	return true
}

// PublishEvent maps a flow lifecycle event to an audit record and posts it
// asynchronously. Errors never propagate to the caller.
func (a *auditor) PublishEvent(_ context.Context, evt *providers.Event) {
	if evt == nil {
		return
	}

	req := a.toAuditRequest(evt)

	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), publishTimeout)
		defer cancel()
		if err := a.client.Post(ctx, req); err != nil {
			a.log.Error("audit: failed to publish event",
				applog.String("event_type", evt.Type),
				applog.String("trace_id", evt.TraceID),
				applog.Error(err))
		}
	}()
}

func (a *auditor) toAuditRequest(evt *providers.Event) AuditRequest {
	user := firstNonEmpty(dataString(evt.Data, "user_id"), dataString(evt.Data, "username"))
	if user == "" {
		user = noUser
	}

	return AuditRequest{
		EventID:         evt.Type,
		EventName:       evt.Type,
		EventType:       auditEventType(evt.Status),
		ActionTimeStamp: evt.Timestamp.UTC().Format(utcTimeFormat),
		HostName:        a.hostName,
		HostIP:          a.hostName,
		ApplicationID:   applicationName,
		ApplicationName: applicationName,
		SessionUserID:   user,
		SessionUserName: user,
		ID:              dataString(evt.Data, "execution_id"),
		IDType:          "",
		CreatedBy:       createdBy,
		ModuleName:      evt.Component,
		ModuleID:        evt.Component,
		Description:     auditDescription(evt.Data),
	}
}

// auditEventType maps the engine status to the MOSIP audit event type
// (SUCCESS/ERROR).
func auditEventType(status string) string {
	switch status {
	case providers.StatusSuccess:
		return "SUCCESS"
	case providers.StatusFailure:
		return "ERROR"
	default:
		return strings.ToUpper(status)
	}
}

// auditDescription serializes the relevant event data fields to a JSON string.
func auditDescription(data map[string]interface{}) string {
	if len(data) == 0 {
		return "{}"
	}
	desc := make(map[string]interface{})
	for _, key := range descriptionDataKeys {
		if v, ok := data[key]; ok {
			desc[key] = v
		}
	}
	b, err := json.Marshal(desc)
	if err != nil {
		return fmt.Sprintf("%v", desc)
	}
	return string(b)
}

func dataString(data map[string]interface{}, key string) string {
	if data == nil {
		return ""
	}
	if v, ok := data[key]; ok {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if v != "" {
			return v
		}
	}
	return ""
}

// Client posts audit records to mosip-audit-manager.
type Client struct {
	cfg    AuditConfig
	client *http.Client
	token  *tokenProvider
	log    *applog.Logger
}

// NewClient builds an audit manager client from the given config and HTTP client.
func NewClient(cfg AuditConfig, client *http.Client) *Client {
	return &Client{
		cfg:    cfg,
		client: client,
		token:  newTokenProvider(cfg, client),
		log:    applog.GetLogger(),
	}
}

// Post sends a single audit record. It attaches the authmanager token as a
// Cookie (Authorization=<token>) and purges the cached token on 401/403
// before retrying once.
func (c *Client) Post(ctx context.Context, audit AuditRequest) error {
	body, err := json.Marshal(AuditRequestWrapper[AuditRequest]{
		ID:          "ida",
		RequestTime: GetUTCDateTime(),
		Request:     audit,
	})
	if err != nil {
		return fmt.Errorf("marshal audit request: %w", err)
	}

	status, err := c.send(ctx, body)
	if err != nil {
		return err
	}

	// A stale token yields 401/403; purge and retry once with a fresh token.
	if status == http.StatusUnauthorized || status == http.StatusForbidden {
		c.log.Warn("audit: auth rejected by audit manager, refreshing token",
			applog.Int("status", status))
		c.token.Purge()
		if status, err = c.send(ctx, body); err != nil {
			return err
		}
	}

	if status < 200 || status >= 300 {
		c.log.Error("audit: audit manager returned non-2xx status",
			applog.Int("status", status))
	}
	return nil
}

// send performs one POST and returns the HTTP status code. Response errors are
// logged, not returned, so a failed audit post never fails the caller.
func (c *Client) send(ctx context.Context, body []byte) (int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.cfg.AuditManagerURL, bytes.NewReader(body))
	if err != nil {
		return 0, fmt.Errorf("create audit request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	token, tErr := c.token.GetAuthToken(ctx)
	if tErr != nil {
		return 0, fmt.Errorf("acquire audit auth token: %w", tErr)
	}
	req.Header.Set("Cookie", "Authorization="+token)

	httpResp, err := c.client.Do(req)
	if err != nil {
		return 0, fmt.Errorf("audit request failed: %w", err)
	}
	defer func() { _ = httpResp.Body.Close() }()

	bodyBytes, _ := io.ReadAll(httpResp.Body)

	if httpResp.StatusCode >= 200 && httpResp.StatusCode < 300 {
		var wrapper AuditResponseWrapper
		if json.Unmarshal(bodyBytes, &wrapper) == nil && len(wrapper.Errors) > 0 {
			c.log.Error("audit: audit manager returned errors",
				applog.Any("errors", wrapper.Errors))
		}
	}
	return httpResp.StatusCode, nil
}
