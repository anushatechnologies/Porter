-- Driver live location fields. Run once if production does not use
-- spring.jpa.hibernate.ddl-auto=update.
ALTER TABLE drivers ADD COLUMN latitude DOUBLE NULL;
ALTER TABLE drivers ADD COLUMN longitude DOUBLE NULL;
ALTER TABLE drivers ADD COLUMN heading DOUBLE NULL;
