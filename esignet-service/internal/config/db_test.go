package config

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestLoadDBKeyValueDSNWithoutPassword(t *testing.T) {
	t.Setenv("POSTGRES_URL", "")
	t.Setenv("POSTGRES_PASSWORD", "")
	t.Setenv("POSTGRES_HOST", "localhost")
	t.Setenv("POSTGRES_PORT", "5432")
	t.Setenv("POSTGRES_DB", "esignet")
	t.Setenv("POSTGRES_USER", "postgres")

	dsn := LoadDB().DSN
	require.Equal(t, "host=localhost port=5432 dbname=esignet user=postgres sslmode=disable", dsn)
	require.NotContains(t, dsn, "password=")
}

func TestEnsurePostgresSSLMode(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name string
		in   string
		want string
	}{
		{
			name: "url without sslmode",
			in:   "postgres://user:pass@localhost:5432/db",
			want: "postgres://user:pass@localhost:5432/db?sslmode=disable",
		},
		{
			name: "url with existing query",
			in:   "postgres://user:pass@localhost:5432/db?connect_timeout=5",
			want: "postgres://user:pass@localhost:5432/db?connect_timeout=5&sslmode=disable",
		},
		{
			name: "url already has sslmode",
			in:   "postgres://user:pass@localhost:5432/db?sslmode=disable",
			want: "postgres://user:pass@localhost:5432/db?sslmode=disable",
		},
		{
			name: "postgresql scheme without sslmode",
			in:   "postgresql://user:pass@localhost:5432/db",
			want: "postgresql://user:pass@localhost:5432/db?sslmode=disable",
		},
		{
			name: "key value dsn unchanged",
			in:   "host=localhost port=5432 dbname=db user=u password=p sslmode=disable",
			want: "host=localhost port=5432 dbname=db user=u password=p sslmode=disable",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()
			require.Equal(t, tt.want, ensurePostgresSSLMode(tt.in))
		})
	}
}
