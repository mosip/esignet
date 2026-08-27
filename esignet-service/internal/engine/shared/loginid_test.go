/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import "testing"

func TestResolveIndividualID(t *testing.T) {
	tests := []struct {
		name           string
		identifiers    map[string]any
		wantID         string
		wantLoginIDKey string
		wantOK         bool
	}{
		{"uin", map[string]any{LoginIDUIN: "4358192047"}, "4358192047", LoginIDUIN, true},
		{"phone", map[string]any{LoginIDPhone: "+9198@phone"}, "+9198@phone", LoginIDPhone, true},
		{"email", map[string]any{LoginIDEmail: "a@b.com@email"}, "a@b.com@email", LoginIDEmail, true},
		{"nrc", map[string]any{LoginIDNRC: "123/456@nrc"}, "123/456@nrc", LoginIDNRC, true},
		{"untyped username", map[string]any{LoginIDUsername: "user-1"}, "user-1", LoginIDUsername, true},
		{"unknown key", map[string]any{"passport": "p-1"}, "", "", false},
		{"empty value", map[string]any{LoginIDPhone: ""}, "", "", false},
		{"non-string value", map[string]any{LoginIDUIN: 42}, "", "", false},
		{"no identifiers", map[string]any{}, "", "", false},
		{"nil map", nil, "", "", false},
		// A typed login ID wins over the untyped fallback.
		{"typed wins over username",
			map[string]any{LoginIDUsername: "user-1", LoginIDPhone: "+9198@phone"},
			"+9198@phone", LoginIDPhone, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			id, key, ok := ResolveIndividualID(tt.identifiers)
			if id != tt.wantID || key != tt.wantLoginIDKey || ok != tt.wantOK {
				t.Errorf("ResolveIndividualID() = (%q, %q, %v), want (%q, %q, %v)",
					id, key, ok, tt.wantID, tt.wantLoginIDKey, tt.wantOK)
			}
		})
	}
}
