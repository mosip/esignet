// Package client provides an HTTP client with TLS fingerprinting, HTTP/2
// fingerprint control, custom cookie handling, and proxy support.
package client

import (
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/url"
	"strings"

	http "github.com/aarock1234/fphttp"
	utls "github.com/refraction-networking/utls"
)

const maxRedirects = 10

// ErrTooManyRedirects is returned when a request exceeds the maximum number
// of allowed redirects.
var ErrTooManyRedirects = errors.New("too many redirects")

// Client is an HTTP client with TLS fingerprinting, HTTP/2 fingerprint
// control, custom cookie handling, and proxy support.
type Client struct {
	http                   *http.Client
	jar                    *CookieJar
	extractCookies         CookieExtractor
	proxy                  *Proxy
	browser                Browser
	browserVersion         string
	platform               Platform
	clientHelloID          *utls.ClientHelloID
	defaultHeaderOverrides http.Header
	disableDecompression   bool
	insecureSkipVerify     bool
	defaultHeaders         http.Header
}

// New creates a new Client with the given options. Defaults to Chrome on
// Windows with TLS verification enabled.
func New(opts ...Option) (*Client, error) {
	c := &Client{
		browser:        BrowserChrome,
		browserVersion: defaultBrowserVersion,
		platform:       PlatformWindows,
		extractCookies: DefaultCookieExtractor,
	}

	for _, opt := range opts {
		opt(c)
	}

	fp := resolveFingerprint(c.browser, c.platform)

	if c.clientHelloID != nil {
		fp.ClientHelloID = *c.clientHelloID
	}

	c.defaultHeaders = resolveDefaultHeaders(c.browser, c.browserVersion, c.platform)

	if c.defaultHeaderOverrides != nil {
		for key, values := range c.defaultHeaderOverrides {
			c.defaultHeaders.Del(key)
			if len(values) == 0 || values[0] == "" {
				continue
			}

			c.defaultHeaders.Set(key, values[0])
		}
	}

	transport := &http.Transport{
		Fingerprint:     fp,
		TLSClientConfig: &tls.Config{InsecureSkipVerify: c.insecureSkipVerify},
	}

	if c.proxy != nil {
		transport.Proxy = http.ProxyURL(c.proxy.URL())
	}

	c.jar = newCookieJar()
	c.http = &http.Client{
		Transport: transport,
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}

	return c, nil
}

// Do sends an HTTP request and returns an HTTP response. Redirects are
// followed manually so that Set-Cookie headers from intermediate responses
// are captured and applied to subsequent requests.
func (c *Client) Do(req *http.Request) (*http.Response, error) {
	for range maxRedirects {
		c.applyDefaultHeaders(req)
		c.setRequestCookies(req)

		resp, err := c.doRoundTrip(req)
		if err != nil {
			return nil, err
		}

		if err := c.storeResponseCookies(resp); err != nil {
			_ = resp.Body.Close()

			return nil, err
		}

		if !c.disableDecompression && !resp.Uncompressed {
			if err := decompressResponse(resp); err != nil {
				slog.Warn("decompression failed", "error", err)
			}
		}

		method, ok := shouldRedirect(resp)
		if !ok {
			return resp, nil
		}

		_ = resp.Body.Close()

		next, err := redirectRequest(req, resp, method)
		if err != nil {
			return nil, err
		}

		req = next.WithContext(req.Context())
	}

	return nil, ErrTooManyRedirects
}

func (c *Client) doRoundTrip(req *http.Request) (*http.Response, error) {
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}

	slog.Debug("round trip complete",
		slog.String("host", req.URL.Hostname()),
		slog.Int("status", resp.StatusCode),
	)

	return resp, nil
}

func (c *Client) applyDefaultHeaders(req *http.Request) {
	for key, values := range c.defaultHeaders {
		if req.Header.Get(key) == "" {
			for _, v := range values {
				req.Header.Set(key, v)
			}
		}
	}
}

func (c *Client) setRequestCookies(req *http.Request) {
	if c.jar == nil {
		return
	}

	cookies := c.jar.Cookies(req.URL)
	if len(cookies) == 0 {
		return
	}

	var b strings.Builder
	for i, cookie := range cookies {
		if i > 0 {
			b.WriteString("; ")
		}
		b.WriteString(cookie.Name)
		b.WriteByte('=')
		b.WriteString(cookie.Value)
	}

	req.Header.Set("Cookie", b.String())
}

func (c *Client) storeResponseCookies(resp *http.Response) error {
	if c.jar == nil {
		return nil
	}

	cookies, err := c.extractCookies(resp)
	if err != nil {
		return fmt.Errorf("extracting cookies: %w", err)
	}

	c.jar.SetCookies(resp.Request.URL, cookies)

	return nil
}

