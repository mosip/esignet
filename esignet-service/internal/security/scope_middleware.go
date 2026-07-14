package security

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/golang-jwt/jwt/v5"

	"github.com/mosip/esignet/internal/common"
	"github.com/mosip/esignet/internal/config"
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
				common.WriteError(w, http.StatusUnauthorized, "unauthorized", err.Error())
				return
			}

			claims, err := parseAndValidate(parser, tokenStr, cache)
			if err != nil {
				common.WriteError(w, http.StatusUnauthorized, "unauthorized", "invalid or expired token")
				return
			}

			// TODO do scope validation based on scope mapping in config.SecurityConfig.ScopeMapping
			if !claimHasScope(claims, "test") {
				common.WriteError(w, http.StatusForbidden, "forbidden",
					fmt.Sprintf("token must carry scope %q", "test"))
				return
			}

			next.ServeHTTP(w, r)
		})
	}
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
