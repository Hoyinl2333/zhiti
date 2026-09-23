package httpapi

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"com.xiaoyunduo.zhiti/server/internal/store"
)

func testAPI(t *testing.T) (*API, string) {
	t.Helper()
	dir := t.TempDir()
	state, err := store.Open(filepath.Join(dir, "state.json"), "01234567890123456789012345678901")
	if err != nil {
		t.Fatal(err)
	}
	codes, err := state.CreateCodes(1)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "catalog.json"), []byte(`{"packs":[]}`), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "catalog.sig"), []byte("sig"), 0o600); err != nil {
		t.Fatal(err)
	}
	return New(state, Config{ContentDir: dir, CatalogPath: filepath.Join(dir, "catalog.json"), CatalogSig: filepath.Join(dir, "catalog.sig")}), codes[0]
}

func TestActivationAndAuthorizedCatalog(t *testing.T) {
	api, code := testAPI(t)
	body, _ := json.Marshal(map[string]string{"activationCode": code, "deviceDigest": "01234567890123456789012345678901"})
	request := httptest.NewRequest(http.MethodPost, "/v1/activate", bytes.NewReader(body))
	request.RemoteAddr = "127.0.0.1:1234"
	response := httptest.NewRecorder()
	api.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("activate status %d: %s", response.Code, response.Body.String())
	}
	var activated map[string]string
	json.Unmarshal(response.Body.Bytes(), &activated)
	request = httptest.NewRequest(http.MethodGet, "/v1/catalog", nil)
	request.Header.Set("Authorization", "Bearer "+activated["accessToken"])
	response = httptest.NewRecorder()
	api.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("catalog status %d", response.Code)
	}
	if response.Header().Get("X-Zhiti-Signature") == "" {
		t.Fatal("missing catalog signature")
	}
}

func TestCodeCannotBindAnotherDevice(t *testing.T) {
	api, code := testAPI(t)
	for index, device := range []string{"01234567890123456789012345678901", "abcdefabcdefabcdefabcdefabcdefab"} {
		body, _ := json.Marshal(map[string]string{"activationCode": code, "deviceDigest": device})
		request := httptest.NewRequest(http.MethodPost, "/v1/activate", bytes.NewReader(body))
		request.RemoteAddr = "127.0.0.1:1234"
		response := httptest.NewRecorder()
		api.Handler().ServeHTTP(response, request)
		if index == 1 && response.Code != http.StatusConflict {
			t.Fatalf("expected conflict, got %d", response.Code)
		}
	}
}

func TestAuthorizedRangeDownload(t *testing.T) {
	api, code := testAPI(t)
	directory := api.config.ContentDir
	if err := os.WriteFile(filepath.Join(directory, "judgment-1.zip"), []byte("0123456789"), 0o600); err != nil {
		t.Fatal(err)
	}
	body, _ := json.Marshal(map[string]string{"activationCode": code, "deviceDigest": "01234567890123456789012345678901"})
	activation := httptest.NewRequest(http.MethodPost, "/v1/activate", bytes.NewReader(body))
	activation.RemoteAddr = "127.0.0.1:1234"
	activationResponse := httptest.NewRecorder()
	api.Handler().ServeHTTP(activationResponse, activation)
	var activated map[string]string
	json.Unmarshal(activationResponse.Body.Bytes(), &activated)

	request := httptest.NewRequest(http.MethodGet, "/v1/packs/judgment/1", nil)
	request.Header.Set("Authorization", "Bearer "+activated["accessToken"])
	request.Header.Set("Range", "bytes=4-7")
	response := httptest.NewRecorder()
	api.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusPartialContent || response.Body.String() != "4567" {
		t.Fatalf("range response %d %q", response.Code, response.Body.String())
	}
}

func TestDownloadRequiresToken(t *testing.T) {
	api, _ := testAPI(t)
	request := httptest.NewRequest(http.MethodGet, "/v1/packs/judgment/1", nil)
	response := httptest.NewRecorder()
	api.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("expected unauthorized, got %d", response.Code)
	}
}
