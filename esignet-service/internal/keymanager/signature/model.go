package signature

import "time"

// JWSSignRequest ports the fields of Java's JWSSignatureRequestDto that
// jwsSign (V1) uses: a single leaf certificate in x5c, no additionalHeaders,
// no includeCertificateChain — see the design doc for the v2/chain variant,
// which this port doesn't implement.
//
// ValidateJSON/IncludePayload/IncludeCertificate/IncludeCertHash/B64 use
// *bool (not bool) so a plain bool can't hide whether the caller omitted
// the flag versus explicitly set it false. Most default to true, matching
// Java's SignatureUtil.isIncludeAttrsValid convention — except
// IncludeCertificate/IncludeCertHash, which default to false (certificate
// embedding is opt-in here; see each field's own doc comment).
type JWSSignRequest struct {
	ApplicationID string
	ReferenceID   string

	// DataToSign is the payload to sign, base64url-encoded — required,
	// non-blank. Matches Java's dataToSign contract: the JWS payload octets
	// are the decoded bytes, but (when B64 is true, the default) the
	// *wire* payload segment is this string verbatim, not a re-encoding.
	DataToSign string

	// ValidateJSON, when true (the default), rejects DataToSign if its
	// decoded bytes aren't valid JSON.
	ValidateJSON *bool

	// IncludePayload, when true (the default), embeds the payload in the
	// returned compact JWS. False produces a detached-content JWS (empty
	// middle segment).
	IncludePayload *bool

	// IncludeCertificate, when true, embeds the leaf signing certificate
	// (DER, base64-encoded) as a single-entry x5c header. Defaults to
	// false: unless explicitly requested, the certificate is not embedded.
	IncludeCertificate *bool

	// IncludeCertHash, when true, embeds the leaf certificate's SHA-256
	// thumbprint as the x5t#S256 header. Defaults to false, same as
	// IncludeCertificate.
	IncludeCertHash *bool

	// CertificateURL, if non-empty, is embedded as the x5u header.
	CertificateURL string

	// B64, when true (the default), signs/embeds the payload per standard
	// JWS (base64url text). False signs the raw decoded bytes directly and
	// marks "b64" as a critical header, per RFC 7797.
	B64 *bool

	// SignAlgorithm optionally overrides the algorithm normally derived
	// from ReferenceID (see algorithmForRefID). Empty means "derive it".
	SignAlgorithm string

	// IncludeKeyID, when true, embeds a kid header derived from the
	// signing key's unique identifier (see kidFromUniqueIdentifier).
	IncludeKeyID bool

	// KeyIDPrepend is prefixed to the derived kid. The literal value
	// "PAYLOAD_ISSUER" derives the prefix from the payload's JSON "iss"
	// claim instead, mirroring Java's kidPrepend config semantics.
	KeyIDPrepend string
}

// JWSSignResponse ports JWTSignatureResponseDto.
type JWSSignResponse struct {
	JWTSignedData string
	Timestamp     time.Time
}

// JWSVerifyRequest ports the fields of JWTSignatureVerifyRequestDto this
// port implements. ValidateTrust/Domain are omitted — trust-chain
// validation is a non-goal (see the design doc's Context section:
// PartnerCertificateManagerService has no Go equivalent).
type JWSVerifyRequest struct {
	// JWTSignatureData is the compact JWS to verify — required, non-blank.
	JWTSignatureData string

	// ActualData, if non-empty, is substituted directly into the JWS's
	// payload segment before verifying — for verifying a detached-content
	// JWS (produced with IncludePayload=false), where the caller supplies
	// the payload segment out of band exactly as it would appear on the
	// wire (i.e. already in whatever form B64 produced at signing time:
	// base64url text if B64 was true, raw bytes if false). This mirrors
	// Java's jwtVerify substitution behavior; see the design doc's open
	// questions for the exact byte-format assumption.
	ActualData string

	ApplicationID string
	ReferenceID   string

	// CertificatePEM, if given, is used to verify instead of resolving via
	// ApplicationID/ReferenceID — only consulted when the JWS header has no
	// embedded certificate (see resolveVerifyCert's precedence).
	CertificatePEM string
}

// JWSVerifyResponse ports JWTSignatureVerifyResponseDto minus TrustValid
// (trust-chain validation is a non-goal — see the design doc).
type JWSVerifyResponse struct {
	SignatureValid bool
	Message        string
}
