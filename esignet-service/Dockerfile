# syntax=docker/dockerfile:1
# Build from esignet-service directory: docker build -t esignet:latest .
FROM golang:1.26-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . ./
RUN mkdir -p /out && go build -trimpath -ldflags="-s -w" -o /out/esignet.exe ./cmd/esignet

FROM debian:bookworm-slim

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

ARG ISSUER_URL=http://localhost:8080

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user=mosip

ARG DATA_DIR=/home/${container_user}/data
ARG SIGNING_KEY_PATH=/home/${container_user}/keys/signing.key

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_group=mosip

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_uid=1001

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_gid=1001

ENV ISSUER_URL_ENV=${ISSUER_URL}

ENV DATA_DIR_ENV=${DATA_DIR}

ENV SIGNING_KEY_PATH_ENV=${SIGNING_KEY_PATH}

# set working directory for the user
WORKDIR /home/${container_user}

# install packages and create user
RUN groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && chown -R ${container_user}:${container_user} /home/${container_user} \
    && apt-get update && apt-get install -y --no-install-recommends ca-certificates openssl && rm -rf /var/lib/apt/lists/*

COPY --from=build /out/esignet.exe /home/${container_user}/esignet.exe

COPY data /home/${container_user}/data

COPY docker-entrypoint.sh /home/${container_user}/entrypoint.sh

RUN chmod +x /home/${container_user}/entrypoint.sh \
    && mkdir -p /home/${container_user}/keys \
    && chown -R ${container_user}:${container_user_group} /home/${container_user}

# select container user for runtime
USER ${container_user_uid}:${container_user_gid}

ENV DATA_DIR=${DATA_DIR_ENV} \
    ISSUER_URL=${ISSUER_URL_ENV} \
    SIGNING_KEY_PATH=${SIGNING_KEY_PATH_ENV} \
    PORT=8080

EXPOSE 8080

ENTRYPOINT ["/home/mosip/entrypoint.sh"]

CMD ["/home/mosip/esignet.exe"]
