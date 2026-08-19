// Package wsotp reads one-time passwords from the MOSIP mock-SMTP server over a WebSocket.
package wsotp

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/sha1"
	"crypto/tls"
	"encoding/base64"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"net/url"
	"strings"
	"time"
)

// wsGUID is the RFC 6455 magic value appended to Sec-WebSocket-Key to compute
// the expected Sec-WebSocket-Accept.
const wsGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

const (
	opContinuation = 0x0
	opText         = 0x1
	opBinary       = 0x2
	opClose        = 0x8
	opPing         = 0x9
	opPong         = 0xA
)

// conn is a minimal client-side WebSocket connection.
type conn struct {
	net net.Conn
	br  *bufio.Reader

	idleTimeout  time.Duration
	frameTimeout time.Duration
}

// errStreamDesync marks a read that stopped partway through a frame.
var errStreamDesync = errors.New("websocket: read interrupted mid-frame; stream position lost")

// NormalizeWSURL accepts a ws(s):// URL verbatim or derives one from an http(s):// SMTP base.
func NormalizeWSURL(raw string) (string, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", errors.New("empty ws url")
	}
	u, err := url.Parse(raw)
	if err != nil {
		return "", fmt.Errorf("parse ws url %q: %w", raw, err)
	}
	switch u.Scheme {
	case "ws", "wss":
		// already a websocket URL — use as given
	case "http":
		u.Scheme = "ws"
	case "https":
		u.Scheme = "wss"
	default:
		return "", fmt.Errorf("ws url %q: unsupported scheme %q (want ws|wss|http|https)", raw, u.Scheme)
	}
	if u.Path == "" || u.Path == "/" {
		u.Path = "/mocksmtp/websocket"
	}
	return u.String(), nil
}

// dial performs the opening handshake and returns a ready connection.
func dial(ctx context.Context, rawURL string, tlsVerify bool, timeout time.Duration) (*conn, error) {
	u, err := url.Parse(rawURL)
	if err != nil {
		return nil, fmt.Errorf("parse %q: %w", rawURL, err)
	}
	host := u.Host
	if u.Port() == "" {
		if u.Scheme == "wss" {
			host = net.JoinHostPort(host, "443")
		} else {
			host = net.JoinHostPort(host, "80")
		}
	}

	d := &net.Dialer{Timeout: timeout}
	var nc net.Conn
	switch u.Scheme {
	case "wss":
		td := &tls.Dialer{NetDialer: d, Config: &tls.Config{
			MinVersion:         tls.VersionTLS12,
			InsecureSkipVerify: !tlsVerify, //nolint:gosec // dev environments use self-signed certs; gated by tlsVerify
			ServerName:         u.Hostname(),
		}}
		nc, err = td.DialContext(ctx, "tcp", host)
	case "ws":
		nc, err = d.DialContext(ctx, "tcp", host)
	default:
		return nil, fmt.Errorf("unsupported scheme %q", u.Scheme)
	}
	if err != nil {
		return nil, fmt.Errorf("dial %s: %w", host, err)
	}

	c := &conn{
		net: nc,
		br:  bufio.NewReader(nc),
		// Short idle wait so the listener notices context cancellation promptly;
		// generous in-frame wait so only a genuinely stalled peer trips it.
		idleTimeout:  2 * time.Second,
		frameTimeout: 30 * time.Second,
	}
	if err := c.handshake(u, timeout); err != nil {
		_ = nc.Close()
		return nil, err
	}
	return c, nil
}

