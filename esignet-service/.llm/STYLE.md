# Go Style Guide

Project-agnostic Go style guide. Favor idiomatic Go over clever abstractions.

**Minimum Go version**: 1.23+ (examples use range-over-int, per-iteration loop variables, enhanced ServeMux routing, and iterators).
**Recommended Go version**: 1.26.0 (latest stable version)

## Philosophy

Write boring code. Prefer explicit over implicit. Optimize for reading, not writing. Design zero values to work without initialization. Keep functions small and interfaces smaller.

## Quick Reference

### Tools

```bash
go vet ./...                 # static analysis
gofmt -l .                   # check formatting
go fix ./...                 # modernize code
go mod tidy                  # sync dependencies
go test ./...                # run all tests
go test -race ./...          # with race detection
go build -o bin/app ./cmd/app
```

### Import Order

Four groups, blank line between each: stdlib, external, internal, side-effect.

```go
import (
    "context"
    "errors"
    "fmt"

    "github.com/google/uuid"

    "project/pkg/client"
    "project/pkg/service"

    _ "project/pkg/log"  // side-effect imports: own group with comment
)
```

Side-effect imports (`_`) go in a fourth group so `gofmt` won't sort them into the middle of internal imports.

### Naming Conventions

| Category       | Convention                          | Examples                            |
| -------------- | ----------------------------------- | ----------------------------------- |
| Exported types | PascalCase                          | `Storage`, `UserEvent`, `UserID`    |
| Unexported     | camelCase                           | `processItem`, `defaultTimeout`     |
| Interfaces     | PascalCase; `-er` for single-method | `Reader`, `Writer`, `Processor`     |
| Constants      | PascalCase                          | `DefaultTimeout`, `MaxRetries`      |
| Acronyms       | All-caps both cases                 | `userID`, `HTTPClient`, `parseURL`  |
| Files          | snake_case                          | `item_service.go`, `http_client.go` |
| Packages       | lowercase, single-word              | `rotate`, `auth`, `client`          |

### Package Naming

Short, lowercase, single-word. Name packages after the domain or concept they represent. The package name is part of the API; it provides context to everything it exports, so design the two together.

```go
// GOOD: short, descriptive, domain-oriented
package rotate
package auth
package client

// BAD: generic, multi-word, or utility-dump names
package utils
package helpers
package common
package httpHelpers
```

Avoid stuttering; the package name and its exports are read together. Let the package name carry context so exports don't repeat it.

```go
// GOOD: reads naturally
rotate.File       // not rotate.FileRotator
http.Client       // not http.HTTPClient
auth.Token        // not auth.AuthToken

// BAD: package name repeated in export
rotate.FileRotator
auth.AuthToken
http.HTTPClient
```

## Error Handling

### Basic Pattern

Return errors as last value. Check immediately. Wrap with context using `fmt.Errorf` and `%w`.

```go
func (s *Service) Get(ctx context.Context, id string) (*Item, error) {
    item, err := s.repo.Get(ctx, id)
    if err != nil {
        return nil, fmt.Errorf("get item %s: %w", id, err)
    }

    return item, nil
}
```

Error messages: lowercase, no punctuation, add context. Handle an error or propagate it; never both. Logging and returning creates duplicate noise up the call chain.

```go
// BAD: logs and returns; caller will likely log again
if err != nil {
    s.logger.Error("failed to get item", "error", err)
    return nil, fmt.Errorf("get item: %w", err)
}

// GOOD: propagate with context, let the caller decide
if err != nil {
    return nil, fmt.Errorf("get item %s: %w", id, err)
}
```

### Deferred Close Errors

For read-only resources (response bodies, read-only files, network connections), close errors are almost never actionable. Explicitly discard them with `_ =` to satisfy `errcheck` and signal intent:

```go
// GOOD: explicit discard for read-only cleanup
defer func() { _ = resp.Body.Close() }()
defer func() { _ = f.Close() }()

// BAD: unchecked — errcheck will flag this
defer resp.Body.Close()
```

For **writable** resources where close can indicate data loss (buffered file writes, database transactions), check the error:

```go
// Writable file: close error means data may not have flushed
defer func() {
    if err := f.Close(); err != nil {
        slog.Error("closing output file", "error", err)
    }
}()
```

