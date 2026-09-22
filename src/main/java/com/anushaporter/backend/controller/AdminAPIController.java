package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.OrderRepository;
import com.anushaporter.backend.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminAPIController {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.PassengerBookingRepository passengerBookingRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.NotificationRepository notificationRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.service.S3ImageService s3ImageService;

    @Autowired(required = false)
    private com.anushaporter.backend.service.StorageService storageService;

    @Autowired(required = false)
    private com.anushaporter.backend.service.DriverAuthService driverAuthService;

    @GetMapping("/customers")
    public ResponseEntity<?> getCustomers() {
        return ResponseEntity.ok(appUserRepository.findByRoleIgnoreCase("customer"));
    }

    /**
     * Unified Admin Orders feed with filtering by serviceType (passenger, packers_movers, passengers_and_movers, goods, all).
     * GET /api/admin/orders?serviceType=passenger
     * GET /api/admin/orders?serviceType=packers_movers
     * GET /api/admin/orders?serviceType=passengers_and_movers
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(
            @RequestParam(value = "serviceType", required = false) String serviceType,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") Integer pageSize) {

        String targetType = serviceType != null && !serviceType.isBlank() ? serviceType
                : (type != null && !type.isBlank() ? type : category);

        return ResponseEntity.ok(buildAdminOrdersResponse(targetType, status, search, page, pageSize));
    }

    /**
     * Dedicated Admin endpoint for Passenger Cabs & Bike Rides only.
     * GET /api/admin/orders/passenger
     */
    @GetMapping({"/orders/passenger", "/orders/passengers", "/passenger/orders"})
    public ResponseEntity<?> getPassengerOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") Integer pageSize) {
        return ResponseEntity.ok(buildAdminOrdersResponse("passenger", status, search, page, pageSize));
    }

    /**
     * Dedicated Admin endpoint for Packers and Movers bookings only.
     * GET /api/admin/orders/packers-movers
     */
    @GetMapping({"/orders/packers-movers", "/orders/packers", "/orders/pm", "/pm/orders"})
    public ResponseEntity<?> getPackersMoversOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") Integer pageSize) {
        return ResponseEntity.ok(buildAdminOrdersResponse("packers_movers", status, search, page, pageSize));
    }

    /**
     * Dedicated Admin endpoint for Passengers AND Packers & Movers bookings combined.
     * GET /api/admin/orders/passengers-and-movers
     */
    @GetMapping({"/orders/passengers-and-movers", "/orders/special", "/orders/both"})
    public ResponseEntity<?> getPassengersAndMoversOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "50") Integer pageSize) {
        return ResponseEntity.ok(buildAdminOrdersResponse("passengers_and_movers", status, search, page, pageSize));
    }

    private Map<String, Object> buildAdminOrdersResponse(
            String serviceTypeFilter, String statusFilter, String search, Integer page, Integer pageSize) {

        List<Map<String, Object>> allRecords = new ArrayList<>();
        Set<String> seenBookingIds = new HashSet<>();

        // 1. Collect Passenger Bookings
        if (passengerBookingRepository != null) {
            try {
                List<com.anushaporter.backend.model.PassengerBooking> pbs = passengerBookingRepository.findAll();
                for (com.anushaporter.backend.model.PassengerBooking pb : pbs) {
                    String bId = pb.getBookingNumber() != null ? pb.getBookingNumber() : String.valueOf(pb.getId());
                    if (seenBookingIds.add(bId)) {
                        allRecords.add(formatPassengerBooking(pb));
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Collect Orders (Freight, Movers, and any Passenger orders from Order table)
        List<com.anushaporter.backend.model.Order> orders = orderRepository.findAll();
        for (com.anushaporter.backend.model.Order o : orders) {
            String bId = o.getBookingId() != null ? o.getBookingId() : "ORD-" + o.getId();
            if (!seenBookingIds.contains(bId)) {
                seenBookingIds.add(bId);
                if (isPackersOrder(o)) {
                    allRecords.add(formatPackersOrder(o));
                } else if (isPassengerOrder(o)) {
                    allRecords.add(formatPassengerOrderEntity(o));
                } else {
                    allRecords.add(formatGoodsOrder(o));
                }
            }
        }

        long passengerCount = allRecords.stream().filter(m -> "passenger".equals(m.get("serviceCategory"))).count();
        long packersCount = allRecords.stream().filter(m -> "packers_movers".equals(m.get("serviceCategory"))).count();
        long goodsCount = allRecords.stream().filter(m -> "goods".equals(m.get("serviceCategory"))).count();

        // 3. Filter by serviceType if specified
        List<Map<String, Object>> filtered = allRecords;
        if (serviceTypeFilter != null && !serviceTypeFilter.isBlank()) {
            String f = serviceTypeFilter.trim().toLowerCase();
            if (f.contains("passenger") && (f.contains("mover") || f.contains("packer") || f.contains("special") || f.contains("both"))) {
                filtered = filtered.stream()
                        .filter(m -> "passenger".equals(m.get("serviceCategory")) || "packers_movers".equals(m.get("serviceCategory")))
                        .collect(Collectors.toList());
            } else if (f.contains("passenger") || f.contains("cab") || f.contains("ride")) {
                filtered = filtered.stream()
                        .filter(m -> "passenger".equals(m.get("serviceCategory")))
                        .collect(Collectors.toList());
            } else if (f.contains("packer") || f.contains("mover") || f.contains("pm") || f.contains("shift")) {
                filtered = filtered.stream()
                        .filter(m -> "packers_movers".equals(m.get("serviceCategory")))
                        .collect(Collectors.toList());
            } else if (f.contains("good") || f.contains("freight") || f.contains("truck")) {
                filtered = filtered.stream()
                        .filter(m -> "goods".equals(m.get("serviceCategory")))
                        .collect(Collectors.toList());
            }
        }

        // 4. Filter by status if specified
        if (statusFilter != null && !statusFilter.isBlank()) {
            String sf = statusFilter.trim().toLowerCase();
            filtered = filtered.stream().filter(m -> {
                String st = m.get("status") != null ? String.valueOf(m.get("status")).toLowerCase() : "";
                if ("active".equals(sf)) {
                    return !st.contains("completed") && !st.contains("delivered") && !st.contains("cancelled") && !st.contains("refunded");
                }
                return st.contains(sf);
            }).collect(Collectors.toList());
        }

        // 5. Filter by search query
        if (search != null && !search.isBlank()) {
            String sq = search.trim().toLowerCase();
            filtered = filtered.stream().filter(m -> {
                String bId = m.get("bookingId") != null ? String.valueOf(m.get("bookingId")).toLowerCase() : "";
                String name = m.get("customerName") != null ? String.valueOf(m.get("customerName")).toLowerCase() : "";
                String phone = m.get("customerPhone") != null ? String.valueOf(m.get("customerPhone")).toLowerCase() : "";
                String email = m.get("customerEmail") != null ? String.valueOf(m.get("customerEmail")).toLowerCase() : "";
                String driverName = m.get("driverName") != null ? String.valueOf(m.get("driverName")).toLowerCase() : "";
                return bId.contains(sq) || name.contains(sq) || phone.contains(sq) || email.contains(sq) || driverName.contains(sq);
            }).collect(Collectors.toList());
        }

        // 6. Sort descending by createdAt
        filtered.sort((a, b) -> {
            Object ca = a.get("createdAt");
            Object cb = b.get("createdAt");
            if (ca instanceof Comparable && cb instanceof Comparable && ca.getClass().equals(cb.getClass())) {
                return ((Comparable) cb).compareTo(ca);
            }
            return 0;
        });

        // 7. Pagination
        int total = filtered.size();
        int safePage = Math.max(1, page != null ? page : 1);
        int safePageSize = Math.max(1, pageSize != null ? pageSize : 50);
        int start = (safePage - 1) * safePageSize;
        List<Map<String, Object>> pagedList = start >= total ? Collections.emptyList()
                : filtered.subList(start, Math.min(start + safePageSize, total));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("serviceType", serviceTypeFilter != null ? serviceTypeFilter : "all");
        response.put("total", total);
        response.put("passengerCount", passengerCount);
        response.put("packersCount", packersCount);
        response.put("goodsCount", goodsCount);
        response.put("page", safePage);
        response.put("pageSize", safePageSize);
        response.put("hasMore", (start + safePageSize) < total);
        response.put("orders", pagedList);
        response.put("items", pagedList);
        return response;
    }

    private Map<String, Object> formatPassengerBooking(com.anushaporter.backend.model.PassengerBooking pb) {
        Map<String, Object> m = new LinkedHashMap<>();
        String bNum = pb.getBookingNumber() != null ? pb.getBookingNumber() : String.valueOf(pb.getId());
        m.put("id", bNum);
        m.put("bookingId", bNum);
        m.put("bookingNumber", bNum);
        m.put("trackingNumber", pb.getTrackingNumber() != null ? pb.getTrackingNumber() : bNum);
        m.put("serviceCategory", "passenger");
        m.put("serviceType", "PASSENGER");
        m.put("serviceName", pb.getVehicleCategoryCode() != null ? pb.getVehicleCategoryCode() : "Passenger Ride");
        m.put("vehicleCategory", pb.getVehicleCategoryCode());
        m.put("customerName", pb.getCustomerName() != null ? pb.getCustomerName() : "Customer");
        m.put("customerPhone", pb.getCustomerPhone() != null ? pb.getCustomerPhone() : "");
        m.put("customerEmail", pb.getCustomerEmail() != null ? pb.getCustomerEmail() : "");
        m.put("passengerCount", pb.getPassengerCount() != null ? pb.getPassengerCount() : 1);
        m.put("pickupAddress", pb.getPickupAddress() != null ? pb.getPickupAddress() : "");
        m.put("dropAddress", pb.getDropAddress() != null ? pb.getDropAddress() : "");
        m.put("pickupLat", pb.getPickupLatitude());
        m.put("pickupLng", pb.getPickupLongitude());
        m.put("dropLat", pb.getDropLatitude());
        m.put("dropLng", pb.getDropLongitude());

        double amount = 0.0;
        if (pb.getFareBreakdown() != null && pb.getFareBreakdown().getTotalFare() != null) {
            amount = pb.getFareBreakdown().getTotalFare().doubleValue();
        } else if (pb.getEstimatedFare() != null) {
            amount = pb.getEstimatedFare().doubleValue();
        }
        m.put("amount", amount);
        m.put("status", pb.getStatus() != null ? pb.getStatus().name() : "REQUESTED");
        m.put("paymentStatus", pb.getPaymentStatus() != null ? pb.getPaymentStatus() : "PENDING");
        m.put("paymentMethod", pb.getPaymentMethod() != null ? pb.getPaymentMethod() : "CASH");
        m.put("startOtp", pb.getStartOtp());
        m.put("deliveryOtp", pb.getStartOtp());

        Long driverId = pb.getDriverId();
        String dName = pb.getDriverName();
        String dPhone = pb.getDriverPhone();
        String vNum = pb.getVehicleNumber();
        String vMod = pb.getVehicleModel();
        Driver resolvedDrv = (driverId != null) ? resolveDriver(driverId.toString()) : null;
        if (resolvedDrv != null) {
            if (dName == null || dName.isBlank()) dName = resolvedDrv.getName();
            if (dPhone == null || dPhone.isBlank()) dPhone = resolvedDrv.getPhone();
            if (vNum == null || vNum.isBlank()) vNum = resolvedDrv.getVehicleNumber();
            if (vMod == null || vMod.isBlank()) vMod = resolvedDrv.getVehicleType();
        }

        m.put("driverId", driverId);
        m.put("driverName", dName != null ? dName : "");
        m.put("driverPhone", dPhone != null ? dPhone : "");
        m.put("vehicleNumber", vNum != null ? vNum : "");
        m.put("vehicleModel", vMod != null ? vMod : "");

        if (resolvedDrv != null) {
            Map<String, Object> drvObj = new LinkedHashMap<>();
            drvObj.put("id", resolvedDrv.getId());
            drvObj.put("driverId", resolvedDrv.getId().toString());
            drvObj.put("name", resolvedDrv.getName());
            drvObj.put("phone", resolvedDrv.getPhone());
            drvObj.put("vehicleNumber", resolvedDrv.getVehicleNumber());
            drvObj.put("vehicleType", resolvedDrv.getVehicleType());
            drvObj.put("rating", resolvedDrv.getRating() != null ? resolvedDrv.getRating() : "4.8");
            drvObj.put("profilePhotoUri", storageService != null ? storageService.getPresignedOrSanitizedUrl(resolvedDrv.getProfilePhotoUri()) : resolvedDrv.getProfilePhotoUri());
            m.put("driver", drvObj);
        } else {
            m.put("driver", pb.getDriver());
        }

        m.put("distanceKm", pb.getDistanceKm() != null ? pb.getDistanceKm().doubleValue() : 0.0);
        m.put("durationMinutes", pb.getDurationMinutes());
        m.put("createdAt", pb.getCreatedAt() != null ? pb.getCreatedAt() : java.time.LocalDateTime.now());
        return m;
    }

    private Map<String, Object> formatPassengerOrderEntity(com.anushaporter.backend.model.Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        String bId = o.getBookingId() != null ? o.getBookingId() : "AP-" + o.getId();
        m.put("id", bId);
        m.put("bookingId", bId);
        m.put("bookingNumber", bId);
        m.put("trackingNumber", "TRK-" + bId);
        m.put("serviceCategory", "passenger");
        m.put("serviceType", "PASSENGER");
        m.put("serviceName", o.getServiceName() != null ? o.getServiceName() : "Passenger Ride");
        m.put("vehicleCategory", o.getServiceName());
        m.put("customerName", o.getReceiverName() != null ? o.getReceiverName() : "Customer");
        m.put("customerPhone", o.getReceiverPhone() != null ? o.getReceiverPhone() : "");
        m.put("customerEmail", o.getUserEmail() != null ? o.getUserEmail() : "");
        m.put("passengerCount", o.getPassengerCount() != null ? o.getPassengerCount() : 1);
        m.put("pickupAddress", o.getPickupAddress() != null ? o.getPickupAddress() : "");
        m.put("dropAddress", o.getDropAddress() != null ? o.getDropAddress() : "");
        m.put("pickupLat", o.getPickupLat());
        m.put("pickupLng", o.getPickupLng());
        m.put("dropLat", o.getDropLat());
        m.put("dropLng", o.getDropLng());
        m.put("amount", o.getAmount() != null ? o.getAmount() : 0.0);
        m.put("status", o.getStatus() != null ? o.getStatus() : "REQUESTED");
        m.put("paymentStatus", o.getPaymentStatus() != null ? o.getPaymentStatus() : "PENDING");
        m.put("paymentMethod", o.getPaymentMethod() != null ? o.getPaymentMethod() : "CASH");
        m.put("startOtp", o.getStartOtp() != null ? o.getStartOtp() : o.getDeliveryOtp());
        m.put("deliveryOtp", o.getDeliveryOtp() != null ? o.getDeliveryOtp() : o.getStartOtp());

        String dId = o.getDriverId();
        String dName = o.getDriverName();
        String dPhone = o.getDriverPhone();
        String dVeh = o.getDriverVehicleNumber();
        Driver resolvedDrv = resolveDriver(dId);
        if (resolvedDrv != null) {
            if (dName == null || dName.isBlank()) dName = resolvedDrv.getName();
            if (dPhone == null || dPhone.isBlank()) dPhone = resolvedDrv.getPhone();
            if (dVeh == null || dVeh.isBlank()) dVeh = resolvedDrv.getVehicleNumber();
        }
        m.put("driverId", dId);
        m.put("driverName", dName != null ? dName : "");
        m.put("driverPhone", dPhone != null ? dPhone : "");
        m.put("vehicleNumber", dVeh != null ? dVeh : "");
        if (resolvedDrv != null) {
            Map<String, Object> drvObj = new LinkedHashMap<>();
            drvObj.put("id", resolvedDrv.getId());
            drvObj.put("driverId", resolvedDrv.getId().toString());
            drvObj.put("name", resolvedDrv.getName());
            drvObj.put("phone", resolvedDrv.getPhone());
            drvObj.put("vehicleNumber", resolvedDrv.getVehicleNumber());
            drvObj.put("vehicleType", resolvedDrv.getVehicleType());
            drvObj.put("rating", resolvedDrv.getRating() != null ? resolvedDrv.getRating() : "4.8");
            drvObj.put("profilePhotoUri", storageService != null ? storageService.getPresignedOrSanitizedUrl(resolvedDrv.getProfilePhotoUri()) : resolvedDrv.getProfilePhotoUri());
            m.put("driver", drvObj);
        } else {
            m.put("driver", null);
        }

        m.put("distanceKm", o.getDistanceKm() != null ? o.getDistanceKm() : 0.0);
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt() : java.time.LocalDateTime.now());
        return m;
    }

    private Map<String, Object> formatPackersOrder(com.anushaporter.backend.model.Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        String bId = o.getBookingId() != null ? o.getBookingId() : "PM-" + o.getId();
        m.put("id", bId);
        m.put("bookingId", bId);
        m.put("bookingNumber", bId);
        m.put("trackingNumber", "TRK-" + bId);
        m.put("serviceCategory", "packers_movers");
        m.put("serviceType", "PACKERS_MOVERS");
        m.put("serviceName", o.getServiceName() != null ? o.getServiceName() : "Packers & Movers");
        m.put("customerName", o.getReceiverName() != null && !o.getReceiverName().isBlank() ? o.getReceiverName() : (o.getUserEmail() != null ? o.getUserEmail() : "Customer"));
        m.put("customerPhone", o.getReceiverPhone() != null && !o.getReceiverPhone().isBlank() ? o.getReceiverPhone() : "");
        m.put("customerEmail", o.getUserEmail() != null ? o.getUserEmail() : "");
        m.put("pickupAddress", o.getPickupAddress() != null ? o.getPickupAddress() : "");
        m.put("dropAddress", o.getDropAddress() != null ? o.getDropAddress() : "");
        m.put("pickupLat", o.getPickupLat());
        m.put("pickupLng", o.getPickupLng());
        m.put("dropLat", o.getDropLat());
        m.put("dropLng", o.getDropLng());
        m.put("amount", o.getAmount() != null ? o.getAmount() : 0.0);
        m.put("status", o.getStatus() != null ? o.getStatus() : "searching");
        m.put("paymentStatus", o.getPaymentStatus() != null ? o.getPaymentStatus() : "pending");
        m.put("paymentMethod", o.getPaymentMethod() != null ? o.getPaymentMethod() : "cash");
        m.put("startOtp", o.getStartOtp() != null ? o.getStartOtp() : o.getDeliveryOtp());
        m.put("deliveryOtp", o.getDeliveryOtp() != null ? o.getDeliveryOtp() : o.getStartOtp());

        String dId = o.getDriverId();
        String dName = o.getDriverName();
        String dPhone = o.getDriverPhone();
        String dVeh = o.getDriverVehicleNumber();
        Driver resolvedDrv = resolveDriver(dId);
        if (resolvedDrv != null) {
            if (dName == null || dName.isBlank()) dName = resolvedDrv.getName();
            if (dPhone == null || dPhone.isBlank()) dPhone = resolvedDrv.getPhone();
            if (dVeh == null || dVeh.isBlank()) dVeh = resolvedDrv.getVehicleNumber();
        }
        m.put("driverId", dId);
        m.put("driverName", dName != null ? dName : "");
        m.put("driverPhone", dPhone != null ? dPhone : "");
        m.put("vehicleNumber", dVeh != null ? dVeh : "");
        if (resolvedDrv != null) {
            Map<String, Object> drvObj = new LinkedHashMap<>();
            drvObj.put("id", resolvedDrv.getId());
            drvObj.put("driverId", resolvedDrv.getId().toString());
            drvObj.put("name", resolvedDrv.getName());
            drvObj.put("phone", resolvedDrv.getPhone());
            drvObj.put("vehicleNumber", resolvedDrv.getVehicleNumber());
            drvObj.put("vehicleType", resolvedDrv.getVehicleType());
            drvObj.put("rating", resolvedDrv.getRating() != null ? resolvedDrv.getRating() : "4.8");
            drvObj.put("profilePhotoUri", storageService != null ? storageService.getPresignedOrSanitizedUrl(resolvedDrv.getProfilePhotoUri()) : resolvedDrv.getProfilePhotoUri());
            m.put("driver", drvObj);
        } else {
            m.put("driver", null);
        }

        m.put("houseSize", o.getHouseSize());
        m.put("scheduledDate", o.getScheduledDate());
        m.put("scheduledSlot", o.getScheduledSlot());
        m.put("heavyItems", o.getHeavyItems());
        m.put("goodsCategory", o.getGoodsCategory());
        m.put("helpersCount", o.getHelpersCount());
        m.put("distanceKm", o.getDistanceKm() != null ? o.getDistanceKm() : 0.0);
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt() : java.time.LocalDateTime.now());
        return m;
    }

    private Map<String, Object> formatGoodsOrder(com.anushaporter.backend.model.Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        String bId = o.getBookingId() != null ? o.getBookingId() : "ORD-" + o.getId();
        m.put("id", bId);
        m.put("bookingId", bId);
        m.put("bookingNumber", bId);
        m.put("trackingNumber", "TRK-" + bId);
        m.put("serviceCategory", "goods");
        m.put("serviceType", o.getServiceType() != null && !o.getServiceType().isBlank() ? o.getServiceType() : "GOODS");
        m.put("serviceName", o.getServiceName() != null ? o.getServiceName() : "Freight Delivery");
        m.put("customerName", o.getReceiverName() != null && !o.getReceiverName().isBlank() ? o.getReceiverName() : (o.getUserEmail() != null ? o.getUserEmail() : "Customer"));
        m.put("customerPhone", o.getReceiverPhone() != null && !o.getReceiverPhone().isBlank() ? o.getReceiverPhone() : "");
        m.put("customerEmail", o.getUserEmail() != null ? o.getUserEmail() : "");
        m.put("pickupAddress", o.getPickupAddress() != null ? o.getPickupAddress() : "");
        m.put("dropAddress", o.getDropAddress() != null ? o.getDropAddress() : "");
        m.put("pickupLat", o.getPickupLat());
        m.put("pickupLng", o.getPickupLng());
        m.put("dropLat", o.getDropLat());
        m.put("dropLng", o.getDropLng());
        m.put("amount", o.getAmount() != null ? o.getAmount() : 0.0);
        m.put("status", o.getStatus() != null ? o.getStatus() : "searching");
        m.put("paymentStatus", o.getPaymentStatus() != null ? o.getPaymentStatus() : "pending");
        m.put("paymentMethod", o.getPaymentMethod() != null ? o.getPaymentMethod() : "cash");
        m.put("startOtp", o.getStartOtp() != null ? o.getStartOtp() : o.getDeliveryOtp());
        m.put("deliveryOtp", o.getDeliveryOtp() != null ? o.getDeliveryOtp() : o.getStartOtp());

        String dId = o.getDriverId();
        String dName = o.getDriverName();
        String dPhone = o.getDriverPhone();
        String dVeh = o.getDriverVehicleNumber();
        Driver resolvedDrv = resolveDriver(dId);
        if (resolvedDrv != null) {
            if (dName == null || dName.isBlank()) dName = resolvedDrv.getName();
            if (dPhone == null || dPhone.isBlank()) dPhone = resolvedDrv.getPhone();
            if (dVeh == null || dVeh.isBlank()) dVeh = resolvedDrv.getVehicleNumber();
        }
        m.put("driverId", dId);
        m.put("driverName", dName != null ? dName : "");
        m.put("driverPhone", dPhone != null ? dPhone : "");
        m.put("vehicleNumber", dVeh != null ? dVeh : "");
        if (resolvedDrv != null) {
            Map<String, Object> drvObj = new LinkedHashMap<>();
            drvObj.put("id", resolvedDrv.getId());
            drvObj.put("driverId", resolvedDrv.getId().toString());
            drvObj.put("name", resolvedDrv.getName());
            drvObj.put("phone", resolvedDrv.getPhone());
            drvObj.put("vehicleNumber", resolvedDrv.getVehicleNumber());
            drvObj.put("vehicleType", resolvedDrv.getVehicleType());
            drvObj.put("rating", resolvedDrv.getRating() != null ? resolvedDrv.getRating() : "4.8");
            drvObj.put("profilePhotoUri", storageService != null ? storageService.getPresignedOrSanitizedUrl(resolvedDrv.getProfilePhotoUri()) : resolvedDrv.getProfilePhotoUri());
            m.put("driver", drvObj);
        } else {
            m.put("driver", null);
        }

        m.put("distanceKm", o.getDistanceKm() != null ? o.getDistanceKm() : 0.0);
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt() : java.time.LocalDateTime.now());
        return m;
    }

    public Driver resolveDriver(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        String clean = rawId.trim();
        if (clean.toUpperCase().startsWith("DRV-")) clean = clean.substring(4).trim();
        if (clean.toUpperCase().startsWith("DRV_")) clean = clean.substring(4).trim();
        if (clean.matches("^\\d+$")) {
            try {
                Long id = Long.parseLong(clean);
                Optional<Driver> opt = driverRepository.findById(id);
                if (opt.isPresent()) return opt.get();
            } catch (Exception ignored) {}
        }
        if (driverAuthService != null) {
            Driver resolved = driverAuthService.resolveDriverByIdentifier(rawId);
            if (resolved != null) return resolved;
        }
        final String searchClean = clean;
        Optional<Driver> byPhone = driverRepository.findByPhone(searchClean);
        if (byPhone.isPresent()) return byPhone.get();
        return driverRepository.findByEmail(searchClean).orElse(null);
    }

    public static boolean isPackersOrder(com.anushaporter.backend.model.Order o) {
        if (o == null) return false;
        if ("PACKERS_MOVERS".equalsIgnoreCase(o.getServiceType()) || "packers".equalsIgnoreCase(o.getServiceType())) return true;
        String bId = o.getBookingId() != null ? o.getBookingId().toLowerCase() : "";
        if (bId.startsWith("pm-") || bId.contains("pack")) return true;
        String sn = o.getServiceName() != null ? o.getServiceName().toLowerCase() : "";
        String gc = o.getGoodsCategory() != null ? o.getGoodsCategory().toLowerCase() : "";
        return sn.contains("packer") || sn.contains("shift") || sn.contains("14ft") || sn.contains("17ft")
                || gc.contains("household");
    }

    public static boolean isPassengerOrder(com.anushaporter.backend.model.Order o) {
        if (o == null) return false;
        if ("PASSENGER".equalsIgnoreCase(o.getServiceType())) return true;
        String bId = o.getBookingId() != null ? o.getBookingId().toLowerCase() : "";
        return bId.startsWith("ap-car-") || bId.startsWith("trk-pass-") || bId.startsWith("pb-");
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        long totalDrivers = driverRepository.count();
        long pendingKyc = driverRepository.findAll().stream()
                .filter(d -> "pending".equalsIgnoreCase(d.getKyc()))
                .count();

        List<com.anushaporter.backend.model.Order> allOrders = orderRepository.findAll();

        long activeOrders = allOrders.stream()
                .filter(o -> "driver_assigned".equalsIgnoreCase(o.getStatus())
                        || "picked_up".equalsIgnoreCase(o.getStatus()) || "assigned".equalsIgnoreCase(o.getStatus())
                        || "accepted".equalsIgnoreCase(o.getStatus()) || "transit".equalsIgnoreCase(o.getStatus()))
                .count();

        java.time.LocalDate today = java.time.LocalDate.now();

        long totalOrdersToday = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().isEqual(today))
                .count();

        double revenueToday = allOrders.stream()
                .filter(o -> "completed".equalsIgnoreCase(o.getStatus()))
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().isEqual(today))
                .mapToDouble(o -> o.getAmount() != null ? o.getAmount() : 0.0)
                .sum();

        return ResponseEntity.ok(Map.of(
                "totalDrivers", totalDrivers,
                "pendingKyc", pendingKyc,
                "activeOrders", activeOrders,
                "totalOrdersToday", totalOrdersToday,
                "revenueToday", revenueToday));
    }

    public Map<String, Object> formatDriverDetails(Driver d) {
        if (d == null) return Collections.emptyMap();
        if (driverAuthService != null) {
            d = driverAuthService.ensureFullyRegisteredStatus(d);
        }
        boolean isComplete = d.isFullyRegistered();
        int regStep = isComplete ? 5 : (d.getRegistrationStep() != null ? d.getRegistrationStep() : 1);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "DRV-" + d.getId());
        m.put("driverId", d.getId().toString());
        m.put("numericId", d.getId());
        m.put("name", d.getName() != null ? d.getName() : "Unknown");
        m.put("email", d.getEmail() != null ? d.getEmail() : "");
        m.put("phone", d.getPhone() != null ? d.getPhone() : "");
        m.put("dob", d.getDob() != null ? d.getDob() : "");
        m.put("gender", d.getGender() != null ? d.getGender() : "");

        String vType = d.getVehicleType() != null && !d.getVehicleType().isBlank() ? d.getVehicleType()
                : (d.getVehicle() != null && !d.getVehicle().isBlank() ? d.getVehicle() : "Vehicle");
        String v = d.getVehicle() != null && !d.getVehicle().isBlank() ? d.getVehicle()
                : (d.getVehicleType() != null && !d.getVehicleType().isBlank() ? d.getVehicleType() : "Vehicle");
        m.put("vehicle", v);
        m.put("vehicleType", vType);
        m.put("vehicle_type", vType);
        m.put("vehicleName", vType);
        m.put("vehicleNumber", d.getVehicleNumber() != null ? d.getVehicleNumber() : "");

        String rawService = d.getServiceType() != null ? d.getServiceType().toUpperCase() : "OUR_SERVICES";
        String cleanService = "PASSENGER".equals(rawService) ? "PASSENGER" : "OUR_SERVICES";
        String serviceCategoryLabel = "PASSENGER".equals(cleanService) ? "Passenger Rides" : "Our Services";
        m.put("serviceType", cleanService);
        m.put("service_type", cleanService);
        m.put("serviceCategory", serviceCategoryLabel);
        m.put("service_category", serviceCategoryLabel);

        m.put("status", d.getStatus() != null ? d.getStatus().toLowerCase() : "offline");
        m.put("kyc", d.getKyc() != null ? d.getKyc() : "pending");
        m.put("kycStatus", d.getKyc() != null ? d.getKyc() : "pending");
        m.put("verificationStatus", d.getVerificationStatus() != null ? d.getVerificationStatus() : (d.getKyc() != null ? d.getKyc() : "pending"));
        m.put("rejectionReason", d.getRejectionReason() != null ? d.getRejectionReason() : d.getRejectedReason());
        m.put("rejectedReason", d.getRejectedReason() != null ? d.getRejectedReason() : d.getRejectionReason());
        m.put("rejectedDocuments", d.getRejectedDocuments() != null ? d.getRejectedDocuments() : "");
        m.put("rejectionNotes", d.getRejectionNotes() != null ? d.getRejectionNotes() : "");

        m.put("rcNumber", d.getRcNumber() != null ? d.getRcNumber() : "");
        m.put("licenseNumber", d.getLicenseNumber() != null ? d.getLicenseNumber() : "");
        m.put("aadhaarNumber", d.getAadhaarNumber() != null ? d.getAadhaarNumber() : "");
        m.put("panNumber", d.getPanNumber() != null ? d.getPanNumber() : "");

        m.put("addressLine1", d.getAddressLine1() != null ? d.getAddressLine1() : "");
        m.put("addressLine2", d.getAddressLine2() != null ? d.getAddressLine2() : "");
        m.put("city", d.getCity() != null ? d.getCity() : "");
        m.put("state", d.getState() != null ? d.getState() : "");
        m.put("pincode", d.getPincode() != null ? d.getPincode() : "");

        m.put("bankName", d.getBankName() != null ? d.getBankName() : "");
        m.put("accountHolderName", d.getAccountHolderName() != null ? d.getAccountHolderName() : "");
        m.put("accountNumber", d.getAccountNumber() != null ? d.getAccountNumber() : "");
        m.put("ifscCode", d.getIfscCode() != null ? d.getIfscCode() : "");
        m.put("upiId", d.getUpiId() != null ? d.getUpiId() : "");

        m.put("isRegistered", isComplete);
        m.put("registrationCompleted", isComplete);
        m.put("hasDraft", !isComplete);
        m.put("registrationStep", regStep);

        m.put("rating", d.getRating() != null ? d.getRating() : "4.8");
        m.put("trips", d.getTrips() != null ? d.getTrips() : 0);
        m.put("walletBalance", d.getWalletBalance() != null ? d.getWalletBalance() : 0.0);
        m.put("wallet_balance", d.getWalletBalance() != null ? d.getWalletBalance() : 0.0);

        String licUri = d.getLicenseUri();
        String rcUri = d.getRcUri();
        String aadhUri = d.getAadhaarUri();
        String panUri = d.getPanUri();
        String photoUri = d.getProfilePhotoUri();
        String passbookUri = d.getBankPassbookUri();
        if (storageService != null) {
            licUri = storageService.getPresignedOrSanitizedUrl(licUri);
            rcUri = storageService.getPresignedOrSanitizedUrl(rcUri);
            aadhUri = storageService.getPresignedOrSanitizedUrl(aadhUri);
            panUri = storageService.getPresignedOrSanitizedUrl(panUri);
            photoUri = storageService.getPresignedOrSanitizedUrl(photoUri);
            passbookUri = storageService.getPresignedOrSanitizedUrl(passbookUri);
        }
        m.put("licenseUri", licUri);
        m.put("rcUri", rcUri);
        m.put("aadhaarUri", aadhUri);
        m.put("panUri", panUri);
        m.put("profilePhotoUri", photoUri);
        m.put("bankPassbookUri", passbookUri);

        double lat = d.getLatitude() != null ? d.getLatitude() : 17.4483;
        double lng = d.getLongitude() != null ? d.getLongitude() : 78.3915;
        Map<String, Object> locMap = new LinkedHashMap<>();
        locMap.put("lat", lat);
        locMap.put("lng", lng);
        locMap.put("x", lat);
        locMap.put("y", lng);
        locMap.put("speed", d.getSpeed() != null ? d.getSpeed() : 0.0);
        locMap.put("angle", d.getHeading() != null ? d.getHeading() : 45.0);
        m.put("location", locMap);

        return m;
    }

    @GetMapping("/drivers")
    public ResponseEntity<?> getDrivers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kyc,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String service) {

        List<Driver> drivers = driverRepository.findAll();

        String statusFilter = status != null ? status.trim() : null;
        String kycFilter = kyc != null ? kyc.trim() : null;
        String searchTerm = search != null ? search.trim().toLowerCase() : (q != null ? q.trim().toLowerCase() : null);
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

        List<Map<String, Object>> response = drivers.stream()
                .filter(d -> {
                    // Status filter (online, offline, active, suspended)
                    if (statusFilter != null && !statusFilter.isEmpty() && !"all".equalsIgnoreCase(statusFilter)) {
                        boolean matchesStatus = d.getStatus() != null && d.getStatus().equalsIgnoreCase(statusFilter);
                        boolean matchesKyc = d.getKyc() != null && d.getKyc().equalsIgnoreCase(statusFilter);
                        if (!matchesStatus && !matchesKyc) {
                            return false;
                        }
                    }

                    // KYC filter (verified, approved, pending, rejected, draft)
                    if (kycFilter != null && !kycFilter.isEmpty() && !"all".equalsIgnoreCase(kycFilter)) {
                        if (d.getKyc() == null || !d.getKyc().equalsIgnoreCase(kycFilter)) {
                            return false;
                        }
                    }

                    // Service Type filter
                    if (finalServiceFilter != null) {
                        String rawS = d.getServiceType() != null ? d.getServiceType().toUpperCase() : "OUR_SERVICES";
                        if ("PASSENGER".equals(finalServiceFilter)) {
                            if (!"PASSENGER".equals(rawS)) return false;
                        } else if ("OUR_SERVICES".equals(finalServiceFilter)) {
                            if (!"OUR_SERVICES".equals(rawS) && !"GOODS".equals(rawS)) return false;
                        }
                    }

                    // Search filter
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

                    return true;
                })
                .map(this::formatDriverDetails)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping({"/drivers/{id:[0-9]+}", "/drivers/{id:DRV-[0-9]+}", "/drivers/{id:drv-[0-9]+}"})
    public ResponseEntity<?> getDriverById(@PathVariable String id) {
        Driver d = resolveDriver(id);
        if (d == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Driver not found: " + id));
        }
        return ResponseEntity.ok(formatDriverDetails(d));
    }

    @GetMapping("/drivers/our-services")
    public ResponseEntity<?> getOurServicesDrivers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kyc,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q) {
        return getDrivers(status, kyc, search, q, "OUR_SERVICES", null);
    }

    @GetMapping("/drivers/passengers")
    public ResponseEntity<?> getPassengerDrivers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kyc,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q) {
        return getDrivers(status, kyc, search, q, "PASSENGER", null);
    }

    @PutMapping({"/drivers/{driverId}/kyc", "/drivers/{driverId:[0-9]+}/kyc", "/drivers/{driverId:DRV-[0-9]+}/kyc", "/drivers/{driverId:drv-[0-9]+}/kyc"})
    public ResponseEntity<?> updateDriverKyc(@PathVariable String driverId, @RequestBody Map<String, String> payload) {
        Driver driver = resolveDriver(driverId);
        if (driver == null) {
            return ResponseEntity.notFound().build();
        }

        String status = payload.get("status");
        if (status != null) {
            driver.setKyc(status);
            if ("rejected".equalsIgnoreCase(status)) {
                driver.setRejectedReason(payload.get("reason"));
            }
            driverRepository.save(driver);
        }

        return ResponseEntity
                .ok(Map.of("success", true, "driverId", driver.getId().toString(), "kycStatus", driver.getKyc()));
    }

    @PostMapping({"/drivers/{id}/verify", "/drivers/verify/{id}"})
    public ResponseEntity<Map<String, Object>> verifyDriver(@PathVariable String id) {
        Driver driver = resolveDriver(id);
        if (driver == null) return ResponseEntity.notFound().build();

        driver.setKyc("verified");
        driver.setVerificationStatus("approved");
        driver.setRegistrationStep(5);
        if (driverAuthService != null) {
            driver = driverAuthService.ensureFullyRegisteredStatus(driver);
        }

        if (notificationRepository != null) {
            com.anushaporter.backend.model.Notification notif = new com.anushaporter.backend.model.Notification();
            notif.setTitle("Account Approved!");
            notif.setMessage("Congratulations! Your partner account has been approved. You can now log in and accept orders.");
            notif.setAudience("driver");
            notif.setTarget(driver.getEmail());
            notif.setReadStatus(false);
            notificationRepository.save(notif);
        }

        Driver savedDriver = driverRepository.save(driver);
        return ResponseEntity.ok(Map.of("success", (Object) true, "driver", (Object) formatDriverDetails(savedDriver)));
    }

    @PostMapping({"/drivers/{id}/reject", "/drivers/reject/{id}"})
    public ResponseEntity<?> rejectDriver(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload) {
        Driver driver = resolveDriver(id);
        if (driver == null) return ResponseEntity.notFound().build();

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

        if (notificationRepository != null) {
            com.anushaporter.backend.model.Notification notif = new com.anushaporter.backend.model.Notification();
            notif.setTitle("Verification Requires Document Re-upload");
            String docInfo = (rejectedDocsString != null && !rejectedDocsString.isBlank()) ? " [" + rejectedDocsString + "]" : "";
            notif.setMessage("Your verification requires document re-upload" + docInfo + ". Open the driver app to re-upload.");
            notif.setAudience("driver");
            notif.setTarget(driver.getEmail());
            notif.setReadStatus(false);
            notificationRepository.save(notif);
        }

        Driver savedDriver = driverRepository.save(driver);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "Driver verification rejected with re-upload requirement");
        resp.put("verificationStatus", savedDriver.getVerificationStatus());
        resp.put("rejectionReason", savedDriver.getRejectionReason());
        resp.put("rejectedDocuments", savedDriver.getRejectedDocuments());
        resp.put("driver", formatDriverDetails(savedDriver));
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping({"/drivers/{id:[0-9]+}", "/drivers/{id:DRV-[0-9]+}", "/drivers/{id:drv-[0-9]+}"})
    public ResponseEntity<?> deleteDriver(@PathVariable String id) {
        Driver driver = resolveDriver(id);
        if (driver == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Driver not found: " + id));
        }

        if (s3ImageService != null) {
            try { s3ImageService.deleteImage(driver.getProfilePhotoUri()); } catch (Exception ignored) {}
            try { s3ImageService.deleteImage(driver.getAadhaarUri()); } catch (Exception ignored) {}
            try { s3ImageService.deleteImage(driver.getLicenseUri()); } catch (Exception ignored) {}
            try { s3ImageService.deleteImage(driver.getRcUri()); } catch (Exception ignored) {}
            try { s3ImageService.deleteImage(driver.getBankPassbookUri()); } catch (Exception ignored) {}
        }

        Long deletedId = driver.getId();
        driverRepository.delete(driver);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Driver profile removed successfully",
                "id", deletedId,
                "driverId", deletedId.toString()
        ));
    }

    /**
     * Endpoint 4: Admin Analytics & System Reports
     * GET /api/admin/analytics?period=week|month|year
     */
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @RequestParam(required = false, defaultValue = "week") String period) {
        long totalOrders = orderRepository.count();
        long activeDrivers = driverRepository.findAll().stream()
                .filter(d -> "online".equalsIgnoreCase(d.getStatus()) || "active".equalsIgnoreCase(d.getStatus()))
                .count();

        double totalRevenue = orderRepository.findAll().stream()
                .mapToDouble(o -> o.getAmount() != null ? o.getAmount() : 0.0)
                .sum();

        if (totalRevenue == 0.0)
            totalRevenue = 48500.0;
        if (totalOrders == 0)
            totalOrders = 320;
        if (activeDrivers == 0)
            activeDrivers = 14;

        List<Map<String, Object>> distribution = List.of(
                Map.of("type", "Scooter", "percentage", 45.0),
                Map.of("type", "3 Wheeler", "percentage", 35.0),
                Map.of("type", "Tata Ace", "percentage", 20.0));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("period", period);
        response.put("totalRevenue", totalRevenue);
        response.put("totalOrders", totalOrders);
        response.put("activeDrivers", activeDrivers);
        response.put("cancellationRate", 2.5);
        response.put("vehicleDistribution", distribution);

        return ResponseEntity.ok(response);
    }
}
