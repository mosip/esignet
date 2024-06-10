FROM mosipdev/openjdk-21-jre:latest

ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user=mosip

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_group=mosip

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_uid=1001

# can be passed during Docker build as build time environment for github branch to pickup configuration from.
ARG container_user_gid=1001

ARG KUBECTL_VERSION=1.22.9

# set working directory for the user
WORKDIR /home/${container_user}

ENV work_dir=/home/${container_user}

ADD ./apitest/target/ $work_dir

ADD entrypoint.sh $work_dir/entrypoint.sh

# install packages and create user
RUN apt-get -y update \
&& apt-get install -y unzip jq curl \
&& groupadd -g ${container_user_gid} ${container_user_group} \
&& useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/bash -m ${container_user} \
&& curl -LO "https://storage.googleapis.com/kubernetes-release/release/v${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
&& mkdir -p /home/${container_user} \
&& chmod +x kubectl $work_dir/entrypoint.sh \
&& mv kubectl /usr/local/bin/ \
&& chown -R ${container_user}:${container_user} /home/${container_user} /etc/ssl/certs/java/cacerts \
&& chmod 644 /etc/ssl/certs/java/cacerts

# select container user for all tasks
USER ${container_user_uid}:${container_user_gid}

EXPOSE 8083

ENV MODULES=
ENV ENV_USER=
ENV ENV_ENDPOINT=
ENV ENV_TESTLEVEL=smokeAndRegression

ENTRYPOINT ["./entrypoint.sh"]
