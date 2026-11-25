-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Creates the eSignet database. In MySQL a database is also the namespace that PostgreSQL calls a
-- schema, so there is no CREATE SCHEMA / search_path step; KEYMANAGER_DB_SCHEMA must equal this name.
--
-- Expects the session variable @mosipdbname (set by deploy.sh).
--
-- utf8mb4_0900_bin compares identifiers, tokens and hashes case- and accent-sensitively and does
-- not ignore trailing spaces, matching PostgreSQL's behaviour. Requires MySQL 8.0.17 or later.
-- -------------------------------------------------------------------------------------------------

SET @stmt = CONCAT('CREATE DATABASE `', @mosipdbname, '` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin');
PREPARE create_db FROM @stmt;
EXECUTE create_db;
DEALLOCATE PREPARE create_db;
