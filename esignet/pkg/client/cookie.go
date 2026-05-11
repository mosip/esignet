package client

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"

	http "github.com/aarock1234/fphttp"
)

// CookieExtractor extracts cookies from an HTTP response.
// Called after each round trip, including intermediate redirects.
// The returned cookies are stored in the client's jar.
type CookieExtractor func(resp *http.Response) ([]*http.Cookie, error)

// DefaultCookieExtractor parses standard Set-Cookie response headers using
// relaxed parsing that allows double-quote characters in cookie values.
func DefaultCookieExtractor(resp *http.Response) ([]*http.Cookie, error) {
	var cookies []*http.Cookie
	for _, raw := range resp.Header.Values("Set-Cookie") {
		parsed, err := ParseSetCookie(raw)
		if err != nil {
			return nil, fmt.Errorf("parsing set-cookie: %w", err)
		}

		cookies = append(cookies, parsed)
	}

	return cookies, nil
}

// ParseCookies parses a raw cookie string (name=value; name2=value2) into
// an http.Cookie slice. JSON array format is also supported.
func ParseCookies(cookieStr string) []*http.Cookie {
	cookieStr = strings.TrimSpace(cookieStr)

	if strings.HasPrefix(cookieStr, "[") {
		cookies, err := parseJSONCookies(cookieStr)
		if err != nil {
			return nil
		}

		return cookies
	}

	var cookies []*http.Cookie
	for cookie := range strings.SplitSeq(cookieStr, ";") {
		cookie = strings.TrimSpace(cookie)
		if cookie == "" {
			continue
		}

		parts := strings.SplitN(cookie, "=", 2)
		if len(parts) != 2 {
			continue
		}

		cookies = append(cookies, &http.Cookie{
			Name:  parts[0],
			Value: parts[1],
		})
	}

	return cookies
}

// ParseSetCookie parses a single Set-Cookie header line using relaxed
// validation that allows double-quote characters in cookie values.
func ParseSetCookie(line string) (*http.Cookie, error) {
	var (
		cookie http.Cookie
		first  = true
	)

	for part := range strings.SplitSeq(line, ";") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}

		if first {
			first = false
			eq := strings.IndexByte(part, '=')
			if eq < 0 {
				return nil, fmt.Errorf("missing = in cookie name-value pair: %q", part)
			}

			cookie.Name = part[:eq]
			value := part[eq+1:]

			// Strip surrounding quotes and mark as quoted.
			if len(value) >= 2 && value[0] == '"' && value[len(value)-1] == '"' {
				value = value[1 : len(value)-1]
				cookie.Quoted = true
			}

			// Validate value bytes with relaxed rules.
			for i := range len(value) {
				if !validCookieValueByte(value[i]) {
					return nil, fmt.Errorf("invalid byte %#x in cookie value", value[i])
				}
			}

			cookie.Value = value

			continue
		}

		lowerPart := strings.ToLower(part)

		if lowerPart == "secure" {
			cookie.Secure = true

			continue
		}

		if lowerPart == "httponly" {
			cookie.HttpOnly = true

			continue
		}

		if lowerPart == "partitioned" {
			cookie.Partitioned = true

			continue
		}

		eq := strings.IndexByte(part, '=')
		if eq < 0 {
			continue
		}

		attrName := strings.ToLower(strings.TrimSpace(part[:eq]))
		attrVal := strings.TrimSpace(part[eq+1:])

		switch attrName {
		case "domain":
			cookie.Domain = attrVal
		case "path":
			cookie.Path = attrVal
		case "expires":
			t, err := time.Parse(time.RFC1123, attrVal)
			if err != nil {
				t, err = time.Parse("Mon, 02-Jan-2006 15:04:05 MST", attrVal)
				if err != nil {
					continue
				}
			}
			cookie.Expires = t
			cookie.RawExpires = attrVal
		case "max-age":
			n, err := strconv.Atoi(attrVal)
			if err != nil {
				continue
			}
			cookie.MaxAge = n
		case "samesite":
			switch strings.ToLower(attrVal) {
			case "lax":
				cookie.SameSite = http.SameSiteLaxMode
			case "strict":
				cookie.SameSite = http.SameSiteStrictMode
			case "none":
				cookie.SameSite = http.SameSiteNoneMode
			}
		}
	}

	return &cookie, nil
}

// GetCookieByName finds a cookie by name from a cookie string.
func GetCookieByName(cookieStr, name string) *http.Cookie {
	for _, cookie := range ParseCookies(cookieStr) {
		if cookie.Name == name {
			return cookie
		}
	}

	return nil
}

// validCookieValueByte reports whether b is a valid byte in a cookie value.
// This is a relaxed version that allows double-quote characters.
func validCookieValueByte(b byte) bool {
	return 0x20 <= b && b < 0x7f && b != ';' && b != '\\'
}

func parseJSONCookies(cookieStr string) ([]*http.Cookie, error) {
	var serialized []*http.Cookie
	if err := json.Unmarshal([]byte(cookieStr), &serialized); err != nil {
		return nil, fmt.Errorf("parsing cookies json: %w", err)
	}

	return serialized, nil
}
