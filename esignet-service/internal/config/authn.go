// Package config loads embedder configuration from environment variables.
package config

// Authn provider identifiers.
const (
	AuthnProviderCatalog = "catalog"
	AuthnProviderMosip   = "mosip"
	defaultAuthnProvider = AuthnProviderCatalog
)

// Authn holds authentication provider selection and related settings.
type Authn struct {
	Provider string
	Mosip    MosipAuthn
}

// LoadAuthn reads authn provider settings from environment variables.
func LoadAuthn() Authn {
	return Authn{
		Provider: envOrDefault("THUNDERID_AUTHN_PROVIDER", defaultAuthnProvider),
		Mosip:    LoadMosipAuthn(),
	}
}