### Sentinel Errors

For expected error conditions. Check with `errors.Is`.

```go
var (
    ErrNotFound     = errors.New("not found")
    ErrUnauthorized = errors.New("unauthorized")
    ErrInvalidInput = errors.New("invalid input")
)

if errors.Is(err, ErrNotFound) {
    // handle not found
}
```

### Custom Error Types

For errors that carry additional data. Check with `errors.AsType`.

```go
type ValidationError struct {
    Field string
    Reason string
}

func (e *ValidationError) Error() string {
    return fmt.Sprintf("validation failed: %s: %s", e.Field, e.Reason)
}

// Usage
if valErr, ok := errors.AsType[*ValidationError](err); ok {
    log.Error("validation failed", "field", valErr.Field)
}
```

### When to Panic

Only for truly unrecoverable situations: `init()` setup failures or programmer errors (impossible states, violated invariants). Never in library code for operational errors.

```go
// init: include the error for debuggability
func init() {
    if err := setupTransport(); err != nil {
        panic(fmt.Sprintf("transport initialization failed: %v", err))
    }
}

// Impossible state after exhaustive handling
switch status {
case StatusActive, StatusPending, StatusCompleted:
    // ...
default:
    panic(fmt.Sprintf("unhandled status: %v", status))
}
```

## Struct Patterns

### Struct Literal Formatting

Always use multi-line format for struct literals with two or more fields. Single-line is only acceptable for one field.

```go
// GOOD: single field, inline is fine
item := Item{ID: "abc"}

// GOOD: multiple fields, one per line
item := Item{
    ID:   "abc",
    Name: "example",
}

// BAD: multiple fields crammed on one line
item := Item{ID: "abc", Name: "example"}
```

### Constructor Pattern

Name constructors `New` as the package name already provides context. Callers read `service.New(...)`, not `service.NewService(...)`. This applies to the primary type in a package.

```go
type Service struct {
    repo   Repository
    logger *slog.Logger
    timeout time.Duration
}

func New(repo Repository, logger *slog.Logger) *Service {
    return &Service{
        repo:    repo,
        logger:  logger,
        timeout: 10 * time.Second,
    }
}
```

### Functional Options Pattern

For complex configuration.

```go
type Option func(*Service)

func WithTimeout(d time.Duration) Option {
    return func(s *Service) { s.timeout = d }
}

func New(repo Repository, opts ...Option) *Service {
    s := &Service{
        repo:    repo,
        timeout: 10 * time.Second,
    }
    for _, opt := range opts {
        opt(s)
    }

    return s
}

// Usage
svc := New(repo, WithTimeout(5*time.Second))
```

### Struct Embedding

For shared behavior across implementations.

```go
type BaseProcessor struct {
    logger *slog.Logger
    config Config
}

func (b *BaseProcessor) Validate(input Input) error {
    if input.ID == "" {
        return errors.New("input id required")
    }

    return nil
}

type PDFProcessor struct {
    BaseProcessor  // promotes Validate method
    pdfConfig PDFConfig
}

func (p *PDFProcessor) Process(input Input) error {
    if err := p.Validate(input); err != nil {
        return err
    }
    // PDF-specific logic
}

// Note: if PDFProcessor later defines its own Validate method,
// it silently shadows BaseProcessor.Validate.
```

### Zero Values

Design structs to be usable without initialization when possible.

```go
// GOOD: zero value works
var cache Cache
cache.Set("key", "value")  // works even if not initialized

// Implementation
type Cache struct {
    mu    sync.RWMutex
    items map[string]string
}

func (c *Cache) Set(key, value string) {
    c.mu.Lock()
    defer c.mu.Unlock()
    if c.items == nil {
        c.items = make(map[string]string)
    }
    c.items[key] = value
}
```

## Type Safety & Interfaces

### Never Use `any` Unless Absolutely Necessary

Order of preference: Fully typed → Generics → Semi-typed → `any` (last resort)

