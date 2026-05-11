package client

import (
	"bufio"
	"fmt"
	"net/url"
	"os"
	"slices"
	"strconv"
	"strings"
)

// Proxy holds a parsed proxy configuration.
type Proxy struct {
	Scheme   ProxyScheme
	Host     string
	Port     string
	Username string
	Password string
}

// URL returns the proxy as a *url.URL suitable for http.Transport.Proxy.
func (p *Proxy) URL() *url.URL {
	u := &url.URL{
		Scheme: string(p.Scheme),
		Host:   p.Host + ":" + p.Port,
	}

	if p.Username != "" && p.Password != "" {
		u.User = url.UserPassword(p.Username, p.Password)
	}

	return u
}

// String returns the proxy in "host:port" or "host:port:user:pass" format.
func (p *Proxy) String() string {
	s := p.Host + ":" + p.Port
	if p.Username != "" && p.Password != "" {
		s += ":" + p.Username + ":" + p.Password
	}

	return s
}

// ProxyScheme identifies the protocol used to connect through a proxy.
type ProxyScheme string

const (
	// ProxySchemeHTTP routes traffic through an HTTP CONNECT proxy.
	ProxySchemeHTTP ProxyScheme = "http"
	// ProxySchemeHTTPS routes traffic through an HTTPS CONNECT proxy.
	ProxySchemeHTTPS ProxyScheme = "https"
	// ProxySchemeSOCKS5 routes traffic through a SOCKS5 proxy with local DNS resolution.
	ProxySchemeSOCKS5 ProxyScheme = "socks5"
	// ProxySchemeSOCKS5H routes traffic through a SOCKS5 proxy with remote DNS resolution.
	ProxySchemeSOCKS5H ProxyScheme = "socks5h"
)

// String returns the scheme as its protocol string.
func (s ProxyScheme) String() string {
	return string(s)
}

// ParseProxyScheme converts a string to a ProxyScheme.
func ParseProxyScheme(scheme string) (ProxyScheme, error) {
	switch ProxyScheme(scheme) {
	case ProxySchemeHTTP:
		return ProxySchemeHTTP, nil
	case ProxySchemeHTTPS:
		return ProxySchemeHTTPS, nil
	case ProxySchemeSOCKS5:
		return ProxySchemeSOCKS5, nil
	case ProxySchemeSOCKS5H:
		return ProxySchemeSOCKS5H, nil
	}

	return "", fmt.Errorf("invalid proxy scheme: %s", scheme)
}

// ParseProxy parses a "host:port" or "host:port:user:pass" string into a Proxy.
// An empty string returns (nil, nil).
func ParseProxy(proxy string, scheme ProxyScheme) (*Proxy, error) {
	proxy = strings.TrimSpace(proxy)
	if proxy == "" {
		return nil, nil
	}

	split := strings.Split(proxy, ":")
	if len(split) != 2 && len(split) != 4 {
		return nil, fmt.Errorf("got %d proxy parts, want 2 or 4: %v", len(split), split)
	}

	if slices.Contains(split, "") {
		return nil, fmt.Errorf("got empty proxy part: %v", split)
	}

	if _, err := strconv.Atoi(split[1]); err != nil {
		return nil, fmt.Errorf("got invalid port %q, want numeric: %w", split[1], err)
	}

	p := &Proxy{
		Scheme: scheme,
		Host:   split[0],
		Port:   split[1],
	}

	if len(split) == 4 {
		p.Username = split[2]
		p.Password = split[3]
	}

	return p, nil
}

// ImportProxies reads and parses proxy configurations from a file.
func ImportProxies(filename string, scheme ProxyScheme) ([]*Proxy, error) {
	f, err := os.Open(filename)
	if err != nil {
		return nil, fmt.Errorf("opening proxy file: %w", err)
	}
	defer func() { _ = f.Close() }()

	var proxies []*Proxy
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		proxy, err := ParseProxy(line, scheme)
		if err != nil {
			return nil, fmt.Errorf("parsing proxy line %q: %w", line, err)
		}

		proxies = append(proxies, proxy)
	}

	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("scanning proxy file: %w", err)
	}

	return proxies, nil
}
