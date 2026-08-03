package cryptomanager

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"fmt"

	jose "github.com/go-jose/go-jose/v4"
)

// jweBuildOptions carries JWTEncrypt's per-request header flags through to
// buildJWECompact.
type jweBuildOptions struct {
	compress           bool
	includeCertificate bool
	includeCertHash    bool
	jwkSetURL          string
}

// buildJWECompact builds a compact-serialized JWE: alg=RSA-OAEP-256,
// enc=A256GCM, cty=JWT, typ=JWT, kid=base64url(SHA-256 cert thumbprint) —
// mirrors Java's jwtRsaOaep256AesGcmEncrypt (CryptomanagerServiceImpl).
func buildJWECompact(cert *x509.Certificate, payload []byte, opts jweBuildOptions) (string, error) {
	rsaPub, ok := cert.PublicKey.(*rsa.PublicKey)
	if !ok {
		return "", fmt.Errorf("certificate public key is not RSA")
	}
	thumb := thumbprintRaw(cert)
	kid := base64.RawURLEncoding.EncodeToString(thumb[:])

	encOpts := (&jose.EncrypterOptions{}).WithContentType("JWT").WithType("JWT")
	if opts.compress {
		encOpts.Compression = jose.DEFLATE
	}
	if opts.includeCertificate {
		encOpts = encOpts.WithHeader("x5c", []string{base64.StdEncoding.EncodeToString(cert.Raw)})
	}
	if opts.includeCertHash {
		encOpts = encOpts.WithHeader("x5t#S256", kid)
	}
	if opts.jwkSetURL != "" {
		encOpts = encOpts.WithHeader("jku", opts.jwkSetURL)
	}

	rcpt := jose.Recipient{Algorithm: jose.RSA_OAEP_256, Key: rsaPub, KeyID: kid}
	encrypter, err := jose.NewEncrypter(jose.A256GCM, rcpt, encOpts)
	if err != nil {
		return "", fmt.Errorf("%w: %w", ErrJWEEncryptionFailed, err)
	}
	jwe, err := encrypter.Encrypt(payload)
	if err != nil {
		return "", fmt.Errorf("%w: %w", ErrJWEEncryptionFailed, err)
	}
	compact, err := jwe.CompactSerialize()
	if err != nil {
		return "", fmt.Errorf("%w: %w", ErrJWEEncryptionFailed, err)
	}
	return compact, nil
}

// parseJWECompact parses a compact JWE and extracts the certificate
// thumbprint (lower-case hex, matching what resolveDecryptionKey expects)
// from its kid header — mirrors Java's jwtDecrypt kid extraction:
// base64url-decode, then hex-encode.
func parseJWECompact(token string) (*jose.JSONWebEncryption, string, error) {
	jwe, err := jose.ParseEncryptedCompact(token, []jose.KeyAlgorithm{jose.RSA_OAEP_256}, []jose.ContentEncryption{jose.A256GCM})
	if err != nil {
		return nil, "", fmt.Errorf("%w: %w", ErrJWEInvalidCompactSerialization, err)
	}
	kidBytes, err := base64.RawURLEncoding.DecodeString(jwe.Header.KeyID)
	if err != nil {
		return nil, "", fmt.Errorf("%w: invalid kid header: %w", ErrJWEInvalidCompactSerialization, err)
	}
	return jwe, hex.EncodeToString(kidBytes), nil
}

// decryptJWE decrypts a parsed JWE with the resolved private key — always a
// concrete *rsa.PrivateKey in this port, never an opaque crypto.Decrypter,
// because resolveDecryptionKey never resolves to a keystore-resident
// (potentially HSM-backed) key for JWTDecrypt (see keyresolve.go).
func decryptJWE(jwe *jose.JSONWebEncryption, priv *rsa.PrivateKey) ([]byte, error) {
	plaintext, err := jwe.Decrypt(priv)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", ErrJWEDecryptFailed, err)
	}
	return plaintext, nil
}
