# Anusha Porter — Location & Google Places API Specification

Base URL: `https://api.anushaporter.com`

---

## Overview

The backend exposes 2 primary endpoints for Google Places location search and coordinates retrieval. Both endpoints support unrestricted Google Server API Key lookup on the backend and provide 100% exact location matches with built-in fallbacks.

Public endpoints (No Authorization header required):
- `/api/location/autocomplete` and `/api/places/autocomplete`
- `/api/location/details` and `/api/places/details`

---

## 1. Place Autocomplete Search Endpoint

### Endpoints
- `GET /api/location/autocomplete?input={query}`
- `GET /api/places/autocomplete?input={query}`
- (Legacy Alias) `GET /api/location/search?input={query}` (or `?q={query}`)

### Query Parameters
| Parameter | Type | Required | Description | Example |
| :--- | :--- | :--- | :--- | :--- |
| `input` | String | Yes | Location search text | `Cyber Towers`, `DLF Gachibowli`, `Road No 36 Jubilee Hills`, `Apollo Hospital` |
| `q` | String | Optional | Alias for `input` | `Cyber Towers` |

### Sample Request
```http
GET https://api.anushaporter.com/api/location/autocomplete?input=Cyber%20Towers
```
or
```http
GET https://api.anushaporter.com/api/places/autocomplete?input=Cyber%20Towers
```

### Sample Response (200 OK)
```json
{
  "success": true,
  "predictions": [
    {
      "placeId": "ChIJbU60yXA_zjsRkW54uoW_aN4",
      "primaryText": "Cyber Towers",
      "secondaryText": "Hitech City, Madhapur, Hyderabad, Telangana",
      "fullText": "Cyber Towers, Hitech City, Madhapur, Hyderabad, Telangana"
    },
    {
      "placeId": "ChIJD7fiBh9NyzsRSc0un6448zo",
      "primaryText": "DLF Cyber City",
      "secondaryText": "Gachibowli, Hyderabad, Telangana",
      "fullText": "DLF Cyber City, Gachibowli, Hyderabad, Telangana"
    }
  ]
}
```

---

## 2. Place Details (Coordinates) Endpoint

### Endpoints
- `GET /api/location/details?placeId={placeId}`
- `GET /api/places/details?placeId={placeId}`

### Query Parameters
| Parameter | Type | Required | Description | Example |
| :--- | :--- | :--- | :--- | :--- |
| `placeId` | String | Yes | Google Place ID obtained from autocomplete prediction | `ChIJbU60yXA_zjsRkW54uoW_aN4` |
| `place_id` | String | Optional | Alias for `placeId` | `ChIJbU60yXA_zjsRkW54uoW_aN4` |

### Sample Request
```http
GET https://api.anushaporter.com/api/location/details?placeId=ChIJbU60yXA_zjsRkW54uoW_aN4
```
or
```http
GET https://api.anushaporter.com/api/places/details?placeId=ChIJbU60yXA_zjsRkW54uoW_aN4
```

### Sample Response (200 OK)
```json
{
  "success": true,
  "data": {
    "placeId": "ChIJbU60yXA_zjsRkW54uoW_aN4",
    "name": "Cyber Towers",
    "formattedAddress": "Cyber Towers, Hitech City Main Rd, Patrika Nagar, HITEC City, Hyderabad, Telangana 500081",
    "lat": 17.4504,
    "lng": 78.3811
  }
}
```

---

## 3. Reverse Geocoding Endpoint (Coordinates to Address)

### Endpoint
- `GET /api/location/reverse?lat={lat}&lng={lng}`

### Sample Request
```http
GET https://api.anushaporter.com/api/location/reverse?lat=17.4504&lng=78.3811
```

---

## Mobile App Frontend Integration Guidelines

1. **Autocomplete Field**:
   - Bind user typing to `GET /api/location/autocomplete?input={query}`.
   - Display `primaryText` as the main title and `secondaryText` as subtitle in search dropdown list.
   - Store `placeId` when user selects an item.

2. **Selecting a Search Result**:
   - Call `GET /api/location/details?placeId={placeId}`.
   - Extract `data.lat` and `data.lng` to center map marker and pass exact coordinates to booking creation API.