// shouldRedirect returns (method, true) if the response is a redirect that
// should be followed, or ("", false) otherwise.
func shouldRedirect(resp *http.Response) (method string, ok bool) {
	switch resp.StatusCode {
	case http.StatusMovedPermanently, http.StatusFound, http.StatusSeeOther:
		return http.MethodGet, true
	case http.StatusTemporaryRedirect, http.StatusPermanentRedirect:
		return resp.Request.Method, true
	default:
		return "", false
	}
}

func redirectRequest(origin *http.Request, resp *http.Response, method string) (*http.Request, error) {
	location, err := resp.Location()
	if err != nil {
		return nil, fmt.Errorf("getting redirect location: %w", err)
	}

	if location.Scheme == "" || location.Host == "" {
		location = resp.Request.URL.ResolveReference(location)
	}

	if method == "" {
		method = http.MethodGet
	}

	var body io.ReadCloser
	if method == origin.Method && origin.Body != nil && origin.GetBody != nil {
		var err error
		body, err = origin.GetBody()
		if err != nil {
			return nil, fmt.Errorf("getting request body for redirect: %w", err)
		}
	}

	next, err := http.NewRequestWithContext(origin.Context(), method, location.String(), body)
	if err != nil {
		if body != nil {
			_ = body.Close()
		}

		return nil, fmt.Errorf("creating redirect request: %w", err)
	}

	for k, v := range origin.Header {
		if k != "Cookie" && k != "Host" {
			next.Header[k] = v
		}
	}

	return next, nil
}

// SetCookies stores cookies for the given URL in the client's jar.
func (c *Client) SetCookies(u *url.URL, cookies []*http.Cookie) {
	if c.jar == nil {
		return
	}

	c.jar.SetCookies(u, cookies)
}

// SetCookieString sets cookies with a specific domain for subdomain sharing.
// Domain should include leading dot (e.g., ".uber.com").
// Optional exclude parameter allows filtering out specific cookie names.
func (c *Client) SetCookieString(domain string, cookieString string, exclude ...string) error {
	if c.jar == nil {
		return nil
	}

	if cookieString == "" {
		return nil
	}

	excludeSet := make(map[string]struct{}, len(exclude))
	for _, name := range exclude {
		excludeSet[name] = struct{}{}
	}

	cookies := ParseCookies(cookieString)

	var filtered []*http.Cookie
	for _, cookie := range cookies {
		if _, ok := excludeSet[cookie.Name]; ok {
			continue
		}

		existingCookie, ok := c.FindCookie(cookie.Name, domain)
		if ok && existingCookie.Value == cookie.Value {
			continue
		}

		cookie.Domain = strings.TrimPrefix(domain, "www")
		filtered = append(filtered, cookie)
	}

	c.jar.SetCookies(&url.URL{
		Scheme: "https",
		Host:   domain,
	}, filtered)

	return nil
}

// FindCookie returns the first cookie matching name for the given domain.
func (c *Client) FindCookie(name string, domain string) (*http.Cookie, bool) {
	if c.jar == nil {
		return nil, false
	}

	for _, cookie := range c.GetCookies(domain) {
		if strings.EqualFold(cookie.Name, name) {
			return cookie, true
		}
	}

	return nil, false
}

// GetCookies returns cookies for the given domain.
func (c *Client) GetCookies(domain string) []*http.Cookie {
	if strings.HasPrefix(domain, ".") {
		domain = "www" + domain
	}

	return c.jar.Cookies(&url.URL{
		Scheme: "https",
		Host:   domain,
	})
}

// GetCookieString returns cookies for the given domain as a semicolon-separated string.
func (c *Client) GetCookieString(domain string) string {
	if c.jar == nil {
		return ""
	}

	cookies := c.GetCookies(domain)
	cookieStrings := make([]string, len(cookies))
	for i, cookie := range cookies {
		cookieStrings[i] = cookie.Name + "=" + cookie.Value
	}

	return strings.Join(cookieStrings, "; ")
}

// ClearCookies clears all cookies by creating a new jar.
func (c *Client) ClearCookies() {
	c.jar = newCookieJar()
}

// UserAgent returns the default User-Agent header.
func (c *Client) UserAgent() string {
	return c.defaultHeaders.Get("user-agent")
}

// ClientHint returns the default sec-ch-ua header.
func (c *Client) ClientHint() string {
	return c.defaultHeaders.Get("sec-ch-ua")
}

// Proxy returns the proxy configuration used by this client.
func (c *Client) Proxy() *Proxy {
	return c.proxy
}

// Close closes idle connections. Safe to call multiple times.
func (c *Client) Close() {
	c.http.CloseIdleConnections()
}
