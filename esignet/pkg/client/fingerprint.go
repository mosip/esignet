package client

import (
	"fmt"
	"strconv"
	"strings"

	http "github.com/aarock1234/fphttp"
	utls "github.com/refraction-networking/utls"
)

// Browser identifies the browser for TLS and HTTP/2 fingerprinting.
type Browser string

const (
	// BrowserChrome identifies Google Chrome.
	BrowserChrome Browser = "chrome"
	// BrowserEdge identifies Microsoft Edge.
	BrowserEdge Browser = "edge"
	// BrowserBrave identifies the Brave browser.
	BrowserBrave Browser = "brave"
	// BrowserSafari identifies Apple Safari.
	BrowserSafari Browser = "safari"
	// BrowserFirefox identifies Mozilla Firefox.
	BrowserFirefox Browser = "firefox"
)

// Platform identifies the OS platform for TLS and HTTP/2 fingerprinting.
type Platform string

const (
	// PlatformWindows targets the Windows operating system.
	PlatformWindows Platform = "windows"
	// PlatformMac targets macOS.
	PlatformMac Platform = "mac"
	// PlatformLinux targets Linux.
	PlatformLinux Platform = "linux"
	// PlatformIOS targets iOS.
	PlatformIOS Platform = "ios"
	// PlatformIPadOS targets iPadOS.
	PlatformIPadOS Platform = "ipados"
)

const defaultBrowserVersion = "131.0.0.0"

// resolveFingerprint returns an fphttp Fingerprint for the given browser
// and platform combination.
func resolveFingerprint(browser Browser, platform Platform) *http.Fingerprint {
	switch browser {
	case BrowserSafari:
		fp := http.Safari()
		if platform == PlatformIOS || platform == PlatformIPadOS {
			fp.ClientHelloID = utls.HelloIOS_Auto
		}

		return fp
	case BrowserFirefox:
		return http.Firefox()
	default:
		return http.Chrome()
	}
}

// resolveDefaultHeaders returns default request headers for the given
// browser, version, and platform combination.
func resolveDefaultHeaders(browser Browser, version string, platform Platform) http.Header {
	switch browser {
	case BrowserSafari:
		return safariHeaders(version, platform)
	case BrowserFirefox:
		return firefoxHeaders(version, platform)
	default:
		return chromiumHeaders(browser, version, platform)
	}
}

func chromiumHeaders(browser Browser, version string, platform Platform) http.Header {
	major := parseMajor(version)

	var ua string
	switch platform {
	case PlatformMac:
		ua = fmt.Sprintf("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36", version)
	case PlatformLinux:
		ua = fmt.Sprintf("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36", version)
	default:
		ua = fmt.Sprintf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Safari/537.36", version)
	}

	if browser == BrowserEdge {
		ua += fmt.Sprintf(" Edg/%s", version)
	}

	var brandName string
	switch browser {
	case BrowserEdge:
		brandName = "Microsoft Edge"
	case BrowserBrave:
		brandName = "Brave"
	default:
		brandName = "Google Chrome"
	}

	secChUA := fmt.Sprintf(`"Chromium";v="%d", "%s";v="%d", "Not/A)Brand";v="8"`, major, brandName, major)

	var secChUAPlatform string
	switch platform {
	case PlatformMac:
		secChUAPlatform = `"macOS"`
	case PlatformLinux:
		secChUAPlatform = `"Linux"`
	default:
		secChUAPlatform = `"Windows"`
	}

	headers := http.Header{}
	headers.Set("user-agent", ua)
	headers.Set("sec-ch-ua", secChUA)
	headers.Set("sec-ch-ua-mobile", "?0")
	headers.Set("sec-ch-ua-platform", secChUAPlatform)

	return headers
}

func safariHeaders(version string, platform Platform) http.Header {
	var ua string
	switch platform {
	case PlatformIOS, PlatformIPadOS:
		major := parseMajor(version)
		minor := parseMinor(version)
		ua = fmt.Sprintf("Mozilla/5.0 (iPhone; CPU iPhone OS %d_%d like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/%s Mobile/15E148 Safari/604.1", major, minor, version)
	default:
		ua = fmt.Sprintf("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/%s Safari/605.1.15", version)
	}

	headers := http.Header{}
	headers.Set("user-agent", ua)

	return headers
}

func firefoxHeaders(version string, platform Platform) http.Header {
	var ua string
	switch platform {
	case PlatformMac:
		ua = fmt.Sprintf("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:%s) Gecko/20100101 Firefox/%s", version, version)
	case PlatformLinux:
		ua = fmt.Sprintf("Mozilla/5.0 (X11; Linux x86_64; rv:%s) Gecko/20100101 Firefox/%s", version, version)
	default:
		ua = fmt.Sprintf("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:%s) Gecko/20100101 Firefox/%s", version, version)
	}

	headers := http.Header{}
	headers.Set("user-agent", ua)

	return headers
}

// parseMajor returns the major version number from a dotted version string.
func parseMajor(version string) int {
	parts := strings.SplitN(version, ".", 2)
	n, _ := strconv.Atoi(parts[0])

	return n
}

// parseMinor returns the minor version number from a dotted version string.
func parseMinor(version string) int {
	parts := strings.SplitN(version, ".", 3)
	if len(parts) < 2 {
		return 0
	}

	n, _ := strconv.Atoi(parts[1])

	return n
}
