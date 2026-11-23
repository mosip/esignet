-- Manual cleanup only: deploy.sh never runs this script.
-- Usage: mysql -u root -p -e "SET @dbuname='esignetuser'; SET @dbuhost='%'; SOURCE drop_role.sql"

SET @stmt = CONCAT('DROP USER IF EXISTS ', QUOTE(@dbuname), '@', QUOTE(@dbuhost));
PREPARE drop_user FROM @stmt;
EXECUTE drop_user;
DEALLOCATE PREPARE drop_user;
