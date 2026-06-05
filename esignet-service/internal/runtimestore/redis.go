// Package runtimestore provides backends for the ThunderID engine runtime.Store.
package runtimestore

import (
	"context"
	"errors"
	"fmt"
	"io"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"
)

// Record type segments used in namespaced Redis keys.
const (
	typeFlowCtx   = "flowctx"
	typeAuthCode  = "authcode"
	typeAuthReq   = "authreq"
	typePAR       = "par"
	typeJTI       = "jti"
	typeAttrCache = "attrcache"
)

// redisClient is the minimal Redis API needed by RedisStore.
type redisClient interface {
	Set(ctx context.Context, key string, value any, expiration time.Duration) *redis.StatusCmd
	SetArgs(ctx context.Context, key string, value any, a redis.SetArgs) *redis.StatusCmd
	Get(ctx context.Context, key string) *redis.StringCmd
	Del(ctx context.Context, keys ...string) *redis.IntCmd
	SetNX(ctx context.Context, key string, value any, expiration time.Duration) *redis.BoolCmd
	Exists(ctx context.Context, keys ...string) *redis.IntCmd
	Expire(ctx context.Context, key string, expiration time.Duration) *redis.BoolCmd
}

// Config holds Redis connection settings for the runtime store.
type Config struct {
	Address         string
	Username        string
	Password        string
	DB              int
	DeploymentID    string
	MaxRetries      int
	MinRetryBackoff time.Duration
	MaxRetryBackoff time.Duration
	DialTimeout     time.Duration
	ReadTimeout     time.Duration
	WriteTimeout    time.Duration
}

// RedisStore is a Redis-backed implementation of runtime.Store. Keys are
// namespaced by deployment id so multiple deployments can share a single
// Redis instance.
type RedisStore struct {
	client       redisClient
	deploymentID string
}

// Compile-time assertion that RedisStore satisfies the engine interface.
var _ runtime.Store = (*RedisStore)(nil)

// NewRedisStore builds a Redis-backed runtime store, connecting and pinging
// the server. It fails fast if Redis is unreachable.
func NewRedisStore(cfg Config) (*RedisStore, error) {
	if cfg.DeploymentID == "" {
		return nil, errors.New("runtime store: redis deployment id must not be empty")
	}

	client := redis.NewClient(&redis.Options{
		Addr:            cfg.Address,
		Username:        cfg.Username,
		Password:        cfg.Password,
		DB:              cfg.DB,
		MaxRetries:      cfg.MaxRetries,
		MinRetryBackoff: cfg.MinRetryBackoff,
		MaxRetryBackoff: cfg.MaxRetryBackoff,
		DialTimeout:     cfg.DialTimeout,
		ReadTimeout:     cfg.ReadTimeout,
		WriteTimeout:    cfg.WriteTimeout,
	})

	pingTimeout := cfg.DialTimeout
	if pingTimeout <= 0 {
		pingTimeout = 5 * time.Second
	}
	ctx, cancel := context.WithTimeout(context.Background(), pingTimeout)
	defer cancel()

	if err := client.Ping(ctx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("runtime store: connect to redis at %s: %w", cfg.Address, err)
	}

	return &RedisStore{
		client:       client,
		deploymentID: cfg.DeploymentID,
	}, nil
}

// Close releases the underlying Redis connection.
func (s *RedisStore) Close() error {
	if closer, ok := s.client.(io.Closer); ok {
		return closer.Close()
	}
	return nil
}

// key builds the namespaced Redis key for a record.
func (s *RedisStore) key(recordType, id string) string {
	return fmt.Sprintf("%s:%s:%s", s.deploymentID, recordType, id)
}

// set stores a value with a TTL derived from expiry. An already-passed expiry
// deletes any existing key instead of writing, matching the in-memory store's
// unconditional overwrite (a re-store with a past expiry leaves the key absent)
// while never creating a key without a TTL.
func (s *RedisStore) set(ctx context.Context, recordType, id string, data []byte, expiry time.Time) error {
	ttl := time.Until(expiry)
	if ttl <= 0 {
		return s.del(ctx, recordType, id)
	}
	if err := s.client.Set(ctx, s.key(recordType, id), data, ttl).Err(); err != nil {
		return fmt.Errorf("runtime store: set %s: %w", recordType, err)
	}
	return nil
}

// get returns the stored value, or runtime.ErrNotFound when absent or expired.
func (s *RedisStore) get(ctx context.Context, recordType, id string) ([]byte, error) {
	data, err := s.client.Get(ctx, s.key(recordType, id)).Bytes()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return nil, runtime.ErrNotFound
		}
		return nil, fmt.Errorf("runtime store: get %s: %w", recordType, err)
	}
	return data, nil
}

// del removes a record. Deleting an absent key is not an error, matching the
// in-memory store contract.
func (s *RedisStore) del(ctx context.Context, recordType, id string) error {
	if err := s.client.Del(ctx, s.key(recordType, id)).Err(); err != nil {
		return fmt.Errorf("runtime store: delete %s: %w", recordType, err)
	}
	return nil
}

// StoreFlowContext implements runtime.Store.
func (s *RedisStore) StoreFlowContext(ctx context.Context, executionID string, data []byte, expiry time.Time) error {
	return s.set(ctx, typeFlowCtx, executionID, data, expiry)
}

