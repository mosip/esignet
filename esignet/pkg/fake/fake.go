// Package fake provides random data generation for testing and development.
package fake

import (
	"fmt"
	"math/rand/v2"
	"strings"
	"sync"
)

// Generator creates fake data for testing and development.
type Generator struct {
	mu sync.Mutex
}

// New creates a Generator.
func New() *Generator {
	return &Generator{}
}

// FirstName generates a random first name.
func (g *Generator) FirstName() string {
	return g.randomFrom(firstNames)
}

// LastName generates a random last name.
func (g *Generator) LastName() string {
	return g.randomFrom(lastNames)
}

// Email generates a random email address.
func (g *Generator) Email() string {
	return g.email(g.FirstName(), g.LastName(), g.randomFrom(emailDomains))
}

// StudentEmail generates an email address using the given first and last name
// with the provided .edu domain (e.g., johnd42@ucr.edu).
func (g *Generator) StudentEmail(firstName, lastName, domain string) string {
	first := strings.ToLower(firstName)
	last := strings.ToLower(lastName)
	if len(last) == 0 {
		last = "x"
	}
	return fmt.Sprintf("%s%s%d@%s", first, last[:1], g.randomInt(10, 100), domain)
}

func (g *Generator) email(firstName, lastName, domain string) string {
	return fmt.Sprintf("%s.%s%d@%s", firstName, lastName, g.randomInt(1000, 9999), domain)
}

func (g *Generator) randomFrom(slice []string) string {
	g.mu.Lock()
	defer g.mu.Unlock()
	return slice[rand.IntN(len(slice))]
}

func (g *Generator) randomInt(lo, hi int) int {
	g.mu.Lock()
	defer g.mu.Unlock()
	return lo + rand.IntN(hi-lo)
}
