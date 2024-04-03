FROM nginx:alpine

ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

WORKDIR /app

WORKDIR /home/${container_user}

RUN chown -R ${container_user}:${container_user} /home/${container_user}

COPY . .

COPY ./nginx.conf /etc/nginx/nginx.conf

USER ${container_user_uid}:${container_user_gid}
