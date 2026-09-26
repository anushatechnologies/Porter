# Frontend Developer Guide: Fleet Synchronization & Vehicle Selection Flow

This document provides the complete end-to-end integration guide for the **Frontend Mobile & Web Teams** (Driver App, Customer App, and Admin Web Panel).

---

## 1. System Architecture Overview

The platform supports two distinct lines of business with **100% strict catalog separation**:
1. **Our Services (Logistics & Delivery Fleet):** 2-Wheelers, Tata Ace, Pickup Trucks, 17ft, 20ft, 24ft, 32ft, 40ft Containers, Packers & Movers.
2. **Passenger Taxi (Cab & Ride Fleet):** Bike Taxi, Auto Taxi, Economy Cab, Sedan, SUV, Premium Cab.

```
       ┌──────────────────────── Admin Web Panel ────────────────────────┐
       │                                                                │
       ▼                                                                ▼
[Our Services Tab]                                            [Passenger Taxi Tab]
POST/PUT /api/admin/services                       POST/PUT /api/admin/passenger/vehicle-categories
       │                                                                │
       ▼                                                                ▼
Database Table: `services`                         Database Table: `passenger_vehicle_categories`
       │                                                                │
       └──────────────────► FleetSyncService (Auto-Sync) ◄──────────────┘
                                        │
                                        ▼
                      Database Table: `vehicle_types`
                   (Categorized by serviceType: OUR_SERVICES / PASSENGER / BOTH)
                                        │
            ┌───────────────────────────┴───────────────────────────┐
            │                                                       │
            ▼                                                       ▼
   [Driver App Step 3]                                     [Customer App]
GET /api/driver/vehicle-types?serviceType=OUR_SERVICES    GET /api/services (Delivery)
GET /api/driver/vehicle-types?serviceType=PASSENGER       GET /api/passenger/categories (Rides)
```

---

## 2. Driver Mobile App Flow (Step 3: Vehicle Selection)

When a driver reaches **Step 3 (Vehicle Selection & Details)** in the driver onboarding KYC flow:

### 2.1 Tab Switching on Step 3 Screen
The UI provides two service category options for the driver:
* **Option A: "Our Services" (Delivery, Courier, Freight)**
* **Option B: "Passenger Taxi" (Passenger Rides, Cabs, Autos)**

---

### 2.2 Case 1: Driver Selects "Our Services" (Delivery & Courier)

#### 1. API Call:
```http
GET /api/driver/vehicle-types?status=active&serviceType=OUR_SERVICES
Authorization: Bearer <jwt_token>
```

#### 2. Filtering Guarantee:
* The backend returns **ONLY** delivery, courier, freight, and cargo vehicles.
* Passenger vehicles (Hatchbacks, Sedans, Cabs) are **strictly excluded**.
* If a vehicle is marked "inactive" in Admin, it is filtered out.