```go
// BAD: any everywhere
func ProcessData(data any) any {
    // No type safety, requires type assertions everywhere
    return data
}

func FindItem(items []any, id string) any {
    for _, item := range items {
        if m, ok := item.(map[string]any); ok {
            if m["id"] == id {
                return item
            }
        }
    }
    return nil
}

// GOOD: type constraints enable meaningful operations
type Sizer interface {
    Size() int
}

func Largest[T Sizer](items []T) (T, bool) {
    var zero T
    if len(items) == 0 {
        return zero, false
    }

    max := items[0]
    for _, item := range items[1:] {
        if item.Size() > max.Size() {
            max = item
        }
    }

    return max, true
}

// GOOD: interface constraints
type HasID interface {
    GetID() string
}

func FindItem[T HasID](items []T, id string) *T {
    for i := range items {
        if items[i].GetID() == id {
            return &items[i]
        }
    }

    return nil
}

// GOOD: multiple constraints
type Identifiable interface {
    GetID() string
}

type Timestamped interface {
    GetCreatedAt() time.Time
}

func SortByCreation[T interface{ Identifiable; Timestamped }](items []T) []T {
    sorted := make([]T, len(items))
    copy(sorted, items)
    sort.Slice(sorted, func(i, j int) bool {
        return sorted[i].GetCreatedAt().Before(sorted[j].GetCreatedAt())
    })

    return sorted
}

// GOOD: generics for container types (e.g., thread-safe wrappers, caches)
type Pair[K, V any] struct {
    Key   K
    Value V
}

// ACCEPTABLE: generic utility functions reduce boilerplate for
// common transformations. Use judiciously; a plain for loop
// is often clearer for simple cases.
func Map[T, U any](items []T, fn func(T) U) []U {
    result := make([]U, len(items))
    for i, item := range items {
        result[i] = fn(item)
    }

    return result
}

func Filter[T any](items []T, predicate func(T) bool) []T {
    var result []T
    for _, item := range items {
        if predicate(item) {
            result = append(result, item)
        }
    }

    return result
}

// Usage: type inference works automatically
ids := Map(items, func(item Item) string { return item.ID })
activeItems := Filter(items, func(item Item) bool { return item.Active })
```

### Order of Preference Examples

```go
// 1. BEST: Fully typed
type ProcessorConfig struct {
    Timeout  time.Duration
    Retries  int
    Endpoint string
}

// 2. ACCEPTABLE: Semi-typed with known key types
type ProcessorRegistry map[string]ProcessorConfig
type HandlerMap map[string]func(context.Context, Item) error

// 3. ACCEPTABLE: Semi-typed with union-like values
type Status string

const (
    StatusPending    Status = "pending"
    StatusProcessing Status = "processing"
    StatusCompleted  Status = "completed"
)

type StatusMap map[string]Status

// 4. LAST RESORT: Semi-typed with any
type DynamicHandlers map[string]any // Only when handlers have varying signatures

// 5. AVOID: Fully untyped
// type BadMap map[any]any // DON'T DO THIS
```

### JSON: Always Use Structs, Not Maps

```go
// BAD: map[string]any
func Handle(data map[string]any) error {
    name, ok := data["name"].(string)  // type assertions everywhere
    if !ok {
        return errors.New("invalid name")
    }
}

// GOOD: struct types
type Request struct {
    Name      string    `json:"name"`
    Age       int       `json:"age,omitzero"`
    Email     string    `json:"email,omitempty"`
    CreatedAt time.Time `json:"created_at,omitzero"`
}

func Handle(req Request) error {
    // type-safe, validated at unmarshal time
}
```

Only use `map[string]any` when structure is truly dynamic (plugin configs, user-defined metadata).

### Optional Pointer Fields

Go 1.26 extended `new` to accept an expression, eliminating the need to declare a variable just to take its address. Use this for optional pointer fields in structs.

```go
type Person struct {
    Name string `json:"name"`
    Age  *int   `json:"age,omitempty"`
}

// GOOD: inline with new(expr)
p := Person{
    Name: name,
    Age:  new(yearsSince(born)),
}

// BAD: unnecessary intermediate variable
age := yearsSince(born)
p := Person{
    Name: name,
    Age:  &age,
}
```

### Interface Design

Define interfaces where used (consumer side). Keep them small. Accept interfaces, return concrete types.

```go
// In service package
type Repository interface {
    Get(ctx context.Context, id string) (*Item, error)
    Save(ctx context.Context, item *Item) error
}

type Service struct {
    repo Repository  // accepts interface
}

func New(repo Repository) *Service {  // returns concrete type
    return &Service{
        repo: repo,
    }
}
```

