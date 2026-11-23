#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#
# Provisions a NEW eSignet MySQL database: application user, database, tables, grants and (optionally)
# seed data. Non-destructive by design: if the database already exists the script exits 0 without
# touching anything, and it never drops existing objects (use drop_db.sql / drop_role.sql manually).
#
# Usage:
#   SU_USER_PWD=<admin password> DBUSER_PWD=<application user password> ./deploy.sh deploy.properties
#
# Passwords are read from the environment only; they are never put on a command line. Requires the
# mysql client and MySQL server 8.0.17 or later (8.4 LTS recommended).
set -euo pipefail

log() { echo "$(date '+%m/%d/%Y %H:%M:%S') : $*"; }
die() { log "ERROR: $*" >&2; exit 1; }

properties_file="${1:-}"
[ -f "$properties_file" ] || die "Property file not found, pass the property file name as the first argument."

DB_SERVERIP=""
DB_PORT=3306
SU_USER=root
MOSIP_DB_NAME=mosip_esignet
DB_UNAME=esignetuser
DB_UHOST='%'
DML_FLAG=1

# Only known keys are read, and values are never evaluated as shell code.
while IFS='=' read -r key value || [ -n "$key" ]; do
    value="${value%$'\r'}"
    case "$key" in
        ''|\#*) ;;
        DB_SERVERIP|DB_PORT|SU_USER|MOSIP_DB_NAME|DB_UNAME|DB_UHOST|DML_FLAG)
            printf -v "$key" '%s' "$value" ;;
    esac
done < "$properties_file"

SU_USER_PWD="${SU_USER_PWD:-}"
[ -n "${DBUSER_PWD:-}" ] || die "DBUSER_PWD (application user password) must be set in the environment."

[[ "$MOSIP_DB_NAME" =~ ^[A-Za-z0-9_]{1,64}$ ]]   || die "MOSIP_DB_NAME must be 1-64 letters, digits or underscores."
[[ "$DB_UNAME" =~ ^[A-Za-z0-9_]{1,32}$ ]]        || die "DB_UNAME must be 1-32 letters, digits or underscores."
[[ "$DB_UHOST" =~ ^[A-Za-z0-9_.%:-]{1,255}$ ]]   || die "DB_UHOST contains unsupported characters."
[[ "$SU_USER" =~ ^[A-Za-z0-9_.@-]{1,80}$ ]]      || die "SU_USER contains unsupported characters."
[[ "$DB_SERVERIP" =~ ^[A-Za-z0-9_.:-]*$ ]]       || die "DB_SERVERIP contains unsupported characters."
[[ "$DB_PORT" =~ ^[0-9]{1,5}$ ]]                 || die "DB_PORT must be numeric."
[[ "$DML_FLAG" =~ ^[01]$ ]]                      || die "DML_FLAG must be 0 or 1."

# SOURCE paths inside ddl.sql / dml.sql are relative to the client's working directory.
cd "$(dirname "${BASH_SOURCE[0]}")"

# Admin credentials go through a private option file instead of the command line.
cnf="$(mktemp)"
provisioning=0
cleanup() {
    local rc=$?
    rm -f "$cnf"
    if [ "$rc" -ne 0 ] && [ "$provisioning" -eq 1 ]; then
        log "Provisioning failed part-way; '$MOSIP_DB_NAME' and user '$DB_UNAME' may be partially created." >&2
        log "Remove them with drop_db.sql / drop_role.sql (see README.md) before running this script again." >&2
    fi
}
trap cleanup EXIT
chmod 600 "$cnf"

cnf_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
{
    printf '[client]\n'
    printf 'user="%s"\n' "$(cnf_escape "$SU_USER")"
    printf 'password="%s"\n' "$(cnf_escape "$SU_USER_PWD")"
    printf 'host="%s"\n' "${DB_SERVERIP:-localhost}"
    printf 'port=%s\n' "$DB_PORT"
    printf 'default-character-set=utf8mb4\n'
} > "$cnf"

mysql_admin() { mysql --defaults-extra-file="$cnf" --batch --skip-column-names "$@"; }

# Passes a value into SQL as hex so no quoting or escaping of the secret is needed.
sql_hex_var() {
    local hex
    hex="$(printf '%s' "$2" | od -An -v -tx1 | tr -d ' \n')"
    printf "SET @%s = CONVERT(UNHEX('%s') USING utf8mb4);\n" "$1" "$hex"
}

# Clears only NO_BACKSLASH_ESCAPES (other modes, including strict mode, are kept): QUOTE() and the
# '\\_' pattern in grants.sql rely on backslash escapes.
session_vars() {
    printf '%s\n' "SET SESSION sql_mode = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', @@SESSION.sql_mode, ','), ',NO_BACKSLASH_ESCAPES,', ','));"
    printf "SET @mosipdbname = '%s';\n" "$MOSIP_DB_NAME"
    printf "SET @dbuname = '%s';\n" "$DB_UNAME"
    printf "SET @dbuhost = '%s';\n" "$DB_UHOST"
    sql_hex_var dbuserpwd "$DBUSER_PWD"
}

# run_sql <file> [mysql options]: runs a script in one session that has the variables above set.
run_sql() {
    local file="$1"
    shift
    { session_vars; cat "$file"; } | mysql_admin "$@"
}

log "Checking if database '$MOSIP_DB_NAME' exists..."
exists="$(mysql_admin -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '${MOSIP_DB_NAME}'")"
if [ "$exists" != "0" ]; then
    log "Database '$MOSIP_DB_NAME' already exists, nothing was changed. To reinitialise, delete or rename it first (see README.md)."
    exit 0
fi
log "eSignet database was not found. Creating and initialising it."

provisioning=1

user_exists="$(mysql_admin -e "SELECT COUNT(*) FROM mysql.user WHERE user = '${DB_UNAME}' AND host = '${DB_UHOST}'")"
if [ "$user_exists" != "0" ]; then
    log "WARNING: user '${DB_UNAME}'@'${DB_UHOST}' already exists; it is reused and its password is left unchanged."
fi

log "Creating database user"
run_sql role_dbuser.sql

log "Creating database"
run_sql db.sql

log "Creating tables"
run_sql ddl.sql --database="$MOSIP_DB_NAME"

log "Applying grants"
run_sql grants.sql

if [ "$DML_FLAG" = "1" ]; then
    log "Deploying DML for ${MOSIP_DB_NAME} database"
    run_sql dml.sql --database="$MOSIP_DB_NAME"
fi

log "eSignet MySQL database '${MOSIP_DB_NAME}' is ready."
