-- Safe additive migration for the Master API contracts.
-- Run after taking a production database backup.
ALTER TABLE orders ADD COLUMN delivery_otp VARCHAR(10) NULL;
ALTER TABLE orders ADD COLUMN otp_expires_at DATETIME NULL;
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(500) NULL;

-- If a column already exists, skip that individual ALTER statement.
