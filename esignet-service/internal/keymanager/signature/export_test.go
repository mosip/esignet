package signature

// Exported for signature_test — lets the external test package exercise
// pure internal helpers directly, without going through the full
// Service.JWSSign/JWSVerify flow, mirroring keymanager's own
// *_export_test.go convention (e.g. hierarchy_export_test.go).
var (
	DerToConcat = derToConcat
)
