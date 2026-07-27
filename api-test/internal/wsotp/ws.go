// Package wsotp reads one-time passwords from the MOSIP mock-SMTP server over a
// WebSocket, for the dynamic OTP source (mosipid sends a live OTP to email/SMS
// rather than accepting a static test value). It contains a minimal, read-only
// RFC 6455 client implemented on the standard library only, so the api-test
// module stays dependency-free (see the module's design note in the README).
//
// The mock relays every captured message as a JSON frame; the listener buffers
// them and WaitOTP returns the newest fresh 6-digit code for a recipient. Only
// the client behaviour needed here is implemented: the opening handshake, text
// frame reassembly, and automatic ping/pong + close handling. Client→server
// frames are masked as the RFC requires; server→client frames are never masked.
package wsotp

import (
	"bufio"
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
}

// NormalizeWSURL accepts either a full ws(s):// URL (used verbatim) or an
// http(s):// SMTP base (e.g. https://smtp.collab.mosip.net/) and derives the
// mock-SMTP WebSocket URL from it: scheme http→ws / https→wss, and the default
// path /mocksmtp/websocket when none is given. Returns an error for anything
// else.
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

// dial performs the opening handshake and returns a ready connection. tlsVerify
// toggles certificate verification for wss (dev environments use self-signed
// certs, so callers pass false there).
func dial(rawURL string, tlsVerify bool, timeout time.Duration) (*conn, error) {
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
		nc, err = tls.DialWithDialer(d, "tcp", host, &tls.Config{
			InsecureSkipVerify: !tlsVerify, //nolint:gosec // dev environments use self-signed certs; gated by tlsVerify
			ServerName:         u.Hostname(),
		})
	case "ws":
		nc, err = d.Dial("tcp", host)
	default:
		return nil, fmt.Errorf("unsupported scheme %q", u.Scheme)
	}
	if err != nil {
		return nil, fmt.Errorf("dial %s: %w", host, err)
	}

	c := &conn{net: nc, br: bufio.NewReader(nc)}
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

// readMessage returns the next application (text/binary) message, transparently
// answering pings and honoring close frames. It reassembles fragmented
// messages. A deadline set via SetReadDeadline surfaces as a timeout error.
func (c *conn) readMessage() ([]byte, error) {
	var (
		msg      []byte
		fragOp   int
		assembly bool
	)
	for {
		fin, op, payload, err := c.readFrame()
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
			if fin {
				return msg, nil
			}
			assembly = true
			fragOp = op
		case opContinuation:
			if !assembly {
				return nil, errors.New("websocket: unexpected continuation frame")
			}
			msg = append(msg, payload...)
			if fin {
				_ = fragOp
				return msg, nil
			}
		default:
			return nil, fmt.Errorf("websocket: unknown opcode 0x%x", op)
		}
	}
}

// readFrame reads a single frame header + payload. Server frames must not be
// masked; a masked server frame is a protocol error.
func (c *conn) readFrame() (fin bool, opcode int, payload []byte, err error) {
	var h [2]byte
	if _, err = io.ReadFull(c.br, h[:]); err != nil {
		return false, 0, nil, err
	}
	fin = h[0]&0x80 != 0
	opcode = int(h[0] & 0x0f)
	masked := h[1]&0x80 != 0
	length := uint64(h[1] & 0x7f)

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

	const maxFrame = 8 << 20 // 8 MiB guard — mock emails are tiny
	if length > maxFrame {
		return false, 0, nil, fmt.Errorf("websocket: frame too large (%d bytes)", length)
	}

	var maskKey [4]byte
	if masked {
		if _, err = io.ReadFull(c.br, maskKey[:]); err != nil {
			return false, 0, nil, err
		}
	}

	payload = make([]byte, length)
	if _, err = io.ReadFull(c.br, payload); err != nil {
		return false, 0, nil, err
	}
	if masked {
		for i := range payload {
			payload[i] ^= maskKey[i%4]
		}
	}
	return fin, opcode, payload, nil
}

// writeFrame writes a single, final client frame. Per RFC 6455 every
// client→server frame is masked.
func (c *conn) writeFrame(opcode int, payload []byte) error {
	var header []byte
	header = append(header, byte(0x80|opcode)) // FIN + opcode

	length := len(payload)
	switch {
	case length < 126:
		header = append(header, byte(0x80|length))
	case length < 1<<16:
		header = append(header, 0x80|126)
		var ext [2]byte
		binary.BigEndian.PutUint16(ext[:], uint16(length))
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

// setReadDeadline bounds the next readMessage call.
func (c *conn) setReadDeadline(t time.Time) { _ = c.net.SetReadDeadline(t) }
