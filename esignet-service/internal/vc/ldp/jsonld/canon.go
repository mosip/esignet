/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package jsonld

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/piprate/json-gold/ld"
)

const nquads = "application/n-quads"

var (
	// ErrUndefinedTerm reports a property, type or identifier that JSON-LD
	// expansion would drop, so a proof would not cover it.
	ErrUndefinedTerm = errors.New("JSON-LD term does not expand to an absolute IRI")
	// ErrTooComplex reports a document with more blank nodes than allowed.
	ErrTooComplex = errors.New("JSON-LD document has too many blank nodes")
	// ErrContext reports a context that could not be loaded or is invalid.
	ErrContext = errors.New("JSON-LD context could not be loaded")
)

func (l *Loader) options(ctx context.Context) *ld.JsonLdOptions {
	opts := ld.NewJsonLdOptions("")
	opts.DocumentLoader = l.bind(ctx)
	return opts
}

// CheckSafe expands doc in safe mode. It fails if expansion would drop any
// property, and if any property, @type or @id is left as something RDF
// conversion drops (a relative IRI, or a blank node property). Either way the data would be outside the signature
// while still visible to anyone reading the JSON.
func (l *Loader) CheckSafe(ctx context.Context, doc map[string]any) error {
	_, err := l.expand(ctx, doc, true)
	return err
}

// expand returns the expanded form of doc, applying the CheckSafe rules when
// safe is set.
func (l *Loader) expand(ctx context.Context, doc map[string]any, safe bool) ([]any, error) {
	opts := l.options(ctx)
	opts.SafeMode = safe
	expanded, err := ld.NewJsonLdProcessor().Expand(deepCopy(doc), opts)
	if err != nil {
		var ldErr *ld.JsonLdError
		if safe && errors.As(err, &ldErr) && ldErr.Code == ld.InvalidProperty {
			return nil, fmt.Errorf("%w: %w", ErrUndefinedTerm, err)
		}
		return nil, classify(err)
	}
	if safe {
		if err := checkAbsolute(expanded); err != nil {
			return nil, err
		}
	}
	return expanded, nil
}

// classify tags context loading failures with ErrContext, keeping the cause
// (e.g. ErrContextNotAllowed) in the chain.
func classify(err error) error {
	var ldErr *ld.JsonLdError
	if errors.As(err, &ldErr) {
		switch ldErr.Code {
		case ld.LoadingRemoteContextFailed, ld.LoadingDocumentFailed, ld.InvalidRemoteContext:
			return fmt.Errorf("%w: %w", ErrContext, err)
		}
	}
	return err
}

func checkAbsolute(v any) error {
	switch t := v.(type) {
	case []any:
		for _, e := range t {
			if err := checkAbsolute(e); err != nil {
				return err
			}
		}
	case map[string]any:
		for k, e := range t {
			switch {
			case k == "@value":
				continue
			case k == "@type" || k == "@id":
				if err := checkIRIs(k, e); err != nil {
					return err
				}
				continue
			case !ld.IsKeyword(k) && !isPredicate(k):
				return fmt.Errorf("%w: property %q", ErrUndefinedTerm, k)
			}
			if err := checkAbsolute(e); err != nil {
				return err
			}
		}
	}
	return nil
}

// isPredicate reports whether RDF conversion keeps a property: an absolute
// IRI, not a blank node identifier.
func isPredicate(k string) bool {
	return !strings.HasPrefix(k, "_:") && ld.IsAbsoluteIri(k)
}

func checkIRIs(key string, v any) error {
	values, ok := v.([]any)
	if !ok {
		values = []any{v}
	}
	for _, e := range values {
		s, ok := e.(string)
		if !ok {
			continue
		}
		// Keywords (e.g. "@json") and blank node ids ("_:b0") are fine;
		// RDF conversion drops anything else that is not an absolute IRI.
		if !strings.HasPrefix(s, "@") && !ld.IsAbsoluteIri(s) {
			return fmt.Errorf("%w: %s %q", ErrUndefinedTerm, key, s)
		}
	}
	return nil
}

// Canonicalize returns the URDNA2015 (RDFC-1.0) N-Quads serialization of
// doc. Terms its context does not define are dropped, as JSON-LD requires.
func (l *Loader) Canonicalize(ctx context.Context, doc map[string]any) (string, error) {
	return l.canonicalize(ctx, doc, false)
}

// CanonicalizeSafe is Canonicalize with the CheckSafe rules applied to the
// same expansion: it fails instead of dropping anything.
func (l *Loader) CanonicalizeSafe(ctx context.Context, doc map[string]any) (string, error) {
	return l.canonicalize(ctx, doc, true)
}