### Interface Composition

```go
type Reader interface {
    Read(ctx context.Context, id string) ([]byte, error)
}

type Writer interface {
    Write(ctx context.Context, id string, data []byte) error
}

// Compose interfaces
type ReadWriter interface {
    Reader
    Writer
}

type Storage struct {
    rw ReadWriter  // accepts composed interface
}
```

### Verify Interface Implementation

```go
var _ Repository = (*PostgresRepo)(nil)  // compile-time check
```

## Concurrency Patterns

Prefer synchronization primitives over channels for simple mutual exclusion. Channels are for communication and orchestration; `sync.Mutex` is for protecting shared state. Using the simpler tool makes intent clearer.

**When to use which pattern:**

- **errgroup**: default choice for bounded concurrent work with shared error handling
- **Worker pool**: when you need explicit concurrency limits for resource-sensitive operations (DB connections, rate-limited APIs)
- **Result channels**: when goroutines produce heterogeneous values you need to collect individually

### Worker Pool

```go
func (s *Service) ProcessBatch(ctx context.Context, items []Item) error {
    const concurrency = 10
    taskCh := make(chan Item, concurrency*2)
    var wg sync.WaitGroup

    // Start workers
    for i := 0; i < concurrency; i++ {
        wg.Go(func() {
            for item := range taskCh {
                if err := s.process(ctx, item); err != nil {
                    s.logger.Error("process failed", "error", err)
                }
            }
        })
    }

    // Send work
    go func() {
        defer close(taskCh)
        for _, item := range items {
            select {
            case <-ctx.Done():
                return
            case taskCh <- item:
            }
        }
    }()

    wg.Wait()

    return ctx.Err()
}

// Note: individual item errors are logged, not returned. For operations
// where errors must be collected, prefer errgroup or aggregate into a slice.
```

### Parallel Fetch with Result Channels

```go
func (s *Service) FetchBoth(ctx context.Context, id string) (*Data, error) {
    type result struct {
        data *Response
        err  error
    }

    ch1 := make(chan result, 1)
    ch2 := make(chan result, 1)

    go func() {
        data, err := s.fetchOne(id)
        ch1 <- result{data, err}
    }()

    go func() {
        data, err := s.fetchTwo(id)
        ch2 <- result{data, err}
    }()

    var r1, r2 *Response
    for range 2 {
        select {
        case res := <-ch1:
            if res.err != nil {
                return nil, res.err
            }
            r1 = res.data
        case res := <-ch2:
            if res.err != nil {
                return nil, res.err
            }
            r2 = res.data
        }
    }

    return &Data{
        One: *r1,
        Two: *r2,
    }, nil
}

// For simple parallel fetches like this, errgroup (below) is often cleaner.
```

### Errgroup for Concurrent Operations

```go
import "golang.org/x/sync/errgroup"

func (s *Service) ProcessAll(ctx context.Context, items []Item) error {
    g, ctx := errgroup.WithContext(ctx)
    for _, item := range items {
        g.Go(func() error {
            return s.process(ctx, item)
        })
    }

    return g.Wait()
}
```

### Thread-Safe State

```go
type Cycle[T any] struct {
    items []T
    index int
    mu    sync.Mutex
}

func (c *Cycle[T]) Next() (T, bool) {
    c.mu.Lock()
    defer c.mu.Unlock()

    if len(c.items) == 0 {
        var zero T
        return zero, false
    }

    item := c.items[c.index]
    c.index = (c.index + 1) % len(c.items)

    return item, true
}
```

## HTTP Client Pattern

Structure: `pkg/client/{client.go, option.go, proxy.go, cookie.go, ...}`

