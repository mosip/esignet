-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Runtime privileges for the application user: data access only, no DDL and no TRUNCATE (MySQL
-- requires DROP for TRUNCATE, which the runtime user must not have).
--
-- A database-level grant also covers tables created later, so new tables need no further grants.
-- Expects @mosipdbname, @dbuname and @dbuhost (set by deploy.sh).
--
-- In a database-level grant "_" is a wildcard unless escaped, so it is escaped to keep the grant
-- from matching other databases such as "mosip1esignet".
-- -------------------------------------------------------------------------------------------------

SET @stmt = CONCAT('GRANT SELECT, INSERT, UPDATE, DELETE ON `', REPLACE(@mosipdbname, '_', '\\_'), '`.* TO ',
                   QUOTE(@dbuname), '@', QUOTE(@dbuhost));
PREPARE grant_runtime FROM @stmt;
EXECUTE grant_runtime;
DEALLOCATE PREPARE grant_runtime;
