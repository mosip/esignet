-- Manual cleanup only: deploy.sh never runs this script.
-- Usage: mysql -u root -p -e "SET @mosipdbname='mosip_esignet'; SOURCE drop_db.sql"

SET @stmt = CONCAT('DROP DATABASE IF EXISTS `', @mosipdbname, '`');
PREPARE drop_db FROM @stmt;
EXECUTE drop_db;
DEALLOCATE PREPARE drop_db;