#### 3. Sample Backend Response:
```json
{
  "success": true,
  "serviceType": "OUR_SERVICES",
  "serviceCategory": "Our Services",
  "count": 4,
  "vehicles": [
    {
      "id": "two-wheeler",
      "name": "2 Wheeler",
      "displayName": "2 Wheeler (Bike / Scooter)",
      "type": "two_wheeler",
      "typeCode": "two_wheeler",
      "serviceType": "OUR_SERVICES",
      "description": "Best for documents, groceries & food parcels",
      "capacity": "Up to 20 kg",
      "capacityKg": 20,
      "dimensions": "Ideal for small parcels & courier",
      "iconName": "bike",
      "imageUrl": "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=400&q=80",
      "baseFare": 40.0,
      "baseKm": 1.0,
      "perKmRate": 12.0,
      "status": "active",
      "priority": 1
    },
    {
      "id": "tata-ace",
      "name": "Tata Ace",
      "displayName": "Tata Ace (Chota Hathi)",
      "type": "mini_truck",
      "typeCode": "mini_truck",
      "serviceType": "OUR_SERVICES",
      "description": "750 kg capacity payload for furniture & boxes",
      "capacity": "Up to 750 kg",
      "capacityKg": 750,
      "dimensions": "7ft x 4ft x 5ft",
      "iconName": "truck-delivery",
      "imageUrl": "https://images.unsplash.com/photo-1519003722824-194d4455a60c?w=400&q=80",
      "baseFare": 250.0,
      "baseKm": 2.0,
      "perKmRate": 25.0,
      "status": "active",
      "priority": 2
    },
    {
      "id": "17ft",
      "name": "17ft Truck",
      "displayName": "17ft Container Truck",
      "type": "truck",
      "typeCode": "truck",
      "serviceType": "OUR_SERVICES",
      "description": "Heavy commercial logistics for commercial cargo",
      "capacity": "Up to 4000 kg",
      "capacityKg": 4000,
      "dimensions": "17ft x 6ft x 6ft",
      "iconName": "truck-delivery",
      "imageUrl": "https://images.unsplash.com/photo-1601584115197-04ecc0da31d7?w=400&q=80",
      "baseFare": 1200.0,
      "baseKm": 5.0,
      "perKmRate": 45.0,
      "status": "active",
      "priority": 3
    }
  ]
}
```

---

### 2.3 Case 2: Driver Selects "Passenger Taxi" (Rides & Cabs)

#### 1. API Call:
```http
GET /api/driver/vehicle-types?status=active&serviceType=PASSENGER
Authorization: Bearer <jwt_token>
```

#### 2. Filtering Guarantee:
* The backend returns **ONLY** passenger vehicles (Auto, Bike Taxi, Hatchback, Sedan, SUV, Cab).
* Freight/delivery trucks (Tata Ace, 17ft, 20ft) are **strictly excluded**.

#### 3. Sample Backend Response:
```json
{
  "success": true,
  "serviceType": "PASSENGER",
  "serviceCategory": "Passenger Rides",
  "count": 3,
  "vehicles": [
    {
      "id": "AUTO",
      "name": "Auto Rickshaw",
      "displayName": "Auto Taxi",
      "type": "AUTO",
      "serviceType": "PASSENGER",
      "description": "3 Passengers city ride",
      "capacity": "3 Seats",
      "capacityKg": 50,
      "maxPassengers": 3,
      "maxLuggage": 2,
      "iconName": "rickshaw",
      "imageUrl": "https://images.unsplash.com/photo-1541899481282-d53bffe3c35d?w=400&q=80",
      "baseFare": 35.0,
      "baseKm": 1.5,
      "perKmRate": 15.0,
      "status": "active",
      "priority": 1
    },
    {
      "id": "SEDAN",
      "name": "Sedan Cab",
      "displayName": "Sedan (Dzire / Etios)",
      "type": "SEDAN",
      "serviceType": "PASSENGER",
      "description": "Comfortable AC sedan with boot space",
      "capacity": "4 Seats",
      "capacityKg": 80,
      "maxPassengers": 4,
      "maxLuggage": 3,
      "iconName": "car",
      "imageUrl": "https://poteranusha.s3.ap-south-2.amazonaws.com/vehicles/cab.png",
      "baseFare": 80.0,
      "baseKm": 2.0,
      "perKmRate": 18.0,
      "status": "active",
      "priority": 2
    }
  ]
}
```

---

### 2.4 Submitting Selected Vehicle (Step 3 Submission)

When the driver selects a vehicle from the list and enters their vehicle number, RC number, and driving license details:

#### 1. API Call:
```http
POST /api/drivers/register/save-and-next
Content-Type: application/json
Authorization: Bearer <jwt_token>
```
*(Also works with `POST /api/driver/register`)*

