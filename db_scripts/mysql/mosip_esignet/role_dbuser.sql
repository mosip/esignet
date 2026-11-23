-- This Source Code Form is subject to the terms of the Mozilla Public
-- License, v. 2.0. If a copy of the MPL was not distributed with this
-- file, You can obtain one at https://mozilla.org/MPL/2.0/.
-- -------------------------------------------------------------------------------------------------
-- Creates the application (runtime) user. Expects the session variables @dbuname, @dbuhost and
-- @dbuserpwd (set by deploy.sh). Names and the password are quoted with QUOTE(), never concatenated raw.
-- QUOTE() escapes with backslashes, so deploy.sh clears NO_BACKSLASH_ESCAPES from the session sql_mode
-- before running this script.
--
-- An existing user is left untouched (IF NOT EXISTS), including its password.
-- -------------------------------------------------------------------------------------------------

SET @stmt = CONCAT('CREATE USER IF NOT EXISTS ', QUOTE(@dbuname), '@', QUOTE(@dbuhost),
                   ' IDENTIFIED BY ', QUOTE(@dbuserpwd));
PREPARE create_user FROM @stmt;
EXECUTE create_user;
DEALLOCATE PREPARE create_user;
