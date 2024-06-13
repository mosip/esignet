-- Change the db name accordingly.
SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'mosip_master' AND pid <> pg_backend_pid();
