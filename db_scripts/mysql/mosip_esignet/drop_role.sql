-- Manual cleanup only: deploy.sh never runs this script.
-- Usage: see "Starting over" in README.md (connect to the server and use the values from deploy.properties).

SET @stmt = CONCAT('DROP USER IF EXISTS ', QUOTE(@dbuname), '@', QUOTE(@dbuhost));
PREPARE drop_user FROM @stmt;
EXECUTE drop_user;
DEALLOCATE PREPARE drop_user;
