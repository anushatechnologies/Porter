-- =========================================================================
-- DRIVER VEHICLE FIELD DATA PATCH & MIGRATION SCRIPT
-- =========================================================================
-- Purpose: Resolve "Unregistered Vehicle" / null vehicle fields for existing
-- registered drivers across PostgreSQL and MySQL databases.
-- =========================================================================

-- 1. Sync vehicle column from vehicleType where vehicle is NULL or empty
UPDATE drivers 
SET vehicle = vehicleType 
WHERE (vehicle IS NULL OR TRIM(vehicle) = '') 
  AND vehicleType IS NOT NULL 
  AND TRIM(vehicleType) <> '';

-- 2. Sync vehicleType column from vehicle where vehicleType is NULL or empty
UPDATE drivers 
SET vehicleType = vehicle 
WHERE (vehicleType IS NULL OR TRIM(vehicleType) = '') 
  AND vehicle IS NOT NULL 
  AND TRIM(vehicle) <> '';

-- 3. Set a sensible default for any records where both are empty
UPDATE drivers 
SET vehicle = 'Scooter', vehicleType = 'Scooter' 
WHERE (vehicle IS NULL OR TRIM(vehicle) = '') 
  AND (vehicleType IS NULL OR TRIM(vehicleType) = '');

-- Verify updated rows
SELECT id, name, phone, vehicle, vehicleType, vehicleNumber, status, kyc 
FROM drivers;
