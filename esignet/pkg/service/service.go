// Package service implements business logic and defines the repository
// interface it depends on.
package service

import (
	"context"
	"fmt"
)

// Repository defines the data access methods required by the service.
type Repository interface {
	Example(ctx context.Context) (int32, error)
}

// Service provides business logic backed by a repository.
type Service struct {
	repo Repository
}

// New creates a Service with the given repository.
func New(repo Repository) *Service {
	return &Service{repo: repo}
}

// Example runs the example query via the repository.
func (s *Service) Example(ctx context.Context) (int32, error) {
	result, err := s.repo.Example(ctx)
	if err != nil {
		return 0, fmt.Errorf("query example: %w", err)
	}

	return result, nil
}
