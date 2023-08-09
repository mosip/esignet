
## Properties file
set -e
properties_file="$1"
echo `date "+%m/%d/%Y %H:%M:%S"` ": $properties_file"
if [ -f "$properties_file" ]
then
     echo `date "+%m/%d/%Y %H:%M:%S"` ": Property file \"$properties_file\" found."
    while IFS='=' read -r key value
    do
        key=$(echo $key | tr '.' '_')
         eval ${key}=\${value}
    done < "$properties_file"
else
     echo `date "+%m/%d/%Y %H:%M:%S"` ": Property file not found, Pass property file name as argument."
fi

## Function to display error message
function print_err {
  echo "$1"
  printf "%s\n" "$2"
  exit 1
}

## Terminate existing connections
echo "Terminating active connections" 
CONN=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -t -c "SELECT count(pg_terminate_backend(pg_stat_activity.pid)) FROM pg_stat_activity WHERE datname = '$MOSIP_DB_NAME' AND pid <> pg_backend_pid()" 2>&1;exit;)
echo "$CONN" | grep -q ERROR && print_err "Error: Failed to terminate active connections." "$CONN"
echo "Terminated connections"

## Drop db and role
output=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -f drop_db.sql 2>&1)
echo "$output" | grep -q ERROR && print_err "Error: Failed to drop the database." "$output"
output=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -f drop_role.sql 2>&1)
echo "$output" | grep -q ERROR && print_err "Error: Failed to drop the role." "$output"

## Create users
echo `date "+%m/%d/%Y %H:%M:%S"` ": Creating database users" | tee
output=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -f role_dbuser.sql -v dbuserpwd=\'$DBUSER_PWD\' 2>&1)
echo "$output" | grep -q ERROR && print_err "Error: Failed to create database users." "$output"

## Create DB
output=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -f db.sql 2>&1)
echo "$output" | grep -q ERROR && print_err "Error: Failed to create the database." "$output"
output=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -f ddl.sql 2>&1)
echo "$output" | grep -q ERROR && print_err "Error: Failed to execute DDL SQL script." "$output"

## Grants
output=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -f grants.sql 2>&1)
echo "$output" | grep -q ERROR && print_err "Error: Failed to apply grants." "$output"

## Populate tables
if [ ${DML_FLAG} == 1 ]
then
    echo `date "+%m/%d/%Y %H:%M:%S"` ": Deploying DML for ${MOSIP_DB_NAME} database" 
    output=$(PGPASSWORD=$SU_USER_PWD psql --username=$SU_USER --host=$DB_SERVERIP --port=$DB_PORT --dbname=$DEFAULT_DB_NAME -a -b -f dml.sql 2>&1)
    echo "$output" | grep -q ERROR && print_err "Error: Failed to deploy DML for ${MOSIP_DB_NAME} database." "$output"
fi