Uses [`fphttp`](https://github.com/aarock1234/fphttp), a fork of `net/http` with TLS fingerprinting, HTTP/1.1 header ordering, and HTTP/2 connection fingerprinting built in. The client wraps `*fphttp.Client` with functional options, typed proxy config, custom cookie handling, and manual redirect following. Import fphttp as `http` throughout: `import http "github.com/aarock1234/fphttp"`.

### client.go

Functional options configure the client. Sensible defaults mean callers only override what they need.

```go
package client

// Client is an HTTP client with TLS fingerprinting, cookie handling,
// and proxy support.
type Client struct {
    http      *http.Client
    jar       *CookieJar
    proxy     *Proxy
    browser   Browser
    platform  Platform
    // ...
}

// New creates a new Client with the given options.
func New(opts ...Option) (*Client, error) {
    c := &Client{
        browser:  BrowserChrome,
        platform: PlatformWindows,
        // sensible defaults...
    }

    for _, opt := range opts {
        opt(c)
    }

    // resolve fphttp fingerprint from browser+platform, build transport...

    return c, nil
}

// Do sends an HTTP request. Redirects are followed manually so that
// Set-Cookie headers from intermediate responses are captured.
func (c *Client) Do(req *http.Request) (*http.Response, error) {
    // manual redirect loop with cookie capture
}
```

### option.go

One option per concern. Each returns an `Option` closure.

```go
package client

// Option configures a Client.
type Option func(*Client)

func WithProxy(p *Proxy) Option {
    return func(c *Client) { c.proxy = p }
}

func WithBrowser(b Browser) Option {
    return func(c *Client) { c.browser = b }
}

func WithPlatform(p Platform) Option {
    return func(c *Client) { c.platform = p }
}

func WithCookieExtractor(fn CookieExtractor) Option {
    return func(c *Client) { c.extractCookies = fn }
}

func WithDefaultHeaderOverrides(h http.Header) Option {
    return func(c *Client) { c.defaultHeaderOverrides = h.Clone() }
}
```

### proxy.go

Typed `Proxy` struct instead of raw `*url.URL`. Keeps host, port, credentials, and scheme separate for cleaner access and conversion.

```go
package client

// Proxy holds a parsed proxy configuration.
type Proxy struct {
    Scheme   ProxyScheme
    Host     string
    Port     string
    Username string
    Password string
}

// URL returns the proxy as a *url.URL suitable for http.Transport.Proxy.
func (p *Proxy) URL() *url.URL {
    u := &url.URL{
        Scheme: string(p.Scheme),
        Host:   p.Host + ":" + p.Port,
    }

    if p.Username != "" && p.Password != "" {
        u.User = url.UserPassword(p.Username, p.Password)
    }

    return u
}

// ParseProxy parses a "host:port" or "host:port:user:pass" string.
// An empty string returns (nil, nil).
func ParseProxy(proxy string, scheme ProxyScheme) (*Proxy, error) {
    proxy = strings.TrimSpace(proxy)
    if proxy == "" {
        return nil, nil
    }

    split := strings.Split(proxy, ":")
    if len(split) != 2 && len(split) != 4 {
        return nil, fmt.Errorf("got %d proxy parts, want 2 or 4: %v", len(split), split)
    }

    return &Proxy{
        Scheme: scheme,
        Host:   split[0],
        Port:   split[1],
        // username/password from split[2:4] if present
    }, nil
}

// ImportProxies reads proxy configs from a file, one per line.
func ImportProxies(filename string, scheme ProxyScheme) ([]*Proxy, error) {
    f, err := os.Open(filename)
    if err != nil {
        return nil, fmt.Errorf("opening proxy file: %w", err)
    }
    defer func() { _ = f.Close() }()

    var proxies []*Proxy
    scanner := bufio.NewScanner(f)
    for scanner.Scan() {
        line := strings.TrimSpace(scanner.Text())
        if line == "" {
            continue
        }

        proxy, err := ParseProxy(line, scheme)
        if err != nil {
            return nil, fmt.Errorf("parsing proxy line %q: %w", line, err)
        }

        proxies = append(proxies, proxy)
    }

    if err := scanner.Err(); err != nil {
        return nil, fmt.Errorf("scanning proxy file: %w", err)
    }

    return proxies, nil
}
```

### Usage Example

Downstream packages compose the client with the options they need:

```go
type APIClient struct {
    http    *client.Client
    baseURL string
}

func New(cfg *Config) (*APIClient, error) {
    var proxy client.Proxy
    if cfg.Proxy != "" {
        parsed, err := client.ParseProxy(cfg.Proxy, client.ProxySchemeHTTP)
        if err != nil {
            return nil, fmt.Errorf("parsing proxy: %w", err)
        }

        proxy = *parsed
    }

    opts := []client.Option{
        client.WithPlatform(client.PlatformWindows),
        client.WithCookieExtractor(customExtractor),
    }

    if cfg.Proxy != "" {
        opts = append(opts, client.WithProxy(&proxy))
    }

    httpClient, err := client.New(opts...)
    if err != nil {
        return nil, fmt.Errorf("creating http client: %w", err)
    }

    return &APIClient{
        http:    httpClient,
        baseURL: cfg.BaseURL,
    }, nil
}

// doRequest is a generic top-level function because Go does not support
// generic methods. This avoids `any` in the public API; the caller
// gets a fully typed *T back with no type assertions.
func doRequest[T any](ctx context.Context, c *APIClient, path string) (*T, error) {
    req, err := http.NewRequestWithContext(ctx, "GET", c.baseURL+path, nil)
    if err != nil {
        return nil, fmt.Errorf("creating request: %w", err)
    }
    req.Header.Set("Accept", "application/json")

    res, err := c.http.Do(req)
    if err != nil {
        return nil, fmt.Errorf("executing request: %w", err)
    }
    defer func() { _ = res.Body.Close() }()

    if res.StatusCode != http.StatusOK {
        return nil, fmt.Errorf("unexpected status: %d", res.StatusCode)
    }

    var result T
    if err := json.NewDecoder(res.Body).Decode(&result); err != nil {
        return nil, fmt.Errorf("decoding response: %w", err)
    }

    return &result, nil
}

// Usage
item, err := doRequest[Item](ctx, client, "/items/123")
```

## Code Organization

### Package Structure

```
project/
├── cmd/
│   └── server/
│       └── main.go        # entrypoint
└── pkg/
    ├── handler/            # HTTP handlers
    ├── service/            # business logic
    ├── repository/         # data access
    ├── model/              # shared types
    └── log/                # side-effect init
```

`pkg/` is the default for all project packages. Only use `internal/` when the module is published for external consumers; this will be explicitly communicated.

### Layered Architecture

**Repository Layer** - Data access

```go
type Repository interface {
    Get(ctx context.Context, id string) (*Item, error)
    Save(ctx context.Context, item *Item) error
}

type PgRepo struct {
    db     *sql.DB
    logger *slog.Logger
}

func New(db *sql.DB, logger *slog.Logger) *PgRepo {
    return &PgRepo{
        db:     db,
        logger: logger,
    }
}
```

**Service Layer** - Business logic

```go
type Service struct {
    repo   Repository
    logger *slog.Logger
}

func New(repo Repository, logger *slog.Logger) *Service {
    return &Service{
        repo:   repo,
        logger: logger,
    }
}

func (s *Service) Process(ctx context.Context, id string) error {
    item, err := s.repo.Get(ctx, id)
    if err != nil {
        return fmt.Errorf("get item: %w", err)
    }

    // business logic here

    return s.repo.Save(ctx, item)
}
```

**Handler Layer** - HTTP/transport

```go
type Handler struct {
    service *Service
    logger  *slog.Logger
}

func New(service *Service, logger *slog.Logger) *Handler {
    return &Handler{
        service: service,
        logger:  logger,
    }
}

func (h *Handler) Get(w http.ResponseWriter, r *http.Request) {
    id := r.PathValue("id")

    item, err := h.service.Get(r.Context(), id)
    if err != nil {
        if errors.Is(err, ErrNotFound) {
            http.Error(w, "not found", http.StatusNotFound)
            return
        }
        h.logger.Error("get failed", "error", err)
        http.Error(w, "internal error", http.StatusInternalServerError)
        return
    }

    w.Header().Set("Content-Type", "application/json")
    if err := json.NewEncoder(w).Encode(item); err != nil {
        h.logger.Error("encode response", "error", err)
    }
}
```

### Dependency Wiring

Prefer manual wiring. Keep it explicit in `main.go`. Extract the body into a `run()` function that returns an error so that `defer` statements execute on all exit paths. Reserve `os.Exit` for the top-level `main()`.

```go
func main() {
    if err := run(); err != nil {
        slog.Error("fatal error", "error", err)
        os.Exit(1)
    }
}

func run() error {
    logger := slog.Default()

    db, err := sql.Open("postgres", os.Getenv("DATABASE_URL"))
    if err != nil {
        return fmt.Errorf("open database: %w", err)
    }
    defer func() { _ = db.Close() }()

    repo := repository.New(db, logger)
    service := service.New(repo, logger)
    handler := handler.New(service, logger)

    mux := http.NewServeMux()
    mux.HandleFunc("GET /items/{id}", handler.Get)
    mux.HandleFunc("POST /items", handler.Create)

    server := &http.Server{
        Addr:         ":8080",
        Handler:      mux,
        ReadTimeout:  10 * time.Second,
        WriteTimeout: 10 * time.Second,
    }

    logger.Info("server starting", "port", "8080")
    if err := server.ListenAndServe(); err != nil {
        return fmt.Errorf("server: %w", err)
    }

    return nil
}
```

## Context Propagation

First parameter for functions doing I/O. Pass through the entire call chain. Use for cancellation, timeouts, and request-scoped values.

**Never store `context.Context` in a struct.** Pass it as a function argument so each call gets its own cancellation scope.

```go
// BAD: context stored in struct
type Service struct {
    ctx context.Context  // stale context, unclear lifecycle
}

// GOOD: context passed per-call
func (s *Service) Process(ctx context.Context, id string) error {
    ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
    defer cancel()

    return s.doWork(ctx, id)
}
```

Use `context.Background()` at the top of call chains (`main()`, test setup). Use `context.TODO()` as a placeholder when unsure which context to use. Never pass `nil`; use `context.TODO()` instead.

## Logging

Use `log/slog` with structured logging. Initialize default logger via side-effect import.

```go
// pkg/log/log.go
package log

import (
    "log/slog"
    "os"

    "github.com/lmittmann/tint"
)

func init() {
    slog.SetDefault(slog.New(tint.NewHandler(os.Stdout, nil)))
}
```

```go
// main.go
import (
    "log/slog"

    _ "project/pkg/log"
)

func main() {
    slog.Info("starting application")
}
```

Use structured attributes. All log messages lowercase.

```go
s.logger.InfoContext(ctx, "item processed",
    slog.Duration("duration", elapsed),
    slog.String("item_id", id),
)

s.logger.ErrorContext(ctx, "failed to save",
    slog.Any("error", err),
)
```

## Constants and Enums

Use typed constants for enum behavior. Prefer string-based enums when values are serialized, logged, or displayed; they're self-documenting. Use integer enums with `iota` when values are purely internal or performance-critical.

```go
// String-based enum
type Status string

const (
    StatusPending   Status = "pending"
    StatusActive    Status = "active"
    StatusCompleted Status = "completed"
)

// Integer-based enum
type Priority int

const (
    PriorityLow Priority = iota
    PriorityMedium
    PriorityHigh
    PriorityCritical
)
```

## Iterators

Use `iter.Seq` and `iter.Seq2` to make custom types rangeable. Prefer iterators over returning full slices when the caller may not need all elements.

```go
import "iter"

// Seq for single-value iteration
func (s *Set[E]) All() iter.Seq[E] {
    return func(yield func(E) bool) {
        for e := range s.m {
            if !yield(e) {
                return
            }
        }
    }
}

// Seq2 for key-value iteration
func (s *Store) All() iter.Seq2[string, Item] {
    return func(yield func(string, Item) bool) {
        for k, v := range s.items {
            if !yield(k, v) {
                return
            }
        }
    }
}

// Usage: range directly over custom types
for item := range mySet.All() {
    process(item)
}

for key, item := range store.All() {
    fmt.Println(key, item)
}
```

Compose iterators for lazy transformation chains instead of allocating intermediate slices.

```go
// Lazy filtering; no intermediate allocation
func FilterIter[V any](seq iter.Seq[V], pred func(V) bool) iter.Seq[V] {
    return func(yield func(V) bool) {
        for v := range seq {
            if pred(v) {
                if !yield(v) {
                    return
                }
            }
        }
    }
}

// Usage
for item := range FilterIter(mySet.All(), isActive) {
    process(item)
}
```

## Development Workflow

### Dependency Management

Prefer `go mod tidy` over `go get` for adding dependencies.

**Workflow:**

1. Add import to your `.go` file: `import "github.com/user/package"`
2. Run `go mod tidy` to fetch and add to `go.mod`

```bash
# PREFERRED: Import in code, then tidy
go mod tidy

# Use go get for specific versions
go get github.com/user/package@v1.2.3

# Use go get for upgrading dependencies
go get -u github.com/user/package

# Update all dependencies to latest minor/patch
go get -u ./...
```

**Why prefer `go mod tidy`:**

- Automatically manages both additions and removals
- Ensures `go.mod` and `go.sum` are in sync
- Removes unused dependencies
- Cleaner workflow: write code first, manage deps second
- Less error-prone than manual `go get` for each package

### Static Analysis

Use `go vet` to check for suspicious code without building. Faster than full build for catching common errors.

```bash
go vet ./...                 # all packages
go vet ./internal/services   # specific package
gofmt -l .                   # check formatting
go vet ./... && gofmt -l .   # combine checks
```

**Common issues `go vet` catches:**

- Printf format string mismatches
- Unreachable code
- Incorrect use of sync primitives
- Struct tags validation

**Additional analysis (requires explicit opt-in):**

- Shadow variables: `go vet -vettool=$(which shadow) ./...` or enable in `golangci-lint`

**When to use:**

- During development for quick validation
- In pre-commit hooks
- In CI/CD before running tests

If the project includes a `golangci-lint` configuration, use `golangci-lint run` instead of invoking individual tools; it aggregates `go vet`, formatting checks, and additional linters into a single pass.

Run `go fix ./...` periodically to modernize code — it rewrites to use the latest Go idioms and stdlib APIs automatically.

## Additional Conventions

### Newline Spacing

Use blank lines to separate logical groups within a function body. A `return` statement should always have a preceding blank line. Closely related statements; a variable definition and the loop or condition that immediately uses it, a fetch and the single assignment from it; stay together with no blank line. If three or more ungrouped statements stack up, split them into logical pairs.

```go
// GOOD: grouped by intent, return has breathing room
func Process(ctx context.Context, items []Item) (int, error) {
    total := 0
    for _, item := range items {
        total += item.Value
    }

    if err := validate(total); err != nil {
        return 0, fmt.Errorf("validating total: %w", err)
    }

    return total, nil
}

// BAD: everything jammed together
func Process(ctx context.Context, items []Item) (int, error) {
    total := 0
    for _, item := range items {
        total += item.Value
    }
    if err := validate(total); err != nil {
        return 0, fmt.Errorf("validating total: %w", err)
    }
    return total, nil
}
```

### Doc Comments

Exported types and functions must have doc comments. Comments begin with the name of the thing being described and are complete sentences ending with a period.

```go
// Service processes items according to business rules.
type Service struct { ... }

// Process validates and persists the given item. It returns
// an error if the item fails validation or cannot be saved.
func (s *Service) Process(ctx context.Context, item *Item) error { ... }
```

### Pointer vs Value Receivers

Use pointer receivers when the method mutates state, the struct is large, or for consistency when other methods on the type use pointer receivers. Use value receivers for small, immutable types like enums or thin wrappers.

```go
// Pointer: mutates state or large struct
func (s *Service) Process(ctx context.Context, id string) error { ... }

// Value: small, immutable type
func (s Status) String() string { return string(s) }
```

If any method on a type uses a pointer receiver, all methods on that type should use pointer receivers for consistency.

### Receiver Naming

Use one or two letter abbreviations consistent across all methods of a type. Do not use `this` or `self`.

```go
// GOOD: short, consistent
func (s *Service) Get(ctx context.Context, id string) (*Item, error) { ... }
func (s *Service) Save(ctx context.Context, item *Item) error { ... }

// BAD: verbose, Java-style
func (service *Service) Get(...) { ... }
func (self *Service) Save(...) { ... }
```

### Graceful Shutdown

Production servers should handle OS signals for clean shutdown. Since Go 1.26, `signal.NotifyContext` sets the cancel cause to the received signal, accessible via `context.Cause`.

```go
func main() {
    ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
    defer stop()

    server := &http.Server{
        Addr:    ":8080",
        Handler: mux,
    }

    go func() {
        if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
            slog.ErrorContext(ctx, "server error", "error", err)
        }
    }()

    <-ctx.Done()
    slog.InfoContext(ctx, "shutting down")

    shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    defer cancel()

    if err := server.Shutdown(shutdownCtx); err != nil {
        slog.ErrorContext(ctx, "shutdown error", "error", err)
    }
}
```
