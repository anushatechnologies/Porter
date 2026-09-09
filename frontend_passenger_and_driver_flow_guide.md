# Frontend Integration & Testing Flow Guide

This document outlines the complete testing and integration flow for the frontend team (Mobile App: Flutter / React Native / Android / iOS, and Web).

---

## 🛵 1. Passenger Ride — Bike / 2-Wheeler Flow

### Step 1: Fetch Available Passenger Vehicles
* **URL**: `GET https://api.anushaporter.com/api/passenger/vehicles` (or `/api/passenger/categories`)
* **Headers**: `Accept: application/json`
* **Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": "1",
      "code": "BIKE",
      "categoryCode": "BIKE",
      "name": "Bike",
      "displayName": "Bike",
      "description": "Affordable & quick motorcycle ride (Helmet provided)",
      "imageUrl": "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=400&q=80",
      "passengerCapacity": 1,
      "luggageCapacity": 1,
      "basePrice": 20.00,
      "baseFare": 20.00,
      "perKmRate": 8.00,
      "perHourRate": 60.00,
      "minimumFare": 20.00,
      "minimumKm": 1.50,
      "driverAllowance": 0.00,
      "isActive": true,
      "active": true,
      "displayOrder": 0
    },
    {
      "id": "2",
      "code": "AUTO",
      "categoryCode": "AUTO",
      "name": "Auto",
      "displayName": "Auto",
      "description": "Affordable 3-wheeler auto rickshaw",
      "passengerCapacity": 3,
      "luggageCapacity": 2,
      "basePrice": 30.00,
      "baseFare": 30.00,
      "displayOrder": 1
    },
    {
      "id": "3",
      "code": "HATCHBACK",
      "categoryCode": "HATCHBACK",
      "name": "Mini / Hatchback",
      "displayName": "Mini / Hatchback",
      "displayOrder": 2
    },
    {
      "id": "4",
      "code": "SEDAN",
      "categoryCode": "SEDAN",
      "name": "Sedan",
      "displayName": "Sedan",
      "displayOrder": 3
    }
  ]
}
```

> **Frontend Compatibility Note**:  
> Every vehicle item contains **both** naming options (`code` and `categoryCode`, `name` and `displayName`, `basePrice` and `baseFare`). Your frontend code can read either field without breaking.

---

### Step 2: Estimate Fare for Bike
* **URL**: `POST https://api.anushaporter.com/api/passenger/fare-estimate`
* **Headers**: `Content-Type: application/json`
* **Payload**:
```json
{
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "BIKE",
  "pickupAddress": "Indiranagar 100ft Road, Bengaluru",
  "dropAddress": "Koramangala 4th Block, Bengaluru",
  "pickupLatitude": 12.9784,
  "pickupLongitude": 77.6408,
  "dropLatitude": 12.9352,
  "dropLongitude": 77.6245,
  "passengerCount": 1,
  "luggageCount": 1
}
```

> **Alias Support**:  
> If your client passes `"2_WHEELER"`, `"TWO_WHEELER"`, `"SCOOTER"`, or `"MOTO"`, the backend automatically resolves it to `"BIKE"`.

* **Expected Success (200 OK)**:
```json
{
  "fareLockToken": "e7b049d5-45c2-4919-913a-a1b920199e8d",
  "fareLockExpiresAt": "2026-09-09T17:05:00",
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "BIKE",
  "vehicleDisplayName": "Bike",
  "passengerCapacity": 1,
  "distanceKm": 6.8,
  "durationMinutes": 18,
  "breakdown": {
    "baseFare": 20.00,
    "distanceFare": 42.40,
    "timeFare": 0.00,
    "driverAllowance": 0.00,
    "surgeMultiplier": 1.0,
    "nightCharge": 0.00,
    "discount": 0.00,
    "taxes": 3.12,
    "totalFare": 65.52
  }
}
```

* **Capacity Validation Check**:
If the user selects `passengerCount: 2` (or greater) for `BIKE`, the API returns `400 Bad Request`:
```json
{
  "message": "Please select a larger vehicle for this number of passengers."
}
```
*Frontend Action*: Disable the Bike card or show the warning message: *"Bike accommodates max 1 passenger."*

---

### Step 3: Book the Bike Ride
* **URL**: `POST https://api.anushaporter.com/api/passenger/bookings`
* **Headers**: `Content-Type: application/json`
* **Payload**:
```json
{
  "customerId": 101,
  "customerName": "Rahul Sharma",
  "customerPhone": "+919876543210",
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "BIKE",
  "passengerCount": 1,
  "luggageCount": 1,
  "pickupAddress": "Indiranagar 100ft Road, Bengaluru",
  "pickupLat": 12.9784,
  "pickupLng": 77.6408,
  "dropAddress": "Koramangala 4th Block, Bengaluru",
  "dropLat": 12.9352,
  "dropLng": 77.6245
}
```
* **Response (200 OK)**: Returns the created booking with status `"REQUESTED"` and booking number (e.g. `"AP-CAR-260909164235-2831"`).

---

## 🚫 2. Order Cancellation Window Flow

### Customer App Cancellation Rule:
* The customer **can cancel** the order when:
  1. Placing the order / searching for driver (`"searching"`, `"pending"`, `"created"`).
  2. Driver assigned & en route to pickup (`"driver_assigned"`).
  3. Trip in transit (`"in_transit"`).
