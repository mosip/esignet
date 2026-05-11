# syntax=docker/dockerfile:1

# dev: docker compose watch (go run, full toolchain)
FROM golang:1.26 AS dev

WORKDIR /app

COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY . .

# builder: compiles the binary
FROM dev AS builder

RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /bin/template ./cmd/template

# production image
FROM alpine:3.23 AS production

RUN apk add --no-cache ca-certificates tzdata

COPY --from=builder /bin/template /bin/template

ENTRYPOINT ["/bin/template"]
