// Package inmemory provides an in-memory runtime store implementation.
package inmemory

import "github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

// Initialize creates and returns a new InMemoryStore instance for the given deployment.
func Initialize(deploymentID string) providers.RuntimeStoreProvider {
	return newInMemoryStore(deploymentID)
}
