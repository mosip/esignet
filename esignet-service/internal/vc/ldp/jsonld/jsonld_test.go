/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jsonld

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/vc/ldp/fetch"
)

const contextV2 = "https://www.w3.org/ns/credentials/v2"

type JSONLDTestSuite struct {
	suite.Suite
	ctx context.Context
	l   *Loader
}

func TestJSONLDTestSuite(t *testing.T) {
	suite.Run(t, new(JSONLDTestSuite))
}

func (ts *JSONLDTestSuite) SetupTest() {
	ts.ctx = context.Background()
	var err error
	ts.l, err = NewLoader(Options{MaxBlankNodes: 16})
	ts.Require().NoError(err)
}

func (ts *JSONLDTestSuite) TestPinnedContexts() {
	for _, u := range []string{contextV2, "https://www.w3.org/2018/credentials/v1", "https://w3id.org/security/suites/ed25519-2020/v1"} {
		ts.True(ts.l.Pinned(u), u)
	}
	ts.False(ts.l.Pinned("https://w3id.org/security/v2"))

	_, err := ts.l.load(ts.ctx, "https://example.org/ctx")
	ts.ErrorIs(err, ErrContextNotAllowed)
}

func (ts *JSONLDTestSuite) TestContextsDir() {
	dir := ts.T().TempDir()
	ts.Require().NoError(os.WriteFile(filepath.Join(dir, "index.json"), []byte(`{"https://example.org/ctx": "ctx.jsonld"}`), 0o600))
	ts.Require().NoError(os.WriteFile(filepath.Join(dir, "ctx.jsonld"), []byte(`{"@context": {"@vocab": "https://example.org/#"}}`), 0o600))
	l, err := NewLoader(Options{Dir: dir})
	ts.Require().NoError(err)
	ts.True(l.Pinned("https://example.org/ctx"))

	ts.Require().NoError(os.WriteFile(filepath.Join(dir, "ctx.jsonld"), []byte(`{"no": "context"}`), 0o600))
	_, err = NewLoader(Options{Dir: dir})
	ts.Error(err)
	_, err = NewLoader(Options{Dir: filepath.Join(dir, "missing")})
	ts.Error(err)
}

func (ts *JSONLDTestSuite) TestCheckSafe() {
	doc := func(subject map[string]any) map[string]any {
		return map[string]any{
			"@context":          []any{"https://www.w3.org/2018/credentials/v1"},
			"type":              []any{"VerifiableCredential"},
			"issuer":            "did:example:issuer",
			"credentialSubject": subject,
		}
	}
	ts.NoError(ts.l.CheckSafe(ts.ctx, doc(map[string]any{"id": "did:example:holder"})))
	ts.ErrorIs(ts.l.CheckSafe(ts.ctx, doc(map[string]any{"role": "admin"})), ErrUndefinedTerm)
	ts.ErrorIs(ts.l.CheckSafe(ts.ctx, doc(map[string]any{"id": "relative-id"})), ErrUndefinedTerm)

	withType := doc(map[string]any{"id": "did:example:holder"})
	withType["type"] = []any{"VerifiableCredential", "UndefinedType"}
	ts.ErrorIs(ts.l.CheckSafe(ts.ctx, withType), ErrUndefinedTerm)

	remote := doc(map[string]any{})
	remote["@context"] = []any{"https://www.w3.org/2018/credentials/v1", "https://example.org/ctx"}
	err := ts.l.CheckSafe(ts.ctx, remote)
	ts.ErrorIs(err, ErrContext)
	ts.ErrorIs(err, ErrContextNotAllowed)
}

func (ts *JSONLDTestSuite) TestCanonicalizeSafe() {
	doc := map[string]any{
		"@context":          []any{"https://www.w3.org/2018/credentials/v1"},
		"id":                "urn:uuid:vc",
		"type":              []any{"VerifiableCredential"},
		"credentialSubject": map[string]any{"id": "did:example:holder"},
	}
	safe, err := ts.l.CanonicalizeSafe(ts.ctx, doc)
	ts.Require().NoError(err)
	lenient, err := ts.l.Canonicalize(ts.ctx, doc)
	ts.Require().NoError(err)
	ts.Equal(lenient, safe)

	doc["credentialSubject"] = map[string]any{"id": "did:example:holder", "role": "admin"}
	_, err = ts.l.CanonicalizeSafe(ts.ctx, doc)
	ts.ErrorIs(err, ErrUndefinedTerm)
	dropped, err := ts.l.Canonicalize(ts.ctx, doc)
	ts.Require().NoError(err)
	ts.Equal(lenient, dropped, "the undefined term is dropped")

	delete(doc, "credentialSubject")
	doc["type"] = []any{"VerifiableCredential", "UndefinedType"}
	_, err = ts.l.CanonicalizeSafe(ts.ctx, doc)
	ts.ErrorIs(err, ErrUndefinedTerm)
}