// handshake sends the client Upgrade request and validates the 101 response,
// including the Sec-WebSocket-Accept hash.
func (c *conn) handshake(u *url.URL, timeout time.Duration) error {
	keyRaw := make([]byte, 16)
	if _, err := rand.Read(keyRaw); err != nil {
		return err
	}
	key := base64.StdEncoding.EncodeToString(keyRaw)

	path := u.RequestURI()
	if path == "" {
		path = "/"
	}
	req := "GET " + path + " HTTP/1.1\r\n" +
		"Host: " + u.Host + "\r\n" +
		"Upgrade: websocket\r\n" +
		"Connection: Upgrade\r\n" +
		"Sec-WebSocket-Key: " + key + "\r\n" +
		"Sec-WebSocket-Version: 13\r\n\r\n"

	if timeout > 0 {
		_ = c.net.SetWriteDeadline(time.Now().Add(timeout))
		_ = c.net.SetReadDeadline(time.Now().Add(timeout))
	}
	if _, err := io.WriteString(c.net, req); err != nil {
		return fmt.Errorf("write handshake: %w", err)
	}

	statusLine, err := c.br.ReadString('\n')
	if err != nil {
		return fmt.Errorf("read handshake status: %w", err)
	}
	if !strings.Contains(statusLine, " 101") {
		return fmt.Errorf("websocket handshake: expected 101, got %q", strings.TrimSpace(statusLine))
	}

	var accept string
	for {
		line, err := c.br.ReadString('\n')
		if err != nil {
			return fmt.Errorf("read handshake headers: %w", err)
		}
		line = strings.TrimRight(line, "\r\n")
		if line == "" {
			break // end of headers
		}
		name, val, ok := strings.Cut(line, ":")
		if ok && strings.EqualFold(strings.TrimSpace(name), "Sec-WebSocket-Accept") {
			accept = strings.TrimSpace(val)
		}
	}

	h := sha1.Sum([]byte(key + wsGUID)) //nolint:gosec // SHA-1 is mandated by RFC 6455 for this handshake, not for security
	want := base64.StdEncoding.EncodeToString(h[:])
	if accept != want {
		return fmt.Errorf("websocket handshake: bad Sec-WebSocket-Accept (got %q want %q)", accept, want)
	}
	_ = c.net.SetDeadline(time.Time{}) // clear handshake deadlines
	return nil
}

// readMessage returns the next application message, answering pings and honoring close frames.
func (c *conn) readMessage() ([]byte, error) {
	var (
		msg      []byte
		assembly bool
	)
	for {
		// Only the first frame of a message may time out resumably.
		fin, op, payload, err := c.readFrame(!assembly)
		if err != nil {
			return nil, err
		}
		switch op {
		case opPing:
			if err := c.writeFrame(opPong, payload); err != nil {
				return nil, err
			}
			continue
		case opPong:
			continue
		case opClose:
			_ = c.writeFrame(opClose, nil)
			return nil, io.EOF
		case opText, opBinary:
			if assembly {
				return nil, errors.New("websocket: new data frame before continuation finished")
			}
			msg = append(msg, payload...)
			if len(msg) > maxFrame {
				return nil, fmt.Errorf("websocket: reassembled message too large (%d bytes)", len(msg))
			}
			if fin {
				return msg, nil
			}
			assembly = true
		case opContinuation:
			if !assembly {
				return nil, errors.New("websocket: unexpected continuation frame")
			}
			msg = append(msg, payload...)
			if len(msg) > maxFrame {
				return nil, fmt.Errorf("websocket: reassembled message too large (%d bytes)", len(msg))
			}
			if fin {
				return msg, nil
			}
		default:
			return nil, fmt.Errorf("websocket: unknown opcode 0x%x", op)
		}
	}
}

// maxFrame caps both a single frame and a reassembled message, bounding a peer that never sets FIN.
const maxFrame = 8 << 20

// setReadDeadline bounds the next read by d, or clears the deadline when d is
// not positive — which is how a directly-constructed conn (tests) opts out.
func (c *conn) setReadDeadline(d time.Duration) {
	if d <= 0 {
		_ = c.net.SetReadDeadline(time.Time{})
		return
	}
	_ = c.net.SetReadDeadline(time.Now().Add(d))
}

