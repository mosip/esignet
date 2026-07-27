# syntax=docker/dockerfile:1
#
# api-test harness image — runs the conformance/bdd/e2e surfaces + consolidate
# via run-all.sh (see #2209; design: mosip/esignet#2120). Build
# from api-test/: docker build -t apitest-esignet:latest .
#
# Two build stages compile ahead of time so the runtime image needs no Go
# toolchain (README §"Build/test": the parent module is stdlib-only; bdd/ is a
# separate module pinning godog+gjson):
#   builder-main — stdlib-only orchestrator: conformance, e2e, consolidate.
#   builder-bdd  — the nested bdd/ module, compiled to a standalone test binary
#                  (`go test -c`) so godog runs without `go test` at runtime.

FROM golang:1.26-bookworm AS builder-main
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
COPY profiles ./profiles
RUN mkdir -p /out/bin && \
    go build -trimpath -ldflags="-s -w" -o /out/bin/conformance ./cmd/conformance && \
    go build -trimpath -ldflags="-s -w" -o /out/bin/e2e ./cmd/e2e && \
    go build -trimpath -ldflags="-s -w" -o /out/bin/consolidate ./cmd/consolidate

FROM golang:1.26-bookworm AS builder-bdd
WORKDIR /src/bdd
COPY bdd/go.mod bdd/go.sum ./
RUN go mod download
COPY bdd/ ./
RUN mkdir -p /out/bin && go test -c -trimpath -o /out/bin/bdd.test .

FROM debian:bookworm-slim

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd -g 1001 apitest && useradd -u 1001 -g apitest -m -s /usr/sbin/nologin apitest

WORKDIR /app
COPY --from=builder-main /out/bin/ ./bin/
COPY --from=builder-bdd /out/bin/bdd.test ./bin/bdd.test
COPY profiles ./profiles
COPY bdd/features ./bdd/features
COPY e2e-scenarios.json e2e-scenarios-sunbird.json ./
COPY run-all.sh ./
RUN chmod +x run-all.sh && mkdir -p out && chown -R apitest:apitest /app

ENV BIN_DIR=/app/bin
USER apitest:apitest

ENTRYPOINT ["./run-all.sh"]
