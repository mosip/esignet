package runtimestore

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/mock"
	"github.com/stretchr/testify/require"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"
)

const (
	testDeploymentID = "dep-1"
)

// redisClientMock is a testify mock for the redisClient interface.
type redisClientMock struct {
	mock.Mock
}

func (m *redisClientMock) Set(ctx context.Context, key string, value any, expiration time.Duration) *redis.StatusCmd {
	return m.Called(ctx, key, value, expiration).Get(0).(*redis.StatusCmd)
}

func (m *redisClientMock) SetArgs(ctx context.Context, key string, value any, a redis.SetArgs) *redis.StatusCmd {
	return m.Called(ctx, key, value, a).Get(0).(*redis.StatusCmd)
}

func (m *redisClientMock) Get(ctx context.Context, key string) *redis.StringCmd {
	return m.Called(ctx, key).Get(0).(*redis.StringCmd)
}

func (m *redisClientMock) Del(ctx context.Context, keys ...string) *redis.IntCmd {
	return m.Called(ctx, keys).Get(0).(*redis.IntCmd)
}

func (m *redisClientMock) SetNX(ctx context.Context, key string, value any, expiration time.Duration) *redis.BoolCmd {
	return m.Called(ctx, key, value, expiration).Get(0).(*redis.BoolCmd)
}

func (m *redisClientMock) Exists(ctx context.Context, keys ...string) *redis.IntCmd {
	return m.Called(ctx, keys).Get(0).(*redis.IntCmd)
}

func (m *redisClientMock) Expire(ctx context.Context, key string, expiration time.Duration) *redis.BoolCmd {
	return m.Called(ctx, key, expiration).Get(0).(*redis.BoolCmd)
}

func newTestStore(client redisClient) *RedisStore {
	return &RedisStore{client: client, deploymentID: testDeploymentID}
}

func statusCmd(val string, err error) *redis.StatusCmd {
	cmd := redis.NewStatusCmd(context.Background())
	if err != nil {
		cmd.SetErr(err)
	} else {
		cmd.SetVal(val)
	}
	return cmd
}

func stringCmd(val string, err error) *redis.StringCmd {
	cmd := redis.NewStringCmd(context.Background())
	if err != nil {
		cmd.SetErr(err)
	} else {
		cmd.SetVal(val)
	}
	return cmd
}

func intCmd(val int64, err error) *redis.IntCmd {
	cmd := redis.NewIntCmd(context.Background())
	if err != nil {
		cmd.SetErr(err)
	} else {
		cmd.SetVal(val)
	}
	return cmd
}

func boolCmd(val bool, err error) *redis.BoolCmd {
	cmd := redis.NewBoolCmd(context.Background())
	if err != nil {
		cmd.SetErr(err)
	} else {
		cmd.SetVal(val)
	}
	return cmd
}

func positiveTTL(d time.Duration) bool { return d > 0 }

func TestKeyFormat(t *testing.T) {
	s := newTestStore(nil)
	require.Equal(t, "dep-1:flowctx:abc", s.key(typeFlowCtx, "abc"))
	require.Equal(t, "dep-1:jti:xyz", s.key(typeJTI, "xyz"))
}

func TestStore_WithTTL(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("Set", mock.Anything, "dep-1:authcode:code-1", []byte("data"),
		mock.MatchedBy(positiveTTL)).Return(statusCmd("OK", nil))

	require.NoError(t, s.StoreAuthCode(context.Background(), "code-1", []byte("data"), time.Now().Add(time.Minute)))
	c.AssertExpectations(t)
}

func TestStore_ExpiredTTLDeletesInsteadOfWriting(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	// A past expiry must not create a key, and must clear any pre-existing one
	// so behavior matches the in-memory store's unconditional overwrite.
	c.On("Del", mock.Anything, []string{"dep-1:authcode:code-1"}).Return(intCmd(1, nil))

	require.NoError(t, s.StoreAuthCode(context.Background(), "code-1", []byte("data"), time.Now().Add(-time.Second)))
	c.AssertNotCalled(t, "Set")
	c.AssertExpectations(t)
}

