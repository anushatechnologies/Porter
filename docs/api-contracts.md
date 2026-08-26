# API Contracts Draft

This file is the first pass at the platform API contract surface.

## Cross-Cutting Conventions

- REST over HTTPS
- JSON request and response bodies
- JWT bearer authentication
- Standard error object with trace ID

## Initial Endpoint Groups

### Authentication

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/otp/send`
- `POST /api/v1/auth/otp/verify`

### Orders

- `POST /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `GET /api/v1/orders/my`
- `PUT /api/v1/orders/{id}/cancel`
- `POST /api/v1/orders/{id}/rate`

### Drivers

- `POST /api/v1/drivers/register`
- `PUT /api/v1/drivers/availability`
- `POST /api/v1/drivers/location`
- `GET /api/v1/drivers/orders/pending`
- `PUT /api/v1/drivers/orders/{id}/accept`
- `PUT /api/v1/drivers/orders/{id}/status`

### Pricing

- `POST /api/v1/pricing/estimate`
- `GET /api/v1/pricing/surge/{zone}`

### Payments

- `POST /api/v1/payments/initiate`
- `POST /api/v1/payments/confirm`
- `GET /api/v1/payments/wallet`
- `POST /api/v1/payments/wallet/topup`

