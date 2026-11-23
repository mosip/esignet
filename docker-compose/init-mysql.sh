#!/bin/bash
# Runs once, on first start with an empty data directory, from the official
# MySQL image entrypoint (docker-entrypoint-initdb.d). It only adapts the image
# environment to the canonical provisioning script in
# db_scripts/mysql/mosip_esignet; the schema itself is not defined here.
#
# The entrypoint may source this file, so everything runs in a subshell and
# never changes the caller's shell options or exits it.
(
    set -euo pipefail

    scripts_dir=/opt/esignet-db
    props="$(mktemp)"
    trap 'rm -f "$props"' EXIT

    # An empty DB_SERVERIP makes deploy.sh use the local socket, which is the
    # only way to reach the temporary init-time server (networking is disabled).
    cat > "$props" <<'EOF'
DB_SERVERIP=
DB_PORT=3306
SU_USER=root
MOSIP_DB_NAME=mosip_esignet
DB_UNAME=esignetuser
DB_UHOST=%
DML_FLAG=1
EOF

    SU_USER_PWD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is not set}" \
    DBUSER_PWD="${ESIGNET_DB_PASSWORD:?ESIGNET_DB_PASSWORD is not set}" \
        bash "$scripts_dir/deploy.sh" "$props"
)
