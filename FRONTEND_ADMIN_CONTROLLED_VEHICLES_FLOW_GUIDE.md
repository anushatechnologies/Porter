# Frontend Integration Flow: Admin-Controlled Vehicles

This guide explains the complete end-to-end architecture and API flow for the frontend team (Customer App, Driver App, and Admin Portal) following the backend overhaul.

---

## 1. What Changed?

* **Previous Behavior:** The backend automatically seeded hardcoded vehicles (`2 Wheeler`, `3 Wheeler / Auto`, `Tata Ace`) into the database on startup. If the database was empty, backend APIs automatically injected in-memory default lists.
* **New Behavior:** **No automatic defaults or hardcoded in-memory fallbacks.**
  - **Only vehicles created, managed, and marked `status: "active"` by the Admin in the Admin Panel** will be returned in APIs.
  - Both Customer and Driver apps dynamically render the exact vehicle list configured by the Admin.
  - If the Admin deactivates or removes vehicles, they disappear immediately from both apps.
  - Matching, auto-assignment, seat/passenger capacity, and track separation (Goods vs Passenger) work seamlessly with Admin-added vehicles.

---

## 2. End-to-End User & System Flow

```
+--------------------------------------------------------------------------------+
|                                1. ADMIN PORTAL                                 |
|  Admin adds or updates vehicles (e.g., Tata Ace, Electric Auto, Cab Sedan)     |
|  POST /api/admin/vehicle-types  or  PUT /api/admin/vehicle-types/{id}          |
+---------------------------------------+----------------------------------------+
                                        |
                                        v
                       +---------------------------------+
                       |         POSTGRESQL / DB         |
                       | Table: vehicle_types (active=T) |
                       +----------------+----------------+
                                        |
                 +----------------------+----------------------+
                 |                                             |
                 v                                             v
+---------------------------------+           +---------------------------------+
|         2. CUSTOMER APP         |           |         3. DRIVER APP           |
| Fetch active vehicles:          |           | Fetch vehicle options:          |
| GET /api/customer/services      |           | GET /api/driver/vehicle-types   |
| or GET /api/vehicle/types       |           | or /api/driver/vehicle-options  |
| (Only Admin vehicles displayed) |           | (Driver registers their vehicle)|
+----------------+----------------+           +----------------+----------------+
                 |                                             |
                 | Customer books a ride / delivery            |
                 | POST /api/orders or /api/passenger/book     |
                 v                                             |
+--------------------------------------------------------------v----------------+
|                         4. UNIFIED DISPATCH SERVICE                           |
|  Matches order's vehicle with active drivers having the SAME vehicle/category |
|  Dispatches offer via WebSocket: /topic/driver/{driverId}/offers              |
+-------------------------------------------------------------------------------+
```

---

## 3. Frontend Implementation Details by App

### A. Admin Portal (Vehicle Management)

The Admin is the sole source of truth for available vehicles.

#### 1. Fetch Existing Vehicles
* **Endpoint:** `GET /api/admin/vehicle-types` or `GET /api/admin/vehicles`
* **Response:**
```json
{
  "success": true,
  "vehicles": [
    {
      "id": "1",
      "name": "Tata Ace",
      "type": "tata_ace",
      "serviceType": "OUR_SERVICES",
      "capacityKg": 750,
      "baseFare": 250.0,
      "perKmRate": 25.0,
      "status": "active",
      "priority": 1
    }
  ]
}
```

#### 2. Create a New Vehicle
* **Endpoint:** `POST /api/admin/vehicle-types`
* **Payload:**
```json
{
  "name": "Electric Auto",
  "type": "electric_auto",
  "serviceType": "OUR_SERVICES",
  "description": "Eco-friendly 3-wheeler for city deliveries",
  "capacityKg": 500,
  "baseFare": 120.0,
  "baseDistanceKm": 2.0,
  "perKmRate": 16.0,
  "status": "active",
  "priority": 2
}
```
> **Important Fields:**
> - `serviceType`: Use `"OUR_SERVICES"` for Goods/Freight deliveries, or `"PASSENGER"` for Passenger/Cab rides.
> - `status`: Set to `"active"` for it to show in apps, or `"inactive"` to hide it.
> - `type`: Unique snake_case machine key (e.g., `two_wheeler`, `tata_ace`, `sedan`).

