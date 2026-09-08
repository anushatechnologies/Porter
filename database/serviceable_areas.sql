-- Additive migration for Admin-Approved Serviceable Areas & Pincodes
-- Automatically managed by Hibernate ddl-auto=update in development.

CREATE TABLE IF NOT EXISTS serviceable_areas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    city VARCHAR(100) NOT NULL,
    area_name VARCHAR(150) NOT NULL,
    pincode VARCHAR(10) NOT NULL,
    is_serviceable BOOLEAN NOT NULL DEFAULT TRUE,
    center_lat DOUBLE NULL,
    center_lng DOUBLE NULL,
    radius_km DOUBLE NULL DEFAULT 5.0,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    INDEX idx_serv_city (city),
    INDEX idx_serv_pincode (pincode)
);
