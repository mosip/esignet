/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package status

import (
	"bytes"
	"compress/gzip"
	"context"
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/vc/ldp/fetch"
	"github.com/mosip/esignet/internal/vc/ldp/jsonval"
)

type StatusTestSuite struct {
	suite.Suite
}

func TestStatusTestSuite(t *testing.T) {
	suite.Run(t, new(StatusTestSuite))
}

func encode(ts *StatusTestSuite, bits []byte) string {
	var buf bytes.Buffer
	zw := gzip.NewWriter(&buf)
	_, err := zw.Write(bits)
	ts.Require().NoError(err)
	ts.Require().NoError(zw.Close())
	return base64.RawURLEncoding.EncodeToString(buf.Bytes())
}

// The MOSIP sample status lists hold the credential at index 104282.
func (ts *StatusTestSuite) TestMOSIPStatusLists() {
	for file, want := range map[string]int{"mosipRevokedStatusList.json": 1, "mosipUnrevokedStatusList.json": 0} {
		raw, err := os.ReadFile(filepath.Join("..", "testdata", "ldp_vc", file))
		ts.Require().NoError(err)
		var doc struct {
			CredentialSubject struct {
				EncodedList string `json:"encodedList"`
			} `json:"credentialSubject"`
		}
		ts.Require().NoError(json.Unmarshal(raw, &doc))
		bits, err := decodeList(doc.CredentialSubject.EncodedList)
		ts.Require().NoError(err)
		v, err := readBits(bits, 104282, 1)
		ts.Require().NoError(err)
		ts.Equal(want, v, file)
	}
}

const (
	testIssuer  = "did:example:issuer"
	testListURL = "https://issuer.example.org/status/1"
)

// lists serves status list credentials by URL and counts the requests.
type lists struct {
	docs map[string][]byte
	hits atomic.Int32
}

func (l *lists) RoundTrip(req *http.Request) (*http.Response, error) {
	l.hits.Add(1)
	body, ok := l.docs[req.URL.String()]
	if !ok {
		return &http.Response{StatusCode: http.StatusNotFound, Body: io.NopCloser(bytes.NewReader(nil)), Request: req}, nil
	}
	return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(bytes.NewReader(body)), Request: req}, nil
}

// serve returns a Checker whose only list is doc at testListURL.
func (ts *StatusTestSuite) serve(doc map[string]any) (*Checker, *lists) {
	raw, err := json.Marshal(doc)
	ts.Require().NoError(err)
	l := &lists{docs: map[string][]byte{testListURL: raw}}
	f := fetch.New(fetch.Options{Client: &http.Client{Transport: l}, MaxBytes: 1 << 20})
	return NewChecker(f, time.Minute, 8), l
}

func (ts *StatusTestSuite) list(purpose any, bits []byte) map[string]any {
	return map[string]any{
		"issuer": testIssuer,
		"credentialSubject": map[string]any{
			"type": listType, "statusPurpose": purpose, "encodedList": encode(ts, bits),
		},
	}
}

func (ts *StatusTestSuite) request(now time.Time, purposes ...string) Request {
	return Request{
		Purposes: purposes,
		Verify: func(_ context.Context, doc map[string]any, _ time.Time) (string, error) {
			return jsonval.String(doc["issuer"]), nil
		},
		Issuer: testIssuer,
		Now:    now,
	}
}

func entry(purpose string, index int) map[string]any {
	return map[string]any{
		"type": entryType, "statusPurpose": purpose, "statusListCredential": testListURL, "statusListIndex": strconv.Itoa(index),
	}
}

// A purpose with several entries is invalid if any entry is, whatever their
// order.
func (ts *StatusTestSuite) TestSetEntryIsKeptAcrossEntries() {
	c, _ := ts.serve(ts.list("revocation", []byte{0b1000_0000}))
	for name, entries := range map[string][]any{
		"set first": {entry("revocation", 0), entry("revocation", 1)},
		"set last":  {entry("revocation", 1), entry("revocation", 0)},
	} {
		res, err := c.Check(context.Background(), map[string]any{"credentialStatus": entries}, ts.request(time.Now(), "revocation"))
		ts.Require().NoError(err, name)
		ts.Equal(Result{Valid: false, Value: 1}, res["revocation"], name)
	}
}

