# syntax=docker/dockerfile:1
# Build from test-app directory: docker build -t esignet:latest .
FROM golang:1.26-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . ./
RUN go build -o /out/engine ./cmd/engine

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates openssl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /out/engine ./engine
COPY data ./data
COPY docker-entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh && mkdir -p /app/keys
ENV THUNDERID_DATA_DIR=/app/data \
    THUNDERID_ISSUER=http://localhost:8080 \
    THUNDERID_SIGNING_KEY=/app/keys/signing.key \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
