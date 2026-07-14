/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package common

import (
	"encoding/json"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

func (ts *UtilsTestSuite) TestGetResponseTime() {
	t := ts.T()
	got := GetResponseTime()
	parsed, err := time.Parse(MOSIPTimeLayout, got)
	if err != nil {
		t.Fatalf("GetResponseTime() = %q, not parseable with MOSIPTimeLayout: %v", got, err)
	}
	if time.Since(parsed) > time.Minute {
		t.Errorf("GetResponseTime() = %q, too far from now", got)
	}
}

func (ts *UtilsTestSuite) TestWriteJSON() {
	t := ts.T()
	rec := httptest.NewRecorder()
	WriteJSON(rec, 201, map[string]string{"foo": "bar"})

	if rec.Code != 201 {
		t.Errorf("status = %d, want 201", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", ct)
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal body: %v", err)
	}
	if body["foo"] != "bar" {
		t.Errorf("body[foo] = %q, want bar", body["foo"])
	}
}

func (ts *UtilsTestSuite) TestWriteError() {
	t := ts.T()
	rec := httptest.NewRecorder()
	WriteError(rec, 400, "invalid_input", "something went wrong")

	if rec.Code != 400 {
		t.Errorf("status = %d, want 400", rec.Code)
	}
	var body ResponseWrapper
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("unmarshal body: %v", err)
	}
	if len(body.Errors) != 1 {
		t.Fatalf("len(Errors) = %d, want 1", len(body.Errors))
	}
	if body.Errors[0].ErrorCode != "invalid_input" {
		t.Errorf("ErrorCode = %q, want invalid_input", body.Errors[0].ErrorCode)
	}
	if body.Errors[0].ErrorMessage != "something went wrong" {
		t.Errorf("ErrorMessage = %q, want something went wrong", body.Errors[0].ErrorMessage)
	}
	if body.ResponseTime == "" {
		t.Error("ResponseTime is empty")
	}
}

type UtilsTestSuite struct {
	suite.Suite
}

func TestUtilsTestSuite(t *testing.T) {
	suite.Run(t, new(UtilsTestSuite))
}
