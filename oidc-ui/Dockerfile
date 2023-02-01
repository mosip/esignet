FROM node:12.18.4-alpine as build_idp_ui
COPY package*.json ./
RUN npm install
#Copy the working directory
COPY . ./
RUN npm run build

FROM nginx

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

ENV base_path=/usr/share/nginx/html
ENV i18n_path=${base_path}/locales

# can be passed during Docker build as build time environment for artifactory URL
ARG artifactory_url

# environment variable to pass artifactory url, at docker runtime
ENV artifactory_url_env=${artifactory_url}

# set working directory for the user
WORKDIR /home/${container_user}

# install packages and create user
RUN apt-get -y update \
&& apt-get install -y curl npm python wget unzip zip \
&& groupadd -g ${container_user_gid} ${container_user_group} \
&& useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
&& mkdir -p /var/run/nginx /var/tmp/nginx ${base_path}/locales \
&& chown -R ${container_user}:${container_user} /usr/share/nginx /var/run/nginx /var/tmp/nginx ${base_path}/locales

ADD configure_start.sh configure_start.sh

RUN chmod +x configure_start.sh

COPY ./nginx/nginx.conf /etc/nginx/nginx.conf

# copy build files to nginx html directory
COPY --from=build_idp_ui /build /usr/share/nginx/html

RUN chown -R ${container_user}:${container_user} /home/${container_user}

# select container user for all tasks
USER ${container_user_uid}:${container_user_gid}

EXPOSE 3000

ENTRYPOINT [ "./configure_start.sh" ]

CMD echo "starting nginx" ; \
    nginx ; \
    sleep infinity
