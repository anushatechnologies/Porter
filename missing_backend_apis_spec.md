# Anusha Porter — Missing Backend APIs

Base URL: `https://api.anushaporter.com`

Protected requests use:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

All APIs in this document are implemented in backend commit `e38ed5a`.

## Packers & Movers

### Catalog

```http
GET /api/catalog/packers
```

Returns service cards for house shifting, 1 BHK, 2 BHK, office relocation, and mini packers.

### Custom price estimate

```http
POST /api/pricing/packers
```

```json
{
  "serviceId": "house-shifting",
  "distanceKm": 12,
  "pickupFloor": 2,
  "dropFloor": 3,
  "hasElevatorPickup": true,
  "hasElevatorDrop": false,
  "items": ["Beds", "Sofa", "Refrigerator", "TV"]
}
```

Response includes `baseFare`, `distanceFare`, `laborCharge`, `packingCharge`, `floorCharge`, `gst`, and `totalFare`.

## Drafts and Slots

```http
POST /api/drafts
PUT /api/drafts/{draftId}
GET /api/drafts/{draftId}
```

Draft payload is stored for 24 hours. Create/update response:

```json
{
  "draftId": "draft_987654321",
  "expiresAt": "2026-08-08T12:00:00",
  "status": "draft"
}
```

```http
GET /api/slots?serviceId=house-shifting&date=2026-08-09
```

Returns available 09:00, 14:00, and 18:00 shifting windows.

## Invoice and Delivery OTP

```http
GET /api/bookings/{bookingId}/invoice
```

Returns a validated booking and `downloadUrl`. The production PDF storage route must be enabled before opening the URL.

```http
GET /api/orders/{bookingId}/delivery-otp
```

Returns an active 4-digit OTP with expiry. The driver verifies it with:

```http
POST /api/driver/orders/{bookingId}/verify-otp
```

```json
{"otp":"4829"}
```

## GST and Enterprise

```http
GET /api/user/gst
POST /api/user/gst
```

```json
{
  "gstin": "36AAAAA0000A1Z5",
  "companyName": "Anusha Logistics Pvt Ltd",
  "registeredAddress": "Madhapur, Hyderabad, Telangana"
}
```

```http
POST /api/enterprise/leads
```

```json
{
  "source": "customer_app",
  "companyName": "TechCorp India",
  "contactPerson": "Manager",
  "phone": "+919876543210",
  "email": "manager@techcorp.in"
}
```

## Support and Referral

```http
GET /api/support/topics
POST /api/support/tickets
GET /api/support/tickets
```

```json
{
  "topicId": "billing_issue",
  "bookingId": "BK-10293",
  "message": "Double charged for toll fee."
}
```

```http
GET /api/user/referral
```

Response includes `referralCode`, `totalEarned`, and `successfulReferrals`.

## Deployment

After merging the branch, rebuild and deploy the Docker image. Run the SQL migration `database/customer_driver_missing_apis_mysql.sql` if automatic Hibernate schema update is disabled.

Detailed examples are available in [`customer_driver_missing_api_handoff.txt`](customer_driver_missing_api_handoff.txt).