// GetFlowContext implements runtime.Store.
func (s *RedisStore) GetFlowContext(ctx context.Context, executionID string) ([]byte, error) {
	return s.get(ctx, typeFlowCtx, executionID)
}

// UpdateFlowContext implements runtime.Store. It preserves the remaining TTL
// and returns runtime.ErrNotFound when the key is absent or expired (XX mode),
// so it never resurrects a key without a TTL.
func (s *RedisStore) UpdateFlowContext(ctx context.Context, executionID string, data []byte) error {
	err := s.client.SetArgs(ctx, s.key(typeFlowCtx, executionID), data, redis.SetArgs{
		Mode:    "XX",
		KeepTTL: true,
	}).Err()
	if errors.Is(err, redis.Nil) {
		return runtime.ErrNotFound
	}
	if err != nil {
		return fmt.Errorf("runtime store: update flow context: %w", err)
	}
	return nil
}

// DeleteFlowContext implements runtime.Store.
func (s *RedisStore) DeleteFlowContext(ctx context.Context, executionID string) error {
	return s.del(ctx, typeFlowCtx, executionID)
}

// StoreAuthCode implements runtime.Store.
func (s *RedisStore) StoreAuthCode(ctx context.Context, code string, data []byte, expiry time.Time) error {
	return s.set(ctx, typeAuthCode, code, data, expiry)
}

// GetAuthCode implements runtime.Store.
func (s *RedisStore) GetAuthCode(ctx context.Context, code string) ([]byte, error) {
	return s.get(ctx, typeAuthCode, code)
}

// DeleteAuthCode implements runtime.Store.
func (s *RedisStore) DeleteAuthCode(ctx context.Context, code string) error {
	return s.del(ctx, typeAuthCode, code)
}

// StoreAuthRequest implements runtime.Store.
func (s *RedisStore) StoreAuthRequest(ctx context.Context, requestID string, data []byte, expiry time.Time) error {
	return s.set(ctx, typeAuthReq, requestID, data, expiry)
}

// GetAuthRequest implements runtime.Store.
func (s *RedisStore) GetAuthRequest(ctx context.Context, requestID string) ([]byte, error) {
	return s.get(ctx, typeAuthReq, requestID)
}

// DeleteAuthRequest implements runtime.Store.
func (s *RedisStore) DeleteAuthRequest(ctx context.Context, requestID string) error {
	return s.del(ctx, typeAuthReq, requestID)
}

// StorePAR implements runtime.Store.
func (s *RedisStore) StorePAR(ctx context.Context, requestURI string, data []byte, expiry time.Time) error {
	return s.set(ctx, typePAR, requestURI, data, expiry)
}

// GetPAR implements runtime.Store.
func (s *RedisStore) GetPAR(ctx context.Context, requestURI string) ([]byte, error) {
	return s.get(ctx, typePAR, requestURI)
}

// DeletePAR implements runtime.Store.
func (s *RedisStore) DeletePAR(ctx context.Context, requestURI string) error {
	return s.del(ctx, typePAR, requestURI)
}

// StoreJTI implements runtime.Store. SetNX never overwrites an existing JTI,
// and an already-passed expiry is skipped so no TTL-less key is created.
func (s *RedisStore) StoreJTI(ctx context.Context, jti string, expiry time.Time) error {
	ttl := time.Until(expiry)
	if ttl <= 0 {
		return nil
	}
	if err := s.client.SetNX(ctx, s.key(typeJTI, jti), 1, ttl).Err(); err != nil {
		return fmt.Errorf("runtime store: store jti: %w", err)
	}
	return nil
}

// ExistsJTI implements runtime.Store.
func (s *RedisStore) ExistsJTI(ctx context.Context, jti string) (bool, error) {
	n, err := s.client.Exists(ctx, s.key(typeJTI, jti)).Result()
	if err != nil {
		return false, fmt.Errorf("runtime store: exists jti: %w", err)
	}
	return n > 0, nil
}

// StoreAttributeCache implements runtime.Store.
func (s *RedisStore) StoreAttributeCache(ctx context.Context, id string, data []byte, expiry time.Time) error {
	return s.set(ctx, typeAttrCache, id, data, expiry)
}

// GetAttributeCache implements runtime.Store.
func (s *RedisStore) GetAttributeCache(ctx context.Context, id string) ([]byte, error) {
	return s.get(ctx, typeAttrCache, id)
}

// ExtendAttributeCacheExpiry implements runtime.Store. It resets the TTL on an
// existing key and returns runtime.ErrNotFound when the key is absent or the
// new expiry has already passed.
func (s *RedisStore) ExtendAttributeCacheExpiry(ctx context.Context, id string, expiry time.Time) error {
	ttl := time.Until(expiry)
	if ttl <= 0 {
		return runtime.ErrNotFound
	}
	ok, err := s.client.Expire(ctx, s.key(typeAttrCache, id), ttl).Result()
	if err != nil {
		return fmt.Errorf("runtime store: extend attribute cache expiry: %w", err)
	}
	if !ok {
		return runtime.ErrNotFound
	}
	return nil
}

// DeleteAttributeCache implements runtime.Store.
func (s *RedisStore) DeleteAttributeCache(ctx context.Context, id string) error {
	return s.del(ctx, typeAttrCache, id)
}
