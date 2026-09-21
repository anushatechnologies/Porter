# Frontend Complete Developer Handoff Guide

This document is the single-source-of-truth guide for Frontend Developers working on the **Driver App**, **Customer App**, and **Admin Portal**. It covers:
1. **Multi-Step Driver Registration & Draft Resume Flow**
2. **Recent Backend Changes & Public Vehicle Endpoints**
3. **Frontend Bug Audit & Step-by-Step Fixes**

---

## 📌 Section 1: Driver Registration & Draft Resume Flow

### The Golden Rule:
> **A driver only registers ONCE in their lifetime.**
> - If an existing driver logs in $\rightarrow$ Go straight to `DriverDashboard`.
> - If a new driver leaves in the middle (e.g. at Step 2) $\rightarrow$ Resume at Step 2 with previously entered data pre-filled.
> - Registration is **only completed** when the driver reaches Step 4 and clicks **Submit**.

```
                           [ Driver Enters Phone / OTP ]
                                         │
                                         ▼
                            POST /api/auth/verify-otp
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 ▼                                               ▼
     isRegistered === true                           isRegistered === false
     (or registrationStep >= 5)                      (or hasDraft === true)
                 │                                               │
                 ▼                                               ▼
       [ DriverDashboard ]                        GET /api/drivers/register/progress
     (Skip Registration)                                         │
                                                                 ▼
                                                  Read 'registrationStep' (e.g., Step 3)
                                                  Resume at Step 3 with pre-filled inputs
                                                                 │
                                                                 ▼
                                                    [ Final Step 4 Submit ]
                                                  POST /api/drivers/register { submit: true }
                                                                 │
                                                                 ▼
                                                    registrationStep becomes 5
                                                    Navigate to DriverDashboard
```

### 1.1 Intermediate Step Save ("Save & Next")
When the driver completes Step 1, 2, or 3, call:
```http
POST /api/drivers/register
Authorization: Bearer <token>
Content-Type: application/json

{
  "step": 1,
  "saveAndNext": true,
  "name": "Ramesh Kumar",
  "dob": "1994-06-15",
  "gender": "Male"
}
```
* **Backend State:** Saves fields into DB, sets `hasDraft: true`, `kycStatus: "draft"`, and advances `registrationStep: 2`.

### 1.2 Resuming After App Restart / Leaving in Middle
When an incomplete driver opens the app, fetch their progress:
```http
GET /api/drivers/register/progress
Authorization: Bearer <token>
```
**Response Contract:**
```json
{
  "success": true,
  "isRegistered": false,
  "registrationCompleted": false,
  "hasDraft": true,
  "registrationStep": 3,
  "nextStep": 3,
  "kycStatus": "draft",
  "name": "Ramesh Kumar",
  "phone": "9876543210",
  "vehicle": "Tata Ace",
  "vehicleType": "tata_ace",
  "vehicleNumber": "KA-01-AB-1234",
  "aadhaarNumber": "123456789012"
}
```
* **Frontend Action:** Set `currentStep = res.data.registrationStep` and initialize the form state with the returned data.

### 1.3 Final Step Submit
On the final step (Step 4), send `submit: true`:
```http
POST /api/drivers/register
Authorization: Bearer <token>
Content-Type: application/json

{
  "step": 4,
  "submit": true,
  "accountHolderName": "Ramesh Kumar",
  "accountNumber": "50100012345678",
  "ifscCode": "HDFC0001234",
  "bankName": "HDFC Bank"
}
```
**Response:**
```json
{
  "success": true,
  "isRegistered": true,
  "registrationCompleted": true,
  "hasDraft": false,
  "registrationStep": 5,
  "kycStatus": "approved"
}
```
* **Frontend Action:** Show success message and navigate to `DriverDashboard`.

---

## 📌 Section 2: Recent Backend Changes (Admin-Only Vehicles & Public Routes)

### 2.1 No Hardcoded Default Vehicles
* Backend default seeders and in-memory fallbacks have been removed.
* Only vehicles created and marked active in the Admin Portal are returned by the API.

### 2.2 Public Driver Vehicle Endpoints (No 401 Errors)
During initial driver registration, drivers do not yet possess an auth token. The following routes are **public and require no token**:
* `GET /api/vehicle-types?status=active` (Primary)
* `GET /api/driver/vehicle-types` (Alias — now public)
* `GET /api/driver/vehicle-options` (Alias — now public)
* `GET /api/vehicle-types?status=active&serviceType=OUR_SERVICES` (Goods only)
* `GET /api/vehicle-types?status=active&serviceType=PASSENGER` (Passenger only)

---

## 📌 Section 3: Frontend Bugs Audit & Required Fixes

