/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package consentmgmt

import "testing"

func TestNormalizeClaims_NilAndMissingSections(t *testing.T) {
	got := NormalizeClaims(nil)
	if got["userinfo"] == nil || got["id_token"] == nil {
		t.Fatalf("expected empty userinfo/id_token maps for nil claims, got %#v", got)
	}
	if len(got["userinfo"].(map[string]any)) != 0 || len(got["id_token"].(map[string]any)) != 0 {
		t.Fatalf("expected both sections empty, got %#v", got)
	}
}

func TestNormalizeClaims_NilConstraintsBecomeEmptyObjects(t *testing.T) {
	claims := map[string]any{
		"userinfo": map[string]any{
			"email": map[string]any{"essential": true},
			"name":  nil,
		},
		"id_token": map[string]any{"sub": nil},
	}

	got := NormalizeClaims(claims)

	userinfo := got["userinfo"].(map[string]any)
	if name, ok := userinfo["name"].(map[string]any); !ok || len(name) != 0 {
		t.Errorf("name should normalize to an empty object, got %#v", userinfo["name"])
	}
	email, ok := userinfo["email"].(map[string]any)
	if !ok || email["essential"] != true {
		t.Errorf("email constraint should be preserved, got %#v", userinfo["email"])
	}

	idToken := got["id_token"].(map[string]any)
	if sub, ok := idToken["sub"].(map[string]any); !ok || len(sub) != 0 {
		t.Errorf("sub should normalize to an empty object, got %#v", idToken["sub"])
	}
}

func TestNormalizeClaims_IgnoresNonMapSections(t *testing.T) {
	claims := map[string]any{
		"userinfo": "not-a-map",
	}
	got := NormalizeClaims(claims)
	if len(got["userinfo"].(map[string]any)) != 0 {
		t.Fatalf("expected userinfo to fall back to empty map, got %#v", got["userinfo"])
	}
}

func TestExtractAttributes_EssentialAndOptional(t *testing.T) {
	claims := map[string]any{
		"userinfo": map[string]any{
			"email":   map[string]any{"essential": true},
			"name":    nil,
			"picture": map[string]any{"essential": false},
		},
		"id_token": map[string]any{
			"auth_time": map[string]any{"essential": true},
			"acr":       map[string]any{"values": []any{"urn:mace:incommon:iap:silver"}},
		},
	}

	essential, optional := ExtractAttributes(claims)

	if got, want := essential, []string{"auth_time", "email"}; !equalStrings(got, want) {
		t.Errorf("essential = %#v, want %#v", got, want)
	}
	if got, want := optional, []string{"acr", "name", "picture"}; !equalStrings(got, want) {
		t.Errorf("optional = %#v, want %#v", got, want)
	}
}

func TestExtractAttributes_SameClaimEssentialInOneSectionWins(t *testing.T) {
	claims := map[string]any{
		"userinfo": map[string]any{"sub": nil},
		"id_token": map[string]any{"sub": map[string]any{"essential": true}},
	}

	essential, optional := ExtractAttributes(claims)

	if got, want := essential, []string{"sub"}; !equalStrings(got, want) {
		t.Errorf("essential = %#v, want %#v", got, want)
	}
	if len(optional) != 0 {
		t.Errorf("expected no optional claims, got %#v", optional)
	}
}

func TestExtractAttributes_VerifiedClaimsArray(t *testing.T) {
	// Shape used by the OIDC4IDA extension, matching
	// api-test/.../VerifiedClaims/OAuthDetails/OAuthDetailsRequest.hbs.
	claims := map[string]any{
		"userinfo": map[string]any{
			"phone_number": map[string]any{"essential": true},
			"verified_claims": []any{
				map[string]any{
					"verification": map[string]any{"trust_framework": nil, "time": nil},
					"claims": map[string]any{
						"email": map[string]any{"essential": true},
						"name":  nil,
					},
				},
			},
		},
	}

	essential, optional := ExtractAttributes(claims)

	if got, want := essential, []string{"email", "phone_number"}; !equalStrings(got, want) {
		t.Errorf("essential = %#v, want %#v", got, want)
	}
	if got, want := optional, []string{"name"}; !equalStrings(got, want) {
		t.Errorf("optional = %#v, want %#v", got, want)
	}
}

