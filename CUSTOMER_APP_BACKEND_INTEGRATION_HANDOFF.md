# Backend API Handoff & Customer App Integration Guide

This guide documents the backend enhancements implemented in **Porter** (`com.anushaporter.backend`) to support the Customer App bug fixes and lifecycle restoration while keeping existing auto-assignment, strict vehicle matching, and wallet rules 100% intact.

---

## 1. OTP Resend Cooldown & Code Expiry

### Problem
Previously, frontends conflated the 5-minute OTP code lifetime (`expiresIn: 300`) with the resend timer cooldown, causing users to wait 300 seconds before being able to request a new OTP.

### Backend Endpoints
* **`POST /api/auth/send-otp`**
* **`POST /api/auth/resend-otp`**

### Response Payload (200 OK)
```json
{
  "success": true,
  "message": "OTP sent successfully.",
  "otp": "123456",
  "phone": "9876543210",
  "expiresIn": 300,
  "resendCooldown": 30
}
```
* `expiresIn: 300` — The OTP code remains valid in Redis/DB for 5 minutes (300 seconds).
* `resendCooldown: 30` — Authoritative countdown duration in seconds (30s) before the frontend enables the **[ Resend OTP ]** button.

---

## 2. Customer Services & Category Grid

### Endpoint
* **`GET /api/customer/services`**

### Description
Returns active service categories along with their customer-visible services, pre-sorted by `displayOrder`. Eliminates cascading fallback waterfalls on the client.

### Response Payload (200 OK)
```json
{
  "success": true,
  "categories": [
    {
      "id": 1,
      "name": "Trucks",
      "code": "truck",
      "displayOrder": 1,
      "services": [ ... ]
    },
    {
      "id": 2,
      "name": "2 Wheeler",
      "code": "two_wheeler",
      "displayOrder": 2,
      "services": [ ... ]
    }
  ]
}
```

---

## 3. Customer Cancellation Reasons

### Endpoint
* **`GET /api/bookings/cancellation-reasons`** (or `GET /api/customer/cancellation-reasons`)

### Description
Provides the canonical list of 7 cancellation reasons for the Customer App modal.

### Response Payload (200 OK)
```json
{
  "success": true,
  "reasons": [
    { "id": "driver_taking_long", "title": "Driver is taking too long", "requiresText": false },
    { "id": "found_another_vehicle", "title": "I found another vehicle", "requiresText": false },
    { "id": "booking_by_mistake", "title": "Booking by mistake", "requiresText": false },
    { "id": "change_of_plans", "title": "Change of plans", "requiresText": false },
    { "id": "driver_requested_cancellation", "title": "Driver requested cancellation", "requiresText": false },
    { "id": "price_issue", "title": "Price issue", "requiresText": false },
    { "id": "other", "title": "Other", "requiresText": true }
  ]
}
```

---

## 4. Order Cancellation (Driver Search & Post-Acceptance)

### Endpoints Supported
* **`PUT /api/bookings/{bookingId}/cancel`**
* **`POST /api/bookings/{bookingId}/cancel`**

### Request Payload
```json
{
  "cancelledBy": "CUSTOMER",
  "reason": "Driver is taking too long",
  "remarks": "Waited over 15 minutes without movement"
}
```

### Flow & Rules
1. **Cancellation During Driver Search (`searching` / `pending` / `created`)**:
   * Auto-Assignment engine immediately revokes pending driver offers via `driverOfferService.onOrderCancelled(bookingId)`.
   * **`cancellationFee: 0.0`** (100% full refund).
2. **Cancellation After Driver Acceptance (`assigned` / `arriving` / `on_the_way`)**:
   * Assigned driver is unlinked and returned to active available pool.
   * **`cancellationFee: 50.0`**, refund initiated for the remaining amount.
3. **Cancellation Blocked Window**:
   * Returns `400 Bad Request` with `canCancel: false` if driver has already arrived at drop-off or delivery OTP has been verified.

### Response Payload (200 OK)
```json
{
  "success": true,
  "status": "cancelled",
  "canCancel": false,
  "isCancellable": false,
  "refundAmount": 500.0,
  "cancellationFee": 0.0,
  "cancellationReason": "Customer cancelled during driver search",
  "cancelledBy": "CUSTOMER",
  "message": "Driver search cancelled. Full refund initiated."
}
```

---

## 5. Active Booking Check & App State Restoration

### Endpoint
* **`GET /api/bookings/active`** (or `GET /api/customer/bookings/active`)

### Query Parameters / Headers
* Header: `Authorization: Bearer <token>` OR Query Parameter: `?phone=9876543210`

### Description
Called on app cold-start and when returning from background (`AppState === 'active'`) to restore the user to the correct screen.

### Response When No Active Booking (200 OK)
```json
{
  "success": true,
  "hasActiveBooking": false,
  "booking": null
}
```

### Response When Booking is Active (200 OK)
```json
{
  "success": true,
  "hasActiveBooking": true,
  "bookingId": "BK-1042",
  "status": "searching",
  "booking": {
    "bookingId": "BK-1042",
    "status": "searching",
    "hasAssignedDriver": false,
    "driverName": null,
    "driverPhone": null,
    "pickupAddress": "Hitech City, Hyderabad",
    "dropAddress": "Gachibowli, Hyderabad"
  }
}
```

### Client Routing Logic
* If `hasActiveBooking === false`: Stay on / Navigate to `HomeScreen`.
* If `status === 'searching' | 'pending' | 'created'`: Route to `SearchingDriverScreen` with `{ bookingId }`.
* If `status === 'driver_accepted' | 'assigned' | 'arriving' | 'in_transit'`: Route to `TrackingScreen` with `{ bookingId }`.
