/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package security

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/golang-jwt/jwt/v5"

	"github.com/mosip/esignet/internal/common"
	"github.com/mosip/esignet/internal/config"
	applog "github.com/mosip/esignet/internal/log"
)

// ScopeMiddleware returns an http.Handler middleware that:
//  1. Requires a Bearer token in the Authorization header.
//  2. Validates the token's signature using the JWKS cache.
//  3. Validates standard claims: iss, exp.
//  4. Checks that the token's scope claim contains requiredScope.
func ScopeMiddleware(cache *JWKSCache, config config.SecurityConfig) func(http.Handler) http.Handler {
	parser := jwt.NewParser(
		jwt.WithIssuer(config.IssuerURL),
		jwt.WithExpirationRequired(),
		jwt.WithValidMethods([]string{
			"RS256", "RS384", "RS512",
			"ES256", "ES384", "ES512",
			"PS256", "PS384", "PS512",
		}),
	)

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			tokenStr, err := bearerToken(r)
			if err != nil {
				logger.Debug("rejected request with missing/malformed Authorization header",
					applog.String("path", r.URL.Path), applog.Error(err))
				common.WriteError(w, http.StatusUnauthorized, "unauthorized", err.Error())
				return
			}

			claims, err := parseAndValidate(parser, tokenStr, cache)
			if err != nil {
				logger.Warn("rejected request with invalid or expired token",
					applog.String("path", r.URL.Path), applog.Error(err))
				common.WriteError(w, http.StatusUnauthorized, "unauthorized", "invalid or expired token")
				return
			}

			requiredScope, ok := resolveRequiredScope(config.ScopeMapping, r.Method, r.URL.Path)
			if !ok {
				logger.Warn("rejected request with no configured scope mapping",
					applog.String("path", r.URL.Path), applog.String("method", r.Method))
				common.WriteError(w, http.StatusForbidden, "forbidden", "no scope mapping configured for this endpoint")
				return
			}
			if !claimHasScope(claims, requiredScope) {
				logger.Warn("rejected request missing required scope",
					applog.String("path", r.URL.Path), applog.String("requiredScope", requiredScope))
				common.WriteError(w, http.StatusForbidden, "forbidden",
					fmt.Sprintf("token must carry scope %q", requiredScope))
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

// resolveRequiredScope returns the scope required for method and path, as
// configured in config.SecurityConfig.ScopeMapping. It reports false when no
// mapping entry matches, so callers can fail closed instead of falling back
// to an implicit default scope.
func resolveRequiredScope(mapping []config.AuthorizationConfig, method, path string) (string, bool) {
	for _, m := range mapping {
		if !strings.EqualFold(m.Method, method) {
			continue
		}
		if endpointMatches(m.Endpoint, path) {
			return m.Scope, true
		}
	}
	return "", false
}

// endpointMatches reports whether path matches the endpoint pattern.
// Pattern segments of the form "{name}" match any single path segment, the
// same placeholder syntax used to register routes on http.ServeMux.
func endpointMatches(pattern, path string) bool {
	patternSegs := strings.Split(strings.Trim(pattern, "/"), "/")
	pathSegs := strings.Split(strings.Trim(path, "/"), "/")
	if len(patternSegs) != len(pathSegs) {
		return false
	}
	for i, seg := range patternSegs {
		if strings.HasPrefix(seg, "{") && strings.HasSuffix(seg, "}") {
			continue
		}
		if seg != pathSegs[i] {
			return false
		}
	}
	return true
}

func bearerToken(r *http.Request) (string, error) {
	auth := r.Header.Get("Authorization")
	if auth == "" {
		return "", fmt.Errorf("missing Authorization header")
	}
	scheme, token, ok := strings.Cut(auth, " ")
	if !ok || !strings.EqualFold(scheme, "Bearer") || strings.TrimSpace(token) == "" {
		return "", fmt.Errorf("authorization header must be Bearer <token>")
	}
	return strings.TrimSpace(token), nil
}

func parseAndValidate(parser *jwt.Parser, tokenStr string, cache *JWKSCache) (jwt.MapClaims, error) {
	token, err := parser.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		kid, _ := t.Header["kid"].(string)
		if kid == "" {
			return nil, fmt.Errorf("token header missing kid")
		}
		return cache.GetKey(kid)
	})
	if err != nil {
		return nil, err
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok || !token.Valid {
		return nil, fmt.Errorf("invalid token claims")
	}
	return claims, nil
}

// claimHasScope checks whether the token's scope claim (space-separated string)
// contains the required scope value.
func claimHasScope(claims jwt.MapClaims, required string) bool {
	raw, ok := claims["scope"]
	if !ok {
		return false
	}
	// scope is a space-separated string per RFC 6749
	scope, ok := raw.(string)
	if !ok {
		return false
	}
	for _, s := range strings.Fields(scope) {
		if s == required {
			return true
		}
	}
	return false
}
