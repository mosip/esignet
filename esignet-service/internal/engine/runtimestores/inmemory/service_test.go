/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package inmemory

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

const ns = providers.NamespaceFlow

func (ts *ServiceTestSuite) TestPutAndGet() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if err := store.Put(ctx, ns, "key1", []byte("value1"), 0); err != nil {
		t.Fatalf("Put: %v", err)
	}
	got, err := store.Get(ctx, ns, "key1")
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	if string(got) != "value1" {
		t.Errorf("Get() = %q, want value1", got)
	}
}

func (ts *ServiceTestSuite) TestGetMissingKeyReturnsNil() {
	t := ts.T()
	store := Initialize("dep-1")
	got, err := store.Get(context.Background(), ns, "missing")
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	if got != nil {
		t.Errorf("Get() = %v, want nil", got)
	}
}

func (ts *ServiceTestSuite) TestPutWithTTLExpires() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if err := store.Put(ctx, ns, "key1", []byte("value1"), 1); err != nil {
		t.Fatalf("Put: %v", err)
	}

	// Reach into the concrete type to force expiry deterministically instead of sleeping.
	s := store.(*inMemoryStore)
	fk := s.getFormattedKey(ns, "key1")
	s.mu.Lock()
	s.data[fk].expiresAt = time.Now().Add(-time.Second)
	s.mu.Unlock()

	got, err := store.Get(ctx, ns, "key1")
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	if got != nil {
		t.Errorf("Get() = %v, want nil for expired entry", got)
	}
}

func (ts *ServiceTestSuite) TestUpdate() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if err := store.Update(ctx, ns, "missing", []byte("v")); !errors.Is(err, providers.ErrRuntimeStoreKeyNotFound) {
		t.Errorf("Update(missing) error = %v, want ErrRuntimeStoreKeyNotFound", err)
	}

	if err := store.Put(ctx, ns, "key1", []byte("v1"), 0); err != nil {
		t.Fatalf("Put: %v", err)
	}
	if err := store.Update(ctx, ns, "key1", []byte("v2")); err != nil {
		t.Fatalf("Update: %v", err)
	}
	got, err := store.Get(ctx, ns, "key1")
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	if string(got) != "v2" {
		t.Errorf("Get() after Update = %q, want v2", got)
	}
}

func (ts *ServiceTestSuite) TestUpdatePreservesTTL() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if err := store.Put(ctx, ns, "key1", []byte("v1"), 60); err != nil {
		t.Fatalf("Put: %v", err)
	}
	if err := store.Update(ctx, ns, "key1", []byte("v2")); err != nil {
		t.Fatalf("Update: %v", err)
	}

	s := store.(*inMemoryStore)
	fk := s.getFormattedKey(ns, "key1")
	s.mu.RLock()
	expiresAt := s.data[fk].expiresAt
	s.mu.RUnlock()
	if expiresAt.IsZero() {
		t.Error("expected TTL to be preserved across Update")
	}
}

func (ts *ServiceTestSuite) TestDelete() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if err := store.Put(ctx, ns, "key1", []byte("v1"), 0); err != nil {
		t.Fatalf("Put: %v", err)
	}
	if err := store.Delete(ctx, ns, "key1"); err != nil {
		t.Fatalf("Delete: %v", err)
	}
	got, err := store.Get(ctx, ns, "key1")
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	if got != nil {
		t.Errorf("Get() after Delete = %v, want nil", got)
	}

	// Deleting a non-existent key is a no-op, not an error.
	if err := store.Delete(ctx, ns, "does-not-exist"); err != nil {
		t.Errorf("Delete(missing) error = %v, want nil", err)
	}
}

func (ts *ServiceTestSuite) TestTake() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if got, err := store.Take(ctx, ns, "missing"); err != nil || got != nil {
		t.Errorf("Take(missing) = (%v, %v), want (nil, nil)", got, err)
	}

	if err := store.Put(ctx, ns, "key1", []byte("v1"), 0); err != nil {
		t.Fatalf("Put: %v", err)
	}
	got, err := store.Take(ctx, ns, "key1")
	if err != nil {
		t.Fatalf("Take: %v", err)
	}
	if string(got) != "v1" {
		t.Errorf("Take() = %q, want v1", got)
	}

	// Second take returns nil since the key was removed.
	got, err = store.Take(ctx, ns, "key1")
	if err != nil || got != nil {
		t.Errorf("Take() after removal = (%v, %v), want (nil, nil)", got, err)
	}
}

func (ts *ServiceTestSuite) TestExtendTTL() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if err := store.ExtendTTL(ctx, ns, "missing", 60); !errors.Is(err, providers.ErrRuntimeStoreKeyNotFound) {
		t.Errorf("ExtendTTL(missing) error = %v, want ErrRuntimeStoreKeyNotFound", err)
	}

	if err := store.Put(ctx, ns, "key1", []byte("v1"), 5); err != nil {
		t.Fatalf("Put: %v", err)
	}
	if err := store.ExtendTTL(ctx, ns, "key1", 3600); err != nil {
		t.Fatalf("ExtendTTL: %v", err)
	}

	s := store.(*inMemoryStore)
	fk := s.getFormattedKey(ns, "key1")
	s.mu.RLock()
	expiresAt := s.data[fk].expiresAt
	s.mu.RUnlock()
	if time.Until(expiresAt) < 30*time.Minute {
		t.Errorf("expiresAt = %v, want extended to ~1h from now", expiresAt)
	}
}

func (ts *ServiceTestSuite) TestNamespaceIsolation() {
	t := ts.T()
	store := Initialize("dep-1")
	ctx := context.Background()

	if err := store.Put(ctx, providers.NamespaceFlow, "key1", []byte("flow-value"), 0); err != nil {
		t.Fatalf("Put: %v", err)
	}
	if err := store.Put(ctx, providers.NamespaceAuthzCode, "key1", []byte("authz-value"), 0); err != nil {
		t.Fatalf("Put: %v", err)
	}

	flowVal, _ := store.Get(ctx, providers.NamespaceFlow, "key1")
	authzVal, _ := store.Get(ctx, providers.NamespaceAuthzCode, "key1")
	if string(flowVal) != "flow-value" {
		t.Errorf("flow namespace value = %q, want flow-value", flowVal)
	}
	if string(authzVal) != "authz-value" {
		t.Errorf("authz namespace value = %q, want authz-value", authzVal)
	}
}

type ServiceTestSuite struct {
	suite.Suite
}

func TestServiceTestSuite(t *testing.T) {
	suite.Run(t, new(ServiceTestSuite))
}
