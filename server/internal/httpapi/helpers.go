package httpapi

import (
	"encoding/base64"
	"sync"
	"time"
)

func encodeBase64(value []byte) string { return base64.StdEncoding.EncodeToString(value) }

type rateWindow struct {
	started time.Time
	count   int
}

type ipLimiter struct {
	mu       sync.Mutex
	limit    int
	duration time.Duration
	windows  map[string]rateWindow
}

func newIPLimiter(limit int, duration time.Duration) *ipLimiter {
	return &ipLimiter{limit: limit, duration: duration, windows: make(map[string]rateWindow)}
}

func (l *ipLimiter) Allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	window := l.windows[key]
	if window.started.IsZero() || now.Sub(window.started) >= l.duration {
		l.windows[key] = rateWindow{started: now, count: 1}
		return true
	}
	if window.count >= l.limit {
		return false
	}
	window.count++
	l.windows[key] = window
	return true
}