func (l *Loader) canonicalize(ctx context.Context, doc map[string]any, safe bool) (string, error) {
	expanded, err := l.expand(ctx, doc, safe)
	if err != nil {
		return "", err
	}
	fixGraphContainers(expanded)
	// Expanded form has no context, so re-expansion inside ToRDF is a no-op.
	out, err := ld.NewJsonLdProcessor().ToRDF(expanded, l.options(ctx))
	if err != nil {
		return "", classify(err)
	}
	dataset, ok := out.(*ld.RDFDataset)
	if !ok {
		return "", fmt.Errorf("unexpected RDF output %T", out)
	}
	dropRelativeIRIs(dataset)
	if n := countBlankNodes(dataset); l.maxBlankNodes > 0 && n > l.maxBlankNodes {
		return "", fmt.Errorf("%w: %d > %d", ErrTooComplex, n, l.maxBlankNodes)
	}
	opts := l.options(ctx)
	opts.Algorithm = ld.AlgorithmURDNA2015
	opts.Format = nquads
	normalized, err := ld.NewJsonLdApi().Normalize(dataset, opts)
	if err != nil {
		return "", err
	}
	s, ok := normalized.(string)
	if !ok {
		return "", fmt.Errorf("unexpected normalized output %T", normalized)
	}
	return s, nil
}

// verifiableCredentialIRI is declared an @graph container by both VCDM 1.1
// and 2.0.
const verifiableCredentialIRI = "https://www.w3.org/2018/credentials#verifiableCredential"

// fixGraphContainers works around json-gold resolving container mappings
// against a term's scoped context instead of the active context (JSON-LD 1.1
// expansion, step 13): VCDM 2.0 declares verifiableCredential with
// "@context": null, so json-gold loses its @graph container and puts the
// enclosed credentials into the presentation's own graph, which changes the
// canonical form. Every value of the property must be a graph object; wrap
// the ones json-gold left bare. VCDM 1.1 values are already wrapped. Literal
// values (@value, including @json contents) are left as they are.
func fixGraphContainers(v any) {
	switch t := v.(type) {
	case []any:
		for _, e := range t {
			fixGraphContainers(e)
		}
	case map[string]any:
		for k, e := range t {
			if k == "@value" {
				continue
			}
			if k == verifiableCredentialIRI {
				values, _ := e.([]any)
				for i, val := range values {
					if node, ok := val.(map[string]any); ok && node["@graph"] == nil {
						values[i] = map[string]any{"@graph": []any{node}}
					}
				}
			}
			fixGraphContainers(e)
		}
	}
}

// dropRelativeIRIs removes quads json-gold builds from relative IRIs. JSON-LD
// to RDF skips a triple whose subject, predicate, object, graph name or
// literal datatype is not an absolute IRI; json-gold keeps it (an undefined
// type "FarmerCredential" becomes <FarmerCredential>), so its hash data would
// differ from other processors' (Titanium, jsonld.js).
func dropRelativeIRIs(ds *ld.RDFDataset) {
	for name, quads := range ds.Graphs {
		if name != "@default" && !ld.IsAbsoluteIri(name) {
			delete(ds.Graphs, name)
			continue
		}
		kept := quads[:0]
		for _, q := range quads {
			if wellFormed(q.Subject) && wellFormed(q.Predicate) && wellFormed(q.Object) && wellFormed(q.Graph) {
				kept = append(kept, q)
			}
		}
		ds.Graphs[name] = kept
	}
}

func wellFormed(n ld.Node) bool {
	switch t := n.(type) {
	case ld.IRI:
		return ld.IsAbsoluteIri(t.Value)
	case ld.Literal:
		return t.Datatype == "" || ld.IsAbsoluteIri(t.Datatype)
	default:
		return true
	}
}

func countBlankNodes(ds *ld.RDFDataset) int {
	seen := map[string]struct{}{}
	add := func(n ld.Node) {
		if n != nil && ld.IsBlankNode(n) {
			seen[n.GetValue()] = struct{}{}
		}
	}
	for _, quads := range ds.Graphs {
		for _, q := range quads {
			add(q.Subject)
			add(q.Object)
			add(q.Graph)
		}
	}
	return len(seen)
}

// deepCopy clones the JSON tree so json-gold can't alias or mutate the
// caller's document.
func deepCopy(v map[string]any) map[string]any {
	return copyValue(v).(map[string]any)
}

func copyValue(v any) any {
	switch t := v.(type) {
	case map[string]any:
		m := make(map[string]any, len(t))
		for k, e := range t {
			m[k] = copyValue(e)
		}
		return m
	case []any:
		s := make([]any, len(t))
		for i, e := range t {
			s[i] = copyValue(e)
		}
		return s
	default:
		return v
	}
}
