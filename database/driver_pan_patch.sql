-- Additive production migration for Driver PAN card support
-- Hibernate will automatically create these with ddl-auto=update, but this script provides explicit DDL if needed.

ALTER TABLE drivers ADD COLUMN pan_number VARCHAR(20) NULL;
ALTER TABLE drivers ADD COLUMN pan_uri VARCHAR(500) NULL;
