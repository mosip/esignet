package api

import (
	"strings"
	"testing"

	"github.com/cucumber/godog"
	messages "github.com/cucumber/messages/go/v21"
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

// A scenario without @known_issue must never be silently reclassified — the
// tag is what makes a FAILED->KNOWN_ISSUE swap deliberate rather than a bug
// hiding itself.
func TestKnownIssueReasonUntaggedScenarioIsNotKnown(t *testing.T) {
	scn := &godog.Scenario{Name: "some ordinary scenario"}
	if _, ok := knownIssueReason(scn); ok {
		t.Error("knownIssueReason: untagged scenario reported as known, want false")
	}
}

// The tagged, tracked scenario must resolve to its recorded reason.
func TestKnownIssueReasonReturnsTheRecordedReason(t *testing.T) {
	scn := &godog.Scenario{
		Name: "Uploading certificateData that is not a certificate is rejected as an invalid certificate",
		Tags: []*messages.PickleTag{{Name: "@known_issue"}},
	}
	reason, ok := knownIssueReason(scn)
	if !ok {
		t.Fatal("knownIssueReason: tagged scenario reported as not known, want true")
	}
	if !strings.Contains(reason, "mosip/esignet#2527") {
		t.Errorf("reason = %q, want it to name the tracked issue", reason)
	}
}

// A scenario tagged @known_issue but missing from apiKnownIssueReasons is a
// spec mistake — someone added the tag without recording why — and must
// surface as such rather than silently reporting no reason at all.
func TestKnownIssueReasonFlagsAMissingEntry(t *testing.T) {
	scn := &godog.Scenario{
		Name: "a scenario someone tagged but forgot to record",
		Tags: []*messages.PickleTag{{Name: "@known_issue"}},
	}
	reason, ok := knownIssueReason(scn)
	if !ok {
		t.Fatal("knownIssueReason: tagged scenario reported as not known, want true")
	}
	if !strings.Contains(reason, "no entry") {
		t.Errorf("reason = %q, want it to flag the missing apiKnownIssueReasons entry", reason)
	}
}
