// Package store provides a Redis-backed implementation of the thunderidengine
// runtime.Store interface.
package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"
)

// Key prefixes — each entity type lives in its own namespace so keys never
// collide and you can inspect/flush a single entity type easily in Redis.
const (
	prefixFlow      = "flow:"
	prefixAuthCode  = "authcode:"
	prefixAuthReq   = "authreq:"
	prefixPAR       = "par:"
	prefixJTI       = "jti:"
	prefixAttrCache = "attrcache:"

	// jtiMarker is the stored value for JTI entries; the value is irrelevant —
	// only existence matters for replay prevention.
	jtiMarker = "1"
)

// RedisStore implements runtime.Store using Redis.
// All keys are namespaced under a configurable prefix (e.g. "esignet:").
type RedisStore struct {
	client    *redis.Client
	keyPrefix string
}

// NewRedisStore wraps an existing Redis client. keyPrefix is prepended to
// every key (e.g. "esignet:" produces "esignet:flow:<id>").
func NewRedisStore(client *redis.Client, keyPrefix string) *RedisStore {
	return &RedisStore{client: client, keyPrefix: keyPrefix}
}

// --- FlowContext ---

func (s *RedisStore) StoreFlowContext(ctx context.Context, executionID string, data []byte, expiry time.Time) error {
	return s.set(ctx, prefixFlow, executionID, data, expiry)
}

func (s *RedisStore) GetFlowContext(ctx context.Context, executionID string) ([]byte, error) {
	return s.get(ctx, prefixFlow, executionID)
}

// UpdateFlowContext replaces the payload while preserving the existing TTL.
// Uses Redis KEEPTTL (requires Redis ≥ 6.0).
func (s *RedisStore) UpdateFlowContext(ctx context.Context, executionID string, data []byte) error {
	k := s.key(prefixFlow, executionID)
	err := s.client.Set(ctx, k, data, redis.KeepTTL).Err()
	if err != nil {
		return fmt.Errorf("update flow context %s: %w", executionID, err)
	}
	return nil
}

func (s *RedisStore) DeleteFlowContext(ctx context.Context, executionID string) error {
	return s.del(ctx, prefixFlow, executionID)
}

// --- AuthCode ---

func (s *RedisStore) StoreAuthCode(ctx context.Context, code string, data []byte, expiry time.Time) error {
	return s.set(ctx, prefixAuthCode, code, data, expiry)
}

func (s *RedisStore) GetAuthCode(ctx context.Context, code string) ([]byte, error) {
	return s.get(ctx, prefixAuthCode, code)
}

func (s *RedisStore) DeleteAuthCode(ctx context.Context, code string) error {
	return s.del(ctx, prefixAuthCode, code)
}

// --- AuthRequest ---

func (s *RedisStore) StoreAuthRequest(ctx context.Context, requestID string, data []byte, expiry time.Time) error {
	return s.set(ctx, prefixAuthReq, requestID, data, expiry)
}

func (s *RedisStore) GetAuthRequest(ctx context.Context, requestID string) ([]byte, error) {
	return s.get(ctx, prefixAuthReq, requestID)
}

func (s *RedisStore) DeleteAuthRequest(ctx context.Context, requestID string) error {
	return s.del(ctx, prefixAuthReq, requestID)
}

// --- PAR ---

func (s *RedisStore) StorePAR(ctx context.Context, requestURI string, data []byte, expiry time.Time) error {
	return s.set(ctx, prefixPAR, requestURI, data, expiry)
}

func (s *RedisStore) GetPAR(ctx context.Context, requestURI string) ([]byte, error) {
	return s.get(ctx, prefixPAR, requestURI)
}

func (s *RedisStore) DeletePAR(ctx context.Context, requestURI string) error {
	return s.del(ctx, prefixPAR, requestURI)
}

// --- JTI (replay prevention) ---

// StoreJTI records a JTI so it cannot be reused before expiry.
func (s *RedisStore) StoreJTI(ctx context.Context, jti string, expiry time.Time) error {
	k := s.key(prefixJTI, jti)
	if err := s.client.Set(ctx, k, jtiMarker, ttl(expiry)).Err(); err != nil {
		return fmt.Errorf("store jti %s: %w", jti, err)
	}
	return nil
}

// ExistsJTI returns true if the JTI has been seen and has not expired.
func (s *RedisStore) ExistsJTI(ctx context.Context, jti string) (bool, error) {
	n, err := s.client.Exists(ctx, s.key(prefixJTI, jti)).Result()
	if err != nil {
		return false, fmt.Errorf("exists jti %s: %w", jti, err)
	}
	return n > 0, nil
}

// --- AttributeCache ---

func (s *RedisStore) StoreAttributeCache(ctx context.Context, id string, data []byte, expiry time.Time) error {
	return s.set(ctx, prefixAttrCache, id, data, expiry)
}

func (s *RedisStore) GetAttributeCache(ctx context.Context, id string) ([]byte, error) {
	return s.get(ctx, prefixAttrCache, id)
}

// ExtendAttributeCacheExpiry updates the expiry on an existing entry without
// touching the value. Returns runtime.ErrNotFound if the key is gone.
func (s *RedisStore) ExtendAttributeCacheExpiry(ctx context.Context, id string, expiry time.Time) error {
	ok, err := s.client.ExpireAt(ctx, s.key(prefixAttrCache, id), expiry).Result()
	if err != nil {
		return fmt.Errorf("extend attribute cache expiry %s: %w", id, err)
	}
	if !ok {
		return runtime.ErrNotFound
	}
	return nil
}

func (s *RedisStore) DeleteAttributeCache(ctx context.Context, id string) error {
	return s.del(ctx, prefixAttrCache, id)
}

// --- internal helpers ---

func (s *RedisStore) key(prefix, id string) string {
	return s.keyPrefix + prefix + id
}

// ttl converts an absolute expiry into a duration for Redis SET EX.
// Returns 0 if expiry is already in the past (Redis will reject negative TTLs).
func ttl(expiry time.Time) time.Duration {
	d := time.Until(expiry)
	if d < 0 {
		return 0
	}
	return d
}

func (s *RedisStore) set(ctx context.Context, prefix, id string, data []byte, expiry time.Time) error {
	k := s.key(prefix, id)
	if err := s.client.Set(ctx, k, data, ttl(expiry)).Err(); err != nil {
		return fmt.Errorf("redis set %s: %w", k, err)
	}
	return nil
}

func (s *RedisStore) get(ctx context.Context, prefix, id string) ([]byte, error) {
	k := s.key(prefix, id)
	data, err := s.client.Get(ctx, k).Bytes()
	if errors.Is(err, redis.Nil) {
		return nil, runtime.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("redis get %s: %w", k, err)
	}
	return data, nil
}

func (s *RedisStore) del(ctx context.Context, prefix, id string) error {
	k := s.key(prefix, id)
	if err := s.client.Del(ctx, k).Err(); err != nil {
		return fmt.Errorf("redis del %s: %w", k, err)
	}
	return nil
}