func TestGet_NotFound(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("Get", mock.Anything, "dep-1:par:uri-1").Return(stringCmd("", redis.Nil))

	_, err := s.GetPAR(context.Background(), "uri-1")
	require.ErrorIs(t, err, runtime.ErrNotFound)
	c.AssertExpectations(t)
}

func TestGet_BackendError(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("Get", mock.Anything, mock.Anything).Return(stringCmd("", errors.New("conn refused")))

	_, err := s.GetAuthRequest(context.Background(), "req-1")
	require.Error(t, err)
	require.NotErrorIs(t, err, runtime.ErrNotFound)
	c.AssertExpectations(t)
}

func TestDelete_AbsentKeyIsNoError(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("Del", mock.Anything, []string{"dep-1:authcode:gone"}).Return(intCmd(0, nil))

	require.NoError(t, s.DeleteAuthCode(context.Background(), "gone"))
	c.AssertExpectations(t)
}

func TestUpdateFlowContext_UsesKeepTTLAndXX(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("SetArgs", mock.Anything, "dep-1:flowctx:exec-1", []byte("new"),
		redis.SetArgs{Mode: "XX", KeepTTL: true}).Return(statusCmd("OK", nil))

	require.NoError(t, s.UpdateFlowContext(context.Background(), "exec-1", []byte("new")))
	c.AssertExpectations(t)
}

func TestUpdateFlowContext_MissingKeyNotFound(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("SetArgs", mock.Anything, mock.Anything, mock.Anything, mock.Anything).
		Return(statusCmd("", redis.Nil))

	err := s.UpdateFlowContext(context.Background(), "exec-1", []byte("new"))
	require.ErrorIs(t, err, runtime.ErrNotFound)
	c.AssertExpectations(t)
}

func TestStoreJTI_UsesSetNX(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("SetNX", mock.Anything, "dep-1:jti:jti-1", 1,
		mock.MatchedBy(positiveTTL)).Return(boolCmd(true, nil))

	require.NoError(t, s.StoreJTI(context.Background(), "jti-1", time.Now().Add(time.Minute)))
	c.AssertExpectations(t)
}

func TestStoreJTI_ExpiredSkips(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	require.NoError(t, s.StoreJTI(context.Background(), "jti-1", time.Now().Add(-time.Minute)))
	c.AssertNotCalled(t, "SetNX")
}

func TestExistsJTI(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("Exists", mock.Anything, []string{"dep-1:jti:present"}).Return(intCmd(1, nil))
	c.On("Exists", mock.Anything, []string{"dep-1:jti:absent"}).Return(intCmd(0, nil))

	present, err := s.ExistsJTI(context.Background(), "present")
	require.NoError(t, err)
	require.True(t, present)

	absent, err := s.ExistsJTI(context.Background(), "absent")
	require.NoError(t, err)
	require.False(t, absent)
	c.AssertExpectations(t)
}

func TestExtendAttributeCacheExpiry(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	c.On("Expire", mock.Anything, "dep-1:attrcache:a-1",
		mock.MatchedBy(positiveTTL)).Return(boolCmd(true, nil)).Once()
	c.On("Expire", mock.Anything, "dep-1:attrcache:gone",
		mock.MatchedBy(positiveTTL)).Return(boolCmd(false, nil)).Once()

	require.NoError(t, s.ExtendAttributeCacheExpiry(context.Background(), "a-1", time.Now().Add(time.Minute)))

	err := s.ExtendAttributeCacheExpiry(context.Background(), "gone", time.Now().Add(time.Minute))
	require.ErrorIs(t, err, runtime.ErrNotFound)
	c.AssertExpectations(t)
}

func TestExtendAttributeCacheExpiry_PastExpiryNotFound(t *testing.T) {
	c := &redisClientMock{}
	s := newTestStore(c)
	err := s.ExtendAttributeCacheExpiry(context.Background(), "a-1", time.Now().Add(-time.Second))
	require.ErrorIs(t, err, runtime.ErrNotFound)
	c.AssertNotCalled(t, "Expire")
}
