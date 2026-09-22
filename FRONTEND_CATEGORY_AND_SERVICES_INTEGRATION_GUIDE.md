# Frontend Developer Integration Guide: Service Categorization & 2-Wheeler / Scooter Flow

This guide details the backend changes made to category filtering, service grouping, and database normalization so the Frontend team (Customer App & Admin Panel) can easily integrate and verify.

---

## 1. What Changed in the Backend?

### The Previous Issue
* In the database, the vehicle **`2-wheeler`** had `category: "two_wheeler"`, while **`scooter`** had `category: "bike"`.
* When frontend called `GET /api/services?category=two_wheeler`, the backend ran a strict SQL query (`WHERE category = 'two_wheeler'`), which returned `2-wheeler` but **completely excluded `scooter`**.
* The same exclusion affected `/api/services/vehicles` and Admin category filtering.

### The Backend Fix Implemented
1. **Intelligent Category Aliasing & Normalization**:
   Backend queries for Category 2 now automatically recognize synonyms:
   * Querying `?category=two_wheeler`, `?category=bike`, `?category=scooter`, `?category=2-wheeler`, or `?category=2` will **all return both `2-wheeler` and `scooter`**.
2. **Category 1 (Trucks) & Category 3 (Packers) Aliases**:
   * Category 1: `truck`, `trucks`, `fleet`, `vehicle`, `1`
   * Category 3: `packers`, `packers-movers`, `shifting`, `3`
3. **Automatic Startup Migration**:
   On backend startup, any existing database records with `category = 'bike'` or `serviceId = 'scooter'` are automatically normalized to `category = "two_wheeler"`, `categoryId = "2"`, and `categoryName = "2 Wheeler / Bike"`.
4. **Admin Panel Auto-Standardization**:
   When saving or editing services with `categoryId: "2"`, the backend automatically standardizes `category` to `"two_wheeler"`.

---

## 2. Frontend Integration Flow by Screen

```
+-----------------------------------------------------------------------------------+
|                              CUSTOMER APP FLOW                                    |
+-----------------------------------------------------------------------------------+
                                        |
      +---------------------------------+---------------------------------+
      |                                                                   |
      v                                                                   v
[ Home Screen / Category Grid ]                             [ Category Tab / Filter Screen ]
Endpoint: GET /api/customer/services                        Endpoint: GET /api/services?category=two_wheeler
Returns: Grouped active categories                          Returns: Flat list of matching vehicles
Category 2 contains:                                        Includes BOTH:
  • 2-wheeler (₹49 base, ₹11/km)                              • 2-wheeler (₹49 base, ₹11/km)
  • scooter (₹35 base, ₹9/km)                                 • scooter (₹35 base, ₹9/km)
```

---

## 3. Endpoints & Query Parameters for Frontend

### A. Customer App: Category Grouped Services (Recommended for Home Feed)
* **Endpoint:** `GET /api/customer/services`
* **Access:** Public (No token required)
* **Description:** Returns active categories and their customer-visible services, sorted by `displayOrder`.
* **Category 2 Item in Response:**
```json
{
  "success": true,
  "categories": [
    {
      "id": "2",
      "name": "2 Wheeler / Bike",
      "slug": "2-wheeler-bike",
      "icon": "motorbike",
      "displayOrder": 2,
      "services": [
        {
          "id": "2-wheeler",
          "name": "2 Wheeler",
          "categoryId": "2",
          "categoryName": "2 Wheeler / Bike",
          "description": "Instant delivery for small packages & documents up to 20 Kg",
          "imageUrl": "https://api.anushaporter.com/assets/bikes/2-wheeler.png",
          "capacity": "20 kg",
          "capacityKg": 20,
          "basePrice": 49.0,
          "perKmRate": 11.0,
          "eta": "3 mins",
          "displayOrder": 1
        },
        {
          "id": "scooter",
          "name": "Scooter",
          "categoryId": "2",
          "categoryName": "2 Wheeler / Bike",
          "description": "Fast & affordable rides and parcel delivery",
          "imageUrl": "https://api.anushaporter.com/assets/bikes/scooter.png",
          "capacity": "Max 1 Pax • 2 Bag",
          "capacityKg": 25,
          "basePrice": 35.0,
          "perKmRate": 9.0,
          "eta": "4 mins",
          "displayOrder": 2
        }
      ]
    }
  ]
}
```

---

### B. Customer App: Specific Category Filter
* **Endpoint:** `GET /api/services?category={category}`
* **Allowed Query Values for 2-Wheeler:**
  * `category=two_wheeler`
  * `category=bike`
  * `category=2-wheeler`
  * `category=scooter`
  * `category=2` (Category ID)
