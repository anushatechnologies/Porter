# Frontend Handoff: Driver Vehicle Selection & Registration Flow

This document details the backend changes made to resolve the `"Selected vehicle type is no longer available."` error and provides the exact contract for the Frontend team to test and integrate.

---

## 1. Summary of Backend Changes

| Area | Before | After (Now Live) |
|---|---|---|
| **Validation Lookup** | Strict query: `findByIdAndStatus(vehicleId, "active")`. Required exact DB primary key ID (e.g. `"1"`). Failed on `"veh_2_wheeler"`. | **Multi-strategy Resolver**: Checks `id`, `type` slug, `name`, stripped prefix (`veh_2_wheeler` -> `2_wheeler`), and standard category keywords. |
| **Payload Support** | Only evaluated `vehicleId`. | Evaluates all companion fields (`vehicleId`, `vehicle`, `vehicleType`, `vehicle_type`, `vehicleName`). |
| **Fallback & Drafts** | Resuming draft without `vehicleId` or with client-side fallback caused HTTP 400. | Non-destructive: preserves existing vehicle data across steps if omitted. Fallback slugs are seamlessly mapped. |
| **Admin Inactive Control** | Inactive vehicles were not always properly checked if IDs differed. | If a vehicle exists in DB with `status: "inactive"` or `"disabled"`, it strictly returns HTTP 400 with `"Selected vehicle type is no longer available."`. |

---

## 2. End-to-End Flow for Frontend

### Step A: Load Vehicles in Registration Screen
When the user arrives at the vehicle selection step (Step 2 or 3):

* **Endpoint**: `GET /api/driver/vehicle-types`
* **Headers**: `Authorization: Bearer <jwt_token>`
* **Success Response (200 OK)**:
```json
{
  "success": true,
  "serviceType": "OUR_SERVICES",
  "vehicles": [
    {
      "id": "1",
      "name": "2 Wheeler",
      "type": "two_wheeler",
      "displayName": "2 Wheeler / Bike",
      "serviceType": "OUR_SERVICES",
      "capacity": "Load: Up to 20kg",
      "capacityKg": 20,
      "baseFare": 40.0,
      "perKmRate": 12.0,
      "imageUrl": "https://images.unsplash.com/...",
      "status": "active"
    },
    {
      "id": "2",
      "name": "3 Wheeler",
      "type": "three_wheeler",
      "displayName": "3 Wheeler / Auto",
      "serviceType": "OUR_SERVICES",
      "capacity": "Load: Up to 500kg",
      "capacityKg": 500,
      "baseFare": 80.0,
      "perKmRate": 15.0,
      "imageUrl": "https://images.unsplash.com/...",
      "status": "active"
    }
  ]
}
```

---

### Step B: User Selects Vehicle & Submits
When the user selects a vehicle option and taps **Next** or **Submit Application**:

* **Endpoint**: `POST /api/driver/register` (or `/api/drivers/register/save-and-next`)
* **Headers**:
  * `Content-Type: application/json`
  * `Authorization: Bearer <jwt_token>`

#### Recommended Payload:
Bind the actual `id` and `type` from the selected API item:
```json
{
  "vehicleId": "1",
  "vehicle": "2 Wheeler",
  "vehicleType": "2 Wheeler",
  "vehicle_type": "two_wheeler",
  "serviceType": "OUR_SERVICES",
  "vehicleNumber": "KA-01-AB-1234",
  "rcNumber": "RC123456",
  "licenseNumber": "DL1234567890ABC",
  "step": 2,
  "saveAndNext": true
}
```

> **Note on Backward Compatibility**:
> If the frontend still sends fallback strings like `"vehicleId": "veh_2_wheeler"` or `"vehicle_type": "2_wheeler"`, the backend will now automatically accept it and resolve it to `"2 Wheeler"`.

---

### Step C: Handling Responses in Frontend

#### 1. Success (HTTP 200 OK)
```json
{
  "success": true,
  "registrationStep": 3,
  "driver": {
    "vehicle": "2 Wheeler",
    "vehicleType": "2 Wheeler",
    "serviceType": "OUR_SERVICES"
  }
}
```
* **Frontend Action**: Proceed to next registration step.

#### 2. Vehicle Inactive / Disabled by Admin (HTTP 400 Bad Request)
```json
{
  "success": false,
  "message": "Selected vehicle type is no longer available."
}
```
* **Frontend Action**: Show user alert dialog:
  > *"The vehicle category you selected is currently unavailable. Please select another vehicle option to continue."*
* Re-fetch `GET /api/driver/vehicle-types` to refresh the active list.

---

## 3. Frontend Development Checklist

1. [ ] **Verify Dynamic Rendering**: Ensure vehicle options are populated from `GET /api/driver/vehicle-types` rather than hardcoded UI cards.
2. [ ] **Payload ID Binding**: Check that `selectedVehicle.id` is passed as `vehicleId`.
3. [ ] **Payload Names**: Pass `vehicle: selectedVehicle.name` and `vehicleType: selectedVehicle.name`.
4. [ ] **Test Fallback**: Try submitting `"vehicleId": "veh_2_wheeler"` &mdash; verify it passes with HTTP 200.
5. [ ] **Test Inactive State**: When admin sets a vehicle to inactive, verify the frontend displays the user-friendly error dialog.
