package wsotp

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"regexp"
	"slices"
	"strings"
	"sync"
	"time"
)

// otpPattern matches an isolated 6-digit code, the same shape the MOSIP
// functional tests use (\b(\d{6})\b).
var otpPattern = regexp.MustCompile(`\b(\d{6})\b`)

// mailMessage is the subset of the mock-SMTP JSON frame we need: the body and the recipient.
type mailMessage struct {
	Text    string `json:"text"`
	HTML    string `json:"html"`
	Subject string `json:"subject"`
	// Date is when the deployment sent the message, as opposed to when this
	// listener happened to read it. The mock-SMTP server replays its recent
	// history to every client that connects, so a batch of old messages arrives
	// the moment the listener starts; timing them by arrival would make all of
	// them look newer than the flow that is waiting, and a stale code would be
	// handed to the OTP step as if it were live. Absent or unparseable, the
	// arrival time is used instead.
	Date string `json:"date"`
	To      struct {
		Text  string `json:"text"`
		Value []struct {
			Address string `json:"address"`
		} `json:"value"`
	} `json:"to"`
}

// record is one buffered message with the local time we received it, used for freshness.
type record struct {
	at   time.Time
	otp  string
	dest []string // normalized recipient identifiers (addresses + to.text)
}

// Listener maintains a live WebSocket to the mock-SMTP server, buffering received messages.
type Listener struct {
	wsURL     string
	tlsVerify bool

	mu      sync.Mutex
	records []record

	cancel    context.CancelFunc
	done      chan struct{}
	started   bool
	dialErr   error
	dbgFrames int // remaining raw frames to log when WSOTP_DEBUG is set
}

// NewListener builds a listener for the given SMTP base or ws(s) URL. The URL is
// normalized (see NormalizeWSURL); an invalid URL surfaces from Start.
func NewListener(smtpURL string, tlsVerify bool) *Listener {
	return &Listener{wsURL: smtpURL, tlsVerify: tlsVerify}
}

// Start dials the mock and begins buffering messages in the background.
func (l *Listener) Start(ctx context.Context) error {
	l.mu.Lock()
	if l.started {
		l.mu.Unlock()
		return nil
	}
	l.started = true
	l.mu.Unlock()

	if os.Getenv("WSOTP_DEBUG") != "" {
		l.dbgFrames = 3 // log the first few raw frames so the JSON shape can be eyeballed
	}

	wsURL, err := NormalizeWSURL(l.wsURL)
	if err != nil {
		return err
	}
	c, err := dial(ctx, wsURL, l.tlsVerify, 15*time.Second)
	if err != nil {
		return fmt.Errorf("connect mock-smtp websocket %s: %w", wsURL, err)
	}

	ctx, cancel := context.WithCancel(ctx)
	l.mu.Lock()
	l.cancel = cancel
	l.done = make(chan struct{})
	done := l.done
	l.mu.Unlock()

	go func() {
		defer close(done)
		defer func() { _ = c.close() }()
		for {
			select {
			case <-ctx.Done():
				return
			default:
			}
			// readMessage bounds its own reads, so a timeout it reports always happened between frames.
			data, err := c.readMessage()
			if err != nil {
				if isTimeout(err) {
					continue
				}
				l.mu.Lock()
				l.dialErr = err
				l.mu.Unlock()
				return
			}
			l.ingest(data, time.Now())
		}
	}()
	return nil
}

// Close stops the background reader and closes the connection.
func (l *Listener) Close() {
	l.mu.Lock()
	cancel, done := l.cancel, l.done
	l.mu.Unlock()
	if cancel != nil {
		cancel()
	}
	if done != nil {
		<-done
	}
}

// ingest parses one frame and buffers it if it carries a 6-digit OTP. Exported
// behaviour is tested through this: it takes the raw JSON and a receive time.
func (l *Listener) ingest(data []byte, at time.Time) {
	l.mu.Lock()
	if l.dbgFrames > 0 {
		l.dbgFrames--
		fmt.Fprintf(os.Stderr, "[wsotp-debug] frame (%d bytes): %s\n", len(data), maskFrame(data))
	}
	l.mu.Unlock()

	var m mailMessage
	if err := json.Unmarshal(data, &m); err != nil {
		return // non-JSON / heartbeat frames are ignored
	}
	otp := extractOTP(m)
	if otp == "" {
		return
	}
	l.mu.Lock()
	l.records = append(l.records, record{at: sentAt(m, at), otp: otp, dest: recipients(m)})
	l.pruneLocked(at)
	l.mu.Unlock()
}

