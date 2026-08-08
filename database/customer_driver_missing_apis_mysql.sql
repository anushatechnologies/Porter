-- Additive production migration for the customer/driver API additions.
ALTER TABLE booking_drafts ADD COLUMN expires_at DATETIME NULL;

CREATE TABLE IF NOT EXISTS enterprise_leads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source VARCHAR(100),
    company_name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(255),
    created_at DATETIME NULL
);

-- If expires_at already exists, skip that ALTER statement.
