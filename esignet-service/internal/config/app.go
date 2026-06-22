// Package config loads application, database, and Redis settings from the environment.
package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

const (
	defaultPort    = "8088"
	defaultDataDir = "./data"
)

// AppConfig holds core HTTP and infrastructure settings for the service.
type AppConfig struct {
	Port    string
	Issuer  string
	DataDir string
	DB      DB
	Redis   Redis
}

// LoadAppConfig reads application settings from the environment.
func LoadAppConfig() AppConfig {
	port := envOrDefault("PORT", defaultPort)
	return AppConfig{
		Port:    port,
		Issuer:  envOrDefault("MOSIP_ESIGNET_HOST", fmt.Sprintf("http://127.0.0.1:%s", port)),
		DataDir: envOrDefault("DATA_DIR", defaultDataDir),
		DB:      loadDB(),
		Redis:   loadRedis(),
	}
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func envInt(key string) int {
	raw := os.Getenv(key)
	if raw == "" {
		return 0
	}
	n, err := strconv.Atoi(raw)
	if err != nil {
		return 0
	}
	return n
}

func envBool(key string) bool {
	switch strings.ToLower(strings.TrimSpace(os.Getenv(key))) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}