// sentAt is when the deployment sent the message, falling back to when this
// listener read it. See mailMessage.Date for why the distinction matters.
func sentAt(m mailMessage, fallback time.Time) time.Time {
	d := strings.TrimSpace(m.Date)
	if d == "" {
		return fallback
	}
	for _, layout := range []string{time.RFC3339Nano, time.RFC3339, time.RFC1123Z, time.RFC1123} {
		if t, err := time.Parse(layout, d); err == nil {
			return t
		}
	}
	return fallback
}

// retention bounds the buffer. One listener serves every module for the whole
// run, and match never reads a record older than the current flow start, so
// anything past this window is live OTPs and recipient identifiers held in
// memory for nothing.
const retention = 10 * time.Minute

// pruneLocked drops records too old to satisfy any future match. Caller holds l.mu.
func (l *Listener) pruneLocked(now time.Time) {
	cutoff := now.Add(-retention)
	kept := l.records[:0]
	for _, r := range l.records {
		if r.at.After(cutoff) {
			kept = append(kept, r)
		}
	}
	l.records = kept
}

// maskFrame renders a frame for WSOTP_DEBUG with the OTP digits removed. The
// flag exists to eyeball the JSON shape, which survives the mask, whereas a CI
// job with it set would otherwise retain live OTPs in its build log. The
// recipient is left readable on purpose: diagnosing why a message did not match
// a recipient is the other half of what the flag is for.
func maskFrame(data []byte) string {
	s := otpPattern.ReplaceAllString(string(data), "******")
	const n = 400
	if len(s) > n {
		return s[:n] + "…"
	}
	return s
}

// WaitOTP returns the newest OTP received at or after `since` for `recipient`, polling until `timeout`.
func (l *Listener) WaitOTP(recipient string, since time.Time, timeout, poll time.Duration) (string, error) {
	if poll <= 0 {
		poll = 500 * time.Millisecond
	}
	deadline := time.Now().Add(timeout)
	want := normalize(recipient)
	for {
		if otp := l.match(want, since); otp != "" {
			return otp, nil
		}
		l.mu.Lock()
		derr := l.dialErr
		l.mu.Unlock()
		if derr != nil {
			return "", fmt.Errorf("mock-smtp websocket closed before OTP arrived: %w", derr)
		}
		if time.Now().After(deadline) {
			who := recipient
			if who == "" {
				who = "(any recipient)"
			}
			return "", fmt.Errorf("no OTP for %s within %s", who, timeout)
		}
		time.Sleep(poll)
	}
}

// match returns the newest buffered OTP for want (or any, if want is empty)
// received at/after since.
func (l *Listener) match(want string, since time.Time) string {
	l.mu.Lock()
	defer l.mu.Unlock()
	var (
		best   string
		bestAt time.Time
	)
	for _, r := range l.records {
		if r.at.Before(since) {
			continue
		}
		if want != "" && !containsRecipient(r.dest, want) {
			continue
		}
		if r.at.After(bestAt) || best == "" {
			best, bestAt = r.otp, r.at
		}
	}
	return best
}

// extractOTP pulls the first 6-digit code from the message body (text first,
// then html).
func extractOTP(m mailMessage) string {
	for _, body := range []string{m.Text, m.HTML} {
		if match := otpPattern.FindStringSubmatch(body); match != nil {
			return match[1]
		}
	}
	return ""
}

// recipients returns the normalized recipient identifiers of a message: every
// to.value[].address plus to.text (the SMS/phone form).
func recipients(m mailMessage) []string {
	var out []string
	if t := normalize(m.To.Text); t != "" {
		out = append(out, t)
	}
	for _, v := range m.To.Value {
		if a := normalize(v.Address); a != "" {
			out = append(out, a)
		}
	}
	return out
}

func containsRecipient(dests []string, want string) bool {
	return slices.Contains(dests, want)
}

func normalize(s string) string { return strings.ToLower(strings.TrimSpace(s)) }

func isTimeout(err error) bool {
	var t interface{ Timeout() bool }
	if errors.As(err, &t) {
		return t.Timeout()
	}
	return false
}