// json-gold loses the @graph container of VCDM 2.0 verifiableCredential
// (declared with "@context": null); fixGraphContainers restores it.
func (ts *JSONLDTestSuite) TestPresentationGraphContainer() {
	vp := map[string]any{
		"@context": []any{contextV2},
		"id":       "urn:uuid:vp",
		"type":     []any{"VerifiablePresentation"},
		"verifiableCredential": []any{map[string]any{
			"@context": []any{contextV2},
			"id":       "urn:uuid:vc",
			"type":     []any{"VerifiableCredential"},
		}},
	}
	nquads, err := ts.l.Canonicalize(ts.ctx, vp)
	ts.Require().NoError(err)
	ts.Contains(nquads, "<urn:uuid:vp> <https://www.w3.org/2018/credentials#verifiableCredential> _:c14n0 .")
	ts.Contains(nquads, "<urn:uuid:vc> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#VerifiableCredential> _:c14n0 .")
}

// Relative IRIs are dropped from the dataset, as JSON-LD to RDF requires.
func (ts *JSONLDTestSuite) TestRelativeIRIsDropped() {
	doc := map[string]any{
		"@context": []any{"https://www.w3.org/2018/credentials/v1"},
		"id":       "urn:uuid:vc",
		"type":     []any{"VerifiableCredential", "UndefinedType"},
	}
	nquads, err := ts.l.Canonicalize(ts.ctx, doc)
	ts.Require().NoError(err)
	ts.NotContains(nquads, "UndefinedType")
	ts.Equal(1, strings.Count(nquads, "\n"))
}

func (ts *JSONLDTestSuite) TestBlankNodeCap() {
	subjects := make([]any, 20)
	for i := range subjects {
		subjects[i] = map[string]any{"name": "n"}
	}
	doc := map[string]any{
		"@context":          []any{contextV2},
		"type":              []any{"VerifiableCredential"},
		"credentialSubject": subjects,
	}
	_, err := ts.l.Canonicalize(ts.ctx, doc)
	ts.ErrorIs(err, ErrTooComplex)
}

// Safe mode rejects properties and identifiers that expansion keeps but RDF
// conversion drops, since they would be outside the signature.
func (ts *JSONLDTestSuite) TestCanonicalizeSafeRejectsWhatRDFDrops() {
	for name, subject := range map[string]map[string]any{
		"blank node property":    {"id": "did:example:holder", "_:role": "admin"},
		"relative property":      {"id": "did:example:holder", "./x:y": "admin"},
		"relative id with colon": {"id": "./x:y"},
	} {
		doc := map[string]any{
			"@context":          []any{"https://www.w3.org/2018/credentials/v1"},
			"id":                "urn:uuid:vc",
			"type":              []any{"VerifiableCredential"},
			"credentialSubject": subject,
		}
		_, err := ts.l.CanonicalizeSafe(ts.ctx, doc)
		ts.ErrorIs(err, ErrUndefinedTerm, name)
	}
}

// fixGraphContainers leaves @json literals alone, so a literal holding the
// verifiableCredential IRI does not canonicalize like a graph-wrapped one.
func (ts *JSONLDTestSuite) TestGraphContainerFixSkipsJSONLiterals() {
	doc := func(value any) map[string]any {
		return map[string]any{
			"@context": []any{"https://www.w3.org/2018/credentials/v1", map[string]any{
				"data": map[string]any{"@id": "https://example.org/data", "@type": "@json"},
			}},
			"id":   "urn:uuid:vc",
			"data": value,
		}
	}
	bare, err := ts.l.CanonicalizeSafe(ts.ctx, doc(map[string]any{verifiableCredentialIRI: []any{map[string]any{"x": 1}}}))
	ts.Require().NoError(err)
	wrapped, err := ts.l.CanonicalizeSafe(ts.ctx, doc(map[string]any{verifiableCredentialIRI: []any{map[string]any{"@graph": []any{map[string]any{"x": 1}}}}}))
	ts.Require().NoError(err)
	ts.NotEqual(bare, wrapped)
}

// A redirect from an allow-listed context URL must land on an allow-listed
// URL too.
func (ts *JSONLDTestSuite) TestRemoteContextRedirectMustBeAllowed() {
	srv := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/allowed/ctx", "/allowed/other", "/elsewhere/ctx":
			_, _ = w.Write([]byte(`{"@context": {"@vocab": "https://example.org/#"}}`))
		case "/allowed/to-allowed":
			http.Redirect(w, r, "/allowed/other", http.StatusFound)
		case "/allowed/to-elsewhere":
			http.Redirect(w, r, "/elsewhere/ctx", http.StatusFound)
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()
	f := fetch.New(fetch.Options{Client: srv.Client(), MaxBytes: 1 << 16})
	l, err := NewLoader(Options{RemoteAllowlist: []string{srv.URL + "/allowed/"}, Fetcher: f})
	ts.Require().NoError(err)

	for _, path := range []string{"/allowed/ctx", "/allowed/to-allowed"} {
		_, err := l.load(ts.ctx, srv.URL+path)
		ts.NoError(err, path)
	}
	_, err = l.load(ts.ctx, srv.URL+"/allowed/to-elsewhere")
	ts.ErrorIs(err, ErrContextNotAllowed)
	// The shared fetcher's own policy is unchanged.
	_, err = f.Get(ts.ctx, srv.URL+"/allowed/to-elsewhere", "")
	ts.NoError(err)
}

func (ts *JSONLDTestSuite) TestDeepCopy() {
	orig := map[string]any{"a": []any{map[string]any{"b": "c"}}}
	cp := deepCopy(orig)
	cp["a"].([]any)[0].(map[string]any)["b"] = "changed"
	ts.Equal("c", orig["a"].([]any)[0].(map[string]any)["b"])
}
