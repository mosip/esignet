/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jsonval

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

type JSONValTestSuite struct {
	suite.Suite
}

func TestJSONValTestSuite(t *testing.T) {
	suite.Run(t, new(JSONValTestSuite))
}

func (ts *JSONValTestSuite) TestList() {
	ts.Nil(List(nil))
	ts.Equal([]any{"a"}, List("a"))
	ts.Equal([]any{"a", 1}, List([]any{"a", 1}))
}

func (ts *JSONValTestSuite) TestStrings() {
	ts.Equal("a", String("a"))
	ts.Empty(String(json.Number("1")))
	ts.Equal([]string{"a", "b"}, Strings([]any{"a", 1, "b"}))
	ts.Equal([]string{"a"}, Strings("a"))
	ts.Equal("did:example:1", ID("did:example:1"))
	ts.Equal("did:example:1", ID(map[string]any{"id": "did:example:1", "name": "x"}))
	ts.Empty(ID([]any{"did:example:1"}))
}

func (ts *JSONValTestSuite) TestObjects() {
	o := map[string]any{"a": "b"}
	got, ok := Objects(o)
	ts.True(ok)
	ts.Equal([]map[string]any{o}, got)
	got, ok = Objects([]any{o, o})
	ts.True(ok)
	ts.Len(got, 2)
	got, ok = Objects([]any{})
	ts.True(ok)
	ts.Empty(got)
	_, ok = Objects([]any{o, "x"})
	ts.False(ok)
	_, ok = Objects("x")
	ts.False(ok)
}

func (ts *JSONValTestSuite) TestTime() {
	for s, want := range map[string]time.Time{
		"2026-01-02T03:04:05Z":          time.Date(2026, 1, 2, 3, 4, 5, 0, time.UTC),
		"2026-01-02T03:04:05.5+05:30":   time.Date(2026, 1, 2, 3, 4, 5, 5e8, time.FixedZone("", 19800)),
		"2026-01-02T03:04Z":             time.Date(2026, 1, 2, 3, 4, 0, 0, time.UTC),
		"2026-01-02T03:04:05":           time.Date(2026, 1, 2, 3, 4, 5, 0, time.UTC),
		"2026-01-02T03:04:05.123456789": time.Date(2026, 1, 2, 3, 4, 5, 123456789, time.UTC),
	} {
		got, ok := Time(s)
		ts.Require().True(ok, s)
		ts.True(want.Equal(got), s)
	}
	for _, v := range []any{"2026-01-02", "next year", "", nil, json.Number("1")} {
		_, ok := Time(v)
		ts.False(ok, v)
	}
}
