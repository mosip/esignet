/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package redisstore

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/redis/go-redis/v9"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	applog "github.com/mosip/esignet/internal/log"
)

const ns = providers.NamespaceFlow

// fakeRedisClient is a minimal in-memory stand-in for redisClient, letting us
// exercise redisStore's key formatting and error mapping without a real Redis.
type fakeRedisClient struct {
	setKey    string
	setVal    any
	setTTL    time.Duration
	setErr    error
	setArgs   redis.SetArgs
	getErr    error
	getVal    string
	delErr    error
	getDelErr error
	getDelVal string
	expireErr error
	expireOK  bool
}

func (f *fakeRedisClient) Set(_ context.Context, key string, value any, expiration time.Duration) *redis.StatusCmd {
	f.setKey, f.setVal, f.setTTL = key, value, expiration
	cmd := redis.NewStatusCmd(context.Background())
	if f.setErr != nil {
		cmd.SetErr(f.setErr)
	} else {
		cmd.SetVal("OK")
	}
	return cmd
}

func (f *fakeRedisClient) Get(_ context.Context, key string) *redis.StringCmd {
	f.setKey = key
	cmd := redis.NewStringCmd(context.Background())
	if f.getErr != nil {
		cmd.SetErr(f.getErr)
	} else {
		cmd.SetVal(f.getVal)
	}
	return cmd
}

func (f *fakeRedisClient) SetArgs(_ context.Context, key string, value any, a redis.SetArgs) *redis.StatusCmd {
	f.setKey, f.setVal, f.setArgs = key, value, a
	cmd := redis.NewStatusCmd(context.Background())
	if f.setErr != nil {
		cmd.SetErr(f.setErr)
	} else {
		cmd.SetVal("OK")
	}
	return cmd
}

func (f *fakeRedisClient) Del(_ context.Context, keys ...string) *redis.IntCmd {
	f.setKey = keys[0]
	cmd := redis.NewIntCmd(context.Background())
	if f.delErr != nil {
		cmd.SetErr(f.delErr)
	} else {
		cmd.SetVal(1)
	}
	return cmd
}

func (f *fakeRedisClient) GetDel(_ context.Context, key string) *redis.StringCmd {
	f.setKey = key
	cmd := redis.NewStringCmd(context.Background())
	if f.getDelErr != nil {
		cmd.SetErr(f.getDelErr)
	} else {
		cmd.SetVal(f.getDelVal)
	}
	return cmd
}

func (f *fakeRedisClient) Expire(_ context.Context, key string, expiration time.Duration) *redis.BoolCmd {
	f.setKey, f.setTTL = key, expiration
	cmd := redis.NewBoolCmd(context.Background())
	if f.expireErr != nil {
		cmd.SetErr(f.expireErr)
	} else {
		cmd.SetVal(f.expireOK)
	}
	return cmd
}

func newTestStore(client redisClient) *redisStore {
	return &redisStore{keyPrefix: "esignet", deploymentID: "dep-1", client: client, logger: applog.GetLogger()}
}

func (ts *ServiceTestSuite) TestGetFormattedKey() {
	t := ts.T()
	s := newTestStore(nil)
	got := s.getFormattedKey(ns, "mykey")
	want := "esignet:runtime:dep-1:flow:state:mykey"
	if got != want {
		t.Errorf("getFormattedKey() = %q, want %q", got, want)
	}
}

func (ts *ServiceTestSuite) TestPut() {
	t := ts.T()
	t.Run("no ttl", func(t *testing.T) {
		fake := &fakeRedisClient{}
		s := newTestStore(fake)
		if err := s.Put(context.Background(), ns, "k", []byte("v"), 0); err != nil {
			t.Fatalf("Put: %v", err)
		}
		if fake.setTTL != 0 {
			t.Errorf("setTTL = %v, want 0", fake.setTTL)
		}
		if fake.setKey != s.getFormattedKey(ns, "k") {
			t.Errorf("setKey = %q, want formatted key", fake.setKey)
		}
	})

	t.Run("with ttl", func(t *testing.T) {
		fake := &fakeRedisClient{}
		s := newTestStore(fake)
		if err := s.Put(context.Background(), ns, "k", []byte("v"), 30); err != nil {
			t.Fatalf("Put: %v", err)
		}
		if fake.setTTL != 30*time.Second {
			t.Errorf("setTTL = %v, want 30s", fake.setTTL)
		}
	})

	t.Run("error", func(t *testing.T) {
		fake := &fakeRedisClient{setErr: errors.New("boom")}
		s := newTestStore(fake)
		if err := s.Put(context.Background(), ns, "k", []byte("v"), 0); err == nil {
			t.Fatal("expected error from Put")
		}
	})
}