* **Sample Request:**
  `GET https://api.anushaporter.com/api/services?category=two_wheeler`
* **Response (HTTP 200 OK):**
```json
{
  "success": true,
  "count": 2,
  "services": [
    {
      "id": "2-wheeler",
      "serviceId": "2-wheeler",
      "name": "2 Wheeler",
      "label": "2 Wheeler",
      "category": "two_wheeler",
      "categoryId": "2",
      "categoryName": "2 Wheeler / Bike",
      "basePrice": 49.0,
      "perKmRate": 11.0,
      "capacity": "20 kg",
      "capacityKg": 20,
      "eta": "3 mins"
    },
    {
      "id": "scooter",
      "serviceId": "scooter",
      "name": "Scooter",
      "label": "Scooter",
      "category": "two_wheeler",
      "categoryId": "2",
      "categoryName": "2 Wheeler / Bike",
      "basePrice": 35.0,
      "perKmRate": 9.0,
      "capacity": "Max 1 Pax • 2 Bag",
      "capacityKg": 25,
      "eta": "4 mins"
    }
  ]
}
```

---

### C. Customer App: All Vehicle Selection Screen
* **Endpoint:** `GET /api/services/vehicles` (or `/api/services/trucks`)
* **Description:** Returns all freight and 2-wheeler delivery vehicles for vehicle selection.
* **Now includes:** Both `2-wheeler` and `scooter` alongside all truck types.

---

### D. Admin Panel: Manage & Filter Services
* **List with Filter:** `GET /api/admin/services?category=two_wheeler`
  * Returns all Category 2 vehicles (`2-wheeler` and `scooter`).
* **Create/Update Service:** `POST /api/admin/services` or `PUT /api/admin/services/{id}`
  * Frontend can send:
    ```json
    {
      "serviceId": "scooter",
      "name": "Scooter",
      "categoryId": "2",
      "baseFare": 35.0,
      "perKmRate": 9.0
    }
    ```
  * The backend automatically assigns `category: "two_wheeler"` and `categoryName: "2 Wheeler / Bike"`.

---

## 4. Frontend Action Items for Verification

1. **Verify Home Screen Categories**:
   * Call `GET /api/customer/services`.
   * Check Category 2: Confirm both `2-wheeler` and `scooter` render in the 2-Wheeler tab/grid.
2. **Verify Filtered Category Call**:
   * Call `GET /api/services?category=two_wheeler`.
   * Verify the response array contains 2 vehicles: `2-wheeler` and `scooter`.
3. **Verify Vehicle Selection List**:
   * Call `GET /api/services/vehicles`.
   * Verify both `2-wheeler` and `scooter` appear in the vehicle list.
4. **Order Placement (`POST /api/bookings`)**:
   * When user selects either vehicle, pass the vehicle ID (`vehicleId: "2-wheeler"` or `vehicleId: "scooter"`).
   * Backend will match drivers and calculate fares using the respective admin rates (₹49 base for 2-wheeler, ₹35 base for scooter).

---

## 5. Admin Driver Details & Flow Guide

### Overview of Backend Fixes
1. **Full Registration Details**: `GET /api/admin/drivers` now returns the complete registration profile for every driver (including document numbers, document presigned/sanitized URLs, bank accounts, personal info, address, service category, and registration steps).
2. **Fixed Filtering Parameters**: Filtering by `status` (e.g. `online`, `offline`, `active`), `kyc` (e.g. `approved`, `pending`, `draft`), `search`/`q`, or `serviceType` now works correctly and no longer returns empty arrays.
3. **Single Driver Details Endpoint**: Added `GET /api/admin/drivers/{id}` (supporting numeric `1` or formatted `DRV-1`).
4. **Dedicated Category Endpoints**: Added `GET /api/admin/drivers/our-services` and `GET /api/admin/drivers/passengers`.
5. **Assigned Driver Persistence & Order Enrichment**: When a driver accepts an order, their details are permanently saved to the database. `GET /api/admin/orders` automatically enriches orders with driver details and nested `driver` profile objects.

---

### Endpoints Reference for Admin Panel

#### A. Fetch Drivers Roster (With Filtering & Complete Details)
* **Endpoint:** `GET /api/admin/drivers`
* **Query Parameters:**
  * `status`: (optional) Filter by `online`, `offline`, `active`, `suspended`, or `all`.
  * `kyc`: (optional) Filter by `approved`, `verified`, `pending`, `rejected`, `draft`, or `all`.
  * `serviceType`: (optional) Filter by `OUR_SERVICES` or `PASSENGER`.
  * `search` or `q`: (optional) Search by Driver Name, Phone Number, Email, Vehicle Plate Number, or Driver ID.