// readFrame reads a single frame header and payload.
func (c *conn) readFrame(resumable bool) (fin bool, opcode int, payload []byte, err error) {
	// Wait for the frame to start. Nothing has been consumed yet, so a timeout
	// here leaves the stream exactly where it was.
	if resumable {
		c.setReadDeadline(c.idleTimeout)
	} else {
		c.setReadDeadline(c.frameTimeout)
	}
	if _, err = c.br.Peek(1); err != nil {
		if resumable {
			return false, 0, nil, err
		}
		// %v, not %w, for the transport error: isTimeout walks the chain with
		// errors.As, and exposing the net.Error would make a mid-frame desync
		// look retryable.
		return false, 0, nil, fmt.Errorf("%w: %v", errStreamDesync, err) //nolint:errorlint // see above: the transport error stays opaque to errors.As
	}

	// Committed: read the whole frame or lose stream alignment.
	c.setReadDeadline(c.frameTimeout)
	defer func() {
		if err != nil {
			err = fmt.Errorf("%w: %v", errStreamDesync, err) //nolint:errorlint // %v keeps the transport error opaque to isTimeout's errors.As
		}
	}()

	var h [2]byte
	if _, err = io.ReadFull(c.br, h[:]); err != nil {
		return false, 0, nil, err
	}
	fin = h[0]&0x80 != 0
	opcode = int(h[0] & 0x0f)
	masked := h[1]&0x80 != 0
	length := uint64(h[1] & 0x7f)
	if masked {
		// RFC 6455 §5.1: a server MUST NOT mask its frames.
		return false, 0, nil, errors.New("websocket: server frame is masked")
	}

	switch length {
	case 126:
		var ext [2]byte
		if _, err = io.ReadFull(c.br, ext[:]); err != nil {
			return false, 0, nil, err
		}
		length = uint64(binary.BigEndian.Uint16(ext[:]))
	case 127:
		var ext [8]byte
		if _, err = io.ReadFull(c.br, ext[:]); err != nil {
			return false, 0, nil, err
		}
		length = binary.BigEndian.Uint64(ext[:])
	}

	if length > maxFrame {
		return false, 0, nil, fmt.Errorf("websocket: frame too large (%d bytes)", length)
	}

	payload = make([]byte, length)
	if _, err = io.ReadFull(c.br, payload); err != nil {
		return false, 0, nil, err
	}
	return fin, opcode, payload, nil
}

// writeFrame writes a single, final client frame. Per RFC 6455 every
// client→server frame is masked.
func (c *conn) writeFrame(opcode int, payload []byte) error {
	// The handshake clears every deadline, so bound each write here or close() can block forever.
	if c.frameTimeout > 0 {
		_ = c.net.SetWriteDeadline(time.Now().Add(c.frameTimeout))
		defer func() { _ = c.net.SetWriteDeadline(time.Time{}) }()
	}
	var header []byte
	// opcode is always one of the package opXxx constants (<=0xA), so this fits
	// a byte; each length write below is bounded by its case guard.
	header = append(header, byte(0x80|opcode)) //nolint:gosec // bounded by the opXxx constants

	length := len(payload)
	switch {
	case length < 126:
		header = append(header, byte(0x80|length)) //nolint:gosec // bounded by the case guard
	case length < 1<<16:
		header = append(header, 0x80|126)
		var ext [2]byte
		binary.BigEndian.PutUint16(ext[:], uint16(length)) //nolint:gosec // bounded by the case guard
		header = append(header, ext[:]...)
	default:
		header = append(header, 0x80|127)
		var ext [8]byte
		binary.BigEndian.PutUint64(ext[:], uint64(length))
		header = append(header, ext[:]...)
	}

	var maskKey [4]byte
	if _, err := rand.Read(maskKey[:]); err != nil {
		return err
	}
	header = append(header, maskKey[:]...)

	masked := make([]byte, length)
	for i := range payload {
		masked[i] = payload[i] ^ maskKey[i%4]
	}

	if _, err := c.net.Write(header); err != nil {
		return err
	}
	_, err := c.net.Write(masked)
	return err
}

// close sends a close frame (best effort) and closes the transport.
func (c *conn) close() error {
	_ = c.writeFrame(opClose, nil)
	return c.net.Close()
}