func (ts *ServiceTestSuite) TestGet() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		fake := &fakeRedisClient{getVal: "hello"}
		s := newTestStore(fake)
		got, err := s.Get(context.Background(), ns, "k")
		if err != nil {
			t.Fatalf("Get: %v", err)
		}
		if string(got) != "hello" {
			t.Errorf("Get() = %q, want hello", got)
		}
	})

	t.Run("not found returns nil, nil", func(t *testing.T) {
		fake := &fakeRedisClient{getErr: redis.Nil}
		s := newTestStore(fake)
		got, err := s.Get(context.Background(), ns, "k")
		if err != nil {
			t.Fatalf("Get: %v", err)
		}
		if got != nil {
			t.Errorf("Get() = %v, want nil", got)
		}
	})

	t.Run("error", func(t *testing.T) {
		fake := &fakeRedisClient{getErr: errors.New("boom")}
		s := newTestStore(fake)
		if _, err := s.Get(context.Background(), ns, "k"); err == nil {
			t.Fatal("expected error from Get")
		}
	})
}

func (ts *ServiceTestSuite) TestUpdate() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		fake := &fakeRedisClient{}
		s := newTestStore(fake)
		if err := s.Update(context.Background(), ns, "k", []byte("v2")); err != nil {
			t.Fatalf("Update: %v", err)
		}
		if fake.setArgs.Mode != "XX" || !fake.setArgs.KeepTTL {
			t.Errorf("setArgs = %+v, want Mode=XX KeepTTL=true", fake.setArgs)
		}
	})

	t.Run("missing key maps to ErrRuntimeStoreKeyNotFound", func(t *testing.T) {
		fake := &fakeRedisClient{setErr: redis.Nil}
		s := newTestStore(fake)
		err := s.Update(context.Background(), ns, "k", []byte("v2"))
		if !errors.Is(err, providers.ErrRuntimeStoreKeyNotFound) {
			t.Errorf("Update() error = %v, want ErrRuntimeStoreKeyNotFound", err)
		}
	})

	t.Run("error", func(t *testing.T) {
		fake := &fakeRedisClient{setErr: errors.New("boom")}
		s := newTestStore(fake)
		if err := s.Update(context.Background(), ns, "k", []byte("v2")); err == nil {
			t.Fatal("expected error from Update")
		}
	})
}

func (ts *ServiceTestSuite) TestDelete() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		fake := &fakeRedisClient{}
		s := newTestStore(fake)
		if err := s.Delete(context.Background(), ns, "k"); err != nil {
			t.Fatalf("Delete: %v", err)
		}
	})

	t.Run("error", func(t *testing.T) {
		fake := &fakeRedisClient{delErr: errors.New("boom")}
		s := newTestStore(fake)
		if err := s.Delete(context.Background(), ns, "k"); err == nil {
			t.Fatal("expected error from Delete")
		}
	})
}

func (ts *ServiceTestSuite) TestTake() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		fake := &fakeRedisClient{getDelVal: "hello"}
		s := newTestStore(fake)
		got, err := s.Take(context.Background(), ns, "k")
		if err != nil {
			t.Fatalf("Take: %v", err)
		}
		if string(got) != "hello" {
			t.Errorf("Take() = %q, want hello", got)
		}
	})

	t.Run("not found returns nil, nil", func(t *testing.T) {
		fake := &fakeRedisClient{getDelErr: redis.Nil}
		s := newTestStore(fake)
		got, err := s.Take(context.Background(), ns, "k")
		if err != nil {
			t.Fatalf("Take: %v", err)
		}
		if got != nil {
			t.Errorf("Take() = %v, want nil", got)
		}
	})

	t.Run("error", func(t *testing.T) {
		fake := &fakeRedisClient{getDelErr: errors.New("boom")}
		s := newTestStore(fake)
		if _, err := s.Take(context.Background(), ns, "k"); err == nil {
			t.Fatal("expected error from Take")
		}
	})
}

func (ts *ServiceTestSuite) TestExtendTTL() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		fake := &fakeRedisClient{expireOK: true}
		s := newTestStore(fake)
		if err := s.ExtendTTL(context.Background(), ns, "k", 60); err != nil {
			t.Fatalf("ExtendTTL: %v", err)
		}
		if fake.setTTL != 60*time.Second {
			t.Errorf("setTTL = %v, want 60s", fake.setTTL)
		}
	})

	t.Run("missing key maps to ErrRuntimeStoreKeyNotFound", func(t *testing.T) {
		fake := &fakeRedisClient{expireOK: false}
		s := newTestStore(fake)
		err := s.ExtendTTL(context.Background(), ns, "k", 60)
		if !errors.Is(err, providers.ErrRuntimeStoreKeyNotFound) {
			t.Errorf("ExtendTTL() error = %v, want ErrRuntimeStoreKeyNotFound", err)
		}
	})

	t.Run("error", func(t *testing.T) {
		fake := &fakeRedisClient{expireErr: errors.New("boom")}
		s := newTestStore(fake)
		if err := s.ExtendTTL(context.Background(), ns, "k", 60); err == nil {
			t.Fatal("expected error from ExtendTTL")
		}
	})
}

func (ts *ServiceTestSuite) TestInitialize() {
	t := ts.T()
	store, err := Initialize("dep-1", "esignet", redis.NewClient(&redis.Options{Addr: "localhost:0"}))
	if err != nil {
		t.Fatalf("Initialize: %v", err)
	}
	if store == nil {
		t.Fatal("expected non-nil store")
	}
}

type ServiceTestSuite struct {
	suite.Suite
}

func TestServiceTestSuite(t *testing.T) {
	suite.Run(t, new(ServiceTestSuite))
}
