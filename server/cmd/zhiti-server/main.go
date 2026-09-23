package main

import (
	"log/slog"
	"net/http"
	"os"
	"time"

	"com.xiaoyunduo.zhiti/server/internal/config"
	"com.xiaoyunduo.zhiti/server/internal/httpapi"
	"com.xiaoyunduo.zhiti/server/internal/store"
)

func main() {
	cfg, err := config.FromEnv()
	if err != nil {
		slog.Error("invalid configuration", "error", err)
		os.Exit(1)
	}
	state, err := store.Open(cfg.StatePath, cfg.Pepper)
	if err != nil {
		slog.Error("open state", "error", err)
		os.Exit(1)
	}
	api := httpapi.New(state, httpapi.Config{ContentDir: cfg.ContentDir, CatalogPath: cfg.CatalogPath, CatalogSig: cfg.CatalogSig})
	server := &http.Server{
		Addr:              cfg.Address,
		Handler:           api.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Minute,
		IdleTimeout:       2 * time.Minute,
		MaxHeaderBytes:    16 << 10,
	}
	slog.Info("zhiti server starting", "address", cfg.Address)
	if err := server.ListenAndServeTLS(cfg.TLSCert, cfg.TLSKey); err != nil {
		slog.Error("server stopped", "error", err)
		os.Exit(1)
	}
}
