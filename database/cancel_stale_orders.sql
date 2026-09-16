-- =======================================================================
-- Anusha Porter - Cancel Stale Test Orders and Expire Pending Offers
-- Run this once on your MySQL RDS database to clean up old test orders
-- =======================================================================

-- 1. Cancel unassigned orders that were left in searching or pending status
UPDATE orders 
SET status = 'cancelled' 
WHERE status IN ('searching', 'pending', 'SEARCHING', 'PENDING') 
  AND (driver_id IS NULL OR driver_id = '');

-- 2. Mark any dangling driver offers as EXPIRED
UPDATE driver_offers 
SET status = 'EXPIRED' 
WHERE status = 'OFFERED';

-- Verify remaining active orders
SELECT id, booking_id, service_name, status, created_at 
FROM orders 
WHERE status IN ('searching', 'pending', 'assigned', 'in_transit')
ORDER BY id DESC;
