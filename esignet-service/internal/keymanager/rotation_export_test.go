package keymanager

// Exported test-only aliases so the external test package (keymanager_test)
// can exercise unexported rotation helpers directly, matching the pattern
// of keeping production APIs unexported while still testing them precisely.
var (
	ExpiryFor        = expiryFor
	IsCurrent        = isCurrent
	UniqueIdentifier = uniqueIdentifier
	ResolveKeyType   = resolveKeyType
)
