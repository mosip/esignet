/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package jsonld loads pinned JSON-LD contexts and canonicalizes documents
// (URDNA2015) for Linked Data proof verification. Remote contexts are
// fetched only when their URL matches a configured allow-list prefix.
package jsonld

import (
	"bytes"
	"context"
	"embed"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"net/url"
	"os"
	"path"
	"strings"
	"time"

	"github.com/piprate/json-gold/ld"

	"github.com/mosip/esignet/internal/vc/ldp/fetch"
)

// manifest maps context URLs to files in the same directory.
const manifest = "index.json"

const contextAccept = "application/ld+json, application/json"

//go:embed contexts
var embedded embed.FS

// ErrContextNotAllowed reports a context that is neither pinned nor allowed
// for remote fetch.
var ErrContextNotAllowed = errors.New("JSON-LD context not allowed")

// Options configures a Loader.
type Options struct {
	// Dir holds extra pinned contexts: an index.json mapping context URLs to
	// file names, plus those files. Empty means embedded contexts only.
	Dir string
	// RemoteAllowlist lists URL prefixes that may be fetched. Empty means none.
	RemoteAllowlist []string
	Fetcher         *fetch.Fetcher
	CacheTTL        time.Duration
	CacheSize       int
	// MaxBlankNodes caps the blank nodes Canonicalize accepts.
	MaxBlankNodes int
}

// Loader resolves JSON-LD contexts from the pinned set, then from
// allow-listed remote URLs.
type Loader struct {
	static        map[string]*ld.RemoteDocument
	allow         []string
	fetch         *fetch.Fetcher
	cache         *fetch.Cache[*ld.RemoteDocument]
	maxBlankNodes int
}

// NewLoader builds a Loader from the embedded contexts plus o.Dir.
func NewLoader(o Options) (*Loader, error) {
	l := &Loader{
		static:        map[string]*ld.RemoteDocument{},
		allow:         o.RemoteAllowlist,
		cache:         fetch.NewCache[*ld.RemoteDocument](o.CacheTTL, o.CacheSize),
		maxBlankNodes: o.MaxBlankNodes,
	}
	if o.Fetcher != nil {
		// A redirect must land on an allow-listed URL too.
		l.fetch = o.Fetcher.WithRedirectCheck(func(u, _ *url.URL) error {
			if !l.allowed(u.String()) {
				return fmt.Errorf("%w: redirect to %s", ErrContextNotAllowed, u)
			}
			return nil
		})
	}
	sub, err := fs.Sub(embedded, "contexts")
	if err != nil {
		return nil, err
	}
	if err := l.addPinned(sub); err != nil {
		return nil, fmt.Errorf("embedded contexts: %w", err)
	}
	if o.Dir != "" {
		if err := l.addPinned(os.DirFS(o.Dir)); err != nil {
			return nil, fmt.Errorf("contexts dir %s: %w", o.Dir, err)
		}
	}
	return l, nil
}

func (l *Loader) addPinned(fsys fs.FS) error {
	raw, err := fs.ReadFile(fsys, manifest)
	if err != nil {
		return err
	}
	var index map[string]string
	if err := json.Unmarshal(raw, &index); err != nil {
		return fmt.Errorf("%s: %w", manifest, err)
	}
	for u, file := range index {
		body, err := fs.ReadFile(fsys, path.Clean(file))
		if err != nil {
			return err
		}
		doc, err := parseContext(u, body)
		if err != nil {
			return err
		}
		l.static[u] = doc
	}
	return nil
}

func parseContext(u string, body []byte) (*ld.RemoteDocument, error) {
	doc, err := ld.DocumentFromReader(bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("context %s: %w", u, err)
	}
	if m, ok := doc.(map[string]any); !ok || m["@context"] == nil {
		return nil, fmt.Errorf("context %s: no top-level @context", u)
	}
	return &ld.RemoteDocument{DocumentURL: u, Document: doc}, nil
}

// Pinned reports whether u is in the pinned (embedded or ContextsDir) set.
func (l *Loader) Pinned(u string) bool {
	_, ok := l.static[u]
	return ok
}

// bind returns a json-gold DocumentLoader that carries ctx into remote
// fetches for one processing call.
func (l *Loader) bind(ctx context.Context) ld.DocumentLoader {
	return boundLoader{l: l, ctx: ctx}
}

type boundLoader struct {
	l   *Loader
	ctx context.Context
}

func (b boundLoader) LoadDocument(u string) (*ld.RemoteDocument, error) {
	return b.l.load(b.ctx, u)
}

func (l *Loader) load(ctx context.Context, u string) (*ld.RemoteDocument, error) {
	if doc, ok := l.static[u]; ok {
		return doc, nil
	}
	if !l.allowed(u) {
		return nil, fmt.Errorf("%w: %s", ErrContextNotAllowed, u)
	}
	if doc, ok := l.cache.Get(u); ok {
		return doc, nil
	}
	body, err := l.fetch.Get(ctx, u, contextAccept)
	if err != nil {
		return nil, err
	}
	doc, err := parseContext(u, body)
	if err != nil {
		return nil, err
	}
	l.cache.Put(u, doc)
	return doc, nil
}

func (l *Loader) allowed(u string) bool {
	if l.fetch == nil || l.fetch.CheckURL(u) != nil {
		return false
	}
	for _, prefix := range l.allow {
		if prefix != "" && strings.HasPrefix(u, prefix) {
			return true
		}
	}
	return false
}