* **Sample Response Item:**
```json
[
  {
    "id": "DRV-1",
    "driverId": "1",
    "numericId": 1,
    "name": "Ramesh Kumar",
    "email": "ramesh@example.com",
    "phone": "9876543210",
    "dob": "1992-05-15",
    "gender": "male",
    "vehicle": "Tata Ace",
    "vehicleType": "Tata Ace",
    "vehicleNumber": "TS09EA1234",
    "serviceType": "OUR_SERVICES",
    "serviceCategory": "Our Services",
    "status": "online",
    "kyc": "approved",
    "kycStatus": "approved",
    "verificationStatus": "approved",
    "rcNumber": "RC9876543210",
    "licenseNumber": "DL1234567890123",
    "aadhaarNumber": "123456789012",
    "panNumber": "ABCDE1234F",
    "addressLine1": "Plot 42, Jubilee Hills",
    "city": "Hyderabad",
    "state": "Telangana",
    "pincode": "500033",
    "bankName": "State Bank of India",
    "accountHolderName": "Ramesh Kumar",
    "accountNumber": "112233445566",
    "ifscCode": "SBIN0001234",
    "upiId": "ramesh@upi",
    "isRegistered": true,
    "registrationCompleted": true,
    "hasDraft": false,
    "registrationStep": 5,
    "rating": "4.8",
    "trips": 124,
    "walletBalance": 450.0,
    "licenseUri": "https://bucket.s3.ap-south-2.amazonaws.com/drivers/license/1_license.jpg",
    "rcUri": "https://bucket.s3.ap-south-2.amazonaws.com/drivers/rc/1_rc.jpg",
    "aadhaarUri": "https://bucket.s3.ap-south-2.amazonaws.com/drivers/aadhaar/1_aadhaar.jpg",
    "panUri": "https://bucket.s3.ap-south-2.amazonaws.com/drivers/pan/1_pan.jpg",
    "profilePhotoUri": "https://bucket.s3.ap-south-2.amazonaws.com/drivers/profile/1_photo.jpg",
    "bankPassbookUri": "https://bucket.s3.ap-south-2.amazonaws.com/drivers/bank/1_passbook.jpg",
    "location": {
      "lat": 17.4483,
      "lng": 78.3915,
      "speed": 0.0,
      "angle": 45.0
    }
  }
]
```

---

#### B. Single Driver Details (Profile & KYC Verification View)
* **Endpoint:** `GET /api/admin/drivers/{id}`
* **Path Variables Supported:**
  * Numeric ID: `GET /api/admin/drivers/1`
  * Formatted ID: `GET /api/admin/drivers/DRV-1`
* **Response:** Returns the single driver details JSON object (same schema as above).

---

#### C. Dedicated Service Categories
* **Our Services (Trucks, Freight & Logistics):** `GET /api/admin/drivers/our-services`
* **Passenger Rides (Bikes, Autos & Cabs):** `GET /api/admin/drivers/passengers`

---

#### D. Approve / Verify Driver KYC
* **Endpoint:** `POST /api/admin/drivers/{id}/verify` (or `POST /api/admin/drivers/verify/{id}`)
* **Effect:** Sets KYC to `"verified"`, verification status to `"approved"`, `registrationStep` to `5`, and sends approval push notification.

---

#### E. Reject Driver KYC (With Re-upload Requirements)
* **Endpoint:** `POST /api/admin/drivers/{id}/reject`
* **Body:**
```json
{
  "rejectionReason": "Invalid RC or Expired Driving License",
  "rejectedDocuments": ["rc", "license"],
  "notes": "Please re-upload clear photos of your RC and License."
}
```

---

#### F. Assigned Driver Details in Orders (`GET /api/admin/orders`)
When viewing orders in the Admin panel, assigned orders now include both flattened driver fields and a structured `driver` object:
```json
{
  "id": "ORD-101",
  "bookingId": "ORD-101",
  "status": "accepted",
  "driverId": "1",
  "driverName": "Ramesh Kumar",
  "driverPhone": "9876543210",
  "vehicleNumber": "TS09EA1234",
  "driver": {
    "id": 1,
    "driverId": "1",
    "name": "Ramesh Kumar",
    "phone": "9876543210",
    "vehicleNumber": "TS09EA1234",
    "vehicleType": "Tata Ace",
    "rating": "4.8",
    "profilePhotoUri": "https://bucket.s3.ap-south-2.amazonaws.com/drivers/profile/1_photo.jpg"
  }
}
```