// A status list may serve several purposes.
func (ts *StatusTestSuite) TestListWithSeveralPurposes() {
	c, _ := ts.serve(ts.list([]any{"revocation", "Suspension"}, []byte{0b0100_0000}))
	vc := map[string]any{"credentialStatus": []any{entry("revocation", 0), entry("suspension", 1)}}
	res, err := c.Check(context.Background(), vc, ts.request(time.Now(), "revocation", "suspension"))
	ts.Require().NoError(err)
	ts.Equal(map[string]Result{"revocation": {Valid: true}, "suspension": {Valid: false, Value: 1}}, res)

	_, err = c.Check(context.Background(), map[string]any{"credentialStatus": entry("refresh", 0)}, ts.request(time.Now(), "refresh"))
	ts.ErrorIs(err, ErrStatus)
}

// A list is not cached past its VCDM 1.1 expirationDate or VCDM 2.0
// validUntil.
func (ts *StatusTestSuite) TestListCacheEndsAtExpiry() {
	for _, field := range []string{"expirationDate", "validUntil"} {
		doc := ts.list("revocation", []byte{0})
		// Already past for the cache clock, still valid for the request.
		doc[field] = "2020-01-01T00:00:00Z"
		c, l := ts.serve(doc)
		vc := map[string]any{"credentialStatus": entry("revocation", 0)}
		r := ts.request(time.Date(2019, 1, 1, 0, 0, 0, 0, time.UTC), "revocation")
		for range 2 {
			_, err := c.Check(context.Background(), vc, r)
			ts.Require().NoError(err, field)
		}
		ts.Equal(int32(2), l.hits.Load(), field)
	}
}

func (ts *StatusTestSuite) TestReadBits() {
	bits := []byte{0b1010_0000, 0b0000_0011}
	for _, c := range []struct{ pos, size, want int }{
		{0, 1, 1}, {1, 1, 0}, {2, 1, 1}, {0, 3, 0b101}, {14, 2, 0b11}, {6, 4, 0b0000},
	} {
		v, err := readBits(bits, c.pos, c.size)
		ts.Require().NoError(err)
		ts.Equal(c.want, v, "pos %d size %d", c.pos, c.size)
	}
	_, err := readBits(bits, 15, 2)
	ts.ErrorIs(err, ErrStatus)
}

func (ts *StatusTestSuite) TestDecodeList() {
	bits := []byte{0xff, 0x00}
	for _, encoded := range []string{"u" + encode(ts, bits), encode(ts, bits), encode(ts, bits) + "=="} {
		got, err := decodeList(encoded)
		ts.Require().NoError(err)
		ts.Equal(bits, got)
	}
	for _, encoded := range []string{"", "u!!", "u" + base64.RawURLEncoding.EncodeToString([]byte("not gzip"))} {
		_, err := decodeList(encoded)
		ts.ErrorIs(err, ErrStatus, encoded)
	}
	_, err := decodeList(encode(ts, make([]byte, maxDecompressed+1)))
	ts.ErrorIs(err, ErrStatus, "decompression is capped")
}

func (ts *StatusTestSuite) TestStatusSizeAndMessages() {
	size, err := statusSize(map[string]any{}, &list{})
	ts.Require().NoError(err)
	ts.Equal(1, size)
	size, err = statusSize(map[string]any{}, &list{statusSize: 2})
	ts.Require().NoError(err)
	ts.Equal(2, size, "list subject")
	size, err = statusSize(map[string]any{"statusSize": json.Number("4")}, &list{statusSize: 2})
	ts.Require().NoError(err)
	ts.Equal(4, size, "entry wins (W3C)")
	_, err = statusSize(map[string]any{"statusSize": "64"}, &list{})
	ts.ErrorIs(err, ErrStatus)
	_, err = statusSize(map[string]any{"statusSize": -1}, &list{})
	ts.ErrorIs(err, ErrStatus)

	ts.NoError(checkStatusMessages([]any{1, 2, 3, 4}, 2))
	ts.NoError(checkStatusMessages(map[string]any{"0x0": "a", "0x1": "b"}, 1))
	ts.ErrorIs(checkStatusMessages([]any{1, 2}, 2), ErrStatus)
	ts.ErrorIs(checkStatusMessages(nil, 2), ErrStatus)
}

func (ts *StatusTestSuite) TestNonNegativeInt() {
	for _, v := range []any{"7", json.Number("7")} {
		n, err := nonNegativeInt(v)
		ts.Require().NoError(err)
		ts.Equal(7, n)
	}
	for _, v := range []any{"-1", "1.5", "x", 7.0, nil} {
		_, err := nonNegativeInt(v)
		ts.Error(err, v)
	}
}
