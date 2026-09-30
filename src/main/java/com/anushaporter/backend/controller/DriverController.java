package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.anushaporter.backend.model.Order;
import com.anushaporter.backend.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.stream.Collectors;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.repository.VehicleRepository;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {
    @Autowired
    private DriverRepository repository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired(required = false)
    private AppUserRepository appUserRepository;

    @Autowired
    private com.anushaporter.backend.repository.NotificationRepository notificationRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private com.anushaporter.backend.service.DriverAuthService driverAuthService;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.PassengerBookingRepository passengerBookingRepository;

    @GetMapping({"/me/orders/active", "/active-order", "/{email}/orders/active"})
    public ResponseEntity<?> getActiveOrder(@PathVariable(required = false) String email) {
        List<String> activeStatuses = Arrays.asList("assigned", "accepted", "picked_up", "transit", "driver_assigned", "in_transit");
        List<Order> orders;

        if (email != null && !email.isBlank() && !email.equalsIgnoreCase("me")) {
            orders = orderRepository.findAllByDriverEmailAndStatusInOrderByCreatedAtDesc(email, activeStatuses);
        } else {
            orders = orderRepository.findAll().stream()
                    .filter(o -> o.getStatus() != null && activeStatuses.contains(o.getStatus().toLowerCase()))
                    .sorted((o1, o2) -> o2.getId().compareTo(o1.getId()))
                    .collect(Collectors.toList());
        }

        if (!orders.isEmpty()) {
            Order o = orders.get(0);
            if (o.getDeliveryOtp() == null || o.getDeliveryOtp().isBlank()) {
                o.setDeliveryOtp("8813");
                orderRepository.save(o);
            }

            boolean orderNeedsSave = false;
            if (o.getBookingId() != null && passengerBookingRepository != null) {
                try {
                    var pbOpt = passengerBookingRepository.findByBookingNumber(o.getBookingId());
                    if (pbOpt.isPresent()) {
                        var pb = pbOpt.get();
                        if (o.getPickupLat() == null && pb.getPickupLatitude() != null) { o.setPickupLat(pb.getPickupLatitude()); orderNeedsSave = true; }
                        if (o.getPickupLng() == null && pb.getPickupLongitude() != null) { o.setPickupLng(pb.getPickupLongitude()); orderNeedsSave = true; }
                        if (o.getDropLat() == null && pb.getDropLatitude() != null) { o.setDropLat(pb.getDropLatitude()); orderNeedsSave = true; }
                        if (o.getDropLng() == null && pb.getDropLongitude() != null) { o.setDropLng(pb.getDropLongitude()); orderNeedsSave = true; }
                        if ((o.getDistanceKm() == null || o.getDistanceKm() <= 0) && pb.getDistanceKm() != null && pb.getDistanceKm().doubleValue() > 0) {
                            o.setDistanceKm(pb.getDistanceKm().doubleValue());
                            o.setDistanceMeters((int) Math.round(o.getDistanceKm() * 1000.0));
                            orderNeedsSave = true;
                        }
                        if (pb.getDurationMinutes() != null && pb.getDurationMinutes() > 0 && o.getDurationSeconds() == null) {
                            o.setDurationSeconds(pb.getDurationMinutes() * 60);
                            orderNeedsSave = true;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if ((o.getDistanceKm() == null || o.getDistanceKm() <= 0) && o.getPickupLat() != null && o.getDropLat() != null) {
                double estDist = calculateHaversineKm(o.getPickupLat(), o.getPickupLng(), o.getDropLat(), o.getDropLng());
                if (estDist > 0) {
                    o.setDistanceKm(Math.round(estDist * 10.0) / 10.0);
                    o.setDistanceMeters((int) Math.round(o.getDistanceKm() * 1000.0));
                    if (o.getDurationSeconds() == null) {
                        o.setDurationSeconds((int) Math.round(o.getDistanceKm() * 144.0));
                    }
                    orderNeedsSave = true;
                }
            } else if (o.getDistanceKm() != null && o.getDistanceKm() > 0 && o.getDistanceMeters() == null) {
                o.setDistanceMeters((int) Math.round(o.getDistanceKm() * 1000.0));
                orderNeedsSave = true;
            }
            if (orderNeedsSave) {
                try {
                    o = orderRepository.save(o);
                } catch (Exception ignored) {}
            }

            String userEmail = o.getUserEmail() != null ? o.getUserEmail() : "";
            AppUser user = (appUserRepository != null && !userEmail.isBlank())
                    ? appUserRepository.findFirstByEmailOrderByIdDesc(userEmail).orElse(null)
                    : null;

            String custName = (user != null && user.getName() != null && !user.getName().isBlank())
                    ? user.getName()
                    : (o.getReceiverName() != null && !o.getReceiverName().isBlank() ? o.getReceiverName() : "Customer Name Here");

            String custPhone = (user != null && user.getPhone() != null && !user.getPhone().isBlank())
                    ? user.getPhone()
                    : (o.getReceiverPhone() != null && !o.getReceiverPhone().isBlank() ? o.getReceiverPhone() : "9876543210");

            double dKm = o.getDistanceKm() != null ? o.getDistanceKm() : 0.0;
            int dMeters = o.getDistanceMeters() != null ? o.getDistanceMeters() : (int) Math.round(dKm * 1000.0);
            int dSecs = o.getDurationSeconds() != null ? o.getDurationSeconds() : (int) Math.round(dKm * 144.0);

            Map<String, Object> orderMap = new LinkedHashMap<>();
            orderMap.put("id", o.getBookingId() != null ? o.getBookingId() : "BK_" + o.getId());
            orderMap.put("bookingId", o.getBookingId() != null ? o.getBookingId() : "BK_" + o.getId());
            orderMap.put("status", o.getStatus());
            orderMap.put("amount", o.getAmount() != null ? o.getAmount() : 0.0);
            orderMap.put("customerName", custName);
            orderMap.put("customerPhone", custPhone);
            orderMap.put("customer_name", custName);
            orderMap.put("customer_phone", custPhone);
            orderMap.put("receiverName", o.getReceiverName() != null ? o.getReceiverName() : custName);
            orderMap.put("receiverPhone", o.getReceiverPhone() != null ? o.getReceiverPhone() : custPhone);
            orderMap.put("senderName", custName);
            orderMap.put("senderPhone", custPhone);
            orderMap.put("contactName", custName);
            orderMap.put("contactPhone", custPhone);
            orderMap.put("pickupAddress", o.getPickupAddress() != null ? o.getPickupAddress() : "");
            orderMap.put("dropAddress", o.getDropAddress() != null ? o.getDropAddress() : "");
            orderMap.put("deliveryOtp", o.getDeliveryOtp() != null ? o.getDeliveryOtp() : "8813");
            orderMap.put("startOtp", o.getStartOtp() != null ? o.getStartOtp() : "8813");
            orderMap.put("serviceType", o.getServiceType());
            orderMap.put("serviceName", o.getServiceName());
            orderMap.put("passengerCount", o.getPassengerCount());
            orderMap.put("distance", dKm > 0 ? String.format("%.1f", dKm) : "0.0");
            orderMap.put("distanceKm", dKm);
            orderMap.put("distanceMeters", dMeters);
            orderMap.put("durationSeconds", dSecs);
            orderMap.put("pickupLat", o.getPickupLat());
            orderMap.put("pickupLng", o.getPickupLng());
            orderMap.put("dropLat", o.getDropLat());
            orderMap.put("dropLng", o.getDropLng());
            orderMap.put("pickupLatitude", o.getPickupLat());
            orderMap.put("pickupLongitude", o.getPickupLng());
            orderMap.put("dropLatitude", o.getDropLat());
            orderMap.put("dropLongitude", o.getDropLng());

            boolean isPass = "PASSENGER".equalsIgnoreCase(o.getServiceType());
            int cnt = o.getPassengerCount() != null ? o.getPassengerCount() : 1;
            orderMap.put("serviceLabel", isPass ? ("Passenger Ride (" + cnt + " Rider" + (cnt > 1 ? "s" : "") + ")") : "Goods Delivery");

            Map<String, Object> response = new LinkedHashMap<>(orderMap);
            response.put("success", true);
            response.put("hasActiveOrder", true);
            response.put("order", orderMap);
            return ResponseEntity.ok(response);
        }

        Map<String, Object> emptyResponse = new LinkedHashMap<>();
        emptyResponse.put("success", true);
        emptyResponse.put("hasActiveOrder", false);
        emptyResponse.put("order", null);
        return ResponseEntity.ok(emptyResponse);
    }

    private double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @GetMapping("/{email}/orders/history")
    public List<Order> getOrderHistory(@PathVariable String email) {
        List<Order> list = orderRepository.findAllByDriverEmailOrderByCreatedAtDesc(email);
        for (Order o : list) {
            if ((o.getDistanceKm() == null || o.getDistanceKm() <= 0 || o.getDistanceMeters() == null) && o.getBookingId() != null) {
                boolean healed = false;
                if (passengerBookingRepository != null) {
                    try {
                        var pbOpt = passengerBookingRepository.findByBookingNumber(o.getBookingId());
                        if (pbOpt.isPresent()) {
                            var pb = pbOpt.get();
                            if (pb.getDistanceKm() != null && pb.getDistanceKm().doubleValue() > 0) {
                                o.setDistanceKm(pb.getDistanceKm().doubleValue());
                                o.setDistanceMeters((int) Math.round(o.getDistanceKm() * 1000.0));
                                healed = true;
                            }
                            if (pb.getDurationMinutes() != null && pb.getDurationMinutes() > 0 && o.getDurationSeconds() == null) {
                                o.setDurationSeconds(pb.getDurationMinutes() * 60);
                                healed = true;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if ((o.getDistanceKm() == null || o.getDistanceKm() <= 0) && o.getPickupLat() != null && o.getDropLat() != null) {
                    double dist = calculateHaversineKm(o.getPickupLat(), o.getPickupLng(), o.getDropLat(), o.getDropLng());
                    if (dist > 0) {
                        o.setDistanceKm(Math.round(dist * 10.0) / 10.0);
                        o.setDistanceMeters((int) Math.round(o.getDistanceKm() * 1000.0));
                        if (o.getDurationSeconds() == null) {
                            o.setDurationSeconds((int) Math.round(o.getDistanceKm() * 144.0));
                        }
                        healed = true;
                    }
                } else if (o.getDistanceKm() != null && o.getDistanceKm() > 0 && o.getDistanceMeters() == null) {
                    o.setDistanceMeters((int) Math.round(o.getDistanceKm() * 1000.0));
                    healed = true;
                }
                if (healed) {
                    try {
                        orderRepository.save(o);
                    } catch (Exception ignored) {}
                }
            }
        }
        return list;
    }

    @Autowired
    private com.anushaporter.backend.service.StorageService storageService;

    @Autowired
    private com.anushaporter.backend.service.S3ImageService s3ImageService;

    @Autowired
    private com.anushaporter.backend.service.DriverWalletService driverWalletService;

    @Autowired
    private com.anushaporter.backend.repository.WalletTransactionRepository walletTransactionRepository;

    /**
     * GET /api/drivers
     * GET /api/drivers?serviceType=OUR_SERVICES
     * GET /api/drivers?serviceType=PASSENGER
     * Returns formatted list of drivers for Admin Drivers roster & Live Driver GPS tracking map.
     * Supports filtering by status, KYC, search term, and serviceType (OUR_SERVICES vs PASSENGER).
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "kyc", required = false) String kyc,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "minWallet", required = false) Double minWallet,
            @RequestParam(value = "minBalance", required = false) Double minBalance,
            @RequestParam(value = "serviceType", required = false) String serviceType,
            @RequestParam(value = "service", required = false) String service
    ) {
        List<Driver> drivers;
        if (phone != null && !phone.isBlank()) {
            String cleanPhone = phone.replaceAll("[^0-9]", "");
            List<Driver> byPhone = repository.findAllByPhone(phone.trim());
            if ((byPhone == null || byPhone.isEmpty()) && !cleanPhone.equals(phone.trim()) && !cleanPhone.isBlank()) {
                byPhone = repository.findAllByPhone(cleanPhone);
            }
            drivers = byPhone != null ? byPhone : Collections.emptyList();
        } else {
            drivers = repository.findAll();
        }

        Double filterMinWallet = minWallet != null ? minWallet : minBalance;
        String searchTerm = search != null ? search.trim().toLowerCase() : (q != null ? q.trim().toLowerCase() : (query != null ? query.trim().toLowerCase() : null));

        String requestedService = serviceType != null && !serviceType.isBlank() ? serviceType : service;
        String normalizedService = null;
        if (requestedService != null && !requestedService.isBlank()) {
            String s = requestedService.trim().toUpperCase();
            if (s.contains("PASSENGER") || s.contains("CAB") || s.contains("RIDE")) {
                normalizedService = "PASSENGER";
            } else if (s.contains("OUR") || s.contains("GOOD") || s.contains("TRUCK") || s.contains("LOGISTICS")) {
                normalizedService = "OUR_SERVICES";
            }
        }

        final String finalServiceFilter = normalizedService;

        List<Map<String, Object>> items = drivers.stream()
                .filter(d -> {
                    // Ignore status filter if "all" or empty
                    if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status.trim())) {
                        if (d.getStatus() == null || !d.getStatus().equalsIgnoreCase(status.trim())) {
                            return false;
                        }
                    }
                    // KYC filter
                    if (kyc != null && !kyc.isBlank() && !"all".equalsIgnoreCase(kyc.trim())) {
                        if (d.getKyc() == null || !d.getKyc().equalsIgnoreCase(kyc.trim())) {
                            return false;
                        }
                    }
                    // Service Type filter (Our Services vs Passenger)
                    if (finalServiceFilter != null) {
                        String rawS = d.getServiceType() != null ? d.getServiceType().toUpperCase() : "OUR_SERVICES";
                        if ("PASSENGER".equals(finalServiceFilter)) {
                            if (!"PASSENGER".equals(rawS)) return false;
                        } else if ("OUR_SERVICES".equals(finalServiceFilter)) {
                            if (!"OUR_SERVICES".equals(rawS) && !"GOODS".equals(rawS)) return false;
                        }
                    }
                    // Search term filter (matches Name, Phone, Email, Vehicle Plate, or ID)
                    if (searchTerm != null && !searchTerm.isBlank()) {
                        String name = d.getName() != null ? d.getName().toLowerCase() : "";
                        String drvPhone = d.getPhone() != null ? d.getPhone().toLowerCase() : "";
                        String email = d.getEmail() != null ? d.getEmail().toLowerCase() : "";
                        String vNum = d.getVehicleNumber() != null ? d.getVehicleNumber().toLowerCase() : "";
                        String vType = d.getVehicleType() != null ? d.getVehicleType().toLowerCase() : "";
                        String idStr = d.getId() != null ? d.getId().toString() : "";
                        String drvId = "drv-" + idStr;

                        if (!name.contains(searchTerm)
                                && !drvPhone.contains(searchTerm)
                                && !email.contains(searchTerm)
                                && !vNum.contains(searchTerm)
                                && !vType.contains(searchTerm)
                                && !idStr.equals(searchTerm)
                                && !drvId.equals(searchTerm)) {
                            return false;
                        }
                    }
                    if (filterMinWallet != null) {
                        double bal = d.getWalletBalance() != null ? d.getWalletBalance() : 0.0;
                        if (bal < filterMinWallet) {
                            return false;
                        }
                    }
                    return true;
                })
                .map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId() != null ? "DRV-" + d.getId() : "DRV-100");
            map.put("driverId", d.getId() != null ? d.getId().toString() : "100");
            map.put("name", d.getName() != null ? d.getName() : "Unknown");
            map.put("email", d.getEmail() != null ? d.getEmail() : "");
            map.put("phone", d.getPhone() != null ? d.getPhone() : "");

            String rawService = d.getServiceType() != null ? d.getServiceType().toUpperCase() : "OUR_SERVICES";
            String cleanService = "PASSENGER".equals(rawService) ? "PASSENGER" : "OUR_SERVICES";
            String serviceCategoryLabel = "PASSENGER".equals(cleanService) ? "Passenger Rides" : "Our Services";
            map.put("serviceType", cleanService);
            map.put("service_type", cleanService);
            map.put("serviceCategory", serviceCategoryLabel);
            map.put("service_category", serviceCategoryLabel);

            String vType = d.getVehicleType() != null && !d.getVehicleType().isBlank() && !d.getVehicleType().equalsIgnoreCase("Vehicle")
                    ? d.getVehicleType()
                    : (d.getVehicle() != null && !d.getVehicle().isBlank() && !d.getVehicle().equalsIgnoreCase("Vehicle") ? d.getVehicle() : "");
            String v = d.getVehicle() != null && !d.getVehicle().isBlank() && !d.getVehicle().equalsIgnoreCase("Vehicle")
                    ? d.getVehicle()
                    : (d.getVehicleType() != null && !d.getVehicleType().isBlank() && !d.getVehicleType().equalsIgnoreCase("Vehicle") ? d.getVehicleType() : "");
            map.put("vehicle", v);
            map.put("vehicleType", vType);
            map.put("vehicle_type", vType);
            map.put("vehicleName", vType);
            map.put("vehicleNumber", d.getVehicleNumber() != null ? d.getVehicleNumber() : "");
            map.put("rcNumber", d.getRcNumber() != null ? d.getRcNumber() : "");
            map.put("licenseNumber", d.getLicenseNumber() != null ? d.getLicenseNumber() : "");
            map.put("aadhaarNumber", d.getAadhaarNumber() != null ? d.getAadhaarNumber() : "");
            map.put("dob", d.getDob() != null ? d.getDob() : "");
            map.put("gender", d.getGender() != null ? d.getGender() : "");
            map.put("addressLine1", d.getAddressLine1() != null ? d.getAddressLine1() : "");
            map.put("city", d.getCity() != null ? d.getCity() : "");
            map.put("state", d.getState() != null ? d.getState() : "");
            map.put("pincode", d.getPincode() != null ? d.getPincode() : "");
            map.put("bankName", d.getBankName() != null ? d.getBankName() : "");
            map.put("accountHolderName", d.getAccountHolderName() != null ? d.getAccountHolderName() : "");
            map.put("accountNumber", d.getAccountNumber() != null ? d.getAccountNumber() : "");
            map.put("ifscCode", d.getIfscCode() != null ? d.getIfscCode() : "");
            map.put("status", d.getStatus() != null ? d.getStatus().toLowerCase() : "offline");
            map.put("kyc", d.getKyc() != null ? d.getKyc() : "pending");
            map.put("kycStatus", d.getKyc() != null ? d.getKyc() : "pending");
            map.put("rating", d.getRating() != null ? d.getRating() : "4.8");
            map.put("trips", d.getTrips() != null ? d.getTrips() : 0);
            map.put("walletBalance", d.getWalletBalance() != null ? d.getWalletBalance() : 0.0);
            map.put("wallet_balance", d.getWalletBalance() != null ? d.getWalletBalance() : 0.0);
            map.put("minRequiredBalance", 0.0);
            map.put("minimumBalance", 0.0);
            map.put("minBalance", 0.0);
            map.put("canGoOnline", true);
            map.put("can_go_online", true);
            map.put("isEligible", true);
            map.put("isOnlineOptionAvailable", true);
            map.put("licenseUri", storageService.getPresignedOrSanitizedUrl(d.getLicenseUri()));
            map.put("rcUri", storageService.getPresignedOrSanitizedUrl(d.getRcUri()));
            map.put("aadhaarUri", storageService.getPresignedOrSanitizedUrl(d.getAadhaarUri()));
            map.put("profilePhotoUri", storageService.getPresignedOrSanitizedUrl(d.getProfilePhotoUri()));
            map.put("bankPassbookUri", storageService.getPresignedOrSanitizedUrl(d.getBankPassbookUri()));

            double lat = d.getLatitude() != null ? d.getLatitude() : 17.4483;
            double lng = d.getLongitude() != null ? d.getLongitude() : 78.3915;
            double speed = d.getSpeed() != null ? d.getSpeed() : 0.0;
            double angle = d.getHeading() != null ? d.getHeading() : 45.0;

            Map<String, Object> locMap = new LinkedHashMap<>();
            locMap.put("x", lat);
            locMap.put("y", lng);
            locMap.put("lat", lat);
            locMap.put("lng", lng);
            locMap.put("speed", speed);
            locMap.put("angle", angle);
            map.put("location", locMap);

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    /**
     * Dedicated Admin endpoint: GET /api/admin/drivers/our-services
     * Returns only drivers registered for Our Services (Goods Delivery / Freight / Trucks).
     */
    @GetMapping("/our-services")
    public ResponseEntity<List<Map<String, Object>>> getOurServicesDrivers(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "kyc", required = false) String kyc,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "q", required = false) String q) {
        return getAll(status, kyc, search, q, null, null, null, null, "OUR_SERVICES", null);
    }

    /**
     * Dedicated Admin endpoint: GET /api/admin/drivers/passengers
     * Returns only drivers registered for Passenger Rides (Bike Taxi / Auto Taxi / Cabs).
     */
    @GetMapping("/passengers")
    public ResponseEntity<List<Map<String, Object>>> getPassengerDrivers(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "kyc", required = false) String kyc,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "q", required = false) String q) {
        return getAll(status, kyc, search, q, null, null, null, null, "PASSENGER", null);
    }

    @PostMapping
    public Driver create(@RequestBody Driver entity) {
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("offline");
        } else {
            entity.setStatus(driverAuthService.normalizeStatus(entity.getStatus()));
        }

        String veh = entity.getVehicle();
        String vehType = entity.getVehicleType();
        String resolvedVeh = (veh != null && !veh.trim().isEmpty() && !veh.trim().equalsIgnoreCase("Vehicle"))
                ? veh.trim()
                : ((vehType != null && !vehType.trim().isEmpty() && !vehType.trim().equalsIgnoreCase("Vehicle")) ? vehType.trim() : "");
        entity.setVehicle(resolvedVeh);
        entity.setVehicleType(resolvedVeh);

        if (entity.getServiceType() == null || entity.getServiceType().isBlank()) {
            String vClean = resolvedVeh.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (vClean.contains("cab") || vClean.contains("car") || vClean.contains("taxi") || vClean.contains("sedan") || vClean.contains("hatchback") || vClean.contains("suv") || vClean.equals("6")) {
                entity.setServiceType("PASSENGER");
            } else if (vClean.contains("2wheel") || vClean.contains("bike") || vClean.contains("scooter") || vClean.equals("1")
                    || vClean.contains("3wheel") || vClean.contains("auto") || vClean.contains("rickshaw") || vClean.equals("2")) {
                entity.setServiceType("BOTH");
            } else {
                entity.setServiceType("GOODS");
            }
        }

        // Upload/migrate all Driver document images to S3 under dedicated folder names
        if (entity.getLicenseUri() != null && !entity.getLicenseUri().isBlank()) {
            entity.setLicenseUri(s3ImageService.processAndUploadImageUri(entity.getLicenseUri(), "license"));
        }
        if (entity.getRcUri() != null && !entity.getRcUri().isBlank()) {
            entity.setRcUri(s3ImageService.processAndUploadImageUri(entity.getRcUri(), "rc"));
        }
        if (entity.getAadhaarUri() != null && !entity.getAadhaarUri().isBlank()) {
            entity.setAadhaarUri(s3ImageService.processAndUploadImageUri(entity.getAadhaarUri(), "aadhaar"));
        }
        if (entity.getProfilePhotoUri() != null && !entity.getProfilePhotoUri().isBlank()) {
            entity.setProfilePhotoUri(s3ImageService.processAndUploadImageUri(entity.getProfilePhotoUri(), "profile-photo"));
        }
        if (entity.getBankPassbookUri() != null && !entity.getBankPassbookUri().isBlank()) {
            entity.setBankPassbookUri(s3ImageService.processAndUploadImageUri(entity.getBankPassbookUri(), "bank-passbook"));
        }

        if ((entity.getBankName() == null || entity.getBankName().isBlank() || "N/A".equalsIgnoreCase(entity.getBankName().trim()))
                && entity.getIfscCode() != null && !entity.getIfscCode().isBlank()) {
            entity.setBankName(com.anushaporter.backend.util.IfscBankResolver.resolveBankName(entity.getIfscCode()));
        }

        Driver savedDriver = repository.save(entity);

        if (entity.getVehicleNumber() != null && !entity.getVehicleNumber().trim().isEmpty()) {
            vehicleRepository.findByPlate(entity.getVehicleNumber()).ifPresentOrElse(vehObj -> {
                vehObj.setOwner(entity.getName());
                vehObj.setType(entity.getVehicleType());
                vehicleRepository.save(vehObj);
            }, () -> {
                Vehicle newVeh = new Vehicle();
                newVeh.setModel(entity.getVehicleType() + " Model");
                newVeh.setPlate(entity.getVehicleNumber());
                newVeh.setOwner(entity.getName());
                newVeh.setType(entity.getVehicleType());
                newVeh.setTrips(0);
                newVeh.setCapacity("500 kg");
                vehicleRepository.save(newVeh);
            });
        }
        return savedDriver;
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Driver updated) {
        return repository.findById(id).map(existing -> {
            if (updated.getName() != null) existing.setName(updated.getName());
            if (updated.getPhone() != null) existing.setPhone(updated.getPhone());
            if (updated.getEmail() != null) existing.setEmail(updated.getEmail());
            if (updated.getDob() != null) existing.setDob(updated.getDob());
            if (updated.getGender() != null) existing.setGender(updated.getGender());
            if (updated.getVehicle() != null) existing.setVehicle(updated.getVehicle());
            if (updated.getVehicleType() != null) existing.setVehicleType(updated.getVehicleType());
            if (updated.getServiceType() != null) existing.setServiceType(updated.getServiceType());
            if (updated.getVehicleNumber() != null) existing.setVehicleNumber(updated.getVehicleNumber());
            if (updated.getRcNumber() != null) existing.setRcNumber(updated.getRcNumber());
            if (updated.getAadhaarNumber() != null) existing.setAadhaarNumber(updated.getAadhaarNumber());
            if (updated.getLicenseNumber() != null) existing.setLicenseNumber(updated.getLicenseNumber());
            if (updated.getAddressLine1() != null) existing.setAddressLine1(updated.getAddressLine1());
            if (updated.getCity() != null) existing.setCity(updated.getCity());
            if (updated.getState() != null) existing.setState(updated.getState());
            if (updated.getPincode() != null) existing.setPincode(updated.getPincode());
            if (updated.getIfscCode() != null) existing.setIfscCode(updated.getIfscCode());
            if (updated.getBankName() != null && !updated.getBankName().isBlank()) {
                existing.setBankName(updated.getBankName());
            } else if ((existing.getBankName() == null || existing.getBankName().isBlank() || "N/A".equalsIgnoreCase(existing.getBankName().trim()))
                    && existing.getIfscCode() != null) {
                existing.setBankName(com.anushaporter.backend.util.IfscBankResolver.resolveBankName(existing.getIfscCode()));
            }
            if (updated.getAccountHolderName() != null) existing.setAccountHolderName(updated.getAccountHolderName());
            if (updated.getAccountNumber() != null) existing.setAccountNumber(updated.getAccountNumber());
            if (updated.getStatus() != null) existing.setStatus(driverAuthService.normalizeStatus(updated.getStatus()));
            if (updated.getKyc() != null) existing.setKyc(updated.getKyc());
            if (updated.getRegistrationStep() != null) {
                if (!existing.isFullyRegistered() || updated.getRegistrationStep() >= 5) {
                    existing.setRegistrationStep(updated.getRegistrationStep());
                }
            }
            if (existing.isFullyRegistered()) {
                existing.setRegistrationStep(5);
                existing.setKyc("approved");
                existing.setVerificationStatus("approved");
            }

            // Handle image updates and clean up old S3 images if replaced
            if (updated.getProfilePhotoUri() != null && !updated.getProfilePhotoUri().equals(existing.getProfilePhotoUri())) {
                s3ImageService.deleteImage(existing.getProfilePhotoUri());
                existing.setProfilePhotoUri(s3ImageService.processAndUploadImageUri(updated.getProfilePhotoUri(), "profile-photo"));
            }
            if (updated.getAadhaarUri() != null && !updated.getAadhaarUri().equals(existing.getAadhaarUri())) {
                s3ImageService.deleteImage(existing.getAadhaarUri());
                existing.setAadhaarUri(s3ImageService.processAndUploadImageUri(updated.getAadhaarUri(), "aadhaar"));
            }
            if (updated.getLicenseUri() != null && !updated.getLicenseUri().equals(existing.getLicenseUri())) {
                s3ImageService.deleteImage(existing.getLicenseUri());
                existing.setLicenseUri(s3ImageService.processAndUploadImageUri(updated.getLicenseUri(), "license"));
            }
            if (updated.getRcUri() != null && !updated.getRcUri().equals(existing.getRcUri())) {
                s3ImageService.deleteImage(existing.getRcUri());
                existing.setRcUri(s3ImageService.processAndUploadImageUri(updated.getRcUri(), "rc"));
            }
            if (updated.getBankPassbookUri() != null && !updated.getBankPassbookUri().equals(existing.getBankPassbookUri())) {
                s3ImageService.deleteImage(existing.getBankPassbookUri());
                existing.setBankPassbookUri(s3ImageService.processAndUploadImageUri(updated.getBankPassbookUri(), "bank-passbook"));
            }

            Driver saved = repository.save(existing);
            return ResponseEntity.ok((Object) saved);
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Driver not found: " + id)));
    }

    @DeleteMapping({"/{id:[0-9]+}", "/{id:DRV-[0-9]+}"})
    public ResponseEntity<?> delete(@PathVariable String id) {
        Long driverId = null;
        String cleanId = id != null ? id.trim() : "";
        if (cleanId.toUpperCase().startsWith("DRV-")) {
            cleanId = cleanId.substring(4).trim();
        }
        try {
            driverId = Long.parseLong(cleanId);
        } catch (NumberFormatException ignored) {}

        Optional<Driver> driverOpt = driverId != null ? repository.findById(driverId) : Optional.empty();

        return driverOpt.<ResponseEntity<?>>map(driver -> {
            // Clean up S3 objects
            if (s3ImageService != null) {
                try { s3ImageService.deleteImage(driver.getProfilePhotoUri()); } catch (Exception ignored) {}
                try { s3ImageService.deleteImage(driver.getAadhaarUri()); } catch (Exception ignored) {}
                try { s3ImageService.deleteImage(driver.getLicenseUri()); } catch (Exception ignored) {}
                try { s3ImageService.deleteImage(driver.getRcUri()); } catch (Exception ignored) {}
                try { s3ImageService.deleteImage(driver.getBankPassbookUri()); } catch (Exception ignored) {}
            }

            Long deletedId = driver.getId();
            repository.delete(driver);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Driver profile removed successfully",
                    "id", deletedId,
                    "driverId", deletedId.toString()
            ));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Driver not found: " + id)));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return repository.findById(id).map(driverEntity -> {
            Driver d = driverEntity;
            if (driverAuthService != null) {
                d = driverAuthService.ensureFullyRegisteredStatus(d);
            }
            boolean isFullyRegistered = d.isFullyRegistered();
            int regStep = isFullyRegistered ? 5 : (d.getRegistrationStep() != null ? d.getRegistrationStep() : 1);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId());
            map.put("driverId", d.getId().toString());
            map.put("isRegistered", isFullyRegistered);
            map.put("registrationCompleted", isFullyRegistered);
            map.put("hasDraft", !isFullyRegistered);
            map.put("name", d.getName() != null ? d.getName() : "Unknown");
            map.put("email", d.getEmail() != null ? d.getEmail() : "");
            map.put("phone", d.getPhone() != null ? d.getPhone() : "");

            String rawService = d.getServiceType() != null ? d.getServiceType().toUpperCase() : "OUR_SERVICES";
            String cleanService = "PASSENGER".equals(rawService) ? "PASSENGER" : "OUR_SERVICES";
            String serviceCategoryLabel = "PASSENGER".equals(cleanService) ? "Passenger Rides" : "Our Services";
            map.put("serviceType", cleanService);
            map.put("service_type", cleanService);
            map.put("serviceCategory", serviceCategoryLabel);
            map.put("service_category", serviceCategoryLabel);

            String vType = d.getVehicleType() != null && !d.getVehicleType().isBlank() && !d.getVehicleType().equalsIgnoreCase("Vehicle")
                    ? d.getVehicleType()
                    : (d.getVehicle() != null && !d.getVehicle().isBlank() && !d.getVehicle().equalsIgnoreCase("Vehicle") ? d.getVehicle() : "");
            String v = d.getVehicle() != null && !d.getVehicle().isBlank() && !d.getVehicle().equalsIgnoreCase("Vehicle")
                    ? d.getVehicle()
                    : (d.getVehicleType() != null && !d.getVehicleType().isBlank() && !d.getVehicleType().equalsIgnoreCase("Vehicle") ? d.getVehicleType() : "");
            map.put("vehicle", v);
            map.put("vehicleType", vType);
            map.put("vehicle_type", vType);
            map.put("vehicleName", vType);
            map.put("vehicleNumber", d.getVehicleNumber() != null ? d.getVehicleNumber() : "");
            map.put("rcNumber", d.getRcNumber() != null ? d.getRcNumber() : "");
            map.put("licenseNumber", d.getLicenseNumber() != null ? d.getLicenseNumber() : "");
            map.put("aadhaarNumber", d.getAadhaarNumber() != null ? d.getAadhaarNumber() : "");
            map.put("panNumber", d.getPanNumber() != null ? d.getPanNumber() : "");
            map.put("dob", d.getDob() != null ? d.getDob() : "");
            map.put("gender", d.getGender() != null ? d.getGender() : "");
            map.put("addressLine1", d.getAddressLine1() != null ? d.getAddressLine1() : "");
            map.put("city", d.getCity() != null ? d.getCity() : "");
            map.put("state", d.getState() != null ? d.getState() : "");
            map.put("pincode", d.getPincode() != null ? d.getPincode() : "");
            map.put("bankName", d.getBankName() != null ? d.getBankName() : "");
            map.put("accountHolderName", d.getAccountHolderName() != null ? d.getAccountHolderName() : "");
            map.put("accountNumber", d.getAccountNumber() != null ? d.getAccountNumber() : "");
            map.put("ifscCode", d.getIfscCode() != null ? d.getIfscCode() : "");
            map.put("status", d.getStatus() != null ? d.getStatus().toLowerCase() : "offline");
            map.put("kyc", d.getKyc() != null ? d.getKyc() : "pending");
            map.put("kycStatus", d.getKyc() != null ? d.getKyc() : "pending");
            map.put("registrationStep", regStep);
            map.put("nextStep", regStep);
            map.put("rating", d.getRating() != null ? d.getRating() : "4.8");
            map.put("trips", d.getTrips() != null ? d.getTrips() : 0);
            map.put("walletBalance", d.getWalletBalance() != null ? d.getWalletBalance() : 0.0);
            map.put("wallet_balance", d.getWalletBalance() != null ? d.getWalletBalance() : 0.0);
            map.put("minRequiredBalance", 0.0);
            map.put("minimumBalance", 0.0);
            map.put("minBalance", 0.0);
            map.put("canGoOnline", true);
            map.put("can_go_online", true);
            map.put("isEligible", true);
            map.put("isOnlineOptionAvailable", true);
            map.put("is_online_option_available", true);
            map.put("eligibilityReason", "No minimum balance required. You can go online anytime.");
            map.put("profilePhotoUri", storageService.getPresignedOrSanitizedUrl(d.getProfilePhotoUri()));
            map.put("licenseUri", storageService.getPresignedOrSanitizedUrl(d.getLicenseUri()));
            map.put("rcUri", storageService.getPresignedOrSanitizedUrl(d.getRcUri()));
            map.put("aadhaarUri", storageService.getPresignedOrSanitizedUrl(d.getAadhaarUri()));
            map.put("bankPassbookUri", storageService.getPresignedOrSanitizedUrl(d.getBankPassbookUri()));
            String panPhoto = storageService.getPresignedOrSanitizedUrl(d.getPanUri());
            map.put("panUri", panPhoto);
            map.put("panUrl", panPhoto);
            return ResponseEntity.ok((Object) map);
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Driver not found with ID: " + id)));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<?> getByEmail(@PathVariable String email) {
        Driver driver = driverAuthService.resolveDriverByIdentifier(email);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(driver);
    }

    @RequestMapping(value = "/email/{email}/status", method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> updateStatusByEmail(@PathVariable String email, @RequestBody(required = false) Map<String, Object> payload) {
        Driver driver = driverAuthService.resolveDriverByIdentifier(email);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Driver not found with email: " + email));
        }

        Object rawStatus = null;
        if (payload != null) {
            rawStatus = payload.get("status");
            if (rawStatus == null) rawStatus = payload.get("online");
            if (rawStatus == null) rawStatus = payload.get("isOnline");
        }

        String newStatus = driverAuthService.normalizeStatus(rawStatus);

        // Any wallet balance is allowed to go online freely. Wallet code preserved.

        driver.setStatus(newStatus);
        Driver saved = repository.save(driver);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", saved.getStatus() != null ? saved.getStatus().toLowerCase() : newStatus);
        response.put("driver", saved);
        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = {"/{driverId}/status", "/status", "/me/status"}, method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> updateStatusById(
            @PathVariable(required = false) String driverId,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> payload) {

        Driver driver = null;
        if (driverId == null || "me".equalsIgnoreCase(driverId) || "status".equalsIgnoreCase(driverId)) {
            driver = driverAuthService.resolveAuthenticatedDriver(request);
        } else {
            driver = driverAuthService.resolveDriverByIdentifier(driverId);
        }

        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "Driver not found: " + (driverId != null ? driverId : "me")));
        }

        Object rawStatus = null;
        if (payload != null) {
            rawStatus = payload.get("status");
            if (rawStatus == null) rawStatus = payload.get("online");
            if (rawStatus == null) rawStatus = payload.get("isOnline");
        }

        String newStatus = driverAuthService.normalizeStatus(rawStatus);

        // Any wallet balance is allowed to go online freely. Wallet code preserved.

        driver.setStatus(newStatus);
        Driver saved = repository.save(driver);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("status", saved.getStatus() != null ? saved.getStatus().toLowerCase() : newStatus);
        response.put("driver", saved);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<java.util.Map<String, Object>> verifyDriver(@PathVariable Long id) {
        return repository.findById(id).map(driver -> {
            driver.setKyc("verified");

            com.anushaporter.backend.model.Notification notif = new com.anushaporter.backend.model.Notification();
            notif.setTitle("Account Approved!");
            notif.setMessage("Congratulations! Your partner account has been approved. You can now log in and accept orders.");
            notif.setAudience("driver");
            notif.setTarget(driver.getEmail());
            notif.setReadStatus(false);
            notificationRepository.save(notif);

            Driver savedDriver = repository.save(driver);
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true, "driver", (Object) savedDriver));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping({"/{id}/reject", "/reject/{id}"})
    public ResponseEntity<?> rejectDriver(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> payload) {
        return repository.findById(id).map(driver -> {
            driver.setKyc("rejected");
            driver.setVerificationStatus("REJECTED_REQUIRES_REUPLOAD");

            String rejectionReason = null;
            String rejectedDocsString = null;
            String notes = null;

            if (payload != null) {
                rejectionReason = payload.get("rejectionReason") != null ? payload.get("rejectionReason").toString()
                        : (payload.get("reason") != null ? payload.get("reason").toString() : null);
                notes = payload.get("notes") != null ? payload.get("notes").toString() : null;

                Object rejectedDocsObj = payload.get("rejectedDocuments");
                if (rejectedDocsObj instanceof List) {
                    List<?> list = (List<?>) rejectedDocsObj;
                    rejectedDocsString = list.stream().map(Object::toString).collect(Collectors.joining(","));
                } else if (rejectedDocsObj != null) {
                    rejectedDocsString = rejectedDocsObj.toString();
                }
            }

            if (rejectionReason != null && !rejectionReason.isBlank()) {
                driver.setRejectionReason(rejectionReason);
            }
            if (rejectedDocsString != null && !rejectedDocsString.isBlank()) {
                driver.setRejectedDocuments(rejectedDocsString);
            }
            if (notes != null && !notes.isBlank()) {
                driver.setRejectionNotes(notes);
            }

            com.anushaporter.backend.model.Notification notif = new com.anushaporter.backend.model.Notification();
            notif.setTitle("Verification Requires Document Re-upload");
            String docInfo = (rejectedDocsString != null && !rejectedDocsString.isBlank()) ? " [" + rejectedDocsString + "]" : "";
            notif.setMessage("Your verification requires document re-upload" + docInfo + ". Open the driver app to re-upload.");
            notif.setAudience("driver");
            notif.setTarget(driver.getEmail());
            notif.setReadStatus(false);
            notificationRepository.save(notif);

            Driver savedDriver = repository.save(driver);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("message", "Driver verification rejected with re-upload requirement");
            resp.put("verificationStatus", savedDriver.getVerificationStatus());
            resp.put("rejectionReason", savedDriver.getRejectionReason());
            resp.put("rejectedDocuments", savedDriver.getRejectedDocuments());
            resp.put("driver", savedDriver);
            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.notFound().build());
    }

    @Autowired(required = false)
    private DriverWalletController driverWalletController;

    @GetMapping({"/me/wallet", "/wallet"})
    public ResponseEntity<?> getDriverWallet(jakarta.servlet.http.HttpServletRequest request) {
        if (driverWalletController != null) {
            return driverWalletController.getWallet(request);
        }
        return ResponseEntity.status(500).body(Map.of("success", false, "message", "Wallet service unavailable"));
    }

    @GetMapping({"/me/wallet/transactions", "/wallet/transactions", "/transactions"})
    public ResponseEntity<?> getDriverWalletTransactions(jakarta.servlet.http.HttpServletRequest request) {
        if (driverWalletController != null) {
            return driverWalletController.getWalletTransactions(request);
        }
        return ResponseEntity.status(500).body(Map.of("success", false, "message", "Wallet service unavailable"));
    }

    /**
     * POST /api/drivers/{driverId}/recharge
     * Driver or Admin wallet top-up.
     */
    @PostMapping({"/{driverId}/recharge", "/recharge/{driverId}"})
    public ResponseEntity<?> rechargeDriver(
            @PathVariable String driverId,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            Double amount = payload.get("amount") != null ? Double.parseDouble(String.valueOf(payload.get("amount"))) : 0.0;
            String notes = (String) payload.getOrDefault("notes", payload.get("description"));
            String paymentReference = (String) payload.getOrDefault("paymentReference", payload.get("payment_reference"));

            Map<String, Object> res = driverWalletService.rechargeDriverWalletDirect(driverId, amount, paymentReference, notes);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/drivers/{driverId}/wallet
     * Fetches driver's current wallet balance and transaction ledger.
     */
    @GetMapping({"/{driverId}/wallet", "/wallet/{driverId}"})
    public ResponseEntity<?> getDriverWalletById(@PathVariable String driverId) {
        Driver driver = driverWalletService.findDriverEntity(driverId);
        if (driver == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Driver not found"));
        }

        double walletBalance = driver.getWalletBalance() != null ? driver.getWalletBalance() : 0.0;
        boolean isEligible = walletBalance > 0.0;

        List<com.anushaporter.backend.model.WalletTransaction> txs =
                walletTransactionRepository.findByDriverIdOrderByCreatedAtDesc(String.valueOf(driver.getId()));

        List<Map<String, Object>> recentTx = txs.stream().map(tx -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tx.getId());
            m.put("type", tx.getTransactionType());
            m.put("amount", tx.getAmount());
            m.put("orderId", tx.getOrderId());
            m.put("balanceAfter", tx.getBalanceAfter());
            m.put("createdAt", tx.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("driverId", "DRV-" + driver.getId());
        response.put("walletBalance", walletBalance);
        response.put("isEligibleForOrders", isEligible);
        response.put("recentTransactions", recentTx);
        return ResponseEntity.ok(response);
    }
}
