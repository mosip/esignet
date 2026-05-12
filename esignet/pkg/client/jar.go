package client

import (
	"net/url"
	"strings"
	"sync"

	http "github.com/aarock1234/fphttp"
)

// CookieJar stores cookies with domain-aware matching. Cookies set with
// a leading-dot domain (e.g. ".uber.com") match any subdomain.
type CookieJar struct {
	mu      sync.Mutex
	cookies map[string][]*http.Cookie // keyed by effective domain
}

func newCookieJar() *CookieJar {
	return &CookieJar{
		cookies: make(map[string][]*http.Cookie),
	}
}

// SetCookies stores cookies for the given URL. Each cookie is keyed by
// its Domain field when set, otherwise by the URL's host.
func (j *CookieJar) SetCookies(u *url.URL, cookies []*http.Cookie) {
	j.mu.Lock()
	defer j.mu.Unlock()

	for _, c := range cookies {
		domain := c.Domain
		if domain == "" {
			domain = u.Host
		}

		existing := j.cookies[domain]
		replaced := false
		for i, e := range existing {
			if e.Name == c.Name {
				existing[i] = c
				replaced = true

				break
			}
		}

		if !replaced {
			existing = append(existing, c)
		}

		j.cookies[domain] = existing
	}
}

// Cookies returns all cookies whose domain matches the given URL's host.
func (j *CookieJar) Cookies(u *url.URL) []*http.Cookie {
	j.mu.Lock()
	defer j.mu.Unlock()

	host := u.Host
	var result []*http.Cookie

	for domain, cookies := range j.cookies {
		if domainMatch(host, domain) {
			result = append(result, cookies...)
		}
	}

	return result
}

// domainMatch reports whether host matches the given cookie domain.
// A leading-dot domain (e.g. ".uber.com") matches any subdomain.
func domainMatch(host, domain string) bool {
	if host == domain {
		return true
	}

	if strings.HasPrefix(domain, ".") {
		return strings.HasSuffix(host, domain) || host == domain[1:]
	}

	return false
}
