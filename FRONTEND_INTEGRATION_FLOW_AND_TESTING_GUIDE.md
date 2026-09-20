# Frontend Integration Flow & Testing Guide
**Target Audience:** Frontend Developers (Admin Web Panel, Customer Mobile App, Driver Mobile App)

---

## 1. Admin Panel Vehicle Flow (Web & Mobile Dashboard)

### Which Endpoint to Call?
Both endpoints now read and write to the **exact same database table (`vehicle_types`)**:
* `GET /api/admin/vehicles` *(Recommended)*
* `GET /api/admin/vehicle-types`

### A. Fetch Vehicle Catalogue (Admin View)
Call without query parameters to fetch all vehicles (both active and inactive):
```http
GET /api/admin/vehicles
Authorization: Bearer <adminToken>
```
**Response:**
```json
{
  "success": true,
  "serviceType": "ALL",
  "serviceCategory": "All Services",
  "count": 11,
  "vehicles": [
    {
      "id": "1",
      "name": "2 Wheeler",
      "type": "two_wheeler",
      "description": "Best for goods delivery & parcel",
      "capacity": "Load: Up to 20kg",
      "capacityKg": 20,
      "imageUrl": "https://images.unsplash.com/...",
      "baseFare": 40.0,
      "baseKm": 1.0,
      "perKmRate": 12.0,
      "status": "active",
      "serviceType": "OUR_SERVICES",
      "priority": 1
    },
    {
      "id": "2",
      "name": "3 Wheeler / Auto",
      "type": "auto_rickshaw",
      "status": "active",
      "serviceType": "OUR_SERVICES",
      "baseFare": 120.0
    },
    {
      "id": "6",
      "name": "Cab",
      "type": "cab",
      "status": "active",
      "serviceType": "PASSENGER",
      "baseFare": 150.0
    }
  ]
}
```

### B. Add a New Vehicle Type
```http
POST /api/admin/vehicles
Content-Type: application/json
Authorization: Bearer <adminToken>

{
  "name": "EV Auto",
  "type": "ev_auto",
  "description": "Eco-friendly electric auto",
  "capacity": "3 Passengers",
  "capacityKg": 250,
  "baseFare": 45.0,
  "baseKm": 1.5,
  "perKmRate": 14.0,
  "serviceType": "PASSENGER",
  "status": "active",
  "priority": 9
}
```

### C. Update Pricing or Details
```http
PUT /api/admin/vehicles/{id}
Content-Type: application/json
Authorization: Bearer <adminToken>

{
  "baseFare": 50.0,
  "perKmRate": 15.0
}
```

### D. Toggle Status (Active / Inactive)
Setting a vehicle to `inactive` hides it from the Customer and Driver apps:
```http
PATCH /api/admin/vehicles/{id}/status
Content-Type: application/json
Authorization: Bearer <adminToken>

{
  "status": "inactive"
}
```

---

## 2. Customer App Flow (Passenger Ride Booking)

### Step 1: Get Fare Estimates
```http
POST /api/passenger/fare-estimate
Content-Type: application/json

{
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "AUTO",
  "pickupAddress": "Madhapur, Hyderabad",
  "pickupLat": 17.4486,
  "pickupLng": 78.3908,
  "dropAddress": "Hitech City, Hyderabad",
  "dropLat": 17.4500,
  "dropLng": 78.3920,
  "passengerCount": 2
}
```
**Response contains available vehicle options and estimated fares:**
* `vehicleCategoryCode`: `"AUTO"` (Auto Rickshaw)
* `vehicleCategoryCode`: `"BIKE"` (Bike Taxi)
* `vehicleCategoryCode`: `"CAB"` (Cab / Sedan)

### Step 2: Create Booking
```http
POST /api/passenger/bookings
Content-Type: application/json
Authorization: Bearer <customerJwtToken>

{
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "AUTO",
  "pickupAddress": "Madhapur, Hyderabad",
  "pickupLat": 17.4486,
  "pickupLng": 78.3908,
  "dropAddress": "Hitech City, Hyderabad",
  "dropLat": 17.4500,
  "dropLng": 78.3920,
  "customerName": "Ravi Kumar",
  "customerPhone": "9876543210",
  "passengerCount": 2,
  "paymentMethod": "CASH"
}
```
**Response (`201 Created`):**
```json
{
  "success": true,
  "bookingNumber": "AP-CAR-260920214024-1805",
  "status": "DRIVER_SEARCHING",
  "serviceType": "ONE_WAY",
  "vehicleCategory": "AUTO",
  "startOtp": "4821",
  "fare": 75.0
}
```

