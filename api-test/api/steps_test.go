package api

import (
	"strings"
	"testing"
)

// snippet feeds every step error, and the After hook archives those in the
// envelope's FailedConditions — which the report renders without running its own
// redactor over them. So a credential in a response body must be masked here or
// it reaches the retained HTML report in the clear.
func TestSnippetRedactsCredentials(t *testing.T) {
	body := `{"access_token":"eyJhbGciOi.PAYLOAD.SIG","id_token":"eyJraWQ.ID.SIG",` +
		`"client_secret":"s3cr3t","otp":"111111","password":"Mosip@123"}`
	got := snippet([]byte(body))
	for _, leaked := range []string{"eyJhbGciOi", "eyJraWQ", "s3cr3t", "111111", "Mosip@123"} {
		if strings.Contains(got, leaked) {
			t.Errorf("snippet leaked %q:\n%s", leaked, got)
		}
	}
	// The key names stay, so a reader can still tell what the response was.
	for _, kept := range []string{"access_token", "id_token", "client_secret", "otp", "password"} {
		if !strings.Contains(got, kept) {
			t.Errorf("snippet dropped the key %q:\n%s", kept, got)
		}
	}
}

// flow/execute answers with {code, message}, and the features assert FES-1004 on
// it. Masking that would blank the one value the failure report is read for.
func TestSnippetKeepsTheDiagnosticFields(t *testing.T) {
	got := snippet([]byte(`{"code":"FES-1004","message":"invalid transaction"}`))
	for _, want := range []string{"FES-1004", "invalid transaction"} {
		if !strings.Contains(got, want) {
			t.Errorf("snippet masked %q, which the assertion is about:\n%s", want, got)
		}
	}
	got = snippet([]byte(`{"errors":[{"errorCode":"invalid_client_id","errorMessage":"no such client"}]}`))
	if !strings.Contains(got, "invalid_client_id") {
		t.Errorf("snippet masked the MOSIP errorCode:\n%s", got)
	}
}

// Truncation applies after redaction: masking a long token first must not let a
// prefix of it survive by being cut mid-value.
func TestSnippetTruncatesAfterRedaction(t *testing.T) {
	long := strings.Repeat("A", 600)
	got := snippet([]byte(`{"access_token":"` + long + `"}`))
	if strings.Contains(got, "AAAA") {
		t.Errorf("truncation exposed part of the masked value:\n%s", got)
	}
	if len(got) > 400+len("…") {
		t.Errorf("snippet length = %d, want it capped at 400 runes plus the ellipsis", len(got))
	}
}
