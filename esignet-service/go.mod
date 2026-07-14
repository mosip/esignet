module github.com/mosip/esignet

go 1.26

require (
	github.com/btcsuite/btcd/btcec/v2 v2.5.0
	github.com/google/uuid v1.6.0
	github.com/jmoiron/sqlx v1.4.0
	github.com/golang-jwt/jwt/v5 v5.3.1
	github.com/lib/pq v1.10.9
	github.com/miekg/pkcs11 v1.1.2
	github.com/redis/go-redis/v9 v9.18.0
	github.com/stretchr/testify v1.11.1
	github.com/thunder-id/thunderid v0.0.0-20260701124159-a8f0fb52b5c5
	go.mozilla.org/pkcs7 v0.9.0
	golang.org/x/crypto v0.46.0
	golang.org/x/text v0.38.0
	gopkg.in/yaml.v3 v3.0.1
	software.sslmate.com/src/go-pkcs12 v0.7.3
)

require (
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/decred/dcrd/dcrec/secp256k1/v4 v4.4.0 // indirect
	github.com/dgryski/go-rendezvous v0.0.0-20200823014737-9f7001d12a5f // indirect
	github.com/dustin/go-humanize v1.0.1 // indirect
	github.com/fxamacker/cbor/v2 v2.9.2 // indirect
	github.com/go-logr/logr v1.4.3 // indirect
	github.com/go-logr/stdr v1.2.2 // indirect
	github.com/go-viper/mapstructure/v2 v2.5.0 // indirect
	github.com/go-webauthn/webauthn v0.17.4 // indirect
	github.com/go-webauthn/x v0.2.6 // indirect
	github.com/google/go-tpm v0.9.8 // indirect
	github.com/google/jsonschema-go v0.4.3 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/mattn/go-isatty v0.0.20 // indirect
	github.com/modelcontextprotocol/go-sdk v1.6.1 // indirect
	github.com/ncruces/go-strftime v1.0.0 // indirect
	github.com/philhofer/fwd v1.2.0 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	github.com/remyoudompheng/bigfft v0.0.0-20230129092748-24d4a6f8daec // indirect
	github.com/segmentio/asm v1.1.3 // indirect
	github.com/segmentio/encoding v0.5.4 // indirect
	github.com/tinylib/msgp v1.6.4 // indirect
	github.com/x448/float16 v0.8.4 // indirect
	github.com/yosida95/uritemplate/v3 v3.0.2 // indirect
	go.opentelemetry.io/auto/sdk v1.2.1 // indirect
	go.opentelemetry.io/otel v1.44.0 // indirect
	go.opentelemetry.io/otel/metric v1.44.0 // indirect
	go.opentelemetry.io/otel/trace v1.44.0 // indirect
	go.uber.org/atomic v1.11.0 // indirect
	golang.org/x/crypto v0.53.0 // indirect
	golang.org/x/net v0.55.0 // indirect
	golang.org/x/oauth2 v0.36.0 // indirect
	golang.org/x/sys v0.46.0 // indirect
	modernc.org/libc v1.73.4 // indirect
	modernc.org/mathutil v1.7.1 // indirect
	modernc.org/memory v1.11.0 // indirect
	modernc.org/sqlite v1.53.0 // indirect
)

replace github.com/thunder-id/thunderid => github.com/thunder-id/thunderid/backend v0.0.0-20260713080127-2a05e9846e45
