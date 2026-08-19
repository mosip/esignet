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

ARG MOSIP_ESIGNET_HOST

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user=mosip

ARG DATA_DIR=/home/${container_user}/data

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_group=mosip

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_uid=1001

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_gid=1001

ENV MOSIP_ESIGNET_HOST_ENV=${MOSIP_ESIGNET_HOST}

ENV DATA_DIR_ENV=${DATA_DIR}

# set working directory for the user
WORKDIR /home/${container_user}

ENV work_dir=/home/${container_user}
# Runtime override for the HSM client zip (artifactory / cluster URL). Empty by
# default — the image bakes a fallback client.zip used by docker-entrypoint.sh
# when this is unset, matching the Java esignet image.
ARG hsm_client_zip_url
ENV hsm_client_zip_url_env=${hsm_client_zip_url}
ARG hsm_local_dir=hsm-client
ENV hsm_local_dir_env=${hsm_local_dir}

COPY --from=build /out/esignet.exe /home/${container_user}/esignet.exe
COPY data /home/${container_user}/data
COPY docker-entrypoint.sh /home/${container_user}/entrypoint.sh

# Default HSM client zip baked into the image so PKCS11 (helm default) can load
# /usr/local/lib/softhsm/libpkcs11-proxy.so even when hsm_client_zip_url_env is
# not provided by esignet-softhsm-share. Override at runtime via that env var.
ARG hsm_client_zip_fallback_url=https://raw.githubusercontent.com/mosip/artifactory-ref-impl/master/artifacts/src/hsm/client.zip

# install packages and create user
RUN apt-get -y update \
&& apt-get install -y --no-install-recommends wget unzip file sudo ca-certificates \
&& groupadd -g ${container_user_gid} ${container_user_group} \
&& useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
&& echo "${container_user} ALL=(ALL) NOPASSWD:/home/${container_user}/${hsm_local_dir}/install.sh" >> /etc/sudoers \
&& chmod +x /home/${container_user}/entrypoint.sh \
&& wget -q "${hsm_client_zip_fallback_url}" -O /home/${container_user}/client.zip \
&& chown -R ${container_user}:${container_user_group} /home/${container_user} /home/${container_user}/data \
&& rm -rf /var/lib/apt/lists/*

# select container user for runtime
USER ${container_user_uid}:${container_user_gid}

ENV DATA_DIR=${DATA_DIR_ENV} \
    MOSIP_ESIGNET_HOST=${MOSIP_ESIGNET_HOST_ENV} \
    PORT=8088

EXPOSE 8088
EXPOSE 9090
EXPOSE 6060


ENTRYPOINT ["/home/mosip/entrypoint.sh"]

CMD ["/home/mosip/esignet.exe"]