### 🔴 Fix 1: Include `serviceType` in Profile Edit Payload
* **File:** `src/screens/driver/DriverProfileScreen.tsx`
* **The Bug:** `handleSaveProfile()` sent `vehicle` and `vehicleType`, but omitted `serviceType`. For shared names like "Auto", the backend could misinterpret the driver's track.
* **The Fix:** Explicitly pass `serviceType` in the payload:
```typescript
// Inside handleSaveProfile():
const payload = {
  name,
  phone,
  vehicle,
  vehicleType,
  serviceType: driverProfile?.serviceType || (activeTrack === 'PASSENGER' ? 'PASSENGER' : 'OUR_SERVICES'),
  vehicleNumber,
  address,
  city,
  pincode,
  bankAccountNumber,
  bankIfscCode,
  bankAccountName,
  upiId
};
await updateDriverProfile(payload);
```

### 🟡 Fix 2: Clean Keyword Fallback in `src/services/api.ts`
* **File:** `src/services/api.ts`
* **The Bug:** In `getActiveVehicles()`, fallback logic checked `s.includes('bike') || s.includes('auto')` as passenger vehicles, which could misclassify Goods delivery Bikes and Autos.
* **The Fix:** Check the explicit `serviceType` field from the backend first:
```typescript
export const filterVehiclesByTrack = (vehicles: any[], targetTrack: 'OUR_SERVICES' | 'PASSENGER') => {
  return vehicles.filter(v => {
    // 1. If backend returned explicit serviceType, use it directly (100% reliable)
    if (v.serviceType) {
      return v.serviceType.toUpperCase() === targetTrack;
    }
    // 2. Defensive fallback only if serviceType is missing:
    const s = `${v.name || ''} ${v.type || ''} ${v.description || ''}`.toLowerCase();
    const isDedicatedPassenger = s.includes('cab') || s.includes('taxi') || s.includes('sedan') || s.includes('suv') || s.includes('passenger');
    return targetTrack === 'PASSENGER' ? isDedicatedPassenger : !isDedicatedPassenger;
  });
};
```

### 🟡 Fix 3: Use PATCH for Profile Updates
* **File:** `src/services/api.ts`
* **The Nuance:** Nginx proxies can reject `PUT /api/drivers/me` with HTTP 400 if `Content-Length` headers are omitted by mobile clients.
* **The Solution:** Use `PATCH /api/drivers/me` (or configure fallback to `PATCH` if `PUT` fails). The backend natively supports `PATCH`.
```typescript
export const updateDriverProfile = async (payload: any) => {
  try {
    const res = await apiClient.patch('/api/drivers/me', payload);
    return res.data;
  } catch (err) {
    // Fallback to PUT if needed
    const res = await apiClient.put('/api/drivers/me', payload);
    return res.data;
  }
};
```

---

## 📌 Section 4: Quick Testing Checklist for Frontend QA

| Test Scenario | Action | Expected Result |
|---|---|---|
| **1. Resume Incomplete Registration** | Fill Step 1 & 2, close app, log in again. | App calls `/api/drivers/register/progress`, opens directly at Step 3 with Step 1 & 2 fields populated. |
| **2. One-Time Registration Check** | Finish Step 4 and submit, then restart app. | App reads `isRegistered === true` or `registrationStep === 5`, opens Dashboard immediately. |
| **3. Onboarding Vehicle List** | Open vehicle selection in registration without logging in. | `GET /api/driver/vehicle-types` returns 200 OK with active vehicles. |
| **4. Profile Vehicle Edit** | Edit vehicle in profile as an Auto driver. | `serviceType` is preserved; driver stays in their correct track (Passenger vs Goods). |
| **5. Passenger Vehicle Visibility** | Open Passenger Ride screen in Customer App. | All active passenger vehicles added by Admin appear in vehicle list and fare estimates. |
| **6. Passenger Booking Dispatch** | Book a passenger ride with any available vehicle. | Booking succeeds and dispatches via WebSocket to matching active passenger drivers. |

---

## 📌 Section 5: Passenger Rides & Admin Vehicle Sync Flow

### 5.1 Architecture Overview

```
[ ADMIN PORTAL ]
  └─ Creates/Updates Passenger Vehicle: POST /api/admin/vehicle-types
     { name: "Cab", type: "cab", serviceType: "PASSENGER", baseFare: 150, perKmRate: 15 }
       │
       ▼ (Automatically synced to passenger_vehicle_categories)
       ├───────────────────────────────────────────────┐
       ▼                                               ▼
[ PASSENGER CUSTOMER APP ]                     [ DRIVER APP (Passenger) ]
  ├─ 1. Fetch Passenger Vehicles                 ├─ 1. Fetch Vehicle Options
  │  GET /api/passenger/categories               │  GET /api/vehicle-types?serviceType=PASSENGER
  │  (Renders all Admin passenger vehicles)      │  (Driver registers with "Cab")
  │                                              │
  ├─ 2. Calculate Fare Estimate                  │
  │  POST /api/passenger/fare-estimate           │
  │                                              │
  └─ 3. Book Ride                                └─ 2. Receive Ride Offer
     POST /api/passenger/book                       WebSocket: /topic/driver/{driverId}/offers
     { vehicleCategoryCode: "CAB", ... }            (Matches driver with Cab vehicle)
```

### 5.2 Step-by-Step API Contract for Frontend

