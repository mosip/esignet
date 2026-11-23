# eSignet MySQL database scripts

Provisions a **new** MySQL database for `esignet-service` (`DB_DRIVER=mysql`). This is the MySQL
counterpart of [`../../postgresql/mosip_esignet`](../../postgresql/mosip_esignet) (PostgreSQL); keep both schemas in sync.

## Prerequisites

- MySQL server 8.0.17 or later (8.4 LTS recommended; CHECK constraints need 8.0.16+, the
  `utf8mb4_0900_bin` collation needs 8.0.17+).
- The `mysql` client and `bash` on the machine running `deploy.sh`.
- Strict SQL mode (the default) and a UTC server time zone, or `time_zone` set in the connection URL.

## Usage

```sh
cd db_scripts/mysql/mosip_esignet
# set DB_SERVERIP (and other values) in deploy.properties first
SU_USER_PWD='<admin password>' DBUSER_PWD='<application user password>' ./deploy.sh deploy.properties
```

`deploy.properties` keys: `DB_SERVERIP` (empty = local socket), `DB_PORT`, `SU_USER`,
`MOSIP_DB_NAME`, `DB_UNAME`, `DB_UHOST`, `DML_FLAG` (1 = load seed data).

Passwords are read from the environment only. The script is non-destructive: if the database already
exists it exits 0 and changes nothing. It never drops anything.

The application user receives only `SELECT, INSERT, UPDATE, DELETE` on the database.

## Starting over

If provisioning fails part-way (MySQL DDL is not transactional) or you want to reinitialise, remove
the objects manually, then rerun `deploy.sh`. Run the commands from this folder against the same server
and with the same values that `deploy.sh` used, taken from `deploy.properties`:

```sh
cd db_scripts/mysql/mosip_esignet
mysql --host=<DB_SERVERIP> --port=<DB_PORT> --user=<SU_USER> -p \
  --execute "SET @mosipdbname='<MOSIP_DB_NAME>'; SOURCE drop_db.sql;"
mysql --host=<DB_SERVERIP> --port=<DB_PORT> --user=<SU_USER> -p \
  --execute "SET @dbuname='<DB_UNAME>'; SET @dbuhost='<DB_UHOST>'; SOURCE drop_role.sql;"
```

If `DB_SERVERIP` is empty, `deploy.sh` used the local socket: omit `--host` and `--port`. With the
default `deploy.properties` that is:

```sh
mysql --user=root -p --execute "SET @mosipdbname='mosip_esignet'; SOURCE drop_db.sql;"
mysql --user=root -p --execute "SET @dbuname='esignetuser'; SET @dbuhost='%'; SOURCE drop_role.sql;"
```

## Application settings

Set `DB_DRIVER=mysql` and `DATABASE_PORT=3306`. `KEYMANAGER_DB_SCHEMA` must equal the MySQL
database name (`mosip_esignet` by default), because MySQL has no schemas separate from databases.

## Not included

Upgrade scripts, migrating existing PostgreSQL data and Helm chart changes are out of scope.
