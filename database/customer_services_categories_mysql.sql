-- Additive production migration for service categories and customer visibility
CREATE TABLE IF NOT EXISTS service_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    icon VARCHAR(255),
    description VARCHAR(1000),
    display_order INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

-- Seed default categories if not already present
INSERT IGNORE INTO service_categories (id, slug, name, icon, description, display_order, is_active, created_at, updated_at)
VALUES 
(1, 'porter-trucks-fleet', 'Porter Trucks & Fleet', 'truck-fast', 'Mini trucks, tempos, and commercial flatbeds for goods & house shifting', 1, 1, NOW(), NOW()),
(2, '2-wheeler-bike', '2 Wheeler / Bike', 'motorbike', 'Instant courier and parcel delivery up to 20 Kg', 2, 1, NOW(), NOW()),
(3, 'packers-movers', 'Packers & Movers', 'truck-moving', 'Full-service household and office shifting with packing & loading', 3, 1, NOW(), NOW());

-- Add columns to services table if not already present
ALTER TABLE services ADD COLUMN IF NOT EXISTS category_id VARCHAR(100) DEFAULT '1';
ALTER TABLE services ADD COLUMN IF NOT EXISTS category_name VARCHAR(255) DEFAULT 'Porter Trucks & Fleet';
ALTER TABLE services ADD COLUMN IF NOT EXISTS customer_app_visible BOOLEAN DEFAULT TRUE;