#### Step 1: Customer App Fetches Passenger Vehicles
* **Endpoint:** `GET /api/passenger/categories` (or `/api/passenger/vehicles`)
* **Headers:** `Accept: application/json`
* **Response:**
```json
{
  "success": true,
  "vehicles": [
    {
      "id": "cab",
      "code": "CAB",
      "categoryCode": "CAB",
      "name": "Cab",
      "displayName": "Cab",
      "passengerCapacity": 4,
      "luggageCapacity": 2,
      "baseFare": 150.00,
      "perKmRate": 15.00,
      "imageUrl": "https://poteranusha.s3.ap-south-2.amazonaws.com/vehicles/cab.png",
      "isActive": true
    }
  ]
}
```

#### Step 2: Customer App Requests Fare Estimate
* **Endpoint:** `POST /api/passenger/fare-estimate`
* **Payload:**
```json
{
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "CAB",
  "pickupAddress": "Koramangala, Bangalore",
  "pickupLat": 12.9352,
  "pickupLng": 77.6245,
  "dropAddress": "Indiranagar, Bangalore",
  "dropLat": 12.9719,
  "dropLng": 77.6412,
  "passengerCount": 2
}
```
* **Response:**
```json
{
  "success": true,
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "CAB",
  "distanceKm": 6.5,
  "durationMinutes": 22,
  "estimatedFare": 247.50,
  "estimates": [ ...all available vehicle cards with estimated fares... ]
}
```

#### Step 3: Customer Books Ride
* **Endpoint:** `POST /api/passenger/book` (or `/api/passenger/bookings`)
* **Headers:** `Authorization: Bearer <customerToken>`
* **Payload:**
```json
{
  "serviceType": "ONE_WAY",
  "vehicleCategoryCode": "CAB",
  "pickupAddress": "Koramangala, Bangalore",
  "pickupLat": 12.9352,
  "pickupLng": 77.6245,
  "dropAddress": "Indiranagar, Bangalore",
  "dropLat": 12.9719,
  "dropLng": 77.6412,
  "passengerCount": 2,
  "paymentMethod": "CASH"
}
```
* **Response:**
```json
{
  "success": true,
  "bookingNumber": "AP-CAR-260921-1234",
  "status": "DRIVER_SEARCHING",
  "estimatedFare": 247.50
}
```

#### Step 4: Driver App Receives Live Offer
* Passenger drivers with active online status and matching vehicle receive the broadcast over WebSocket:
  * **Topic:** `/topic/driver/{driverId}/offers`
  * **Offer Payload:** Contains `bookingId`, `pickupAddress`, `dropAddress`, `fare`, and `timerSeconds`.

---

## 📌 Section 6: Customer App Place-Picker & Real Coordinates Contract

### 6.1 The 5.3 km Fixed Distance Issue & The Fix
* **The Root Cause:** In earlier builds, selecting places would default coordinates to Madhapur `(17.4486, 78.3808)`. Any booking created from Raidurgam / Gachibowli `(17.4197, 78.3749)` to `(17.4486, 78.3808)` computed a fixed road driving distance of **5.3 km**.
* **The Backend Resolution:** The backend now features:
  1. Dynamic OpenStreetMap (Nominatim) search and geocoding fallback (no API key required).
  2. In-memory coordinate cache so `placeId` lookup immediately returns real `lat` and `lng`.
  3. Real Hyderabad zone coordinates (Somajiguda, Banjara Hills, Raidurgam, Kukatpally, Secunderabad, etc.) preventing collapse to a single default point.
  4. Direct coordinate parsing: `coord_{lat}_{lng}` is supported as a `placeId`.

### 6.2 Frontend Best Practices for Customer App Place Picker
1. **Search Places:**
   * Call `GET /api/location/autocomplete?input={query}`
   * Returns a list of predictions with `placeId` (e.g., `osm_123456`, Google `ChIJ...`, or landmark ID).
2. **Fetch Exact Coordinates:**
   * When the customer taps a suggestion, call `GET /api/location/details?placeId={placeId}`
   * Response:
     ```json
     {
       "success": true,
       "data": {
         "placeId": "osm_123456",
         "name": "Yashoda Hospitals",
         "formattedAddress": "Yashoda Hospitals, Raj Bhavan Road, Somajiguda, Hyderabad",
         "lat": 17.4265,
         "lng": 78.4554
       }
     }
     ```
3. **Send Real Lat/Lng in Booking:**
   * When calling `POST /api/passenger/book`, pass the exact `lat` and `lng` received:
     ```json
     {
       "pickupAddress": "Raidurgam Police Station, Gachibowli",
       "pickupLat": 17.4197,
       "pickupLng": 78.3749,
       "dropAddress": "Yashoda Hospitals, Somajiguda",
       "dropLat": 17.4265,
       "dropLng": 78.4554,
       "vehicleCategoryCode": "CAB",
       "serviceType": "ONE_WAY"
     }
     ```
   * *If the Customer App already uses client-side Google Places / Maps SDK*, it can send the client-geocoded `lat` and `lng` directly into `dropLat` and `dropLng`.

