package wsotp

import (
	"bufio"
	"context"
	"crypto/sha1" //nolint:gosec // RFC 6455 handshake hash, not security
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"strings"
	"testing"
	"time"
)

func TestNormalizeWSURL(t *testing.T) {
	cases := []struct {
		in, want string
		wantErr  bool
	}{
		{in: "https://smtp.collab.mosip.net/", want: "wss://smtp.collab.mosip.net/mocksmtp/websocket"},
		{in: "http://smtp.local:8080", want: "ws://smtp.local:8080/mocksmtp/websocket"},
		{in: "wss://smtp.collab.mosip.net/mocksmtp/websocket", want: "wss://smtp.collab.mosip.net/mocksmtp/websocket"},
		{in: "ws://host/custom/path", want: "ws://host/custom/path"},
		{in: "https://smtp.host/already/path", want: "wss://smtp.host/already/path"},
		{in: "", wantErr: true},
		{in: "ftp://smtp.host", wantErr: true},
	}
	for _, c := range cases {
		got, err := NormalizeWSURL(c.in)
		if c.wantErr {
			if err == nil {
				t.Errorf("NormalizeWSURL(%q): expected error, got %q", c.in, got)
			}
			continue
		}
		if err != nil {
			t.Errorf("NormalizeWSURL(%q): unexpected error: %v", c.in, err)
			continue
		}
		if got != c.want {
			t.Errorf("NormalizeWSURL(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestExtractOTP(t *testing.T) {
	cases := []struct {
		name string
		m    mailMessage
		want string
	}{
		{name: "text body", m: mailMessage{Text: "Your OTP is 123456 valid for 3 min"}, want: "123456"},
		{name: "html fallback", m: mailMessage{HTML: "<b>987654</b>"}, want: "987654"},
		{name: "text wins over html", m: mailMessage{Text: "code 111111", HTML: "222222"}, want: "111111"},
		{name: "no six digits", m: mailMessage{Text: "no code here 12345"}, want: ""},
		{name: "seven digits not matched", m: mailMessage{Text: "ref 1234567 done"}, want: ""},
		{name: "empty", m: mailMessage{}, want: ""},
		// Real multilingual MOSIP SMS: the OTP (979690) must win over the date
		// (22-07-2026) and time (16:28:40), which carry no 6-consecutive-digit run.
		{name: "real mosip sms template", m: mailMessage{Text: realMosipSMS}, want: "979690"},
	}
	for _, c := range cases {
		if got := extractOTP(c.m); got != c.want {
			t.Errorf("%s: extractOTP = %q, want %q", c.name, got, c.want)
		}
	}
}

// realMosipSMS is a verbatim MOSIP OTP SMS body (multiple languages, some untranslated placeholders).
const realMosipSMS = "OTP for UIN XXXXXXXX59 is 979690 and is valid for 3 minutes. " +
	"(Generated on 22-07-2026 at 16:28:40 Hrs) " +
	"OTP لـ $ idvidType $ idvid هو $ otp وهو صالح لمدة $ validTime دقيقة. (تم إنشاؤه في $ date في $ time Hrs) " +
	"UIN XXXXXXXX59 ಗಾಗಿ OTP 979690 ಆಗಿದೆ ಮತ್ತು 3 ನಿಮಿಷಗಳವರೆಗೆ ಮಾನ್ಯವಾಗಿರುತ್ತದೆ. (22-07-2026 ದಂದು 16:28:40 ಗಂಟೆಗೆ ರಚಿಸಲಾಗಿದೆ) " +
	"OTP pour UIN XXXXXXXX59 est 979690 et est valide pour 3 minutes. (Généré le 22-07-2026 à 16:28:40 Hrs)"

// TestSMSRecipientMatch mirrors an SMS frame: recipient is the phone in to.text
// (not to.value[].address), and the OTP is filtered to that phone.
func TestSMSRecipientMatch(t *testing.T) {
	l := &Listener{}
	sms := `{"text":"OTP for UIN XXXXXXXX59 is 979690 and is valid for 3 minutes.","to":{"text":"9879879098"}}`
	since := time.Now().Add(-time.Second)
	l.ingest([]byte(sms), time.Now())

	if got := l.match("9879879098", since); got != "979690" {
		t.Errorf("match(phone) = %q, want 979690", got)
	}
	if got := l.match("", since); got != "979690" {
		t.Errorf("match(any) = %q, want 979690", got)
	}
	if got := l.match("0000000000", since); got != "" {
		t.Errorf("match(wrong phone) = %q, want empty", got)
	}
}

func TestRecipients(t *testing.T) {
	m := mailMessage{}
	m.To.Text = "+911234567890"
	m.To.Value = []struct {
		Address string `json:"address"`
	}{{Address: "USER@Example.ORG"}, {Address: "  other@x.io "}}

	got := recipients(m)
	want := []string{"+911234567890", "user@example.org", "other@x.io"}
	if len(got) != len(want) {
		t.Fatalf("recipients = %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("recipients[%d] = %q, want %q", i, got[i], want[i])
		}
	}
}

func TestMatchFreshnessAndRecipient(t *testing.T) {
	base := time.Now()
	l := &Listener{}
	// stale message for our recipient (before `since`)
	l.ingestAt(`{"text":"old 000000","to":{"value":[{"address":"me@x.io"}]}}`, base.Add(-time.Minute))
	// fresh message for another recipient
	l.ingestAt(`{"text":"other 111111","to":{"value":[{"address":"you@x.io"}]}}`, base.Add(time.Second))
	// fresh message for our recipient
	l.ingestAt(`{"text":"code 222222","to":{"value":[{"address":"me@x.io"}]}}`, base.Add(2*time.Second))
	// newest fresh for our recipient (should win)
	l.ingestAt(`{"text":"code 333333","to":{"value":[{"address":"me@x.io"}]}}`, base.Add(3*time.Second))

	if got := l.match("me@x.io", base); got != "333333" {
		t.Errorf("match(me@x.io) = %q, want 333333 (newest fresh for recipient)", got)
	}
	if got := l.match("", base); got != "333333" {
		t.Errorf("match(any) = %q, want 333333 (newest fresh overall)", got)
	}
	// nothing fresh for an unknown recipient
	if got := l.match("nobody@x.io", base); got != "" {
		t.Errorf("match(nobody) = %q, want empty", got)
	}
	// a `since` after all messages yields nothing
	if got := l.match("me@x.io", base.Add(time.Hour)); got != "" {
		t.Errorf("match with future since = %q, want empty", got)
	}
}

// ingestAt is a test helper mirroring ingest with an explicit receive time.
func (l *Listener) ingestAt(json string, at time.Time) { l.ingest([]byte(json), at) }

func TestDialReadMessageAndListener(t *testing.T) {
	msg := `{"text":"Your login OTP is 654321","to":{"value":[{"address":"user@mosip.io"}]}}`
	url, stop := startTestWSServer(t, []string{msg})
	defer stop()

	// Full listener path: connect, buffer, WaitOTP.
	l := NewListener(url, false)
	if err := l.Start(context.Background()); err != nil {
		t.Fatalf("Start: %v", err)
	}
	defer l.Close()

	otp, err := l.WaitOTP("user@mosip.io", time.Now().Add(-time.Second), 5*time.Second, 100*time.Millisecond)
	if err != nil {
		t.Fatalf("WaitOTP: %v", err)
	}
	if otp != "654321" {
		t.Fatalf("WaitOTP = %q, want 654321", otp)
	}
}

// writeFrame must mask every client frame (RFC 6455 §5.3), verified by unmasking off the wire by hand.
func TestWriteFrameMasksPayload(t *testing.T) {
	cli, srv := net.Pipe()
	defer cli.Close()
	defer srv.Close()

	payload := []byte("hello websocket masking")
	done := make(chan error, 1)
	go func() {
		c := &conn{net: cli}
		done <- c.writeFrame(opText, payload)
	}()

	br := bufio.NewReader(srv)
	var h [2]byte
	if _, err := io.ReadFull(br, h[:]); err != nil {
		t.Fatalf("read header: %v", err)
	}
	if h[0]&0x80 == 0 || int(h[0]&0x0f) != opText {
		t.Fatalf("header = %08b, want FIN + opText", h[0])
	}
	if h[1]&0x80 == 0 {
		t.Fatal("client frame is not masked")
	}
	length := int(h[1] & 0x7f)
	var maskKey [4]byte
	if _, err := io.ReadFull(br, maskKey[:]); err != nil {
		t.Fatalf("read mask key: %v", err)
	}
	masked := make([]byte, length)
	if _, err := io.ReadFull(br, masked); err != nil {
		t.Fatalf("read payload: %v", err)
	}
	got := make([]byte, length)
	for i := range got {
		got[i] = masked[i] ^ maskKey[i%4]
	}
	if string(got) != string(payload) {
		t.Fatalf("payload = %q, want %q", got, payload)
	}
	if err := <-done; err != nil {
		t.Fatalf("writeFrame: %v", err)
	}
}

// RFC 6455 §5.1 forbids a server from masking its frames.
func TestReadFrameRejectsMaskedServerFrame(t *testing.T) {
	cli, srv := net.Pipe()
	defer cli.Close()
	defer srv.Close()

	go func() {
		c := &conn{net: cli}
		_ = c.writeFrame(opText, []byte("hello"))
	}()

	rc := &conn{net: srv, br: bufio.NewReader(srv)}
	_, _, _, err := rc.readFrame(true)
	if err == nil {
		t.Fatal("readFrame accepted a masked server frame")
	}
	if !strings.Contains(err.Error(), "masked") {
		t.Fatalf("readFrame error = %v, want a masked-frame rejection", err)
	}
}

// startTestWSServer runs a minimal RFC 6455 server that echoes each message as an unmasked text frame.
func startTestWSServer(t *testing.T, messages []string) (wsURL string, stop func()) {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	done := make(chan struct{})
	go func() {
		conn, err := ln.Accept()
		if err != nil {
			return
		}
		defer conn.Close()
		br := bufio.NewReader(conn)

		// Read request line + headers, capture the key.
		var key string
		for {
			line, err := br.ReadString('\n')
			if err != nil {
				return
			}
			line = strings.TrimRight(line, "\r\n")
			if line == "" {
				break
			}
			if name, val, ok := strings.Cut(line, ":"); ok && strings.EqualFold(strings.TrimSpace(name), "Sec-WebSocket-Key") {
				key = strings.TrimSpace(val)
			}
		}
		h := sha1.Sum([]byte(key + wsGUID)) //nolint:gosec
		accept := base64.StdEncoding.EncodeToString(h[:])
		resp := "HTTP/1.1 101 Switching Protocols\r\n" +
			"Upgrade: websocket\r\nConnection: Upgrade\r\n" +
			"Sec-WebSocket-Accept: " + accept + "\r\n\r\n"
		if _, err := io.WriteString(conn, resp); err != nil {
			return
		}
		for _, m := range messages {
			if err := writeServerTextFrame(conn, []byte(m)); err != nil {
				return
			}
		}
		<-done // keep the connection open until the test finishes
	}()

	addr := ln.Addr().(*net.TCPAddr)
	url := fmt.Sprintf("ws://127.0.0.1:%d/mocksmtp/websocket", addr.Port)
	return url, func() { close(done); _ = ln.Close() }
}

// writeServerTextFrame writes a final, unmasked text frame (server→client).
func writeServerTextFrame(w io.Writer, payload []byte) error {
	var header []byte
	header = append(header, 0x80|opText)
	n := len(payload)
	switch {
	case n < 126:
		header = append(header, byte(n))
	case n < 1<<16:
		header = append(header, 126)
		var ext [2]byte
		binary.BigEndian.PutUint16(ext[:], uint16(n))
		header = append(header, ext[:]...)
	default:
		header = append(header, 127)
		var ext [8]byte
		binary.BigEndian.PutUint64(ext[:], uint64(n))
		header = append(header, ext[:]...)
	}
	if _, err := w.Write(header); err != nil {
		return err
	}
	_, err := w.Write(payload)
	return err
}

// A read deadline that lands between frames is safe to retry.
func TestReadMessageIdleTimeoutIsResumable(t *testing.T) {
	cli, srv := net.Pipe()
	defer cli.Close()
	defer srv.Close()

	rc := &conn{net: srv, br: bufio.NewReader(srv), idleTimeout: 50 * time.Millisecond, frameTimeout: 5 * time.Second}

	if _, err := rc.readMessage(); err == nil {
		t.Fatal("readMessage succeeded with nothing on the wire")
	} else if !isTimeout(err) {
		t.Fatalf("readMessage error = %v, want a retryable timeout", err)
	}

	go func() { _, _ = cli.Write(textFrame([]byte(`{"otp":"123456"}`))) }()

	got, err := rc.readMessage()
	if err != nil {
		t.Fatalf("readMessage after an idle timeout: %v", err)
	}
	if string(got) != `{"otp":"123456"}` {
		t.Errorf("payload = %q, want the frame written after the timeout", got)
	}
}

// A deadline that strikes partway through a frame is not retryable: the stream position is lost.
func TestReadMessageMidFrameTimeoutIsNotRetryable(t *testing.T) {
	cli, srv := net.Pipe()
	defer cli.Close()
	defer srv.Close()

	// A header promising 20 payload bytes, followed by only 5 of them.
	go func() { _, _ = cli.Write(append([]byte{0x81, 20}, []byte("hello")...)) }()

	rc := &conn{net: srv, br: bufio.NewReader(srv), idleTimeout: 5 * time.Second, frameTimeout: 100 * time.Millisecond}

	_, err := rc.readMessage()
	if err == nil {
		t.Fatal("readMessage succeeded on a truncated frame")
	}
	if isTimeout(err) {
		t.Fatalf("mid-frame error = %v, want it NOT to report as a timeout (the listener retries those)", err)
	}
	if !errors.Is(err, errStreamDesync) {
		t.Errorf("mid-frame error = %v, want errStreamDesync", err)
	}
}

// textFrame builds an unmasked server->client text frame, the shape the mock
// sends. Payloads here are small, so only the 7-bit length form is needed.
func textFrame(payload []byte) []byte {
	if len(payload) > 125 {
		panic("textFrame: test payloads must fit the 7-bit length form")
	}
	return append([]byte{0x81, byte(len(payload))}, payload...)
}

// One listener serves every module for the whole run, and each record holds a
// live OTP plus the recipient's phone/email. match never looks further back than
// the current flow start, so anything past the retention window is identity data
// kept for nothing.
func TestIngestPrunesRecordsPastRetention(t *testing.T) {
	base := time.Now()
	l := &Listener{}
	l.ingestAt(`{"text":"old 000000","to":{"value":[{"address":"me@x.io"}]}}`, base.Add(-2*retention))
	l.ingestAt(`{"text":"recent 111111","to":{"value":[{"address":"me@x.io"}]}}`, base.Add(-time.Minute))
	l.ingestAt(`{"text":"now 222222","to":{"value":[{"address":"me@x.io"}]}}`, base)

	if len(l.records) != 2 {
		t.Fatalf("records = %d, want the two inside the retention window", len(l.records))
	}
	for _, r := range l.records {
		if r.otp == "000000" {
			t.Error("the record past the retention window was kept")
		}
	}
	// Pruning must not disturb a match that is still in range.
	if got := l.match("me@x.io", base.Add(-5*time.Minute)); got != "222222" {
		t.Errorf("match = %q, want 222222", got)
	}
}

// The mock-SMTP server replays its recent history to every client that
// connects, so a batch of already-delivered messages lands the instant the
// listener starts. Timing them by arrival would make each look newer than the
// flow waiting on an OTP, and a stale code would be returned as if it were live.
func TestIngestUsesMessageDateNotArrival(t *testing.T) {
	l := &Listener{}
	now := time.Now()
	// Replayed on connect: sent 8 minutes ago, read just now.
	l.ingest([]byte(`{"type":"SMS","date":"`+now.Add(-8*time.Minute).UTC().Format(time.RFC3339Nano)+
		`","text":"OTP is 111111","to":{"text":"3449351160"}}`), now)
	flowStart := now.Add(-1 * time.Minute)
	if got := l.match("", flowStart); got != "" {
		t.Fatalf("replayed message matched as fresh: got OTP %q, want none", got)
	}
	// A genuinely live message, sent after the flow began.
	l.ingest([]byte(`{"type":"SMS","date":"`+now.UTC().Format(time.RFC3339Nano)+
		`","text":"OTP is 222222","to":{"text":"3449351160"}}`), now)
	if got := l.match("", flowStart); got != "222222" {
		t.Fatalf("live message: got %q, want 222222", got)
	}
	// No date at all: fall back to arrival time so behaviour is unchanged.
	l2 := &Listener{}
	l2.ingest([]byte(`{"text":"OTP is 333333","to":{"text":"x"}}`), now)
	if got := l2.match("", flowStart); got != "333333" {
		t.Fatalf("dateless message: got %q, want 333333 via arrival-time fallback", got)
	}
}
