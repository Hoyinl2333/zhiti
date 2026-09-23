package store

import (
	"errors"
	"path/filepath"
	"testing"
)

const testPepper = "01234567890123456789012345678901"

func TestStatePersistsAndRevocationInvalidatesToken(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state.json")
	state, err := Open(path, testPepper)
	if err != nil {
		t.Fatal(err)
	}
	codes, err := state.CreateCodes(1)
	if err != nil {
		t.Fatal(err)
	}
	token, err := state.Activate(codes[0], "device")
	if err != nil {
		t.Fatal(err)
	}

	reopened, err := Open(path, testPepper)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := reopened.Authorize(token); err != nil {
		t.Fatalf("persisted token rejected: %v", err)
	}
	if err := reopened.Revoke(codes[0]); err != nil {
		t.Fatal(err)
	}
	if _, err := reopened.Authorize(token); !errors.Is(err, ErrUnauthorized) {
		t.Fatalf("revoked token authorized: %v", err)
	}
}

func TestUnbindAllowsDifferentDevice(t *testing.T) {
	state, err := Open(filepath.Join(t.TempDir(), "state.json"), testPepper)
	if err != nil {
		t.Fatal(err)
	}
	codes, _ := state.CreateCodes(1)
	if _, err := state.Activate(codes[0], "first"); err != nil {
		t.Fatal(err)
	}
	if err := state.Unbind(codes[0]); err != nil {
		t.Fatal(err)
	}
	if _, err := state.Activate(codes[0], "second"); err != nil {
		t.Fatal(err)
	}
}
