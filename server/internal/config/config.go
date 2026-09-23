package config

import (
	"fmt"
	"os"
)

type Config struct {
	Address     string
	StatePath   string
	ContentDir  string
	CatalogPath string
	CatalogSig  string
	TLSCert     string
	TLSKey      string
	Pepper      string
}

func FromEnv() (Config, error) {
	cfg := Config{
		Address:     env("ZHITI_ADDRESS", ":443"),
		StatePath:   env("ZHITI_STATE_PATH", "/data/state.json"),
		ContentDir:  env("ZHITI_CONTENT_DIR", "/content"),
		CatalogPath: env("ZHITI_CATALOG_PATH", "/content/catalog.json"),
		CatalogSig:  env("ZHITI_CATALOG_SIG", "/content/catalog.sig"),
		TLSCert:     env("ZHITI_TLS_CERT", "/run/secrets/server.crt"),
		TLSKey:      env("ZHITI_TLS_KEY", "/run/secrets/server.key"),
		Pepper:      os.Getenv("ZHITI_PEPPER"),
	}
	if len(cfg.Pepper) < 32 {
		return Config{}, fmt.Errorf("ZHITI_PEPPER must contain at least 32 characters")
	}
	return cfg, nil
}

func env(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
