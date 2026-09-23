package httpapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"mime"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"time"

	"com.xiaoyunduo.zhiti/server/internal/store"
)

var packPath = regexp.MustCompile(`^/v1/packs/([a-z-]+)/([0-9A-Za-z._-]+)$`)

type Config struct {
	ContentDir  string
	CatalogPath string
	CatalogSig  string
}

type API struct {
	store      *store.Store
	config     Config
	activation *ipLimiter
	downloads  sync.Map
}

func New(state *store.Store, config Config) *API {
	return &API{store: state, config: config, activation: newIPLimiter(5, time.Minute)}
}

func (a *API) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", a.health)
	mux.HandleFunc("POST /v1/activate", a.activate)
	mux.HandleFunc("GET /v1/catalog", a.catalog)
	mux.HandleFunc("GET /v1/packs/", a.pack)
	return securityHeaders(mux)
}

func (a *API) health(writer http.ResponseWriter, _ *http.Request) {
	writeJSON(writer, http.StatusOK, map[string]string{"status": "ok"})
}

func (a *API) activate(writer http.ResponseWriter, request *http.Request) {
	ip, _, _ := net.SplitHostPort(request.RemoteAddr)
	if !a.activation.Allow(ip) {
		writer.Header().Set("Retry-After", "60")
		writeError(writer, http.StatusTooManyRequests, "rate_limited")
		return
	}
	request.Body = http.MaxBytesReader(writer, request.Body, 4096)
	var body struct {
		ActivationCode string `json:"activationCode"`
		DeviceDigest   string `json:"deviceDigest"`
	}
	if json.NewDecoder(request.Body).Decode(&body) != nil || len(body.DeviceDigest) < 32 || len(body.DeviceDigest) > 256 {
		writeError(writer, http.StatusBadRequest, "invalid_request")
		return
	}
	token, err := a.store.Activate(body.ActivationCode, body.DeviceDigest)
	switch {
	case errors.Is(err, store.ErrUnknownCode):
		writeError(writer, http.StatusUnauthorized, "invalid_code")
	case errors.Is(err, store.ErrBoundElsewhere):
		writeError(writer, http.StatusConflict, "code_already_bound")
	case errors.Is(err, store.ErrRevoked):
		writeError(writer, http.StatusForbidden, "code_revoked")
	case err != nil:
		slog.Error("activation storage failure", "error", err)
		writeError(writer, http.StatusInternalServerError, "server_error")
	default:
		writeJSON(writer, http.StatusOK, map[string]string{"accessToken": token})
	}
}

func (a *API) catalog(writer http.ResponseWriter, request *http.Request) {
	if _, ok := a.authorize(writer, request); !ok {
		return
	}
	catalog, err := os.ReadFile(a.config.CatalogPath)
	if err != nil {
		writeError(writer, http.StatusServiceUnavailable, "catalog_unavailable")
		return
	}
	signature, err := os.ReadFile(a.config.CatalogSig)
	if err != nil {
		writeError(writer, http.StatusServiceUnavailable, "catalog_unavailable")
		return
	}
	writer.Header().Set("Content-Type", "application/json")
	writer.Header().Set("X-Zhiti-Signature", encodeBase64(signature))
	writer.Header().Set("Cache-Control", "private, no-cache")
	writer.Write(catalog)
}

func (a *API) pack(writer http.ResponseWriter, request *http.Request) {
	tokenHash, ok := a.authorize(writer, request)
	if !ok {
		return
	}
	match := packPath.FindStringSubmatch(request.URL.Path)
	if match == nil {
		writeError(writer, http.StatusNotFound, "not_found")
		return
	}
	semaphoreValue, _ := a.downloads.LoadOrStore(tokenHash, make(chan struct{}, 2))
	semaphore := semaphoreValue.(chan struct{})
	select {
	case semaphore <- struct{}{}:
		defer func() { <-semaphore }()
	default:
		writeError(writer, http.StatusTooManyRequests, "download_limit")
		return
	}
	filename := fmt.Sprintf("%s-%s.zip", match[1], match[2])
	path := filepath.Join(a.config.ContentDir, filename)
	file, err := os.Open(path)
	if err != nil {
		writeError(writer, http.StatusNotFound, "not_found")
		return
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		writeError(writer, http.StatusInternalServerError, "server_error")
		return
	}
	writer.Header().Set("Content-Type", mime.TypeByExtension(".zip"))
	writer.Header().Set("Content-Disposition", `attachment; filename="`+filename+`"`)
	writer.Header().Set("ETag", fmt.Sprintf(`"%x-%x"`, info.Size(), info.ModTime().Unix()))
	writer.Header().Set("Cache-Control", "private, max-age=3600")
	http.ServeContent(writer, request, filename, info.ModTime(), file)
}

func (a *API) authorize(writer http.ResponseWriter, request *http.Request) (string, bool) {
	header := request.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") {
		writeError(writer, http.StatusUnauthorized, "unauthorized")
		return "", false
	}
	hash, err := a.store.Authorize(strings.TrimSpace(strings.TrimPrefix(header, "Bearer ")))
	if err != nil {
		writeError(writer, http.StatusUnauthorized, "unauthorized")
		return "", false
	}
	return hash, true
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set("X-Content-Type-Options", "nosniff")
		writer.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(writer, request)
	})
}

func writeJSON(writer http.ResponseWriter, status int, value any) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(status)
	json.NewEncoder(writer).Encode(value)
}

func writeError(writer http.ResponseWriter, status int, code string) {
	writeJSON(writer, status, map[string]string{"error": code})
}