func TestExtractAttributes_VerifiedClaimsSingleObject(t *testing.T) {
	claims := map[string]any{
		"userinfo": map[string]any{
			"verified_claims": map[string]any{
				"verification": map[string]any{"trust_framework": nil},
				"claims":       map[string]any{"birthdate": nil},
			},
		},
	}

	essential, optional := ExtractAttributes(claims)

	if len(essential) != 0 {
		t.Errorf("expected no essential claims, got %#v", essential)
	}
	if got, want := optional, []string{"birthdate"}; !equalStrings(got, want) {
		t.Errorf("optional = %#v, want %#v", got, want)
	}
}

func TestExtractAttributes_NilAndEmptyClaims(t *testing.T) {
	essential, optional := ExtractAttributes(nil)
	if len(essential) != 0 || len(optional) != 0 {
		t.Fatalf("expected empty lists for nil claims, got essential=%#v optional=%#v", essential, optional)
	}
}

func equalStrings(got, want []string) bool {
	if len(got) != len(want) {
		return false
	}
	for i := range got {
		if got[i] != want[i] {
			return false
		}
	}
	return true
}

func TestNormalizeAuthorizationScopes(t *testing.T) {
	got := NormalizeAuthorizationScopes([]string{"resource.read", "resource.write"})
	if len(got) != 2 || got["resource.read"] != false || got["resource.write"] != false {
		t.Fatalf("unexpected normalized scopes: %#v", got)
	}
}

func TestNormalizeAuthorizationScopes_Empty(t *testing.T) {
	got := NormalizeAuthorizationScopes(nil)
	if len(got) != 0 {
		t.Fatalf("expected empty map, got %#v", got)
	}
}

func TestHashRequestedConsent_Deterministic(t *testing.T) {
	claims := NormalizeClaims(map[string]any{
		"userinfo": map[string]any{"email": map[string]any{"essential": true}, "name": nil},
	})
	scopes := NormalizeAuthorizationScopes([]string{"resource.read", "resource.write"})

	h1, err := HashRequestedConsent(claims, scopes)
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	h2, err := HashRequestedConsent(claims, scopes)
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
	claimsA := NormalizeClaims(map[string]any{
		"userinfo": map[string]any{"email": nil, "name": nil},
	})
	claimsB := NormalizeClaims(map[string]any{
		"userinfo": map[string]any{"name": nil, "email": nil},
	})
	scopesA := NormalizeAuthorizationScopes([]string{"a.read", "b.read"})
	scopesB := NormalizeAuthorizationScopes([]string{"b.read", "a.read"})

	ha, err := HashRequestedConsent(claimsA, scopesA)
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	hb, err := HashRequestedConsent(claimsB, scopesB)
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	if ha != hb {
		t.Fatalf("hash should be order-independent: %q vs %q", ha, hb)
	}
}

func TestHashRequestedConsent_DetectsChange(t *testing.T) {
	baseClaims := NormalizeClaims(map[string]any{"userinfo": map[string]any{"email": nil}})
	baseScopes := NormalizeAuthorizationScopes([]string{"a.read"})

	changedClaims := NormalizeClaims(map[string]any{
		"userinfo": map[string]any{"email": nil, "phone": nil},
	})
	changedScopes := NormalizeAuthorizationScopes([]string{"a.read", "b.read"})

	hBase, _ := HashRequestedConsent(baseClaims, baseScopes)
	hClaim, _ := HashRequestedConsent(changedClaims, baseScopes)
	hScope, _ := HashRequestedConsent(baseClaims, changedScopes)

	if hBase == hClaim {
		t.Fatal("adding a claim should change the hash")
	}
	if hBase == hScope {
		t.Fatal("adding a scope should change the hash")
	}
}
