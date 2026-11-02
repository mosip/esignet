# syntax=docker/dockerfile:1
#
# api-test harness image — runs the conformance/api/e2e surfaces + consolidate
# via run-all.sh (see #2209; design: mosip/esignet#2120). Build
# from api-test/: docker build --target runtime -t apitest-esignet:latest .
#
# --target runtime is required, not optional: this file's LAST stage is
# `coverage` (below), and a plain `docker build` with no --target builds
# whatever stage is last — silently producing the multi-GB coverage image
# (full Go toolchain) under the apitest-esignet:latest tag instead of this
# slim one. Confirmed live 2026-09-03: an untargeted build came out 1.35GB
# with `go` on PATH, vs. ~249MB for a correctly `--target runtime` build.
# CI's build_dockers_apitest job (push-trigger.yml) also builds with no
# --target, so the Docker Hub-published apitest-esignet image is exposed to
# the same risk unless that job (or a reusable workflow it calls) already
# targets `runtime` explicitly.
#
# One builder stage compiles everything ahead of time so the runtime image needs
# no Go toolchain. It spans two Go modules — the stdlib-only parent (conformance,
# e2e, consolidate, cfg) and the nested api/ module pinning godog+gjson — which
# is why the module files are copied and downloaded before the sources: the
# dependency layer then caches independently of every source edit. The godog
# module is built with `go test -c` into a standalone binary so the runtime needs
# no `go test`. Both modules emit into /out/bin; the names never collide.
#
# The image also carries the mock MDS (MOSIP SBI device service) that the
# biometric scenarios capture from over the SBI loopback API. docker-entrypoint.sh
# starts it on 127.0.0.1 before handing off to run-all.sh. MOCK_MDS_VERSION picks
# both the Maven Central jar and the matching source tag its data files come from.

ARG MOCK_MDS_VERSION=1.3.1

FROM golang:1.27-bookworm AS builder
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

# Mock MDS. Maven Central publishes only the bare jar, so this stage assembles the
# layout the MDS expects in its working directory: lib/ (the jar plus its runtime
# classpath) from Maven Central, and application.properties, Biometric Devices/,
# Profile/ and files/ from the same release's source tarball. files/sdkDependeny
# (112MB of Windows SDK DLLs, never read by the MDS) is left out.
FROM maven:3.9-eclipse-temurin-21 AS mockmds
ARG MOCK_MDS_VERSION
WORKDIR /src
COPY mock-mds/pom.xml ./
RUN mvn -B -q -Dmock.mds.version=${MOCK_MDS_VERSION} \
      org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
      -DincludeScope=runtime -DoutputDirectory=/out/mock-mds/lib
ADD https://github.com/mosip/mosip-mock-services/archive/refs/tags/v${MOCK_MDS_VERSION}.tar.gz /tmp/mosip-mock-services.tar.gz
RUN tar -xzf /tmp/mosip-mock-services.tar.gz -C /out/mock-mds --strip-components=2 --wildcards \
      '*/MockMDS/application.properties' \
      '*/MockMDS/Biometric Devices/*' \
      '*/MockMDS/Profile/*' \
      '*/MockMDS/files/MockMDS/*' \
      '*/MockMDS/files/keys/*' \
      '*/MockMDS/files/images/*'

# The published mock-mds classes are compiled for Java 21 with --enable-preview,
# so they run on exactly a 21 JVM. Bookworm packages only 17; take Temurin's.
FROM eclipse-temurin:21-jre AS jre

FROM debian:bookworm-slim AS runtime

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
COPY run-all.sh docker-entrypoint.sh ./
# A Windows checkout leaves CRLF line endings, which make the shebang line
# unresolvable — the container then exits with a bare "no such file or
# directory" naming a script that plainly exists. Normalising here keeps the
# image buildable from any checkout; a repo-wide .gitattributes would be the
# permanent fix.
RUN sed -i 's/\r$//' run-all.sh docker-entrypoint.sh && chmod +x run-all.sh docker-entrypoint.sh && \
    mkdir -p out && chown -R apitest:apitest /app

COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:$PATH
# Owned by apitest: the entrypoint rewrites application.properties (port, IDA
# endpoints) on every start.
COPY --from=mockmds --chown=apitest:apitest /out/mock-mds /opt/mock-mds
ENV MOCK_MDS_HOME=/opt/mock-mds

ENV BIN_DIR=/app/bin
# The prebuilt api.test runs from /app rather than from the module directory it
# was compiled in, so point it at the features tree explicitly.
ENV API_FEATURES_DIR=/app/data/features
USER apitest:apitest

ENTRYPOINT ["./docker-entrypoint.sh"]