#### 2. Request Payload:
```json
{
  "step": 3,
  "vehicleId": "two-wheeler",
  "vehicle": "2 Wheeler",
  "vehicleType": "2 Wheeler",
  "vehicle_type": "two_wheeler",
  "serviceType": "OUR_SERVICES",
  "vehicleNumber": "TS09EA1234",
  "rcNumber": "RC9928381928",
  "licenseNumber": "DL1420110012345",
  "saveAndNext": true
}
```

#### 3. Frontend Field Recommendations:
* Pass `vehicleId`: The `item.id` from the selected vehicle object.
* Pass `vehicle`: The `item.name` or `item.displayName`.
* Pass `vehicleType`: The `item.name` or `item.type`.
* Pass `serviceType`: `"OUR_SERVICES"` or `"PASSENGER"`.

> **Note on Resiliency:**  
> The backend resolver automatically inspects `vehicleId`, `vehicle`, `vehicleType`, and `vehicle_type`. Even if legacy code sends prefixed IDs like `"veh_two_wheeler"`, the backend resolver normalizes it automatically and will not reject the registration.

#### 4. Success Response (200 OK):
```json
{
  "success": true,
  "registrationStep": 4,
  "driver": {
    "id": 12,
    "phone": "9014397044",
    "vehicle": "2 Wheeler",
    "vehicleType": "2 Wheeler",
    "serviceType": "OUR_SERVICES",
    "vehicleNumber": "TS09EA1234",
    "registrationStep": 4,
    "approvalStatus": "pending"
  }
}
```

---

## 3. Customer App Integration Flow

### 3.1 Customer Booking Our Services (Goods & Courier)
* **Endpoint:** `GET /api/services`
* Returns active delivery services (2-Wheeler, Tata Ace, Trucks, Packers & Movers) with marketing tags, ETA labels, and base pricing for home screen cards.

### 3.2 Customer Booking Passenger Rides (Taxis & Cabs)
* **Endpoint:** `GET /api/passenger/categories` OR `GET /api/vehicle-types?status=active&serviceType=PASSENGER`
* Returns available passenger ride categories (Auto, Sedan, SUV, Bike Taxi).

---

## 4. Admin Web Dashboard Flow

Admin can manage both catalogs independently without worrying about database discrepancies:

| Catalog Screen | Admin Action | What Happens Behind the Scenes |
|---|---|---|
| **Admin > Our Services** | Add / Edit a delivery truck or courier service at `/api/admin/services` | 1. Saved to `services` table.<br>2. Backend **auto-syncs** to `vehicle_types` table with `serviceType = "OUR_SERVICES"`.<br>3. Immediately visible in Customer App and Driver App (Our Services). |
| **Admin > Passenger Fleet** | Add / Edit a passenger vehicle category at `/api/admin/passenger/vehicle-categories` | 1. Saved to `passenger_vehicle_categories` table.<br>2. Backend **auto-syncs** to `vehicle_types` table with `serviceType = "PASSENGER"`.<br>3. Immediately visible in Customer App and Driver App (Passenger Taxi). |
| **Admin > Toggle Active/Inactive** | Admin toggles a vehicle off | Deactivates the vehicle in both tables. If a driver attempts to register for it, returns HTTP 400 `"Selected vehicle type is no longer available."`. |

---

## 5. Frontend Error Handling Checklist

| HTTP Status | Condition | Recommended UI Feedback |
|---|---|---|
| `200 OK` with `count: 0` | No vehicles available in this category | Show: *"No vehicles currently available for this service. Please select another option or contact support."* |
| `400 Bad Request` | `"Selected vehicle type is no longer available."` | This **only** triggers if an Admin explicitly disabled the vehicle in the Admin dashboard. Refresh the vehicle list and ask the user to pick an active vehicle. |
| `401 Unauthorized` | Invalid / Expired JWT token | Redirect user to Login / OTP verification screen. |
| `500 Server Error` | Network / Server glitch | Show: *"Connection error. Please try again."* with a Retry button. |
