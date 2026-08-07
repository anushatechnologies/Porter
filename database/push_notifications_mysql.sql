-- Production migration for the existing appusers/notifications tables.
-- Run after taking a database backup.
ALTER TABLE appusers ADD COLUMN fcm_token VARCHAR(512) NULL;
ALTER TABLE notifications ADD COLUMN user_id BIGINT NULL;
ALTER TABLE notifications ADD COLUMN booking_id VARCHAR(100) NULL;
ALTER TABLE notifications ADD COLUMN notification_type VARCHAR(50) DEFAULT 'ORDER_UPDATE';
ALTER TABLE notifications ADD COLUMN is_read BOOLEAN DEFAULT FALSE;
ALTER TABLE notifications ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
