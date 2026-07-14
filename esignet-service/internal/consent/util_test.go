package consent

import (
	"context"
	"errors"
	"testing"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// fakeRuntimeStore is a minimal RuntimeStoreProvider whose Get returns preset data/err; the other
// methods are unused by the consent reader.
type fakeRuntimeStore struct {
	data map[string][]byte
	err  error
}

func (f *fakeRuntimeStore) Get(_ context.Context, ns providers.RuntimeStoreNamespace, key string) ([]byte, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.data[string(ns)+":"+key], nil
}

func (f *fakeRuntimeStore) Put(context.Context, providers.RuntimeStoreNamespace, string, []byte, int64) error {
	return nil
}
func (f *fakeRuntimeStore) Update(context.Context, providers.RuntimeStoreNamespace, string, []byte) error {
	return nil
}
func (f *fakeRuntimeStore) Delete(context.Context, providers.RuntimeStoreNamespace, string) error {
	return nil
}
func (f *fakeRuntimeStore) Take(context.Context, providers.RuntimeStoreNamespace, string) ([]byte, error) {
	return nil, nil
}
func (f *fakeRuntimeStore) ExtendTTL(context.Context, providers.RuntimeStoreNamespace, string, int64) error {
	return nil
}

// storedAuthRequest is the JSON shape the engine's authorizationRequestStore persists: the
// OAuthParameters object (Go field names, no json tags) with ClaimsRequest in the OIDC wire shape.
const storedAuthRequest = `{
	"OAuthParameters": {
		"ClientID": "client-1",
		"Prompt": "consent",
		"StandardScopes": ["openid", "profile"],
		"PermissionScopes": ["resource.read"],
		"ClaimsRequest": {
			"userinfo": {
				"email": {"essential": true},
				"name": null,
				"verified_claims": {
					"verification": {"trust_framework": null},
					"claims": {"given_name": null}
				}
			},
			"id_token": {"sub": {"essential": true}}
		}
	}
}`

func TestReadAuthRequest_DecodesStoredRequest(t *testing.T) {
	authID := "authreq-1"
	store := &fakeRuntimeStore{data: map[string][]byte{
		string(providers.NamespaceAuthzReq) + ":" + authID: []byte(storedAuthRequest),
	}}
	s := &Service{runtimeStore: store}

	req, found, err := s.readAuthRequest(context.Background(), authID)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if !found || req == nil {
		t.Fatalf("expected the request to be found")
	}
	if req.Prompt != "consent" {
		t.Errorf("Prompt = %q, want %q", req.Prompt, "consent")
	}
	if len(req.AuthorizeScopes) != 1 || req.AuthorizeScopes[0] != "resource.read" {
		t.Errorf("AuthorizeScopes = %v, want [resource.read]", req.AuthorizeScopes)
	}
	if !isEssentialClaim(req.UserInfo["email"]) {
		t.Errorf("email should decode as essential")
	}
	if _, ok := req.UserInfo["name"]; !ok {
		t.Errorf("name should be present with a nil constraint")
	}
	if _, ok := req.UserInfo["verified_claims"]; !ok {
		t.Errorf("verified_claims member should be retained in the userinfo section")
	}
	if !isEssentialClaim(req.IDToken["sub"]) {
		t.Errorf("sub should decode as essential")
	}
}

func TestReadAuthRequest_EmptyAuthID(t *testing.T) {
	s := &Service{runtimeStore: &fakeRuntimeStore{}}
	req, found, err := s.readAuthRequest(context.Background(), "")
	if err != nil || found || req != nil {
		t.Fatalf("empty authID: got (%v, %v, %v), want (nil, false, nil)", req, found, err)
	}
}

func TestReadAuthRequest_MissingKey(t *testing.T) {
	s := &Service{runtimeStore: &fakeRuntimeStore{data: map[string][]byte{}}}
	req, found, err := s.readAuthRequest(context.Background(), "missing")
	if err != nil || found || req != nil {
		t.Fatalf("missing key: got (%v, %v, %v), want (nil, false, nil)", req, found, err)
	}
}

func TestReadAuthRequest_StoreError(t *testing.T) {
	s := &Service{runtimeStore: &fakeRuntimeStore{err: errors.New("boom")}}
	_, found, err := s.readAuthRequest(context.Background(), "authreq-1")
	if err == nil {
		t.Fatal("expected an error when the store fails")
	}
	if found {
		t.Fatal("found should be false on error")
	}
}

func TestHashRequestedConsent_Deterministic(t *testing.T) {
	req := &requestedConsent{
		UserInfo: map[string]any{
			"email": map[string]any{"essential": true},
			"name":  nil,
		},
		IDToken:         map[string]any{"sub": nil},
		AuthorizeScopes: []string{"resource.read", "resource.write"},
	}

	h1, err := hashRequestedConsent(req)
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	h2, err := hashRequestedConsent(req)
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	if h1 != h2 {
		t.Fatalf("hash not deterministic: %q vs %q", h1, h2)
	}
	if h1 == "" {
		t.Fatal("hash is empty")
	}
}

func TestHashRequestedConsent_OrderIndependent(t *testing.T) {
	a := &requestedConsent{
		UserInfo:        map[string]any{"email": nil, "name": nil},
		AuthorizeScopes: []string{"a.read", "b.read"},
	}
	b := &requestedConsent{
		UserInfo:        map[string]any{"name": nil, "email": nil},
		AuthorizeScopes: []string{"b.read", "a.read"},
	}

	ha, _ := hashRequestedConsent(a)
	hb, _ := hashRequestedConsent(b)
	if ha != hb {
		t.Fatalf("hash should be order-independent: %q vs %q", ha, hb)
	}
}

func TestHashRequestedConsent_DetectsChange(t *testing.T) {
	base := &requestedConsent{
		UserInfo:        map[string]any{"email": nil},
		AuthorizeScopes: []string{"a.read"},
	}
	changedClaim := &requestedConsent{
		UserInfo:        map[string]any{"email": nil, "phone": nil},
		AuthorizeScopes: []string{"a.read"},
	}
	changedScope := &requestedConsent{
		UserInfo:        map[string]any{"email": nil},
		AuthorizeScopes: []string{"a.read", "b.read"},
	}

	hBase, _ := hashRequestedConsent(base)
	hClaim, _ := hashRequestedConsent(changedClaim)
	hScope, _ := hashRequestedConsent(changedScope)

	if hBase == hClaim {
		t.Fatal("adding a claim should change the hash")
	}
	if hBase == hScope {
		t.Fatal("adding a scope should change the hash")
	}
}