---

## 3. Driver App Flow (Auto / Bike / Cab Drivers)

### Compatibility Matrix
* **Auto Drivers** (`vehicleType`: `"Auto"`, `"3 Wheeler / Auto"`): Receive both Goods deliveries & Passenger Auto rides (`AUTO`).
* **Bike Drivers** (`vehicleType`: `"2 Wheeler"`, `"Bike"`): Receive both Courier deliveries & Bike Taxi rides (`BIKE`).
* **Cab Drivers** (`vehicleType`: `"Cab"`, `"Sedan"`, `"SUV"`): Receive Passenger Cab rides (`CAB`).
* **Truck Drivers** (`Tata Ace`, `Pickup`, `Tata 407`, `LPT 1109`): Strictly freight-only; will never receive passenger rides.

### Channel 1: Real-Time WebSocket Push (Instant Ringing)
1. **Connect WebSocket:**
   ```text
   wss://api.anushaporter.com/ws/telemetry
   ```
2. **Listen for incoming offer event:**
   ```json
   {
     "event": "driver:offer:new",
     "bookingId": "AP-CAR-260920214024-1805",
     "targetDriverIds": [101, 102],
     "data": {
       "bookingId": "AP-CAR-260920214024-1805",
       "pickupAddress": "Madhapur, Hyderabad",
       "dropAddress": "Hitech City, Hyderabad",
       "amount": 75.00,
       "distanceKm": 3.2,
       "serviceType": "PASSENGER",
       "serviceLabel": "Passenger Ride (2 Riders)",
       "passengerCount": 2
     }
   }
   ```
3. **Action:** Play ringing sound, show popup ride modal with 60-second countdown timer.

4. **Listen for offer dismiss event (if another driver accepts first or customer cancels):**
   ```json
   {
     "event": "driver:offer:stop",
     "bookingId": "AP-CAR-260920214024-1805",
     "stopSound": true,
     "action": "STOP_RINGTONE"
   }
   ```

### Channel 2: HTTP Polling (Fallback every 4 seconds)
If the WebSocket drops or is reconnecting:
```http
GET /api/driver/orders/available?lat=17.4485&lng=78.3905&radiusKm=10
Authorization: Bearer <driverToken>
```
**Response:**
```json
{
  "success": true,
  "count": 1,
  "orders": [
    {
      "id": 55,
      "bookingId": "AP-CAR-260920214024-1805",
      "serviceName": "AUTO",
      "pickupAddress": "Madhapur, Hyderabad",
      "dropAddress": "Hitech City, Hyderabad",
      "pickupLat": 17.4486,
      "pickupLng": 78.3908,
      "amount": 75.0,
      "distanceKm": 3.2,
      "pickupDistanceKm": 0.05,
      "status": "available"
    }
  ]
}
```

Or poll active offers:
```http
GET /api/driver/offers/active
Authorization: Bearer <driverToken>
```

### Step 3: Driver Accepts or Rejects the Ride
#### To ACCEPT:
```http
POST /api/driver/offers/AP-CAR-260920214024-1805/respond
Content-Type: application/json
Authorization: Bearer <driverToken>

{
  "accept": true
}
```
**Success Response (`200 OK`):**
```json
{
  "success": true,
  "status": "ASSIGNED",
  "bookingId": "AP-CAR-260920214024-1805",
  "driverId": 101,
  "stopSound": true,
  "action": "STOP_RINGTONE",
  "message": "Booking assigned successfully!"
}
```

#### To REJECT:
```http
POST /api/driver/offers/AP-CAR-260920214024-1805/respond
Content-Type: application/json
Authorization: Bearer <driverToken>

{
  "accept": false
}
```

---

## 4. Crucial Testing Checklist for Frontend QA

1. **Driver Online Status:**
   Ensure test driver in DB has `status = "online"`, `kyc = "approved"`, `verificationStatus = "approved"`.
2. **The 5.0 km Radius Rule:**
   The backend tier-1 dispatch searches within **5.0 km** of the customer's `pickupLat`/`pickupLng`.
   * Ensure your test customer pickup coordinates are within **1 to 2 km** of your test driver's GPS location.
   * If testing from different cities, the driver will not receive the tier-1 offer.
3. **Vehicle Matching:**
   * Book with `"vehicleCategoryCode": "AUTO"` -> Test with an Auto driver.
   * Book with `"vehicleCategoryCode": "BIKE"` -> Test with a 2-Wheeler / Bike driver.
   * Book with `"vehicleCategoryCode": "CAB"` -> Test with a Cab driver.
