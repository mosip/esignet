package clientmgmt

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestResponseWrapper_errorResponseIsNull(t *testing.T) {
	body, err := json.Marshal(ResponseWrapper{
		Errors:       []Error{{ErrorCode: "invalid_grant_type", ErrorMessage: "invalid_grant_type"}},
		ResponseTime: time.Date(2026, 6, 29, 16, 20, 10, 980_000_000, time.UTC).Format(mosipTimeLayout),
	})
	require.NoError(t, err)

	var decoded map[string]any
	require.NoError(t, json.Unmarshal(body, &decoded))
	assert.Nil(t, decoded["response"])
	assert.Equal(t, "2026-06-29T16:20:10.980Z", decoded["responseTime"])
}

func TestResponseWrapper_successResponse(t *testing.T) {
	resp := ClientResponse{ClientID: "c1", Status: "ACTIVE"}
	body, err := json.Marshal(ResponseWrapper{
		Response:     resp.APIResponse(),
		ResponseTime: "2026-06-29T16:20:10.980Z",
	})
	require.NoError(t, err)

	var decoded map[string]any
	require.NoError(t, json.Unmarshal(body, &decoded))
	response, ok := decoded["response"].(map[string]any)
	require.True(t, ok)
	assert.Equal(t, "c1", response["clientId"])
	assert.Equal(t, "ACTIVE", response["status"])
}
