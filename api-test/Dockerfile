# syntax=docker/dockerfile:1
#
# api-test harness image — runs the conformance/api/e2e surfaces + consolidate
# via run-all.sh (see #2209; design: mosip/esignet#2120). Build
# from api-test/: docker build -t apitest-esignet:latest .
#
# One builder stage compiles everything ahead of time so the runtime image needs
# no Go toolchain. It spans two Go modules — the stdlib-only parent (conformance,
# e2e, consolidate, cfg) and the nested api/ module pinning godog+gjson — which
# is why the module files are copied and downloaded before the sources: the
# dependency layer then caches independently of every source edit. The godog
# module is built with `go test -c` into a standalone binary so the runtime needs
# no `go test`. Both modules emit into /out/bin; the names never collide.

FROM golang:1.26-bookworm AS builder
WORKDIR /src

# Module files first, so the dependency layers cache independently of any
# source edit. Directory changes go through WORKDIR rather than `cd` inside a
# RUN: Trivy flags the latter (DS-0013), and WORKDIR is what later layers inherit.
COPY go.mod ./
COPY api/go.mod api/go.sum ./api/
RUN go mod download
WORKDIR /src/api
RUN go mod download

WORKDIR /src

COPY cmd ./cmd
COPY internal ./internal
COPY api ./api
RUN mkdir -p /out/bin && \
    go build -trimpath -ldflags="-s -w" -o /out/bin/conformance ./cmd/conformance && \
    go build -trimpath -ldflags="-s -w" -o /out/bin/e2e ./cmd/e2e && \
    go build -trimpath -ldflags="-s -w" -o /out/bin/consolidate ./cmd/consolidate && \
    go build -trimpath -ldflags="-s -w" -o /out/bin/cfg ./cmd/cfg

# Same stage, second module: `go test -c` produces a standalone godog binary
# so the runtime image needs no Go toolchain. Both modules emit into /out/bin.
WORKDIR /src/api
RUN go test -c -trimpath -o /out/bin/api.test .

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
COPY --from=builder /out/bin/ ./bin/
# Everything the run reads that is not code: Gherkin features, the per-plugin
# configs, the e2e scenario specs and the conformance profiles. The tracked
# configs ship with the image so `docker run apitest-esignet -c
# data/config/config.mosip.json` works with nothing mounted but the suite plan
# config and the target URL. They hold no secrets — credentials arrive as env or
# a mounted config.local.json, both of which override these.
COPY data ./data
COPY run-all.sh ./
# A Windows checkout leaves CRLF line endings, which make the shebang line
# unresolvable — the container then exits with a bare "no such file or
# directory" naming a script that plainly exists. Normalising here keeps the
# image buildable from any checkout; a repo-wide .gitattributes would be the
# permanent fix.
RUN sed -i 's/\r$//' run-all.sh && chmod +x run-all.sh && mkdir -p out && chown -R apitest:apitest /app

ENV BIN_DIR=/app/bin
# The prebuilt api.test runs from /app rather than from the module directory it
# was compiled in, so point it at the features tree explicitly.
ENV API_FEATURES_DIR=/app/data/features
USER apitest:apitest

ENTRYPOINT ["./run-all.sh"]
