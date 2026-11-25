-- Manual cleanup only: deploy.sh never runs this script.
-- Usage: see "Starting over" in README.md (connect to the server and use the values from deploy.properties).

SET @stmt = CONCAT('DROP DATABASE IF EXISTS `', @mosipdbname, '`');
PREPARE drop_db FROM @stmt;
EXECUTE drop_db;
DEALLOCATE PREPARE drop_db;
