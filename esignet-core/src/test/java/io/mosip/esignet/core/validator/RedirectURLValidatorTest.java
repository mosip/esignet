/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.validator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that redirect URLs with standard public TLDs are accepted.
 */
public class RedirectURLValidatorTest {

    private static final RedirectURLValidator REDIRECT_URL_VALIDATOR = new RedirectURLValidator();

    /**
     * Tests that valid URLs with standard IANA-registered TLDs are accepted.
     */
    @Test
    public void standardIanaRegisteredTldsTest() {
        // .com
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.com/callback", null));
        // .org
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.org/callback", null));
        // twoletter TLDs like .de
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.de/callback", null));
        // longer TLDs like .technology
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.technology/callback", null));
    }

    /**
     * Tests that valid URLs with non-IANA or custom TLDs are accepted.
     */
    @Test
    public void nonIanaOrCustomTldTest() {
        // .xx is not a real IANA TLD but must be accepted
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://api.dev.mosip.xx/home/test", null));
        // .internal is commonly used for private networks
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://myservice.internal/callback", null));
        // .local is used in mDNS / private environments
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://myapp.local/callback", null));
        // .test is an RFC 2606 reserved name for testing
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://app.test/callback", null));
        // private TLD used inside the MOSIP ecosystem
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://api.service.mosip/callback", null));
        // non IANA TLD .corp
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://auth.sso.corp/callback", null));
    }

    /**
     * Tests that valid URLs with IPv4 and IPv6 addresses, as well as localhost, are accepted.
     */
    @Test
    public void ipAddressV4V6Test() {
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("http://192.168.1.1/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("http://10.0.0.1:8080/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("http://localhost/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("http://localhost:8080/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("http://[::1]/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("http://[2001:db8::1]/callback", null));
      // invalid: only colons, no hex digits
      Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("http://[::::]/callback", null));
      // invalid: group exceeds four hex digits
      Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("http://[12345::1]/callback", null));
      // invalid: nine groups (too many)
      Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("http://[1:2:3:4:5:6:7:8:9]/callback", null));
      // invalid: non-hex character
      Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("http://[::gggg]/callback", null));
    }

    /**
     * Tests that valid URLs with ports are accepted and invalid ports are rejected.
     */
    @Test
    public void portsTest() {
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.com:443/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.com:8443/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.com:65535/callback", null));
        // first port value above the valid range must be rejected
      Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://example.com:65536/callback", null));
        // port 0 is accepted (enforcement is left to upper layers)
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.com:0/callback", null));
        // clearly out-of-range port must be rejected
      Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://example.com:99999/callback", null));
    }

    /**
     * Tests that valid URLs with various schemes are accepted.
     */
    @Test
    public void schemesTest() {
        // http
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("http://example.com/callback", null));
        // ftp
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("ftp://example.com/callback", null));
        // Mobile deep-link used by MOSIP resident app
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("io.mosip.residentapp://oauth", null));
        Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("myapp://auth.internal/callback", null));
    }

    /**
     * Tests that valid URLs with various path variations, query strings, and fragments are accepted.
     */
    @Test
    public void pathVariationsAndQueryStringsTest() {
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://api.dev.mosip.net/home/testament?rr=rrr", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid(
                "https://api.dev.mosip.net/home/werrrwqfdsfg5fgs34sdffggdfgsdfg?state=reefdf", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://example.com/page#section", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://a.b.c.example.com/callback", null));
      Assertions.assertTrue(REDIRECT_URL_VALIDATOR.isValid("https://api.dev.mosip.net/home/test", null));
    }

    /**
     * Tests that invalid URLs are rejected.
     */
    @Test
    public void invalidUrlTest() {
        // null
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid(null, null));
        // empty
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("", null));
        // A URL without a scheme is not valid
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("example.com/callback", null));
        // TLD must be at least two letters; single-char TLD must be rejected
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://example.c/callback", null));
        // TLD must consist of letters only, not digits
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://example.123/callback", null));
        // 256 is not a valid octet
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("http://256.0.0.1/callback", null));
        // space in host
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://exam ple.com/callback", null));
        // label must not start with a hyphen (RFC 1123)
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://-bad.example.com/callback", null));
        // label must not end with a hyphen (RFC 1123)
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://bad-.example.com/callback", null));
        // only scheme
        Assertions.assertFalse(REDIRECT_URL_VALIDATOR.isValid("https://", null));
    }
}