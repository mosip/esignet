package client

// clientStatusActive is the canonical status written for newly-created clients.
const clientStatusActive = "ACTIVE"

// DefaultIDRegex is the fallback pattern when CLIENT_SUPPORTED_ID_REGEX is
// empty: one or more non-whitespace characters.
const DefaultIDRegex = `^\S+$`

// maxRequestBodyBytes caps the request body at 1 MiB. Real requests are
// ~10 KB; the cap rejects abusive payloads early.
const maxRequestBodyBytes = 1 << 20