---

### B. Customer App (Service Selection & Booking)

#### 1. Fetching Available Vehicles
* **Goods & Logistics:** `GET /api/customer/services` or `GET /api/porter/services`
* **All Active Vehicles:** `GET /api/vehicle/types`
* **Passenger Rides:** `GET /api/passenger/categories`

#### 2. Frontend Handling:
* If the API returns `[]` (an empty array), show a graceful state:
  > *"No vehicles are currently operating in this category/area. Please check back shortly."*
* Never hardcode default vehicle cards in the frontend bundle. Dynamically loop over the returned list:
```javascript
// Example React / React Native:
const [vehicles, setVehicles] = useState([]);

useEffect(() => {
  fetch('/api/customer/services')
    .then(res => res.json())
    .then(data => {
      if (data.success && data.services) {
        setVehicles(data.services);
      }
    });
}, []);
```

#### 3. Booking an Order:
* When the customer taps "Book", send the selected vehicle's `name` or `type` in the order creation payload:
```json
POST /api/orders
{
  "pickupAddress": "Koramangala, Bangalore",
  "dropAddress": "Indiranagar, Bangalore",
  "vehicle": "Tata Ace",
  "vehicleType": "tata_ace",
  "goodsType": "Electronics",
  "fare": 350.00
}
```

---

### C. Driver App (Registration & Vehicle Setup)

#### 1. Fetching Vehicle Options During Registration
* **Endpoint:** `GET /api/driver/vehicle-types` or `GET /api/driver/vehicle-options`
* **Response:** Returns only the active vehicles configured by Admin:
```json
{
  "success": true,
  "vehicles": [
    {
      "id": "1",
      "name": "Tata Ace",
      "type": "tata_ace",
      "serviceType": "OUR_SERVICES",
      "capacityKg": 750
    }
  ]
}
```

#### 2. Saving Driver's Vehicle:
* In the registration step or vehicle update profile:
```json
POST /api/driver/register
{
  "name": "Ramesh Kumar",
  "phone": "9876543210",
  "vehicle": "Tata Ace",
  "vehicleType": "tata_ace",
  "vehicleNumber": "KA-01-AB-1234"
}
```

---

### D. Dispatch & Auto-Assignment Matching

When an order is created, the backend's `DriverEligibilityService` matches drivers according to the Admin's vehicle setup:

1. **Same Vehicle Matching:**
   - Exact string match (`driver.vehicle == order.vehicle` or `driver.vehicleType == order.vehicleType`).
   - Cleaned normalized string matching (e.g., `"tataace"` matches `"Tata Ace"`).
2. **Track Separation:**
   - Freight/Goods vehicles (Tata Ace, 8ft Truck, Pickup, etc.) will **never** receive passenger ride offers.
   - Dedicated passenger cabs will **never** receive heavy freight delivery orders.
3. **Passenger Capacity:**
   - Passenger orders verify that `passengerCount <= vehicle.seatingCapacity`.
4. **Driver Offer Delivery:**
   - Matches are broadcasted in expanding radius tiers (5 km -> 10 km -> 15 km) over WebSocket topic: `/topic/driver/{driverId}/offers`.

---

## 4. Testing Checklist for Frontend Developers

| Test Case | Steps | Expected Result |
|---|---|---|
| **1. Empty DB State** | Call `GET /api/customer/services` before any Admin vehicles exist. | Returns `{"success": true, "services": []}`. UI displays graceful empty state. |
| **2. Admin Adds Vehicle** | Admin creates a vehicle (e.g. `Electric Loader`) in Admin Portal. | `GET /api/customer/services` and `GET /api/driver/vehicle-types` immediately return `Electric Loader`. |
| **3. Driver Registration** | Driver registers choosing `Electric Loader`. | Driver profile stores vehicle correctly. |
| **4. Customer Booking** | Customer books ride with `Electric Loader`. | Order created with `vehicle: "Electric Loader"`. |
| **5. Dispatch Matching** | Verify driver offers via WebSocket or `GET /api/driver/offers`. | Driver with `Electric Loader` receives the order offer. |
| **6. Deactivate Vehicle** | Admin sets vehicle status to `inactive`. | Vehicle disappears from Customer and Driver app vehicle selections. |
