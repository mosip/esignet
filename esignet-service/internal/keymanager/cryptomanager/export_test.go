package cryptomanager

// export_test.go exposes unexported identifiers to the external
// cryptomanager_test package, mirroring the hierarchy_export_test.go /
// rotation_export_test.go convention in internal/keymanager.

var (
	SymmetricEncrypt = symmetricEncrypt
	SymmetricDecrypt = symmetricDecrypt
	BuildEnvelope    = buildEnvelope
	ParseEnvelope    = parseEnvelope
	ThumbprintRaw    = thumbprintRaw
	IsJWSData        = isJWSData
	IsDataValid      = isDataValid

	ResolveDecryptionKey     = (*Service).resolveDecryptionKey
	ValidateKeyIdentifierIDs = (*Service).validateKeyIdentifierIDs

	BuildAESEnvelope = buildAESEnvelope
	ParseAESEnvelope = parseAESEnvelope
)

const (
	VerR2Header             = verR2Header
	KeyMaterialLength       = keyMaterialLength
	SymmetricUniIdentLength = symmetricUniIdentLength
)
