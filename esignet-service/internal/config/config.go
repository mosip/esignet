package config

import (
	"fmt"
	"time"

	"github.com/kelseyhightower/envconfig"
)

// Config is the single source of truth for all runtime configuration.
// Values are populated from environment variables; defaults are applied when
// an env var is absent.  Required fields cause Load() to return an error.
type Config struct {
	Server   ServerConfig
	Postgres PostgresConfig
	Redis    RedisConfig
	Log      LogConfig
}

// ServerConfig controls the HTTP server.
type ServerConfig struct {
	Port            int           `envconfig:"PORT"             default:"8088"`
	ReadTimeout     time.Duration `envconfig:"READ_TIMEOUT"     default:"15s"`
	WriteTimeout    time.Duration `envconfig:"WRITE_TIMEOUT"    default:"15s"`
	IdleTimeout     time.Duration `envconfig:"IDLE_TIMEOUT"     default:"60s"`
	ShutdownTimeout time.Duration `envconfig:"SHUTDOWN_TIMEOUT" default:"30s"`
}

// PostgresConfig controls the pgx connection pool.
type PostgresConfig struct {
	URL             string        `envconfig:"DATABASE_URL"           required:"true"`
	MaxConns        int32         `envconfig:"DB_MAX_CONNS"           default:"10"`
	MinConns        int32         `envconfig:"DB_MIN_CONNS"           default:"2"`
	MaxConnLifetime time.Duration `envconfig:"DB_MAX_CONN_LIFETIME"   default:"1h"`
	MaxConnIdleTime time.Duration `envconfig:"DB_MAX_CONN_IDLE_TIME"  default:"30m"`
	HealthTimeout   time.Duration `envconfig:"DB_HEALTH_TIMEOUT"      default:"5s"`
}

// RedisConfig controls the go-redis client.
type RedisConfig struct {
	Addr          string        `envconfig:"REDIS_ADDR"          default:"localhost:6379"`
	Password      string        `envconfig:"REDIS_PASSWORD"      default:""`
	DB            int           `envconfig:"REDIS_DB"            default:"0"`
	DialTimeout   time.Duration `envconfig:"REDIS_DIAL_TIMEOUT"  default:"5s"`
	ReadTimeout   time.Duration `envconfig:"REDIS_READ_TIMEOUT"  default:"3s"`
	WriteTimeout  time.Duration `envconfig:"REDIS_WRITE_TIMEOUT" default:"3s"`
	PoolSize      int           `envconfig:"REDIS_POOL_SIZE"     default:"10"`
	HealthTimeout time.Duration `envconfig:"REDIS_HEALTH_TIMEOUT" default:"5s"`
}

// LogConfig controls structured logging.
type LogConfig struct {
	// Level: debug | info | warn | error  (default: info)
	Level string `envconfig:"LOG_LEVEL"  default:"info"`
	// Format: json | text  (default: json)
	Format string `envconfig:"LOG_FORMAT" default:"json"`
}

// Load reads configuration from environment variables.
// Returns an error if any required variable is missing or cannot be parsed.
func Load() (*Config, error) {
	var cfg Config
	if err := envconfig.Process("", &cfg); err != nil {
		return nil, fmt.Errorf("load config: %w", err)
	}
	return &cfg, nil
}
