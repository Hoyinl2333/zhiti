package store

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base32"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

var (
	ErrUnknownCode    = errors.New("unknown activation code")
	ErrBoundElsewhere = errors.New("activation code bound to another device")
	ErrRevoked        = errors.New("activation code revoked")
	ErrUnauthorized   = errors.New("unauthorized token")
)

type Record struct {
	CodeHash     string    `json:"codeHash"`
	DeviceHash   string    `json:"deviceHash,omitempty"`
	TokenHash    string    `json:"tokenHash,omitempty"`
	CreatedAt    time.Time `json:"createdAt"`
	ActivatedAt  time.Time `json:"activatedAt,omitempty"`
	LastAccessAt time.Time `json:"lastAccessAt,omitempty"`
	Revoked      bool      `json:"revoked"`
}

type fileData struct {
	Version int      `json:"version"`
	Records []Record `json:"records"`
}

type Store struct {
	mu     sync.Mutex
	path   string
	pepper string
	data   fileData
}

func Open(path, pepper string) (*Store, error) {
	s := &Store{path: path, pepper: pepper, data: fileData{Version: 1}}
	bytes, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(bytes, &s.data); err != nil {
		return nil, fmt.Errorf("decode state: %w", err)
	}
	return s, nil
}

func (s *Store) CreateCodes(count int) ([]string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	result := make([]string, 0, count)
	for range count {
		code, err := randomCode()
		if err != nil {
			return nil, err
		}
		s.data.Records = append(s.data.Records, Record{CodeHash: s.hash("code", code), CreatedAt: time.Now().UTC()})
		result = append(result, code)
	}
	return result, s.saveLocked()
}

func (s *Store) Activate(code, deviceDigest string) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	codeHash := s.hash("code", normalizeCode(code))
	deviceHash := s.hash("device", deviceDigest)
	for index := range s.data.Records {
		record := &s.data.Records[index]
		if subtle.ConstantTimeCompare([]byte(record.CodeHash), []byte(codeHash)) != 1 {
			continue
		}
		if record.Revoked {
			return "", ErrRevoked
		}
		if record.DeviceHash != "" && subtle.ConstantTimeCompare([]byte(record.DeviceHash), []byte(deviceHash)) != 1 {
			return "", ErrBoundElsewhere
		}
		token, err := randomToken()
		if err != nil {
			return "", err
		}
		now := time.Now().UTC()
		record.DeviceHash = deviceHash
		record.TokenHash = s.hash("token", token)
		if record.ActivatedAt.IsZero() {
			record.ActivatedAt = now
		}
		record.LastAccessAt = now
		if err := s.saveLocked(); err != nil {
			return "", err
		}
		return token, nil
	}
	return "", ErrUnknownCode
}

func (s *Store) Authorize(token string) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	tokenHash := s.hash("token", token)
	for index := range s.data.Records {
		record := &s.data.Records[index]
		if record.Revoked || record.TokenHash == "" {
			continue
		}
		if subtle.ConstantTimeCompare([]byte(record.TokenHash), []byte(tokenHash)) == 1 {
			record.LastAccessAt = time.Now().UTC()
			if err := s.saveLocked(); err != nil {
				return "", err
			}
			return tokenHash, nil
		}
	}
	return "", ErrUnauthorized
}

func (s *Store) Revoke(code string) error {
	return s.mutateCode(code, func(record *Record) { record.Revoked = true; record.TokenHash = "" })
}

func (s *Store) Unbind(code string) error {
	return s.mutateCode(code, func(record *Record) {
		record.DeviceHash = ""
		record.TokenHash = ""
		record.ActivatedAt = time.Time{}
		record.LastAccessAt = time.Time{}
	})
}

func (s *Store) mutateCode(code string, change func(*Record)) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	hash := s.hash("code", normalizeCode(code))
	for index := range s.data.Records {
		if subtle.ConstantTimeCompare([]byte(s.data.Records[index].CodeHash), []byte(hash)) == 1 {
			change(&s.data.Records[index])
			return s.saveLocked()
		}
	}
	return ErrUnknownCode
}

func (s *Store) Status() []Record {
	s.mu.Lock()
	defer s.mu.Unlock()
	copyRecords := append([]Record(nil), s.data.Records...)
	sort.Slice(copyRecords, func(i, j int) bool { return copyRecords[i].CreatedAt.Before(copyRecords[j].CreatedAt) })
	return copyRecords
}

func (s *Store) hash(kind, value string) string {
	sum := sha256.Sum256([]byte(kind + "\x00" + s.pepper + "\x00" + value))
	return hex.EncodeToString(sum[:])
}

func (s *Store) saveLocked() error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return err
	}
	bytes, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		return err
	}
	temporary := s.path + ".tmp"
	if err := os.WriteFile(temporary, append(bytes, '\n'), 0o600); err != nil {
		return err
	}
	return os.Rename(temporary, s.path)
}

func randomCode() (string, error) {
	bytes := make([]byte, 10)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	raw := base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(bytes)
	return "ZHITI-" + raw[0:4] + "-" + raw[4:8] + "-" + raw[8:12] + "-" + raw[12:16], nil
}

func randomToken() (string, error) {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	return base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(bytes), nil
}

func normalizeCode(code string) string {
	return strings.ToUpper(strings.TrimSpace(code))
}
