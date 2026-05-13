# syntax=docker/dockerfile:1

# ── Stage 1: dev ─────────────────────────────────────────────────────────────
# Used by `docker compose watch` during local development.
# The full Go toolchain is available so code is hot-reloaded with `go run`.
FROM golang:1.23-alpine AS dev

WORKDIR /app

# Pre-download modules using the module cache mount to speed up rebuilds.
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY . .

CMD ["go", "run", "./cmd/esignet"]

# ── Stage 2: builder ─────────────────────────────────────────────────────────
# Produces a statically-linked binary with no CGO dependencies.
FROM dev AS builder

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build \
      -trimpath \
      -ldflags="-s -w" \
      -o /bin/esignet \
      ./cmd/esignet

# ── Stage 3: production ───────────────────────────────────────────────────────
# Minimal Alpine image – only ca-certificates and tzdata are added so HTTPS
# calls and timezone-aware timestamps work correctly.
FROM alpine:3.21 AS production

RUN apk add --no-cache ca-certificates tzdata

# Run as a non-root user for defence-in-depth.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /bin/esignet /bin/esignet

EXPOSE 8088

ENTRYPOINT ["/bin/esignet"]
