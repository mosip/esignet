package client

import (
	http "github.com/aarock1234/fphttp"
	utls "github.com/refraction-networking/utls"
)

// Option configures a Client.
type Option func(*Client)

// WithProxy sets the proxy configuration for the client.
func WithProxy(p *Proxy) Option {
	return func(c *Client) { c.proxy = p }
}

// WithBrowser sets the browser identity for TLS and HTTP/2 fingerprinting.
func WithBrowser(b Browser) Option {
	return func(c *Client) { c.browser = b }
}

// WithBrowserVersion sets the browser version for default header generation.
func WithBrowserVersion(v string) Option {
	return func(c *Client) { c.browserVersion = v }
}

// WithPlatform sets the OS platform for TLS and HTTP/2 fingerprinting.
func WithPlatform(p Platform) Option {
	return func(c *Client) { c.platform = p }
}

// WithClientHelloID overrides the TLS ClientHelloID from the browser profile.
func WithClientHelloID(id utls.ClientHelloID) Option {
	return func(c *Client) { c.clientHelloID = &id }
}

// WithCookieExtractor replaces the default Set-Cookie extraction.
func WithCookieExtractor(fn CookieExtractor) Option {
	return func(c *Client) { c.extractCookies = fn }
}

// WithDefaultHeaderOverrides overrides browser profile default headers.
func WithDefaultHeaderOverrides(h http.Header) Option {
	return func(c *Client) { c.defaultHeaderOverrides = h.Clone() }
}

// WithDisableDecompression controls whether automatic response decompression is disabled.
func WithDisableDecompression(d bool) Option {
	return func(c *Client) { c.disableDecompression = d }
}

// WithInsecureSkipVerify controls whether TLS certificate verification is
// skipped. This should only be enabled for scraping targets or test
// environments where certificate validation is not possible.
func WithInsecureSkipVerify(skip bool) Option {
	return func(c *Client) { c.insecureSkipVerify = skip }
}
