-- ==============================================================================
-- PASSENGERS RIDE & PACKERS & MOVERS DATABASE SCHEMAS
-- Recommended tables for MySQL / PostgreSQL
-- ==============================================================================

-- 1. PASSENGER VEHICLE CATEGORIES
CREATE TABLE IF NOT EXISTS passenger_vehicle_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_code VARCHAR(32) UNIQUE NOT NULL,      -- 'AUTO', 'HATCHBACK', 'SEDAN', 'SUV'
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    image_url TEXT,
    passenger_capacity INT NOT NULL DEFAULT 4,
    luggage_capacity INT NOT NULL DEFAULT 2,
    base_fare DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    per_km_rate DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    per_hour_rate DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    minimum_fare DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    minimum_km DECIMAL(8, 2) NOT NULL DEFAULT 0.00,
    driver_allowance DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    display_order INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. PASSENGER BOOKINGS
CREATE TABLE IF NOT EXISTS passenger_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_number VARCHAR(64) UNIQUE NOT NULL,    -- 'PB-84920' or 'AP-CAR-100245'
    customer_id BIGINT,
    customer_name VARCHAR(128) NOT NULL,
    customer_phone VARCHAR(32) NOT NULL,
    customer_email VARCHAR(128),
    driver_id BIGINT,
    driver_name VARCHAR(128),
    driver_phone VARCHAR(32),
    vehicle_number VARCHAR(32),
    vehicle_model VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'DRIVER_SEARCHING', -- 'DRIVER_SEARCHING','DRIVER_ASSIGNED','DRIVER_ARRIVED','TRIP_STARTED','TRIP_COMPLETED','CANCELLED_BY_CUSTOMER'
    service_type VARCHAR(32) NOT NULL DEFAULT 'ONE_WAY',
    vehicle_category_code VARCHAR(32) NOT NULL,
    pickup_address TEXT NOT NULL,
    pickup_latitude DECIMAL(10, 6),
    pickup_longitude DECIMAL(10, 6),
    drop_address TEXT NOT NULL,
    drop_latitude DECIMAL(10, 6),
    drop_longitude DECIMAL(10, 6),
    passenger_count INT DEFAULT 1,
    luggage_count INT DEFAULT 0,
    round_trip BOOLEAN DEFAULT FALSE,
    scheduled_pickup_time TIMESTAMP,
    return_time TIMESTAMP,
    rental_package_id BIGINT,
    rental_package_name VARCHAR(100),
    distance_km DECIMAL(8, 2) DEFAULT 0.00,
    duration_minutes INT DEFAULT 0,
    pricing_version_id VARCHAR(50),
    fare_lock_token VARCHAR(100),
    fare_lock_expires_at TIMESTAMP,
    payment_mode VARCHAR(16) NOT NULL DEFAULT 'CASH', -- 'CASH','ONLINE','WALLET'
    payment_status VARCHAR(16) DEFAULT 'PENDING',
    start_otp VARCHAR(6) NOT NULL,                    -- '4829'
    driver_rating INT,
    review_notes TEXT,
    cancellation_reason TEXT,
    cancelled_by VARCHAR(50),
    cancelled_at TIMESTAMP,
    cancellation_fee DECIMAL(10, 2) DEFAULT 0.00,
    opt_lock_version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pbooking_num (booking_number),
    INDEX idx_pbooking_status (status),
    INDEX idx_pbooking_driver (driver_id),
    INDEX idx_pbooking_customer (customer_phone)
);

-- 3. ORDERS (PACKERS & MOVERS + GOODS FLEET BOOKINGS)
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id VARCHAR(64) UNIQUE NOT NULL,        -- 'PM-928174' or 'ANP123456'
    user_email VARCHAR(128) NOT NULL,
    service_name VARCHAR(128),
    pickup_address TEXT,
    drop_address TEXT,
    pickup_lat DECIMAL(10, 6),
    pickup_lng DECIMAL(10, 6),
    drop_lat DECIMAL(10, 6),
    drop_lng DECIMAL(10, 6),
    amount DECIMAL(10, 2),
    currency VARCHAR(10) DEFAULT 'INR',
    status VARCHAR(32) DEFAULT 'CONFIRMED',
    payment_method VARCHAR(32) DEFAULT 'CASH',
    scheduled_date VARCHAR(64),
    scheduled_slot VARCHAR(64),
    receiver_name VARCHAR(128),
    receiver_phone VARCHAR(32),
    driver_id VARCHAR(64),
    driver_name VARCHAR(128),
    driver_phone VARCHAR(32),
    driver_vehicle_number VARCHAR(32),
    distance_km DECIMAL(8, 2),
    goods_category VARCHAR(128),
    helpers_count INT DEFAULT 0,
    helper_charges DECIMAL(10, 2) DEFAULT 0.00,
    gst_amount DECIMAL(10, 2) DEFAULT 0.00,
    base_fare DECIMAL(10, 2) DEFAULT 0.00,
    distance_fare DECIMAL(10, 2) DEFAULT 0.00,
    payment_status VARCHAR(32) DEFAULT 'unpaid',
    delivery_otp VARCHAR(10) NOT NULL,             -- Handover OTP
    otp_expires_at TIMESTAMP,
    cancellation_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    INDEX idx_order_booking (booking_id),
    INDEX idx_order_status (status)
);

-- 4. MOVING TIME SLOTS (PACKERS SLOTS)
CREATE TABLE IF NOT EXISTS packers_slots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_label VARCHAR(64) NOT NULL,               -- '09:00 AM - 11:00 AM'
    max_bookings_per_day INT DEFAULT 5,
    surge_price DECIMAL(10, 2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
