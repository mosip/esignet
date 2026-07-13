/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package inmemory provides an in-memory runtime store implementation.
package inmemory

import "github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

// Initialize creates and returns a new InMemoryStore instance for the given deployment.
func Initialize(deploymentID string) providers.RuntimeStoreProvider {
	return newInMemoryStore(deploymentID)
}