* The cancellation is **blocked** once:
  1. Driver has arrived near the drop location (`driver_reached`, `arrived_at_drop`, `unloading`).
  2. Driver GPS coordinates are within **$\le 300$ meters** of the drop location.
  3. Ride is completed or delivery OTP has been verified.

### Step 1: Check If Ride Can Be Cancelled
* **URL**: `GET https://api.anushaporter.com/api/bookings/{id}/tracking` (or `GET /api/bookings/{id}`)
* **Response Fields**:
```json
{
  "orderId": 450,
  "status": "in_transit",
  "canCancel": true,
  "isCancellable": true,
  "cancellationBlockedReason": null,
  "cancellationMessage": "You can cancel this order until the driver reaches near the drop location."
}
```

When the driver is within 300 meters of the drop location:
```json
{
  "orderId": 450,
  "status": "in_transit",
  "canCancel": false,
  "isCancellable": false,
  "cancellationBlockedReason": "DRIVER_NEAR_DROP_LOCATION",
  "cancellationMessage": "Cancellation is no longer allowed. The driver has arrived near the drop location."
}
```

### Step 2: Submit Cancellation
* **URL**: `PUT https://api.anushaporter.com/api/bookings/{id}/cancel` (or `POST /api/bookings/{id}/cancel`)
* **Headers**: `Content-Type: application/json`
* **Payload**:
```json
{
  "reason": "Changed my mind",
  "cancelledBy": "CUSTOMER"
}
```

* **Success (200 OK)**:
```json
{
  "success": true,
  "message": "Ride cancelled successfully",
  "orderId": 450,
  "status": "cancelled"
}
```

* **Expired Window (400 Bad Request)**:
```json
{
  "success": false,
  "error": "CANCELLATION_WINDOW_EXPIRED",
  "message": "Cancellation is no longer allowed. The driver is already near or at the drop location."
}
```
*Frontend Action*: Display an alert toast: *"Driver is already near the drop location. Cancellation is locked."*

---

## 🔔 3. Multi-Driver Broadcast & Instant Stop Ringtone Flow

### What the Backend Does:
1. **Simultaneous Dispatch**: When an order is placed, notification is pushed to **all matching drivers** in the radius at the same time.
2. **Instant Ringtone Stop**: The instant any driver clicks **Accept**:
   * All other drivers' offers are immediately marked `TOO_LATE`.
   * A silent, high-priority push notification is sent with `action: "STOP_RINGTONE"`.
   * A WebSocket event `driver:offer:stop` is broadcast.

### Frontend Driver App Handler:

#### A. Firebase Cloud Messaging (FCM) Background / Foreground Listener:
```javascript
// React Native / Flutter / Android FCM message handler
messaging().onMessage(async remoteMessage => {
  const data = remoteMessage.data || {};

  if (data.action === "STOP_RINGTONE" || data.type === "STOP_DRIVER_OFFER") {
    // 1. Instantly stop sound player & vibration
    RingtonePlayer.stop();
    Vibration.cancel();

    // 2. Dismiss the modal or bottom sheet
    if (currentIncomingOrderId === data.orderId) {
      dismissIncomingRideModal();
      showToast("Ride accepted by another driver.");
    }
  }
});
```

#### B. WebSocket Telemetry Listener:
* **STOMP Destination**: `/topic/driver-telemetry` (or `/topic/driver-offers`)
* **Event**:
```json
{
  "event": "driver:offer:stop",
  "orderId": 450,
  "acceptedDriverId": 88,
  "action": "STOP_RINGTONE"
}
```

---

## 👤 4. Driver Registration & Profile Flow

### Step 1: Check Current Registration Step
* **URL**: `GET https://api.anushaporter.com/api/drivers/me` (or `/api/driver/me`)
* **Headers**: `Authorization: Bearer <token>`
* **Behavior**:
  * If a driver record does not exist yet for this account, the backend **auto-provisions a draft profile** with `registrationStep: 1`, `kycStatus: "draft"`.
  * The app will **never** receive `"Account not found"` or get thrown into an infinite login loop.

### Step 2: Step 1 Profile Information
In Step 1 of Driver Registration, the UI must prompt for:
1. **Full Name** (`name`)
2. **Phone Number** (`phone`)
3. **Email Address** (`email`)
4. **Profile Photo** (`profilePhoto` or `profilePhotoUri`)
5. **Date of Birth** (`dob`, formatted `YYYY-MM-DD`)
6. **Gender** (`gender`, e.g. `"Male"` / `"Female"`)

* **Submit Step 1**:
* **URL**: `POST https://api.anushaporter.com/api/drivers/register`
* **Payload**:
```json
{
  "name": "Suresh Kumar",
  "phone": "+919876543210",
  "email": "suresh@example.com",
  "dob": "1994-06-15",
  "gender": "Male",
  "profilePhotoUri": "https://anushaporter-driver-documents.s3.ap-south-1.amazonaws.com/drivers/101/avatar.jpg",
  "registrationStep": 1
}
```
*(If any optional field is blank, the backend automatically assigns defaults so the final submission succeeds without error.)*
