package com.anushaporter.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * Represents a dynamic vehicle type available across Driver App (onboarding),
 * Customer App (pricing/booking), and Admin Web Dashboard.
 * Controlled by the Admin Portal — apps see only "active" entries.
 */
@Entity
@Table(name = "vehicle_types")
public class VehicleType {

    @Id
    @Column(name = "id", length = 64)
    private String id; // e.g. "1", "2", "veh_bike_01"

    @Column(nullable = false)
    private String name; // Display name: "2 Wheeler", "Tata Ace", "Pickup 8ft"

    @Column(nullable = false)
    private String type; // Slug / code: "two_wheeler", "tata_ace", "pickup_8ft"

    @Column(name = "description", length = 500)
    private String description; // e.g. "Best for documents & small packages"

    private String capacity; // "Load: Up to 20kg"

    @Column(name = "capacity_kg")
    private Integer capacityKg = 20; // 20, 500, 750, 1200, 2500

    private String dimensions; // "Ideal for documents & food parcels" or "7ft x 4ft x 5ft"

    @Column(name = "icon_name")
    private String iconName = "bike"; // "bike", "scooter", "rickshaw", "truck-delivery"

    @Column(name = "image_url", length = 500)
    private String imageUrl; // S3/CDN URL

    @Column(name = "base_fare")
    private Double baseFare = 40.0; // 40.00, 120.00, 250.00

    @Column(name = "base_km")
    private Double baseKm = 1.0; // 1.0 km

    @Column(name = "per_km_rate")
    private Double perKmRate = 12.0; // 12.00, 20.00, 30.00

    @Column(nullable = false)
    private String status = "active"; // "active" | "inactive"

    private Integer priority = 1; // Sort order (1, 2, 3...)

    @Column(name = "service_type", length = 32)
    private String serviceType = "OUR_SERVICES"; // "OUR_SERVICES" | "PASSENGER" | "BOTH"

    // ── Extended Business & Fleet Governance Fields ─────────────────────────
    @Column(name = "display_name")
    private String displayName; // e.g. "Maruti Dzire, Toyota Etios or similar"

    @Column(name = "max_passengers")
    private Integer maxPassengers; // 1, 3, 4, 6

    @Column(name = "max_luggage")
    private Integer maxLuggage; // 1, 2, 3, 4 pieces

    @Column(name = "per_minute_rate")
    private Double perMinuteRate = 0.0; // Waiting/duration fare per min

    @Column(name = "driver_allowance")
    private Double driverAllowance = 0.0; // Outstation/night daily allowance

    @Column(name = "helper_rate")
    private Double helperRate = 0.0; // Helper / labor rate per worker

    @Column(name = "volume")
    private Double volume; // Cargo volume in cft / m3

    @Column(name = "min_fare")
    private Double minFare; // Minimum floor fare

    @Column(name = "max_fare")
    private Double maxFare; // Maximum ceiling fare

    @Column(name = "min_distance")
    private Double minDistance = 1.0; // Minimum dispatch distance

    @Column(name = "max_distance")
    private Double maxDistance = 500.0; // Maximum dispatch boundary

    @Column(name = "commission_percentage")
    private Double commissionPercentage = 15.0; // Platform commission %

    @Column(name = "gst_percentage")
    private Double gstPercentage = 5.0; // GST %

    @Column(name = "customer_app_visible")
    private Boolean customerAppVisible = true; // Show in customer booking app

    @Column(name = "available_cities", length = 500)
    private String availableCities = "ALL"; // e.g. "ALL" or "Hyderabad,Secunderabad"

    // ── Getters & Setters ────────────────────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @JsonProperty("typeCode")
    public String getTypeCode() { return type; }
    public void setTypeCode(String typeCode) {
        if (typeCode != null && !typeCode.isBlank()) {
            this.type = typeCode;
        }
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCapacity() { return capacity; }
    public void setCapacity(String capacity) { this.capacity = capacity; }

    public Integer getCapacityKg() { return capacityKg; }
    public void setCapacityKg(Integer capacityKg) { this.capacityKg = capacityKg; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public String getIconName() { return iconName; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Double getBaseFare() { return baseFare; }
    public void setBaseFare(Double baseFare) { this.baseFare = baseFare; }

    public Double getBaseKm() { return baseKm; }
    public void setBaseKm(Double baseKm) { this.baseKm = baseKm; }

    public Double getPerKmRate() { return perKmRate; }
    public void setPerKmRate(Double perKmRate) { this.perKmRate = perKmRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public String getServiceType() {
        if (serviceType != null && !serviceType.isBlank()) {
            String s = serviceType.trim().toUpperCase();
            if (s.contains("BOTH") || s.contains("ALL")) {
                return "BOTH";
            }
            if (s.contains("PASSENGER") || s.contains("CAB") || s.contains("RIDE")) {
                return "PASSENGER";
            }
            return "OUR_SERVICES";
        }
        return "OUR_SERVICES";
    }

    public void setServiceType(String serviceType) {
        if (serviceType != null && !serviceType.isBlank()) {
            String s = serviceType.trim().toUpperCase();
            if (s.contains("BOTH") || s.contains("ALL")) {
                this.serviceType = "BOTH";
            } else if (s.contains("PASSENGER") || s.contains("CAB") || s.contains("RIDE")) {
                this.serviceType = "PASSENGER";
            } else {
                this.serviceType = "OUR_SERVICES";
            }
        } else {
            this.serviceType = "OUR_SERVICES";
        }
    }

    @JsonProperty("serviceCategory")
    public String getServiceCategory() {
        String st = getServiceType();
        if ("BOTH".equalsIgnoreCase(st)) return "Both (Passenger & Courier)";
        return "PASSENGER".equalsIgnoreCase(st) ? "Passenger Rides" : "Our Services";
    }

    public void setServiceCategory(String serviceCategory) {
        setServiceType(serviceCategory);
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Integer getMaxPassengers() { return maxPassengers; }
    public void setMaxPassengers(Integer maxPassengers) { this.maxPassengers = maxPassengers; }

    public Integer getMaxLuggage() { return maxLuggage; }
    public void setMaxLuggage(Integer maxLuggage) { this.maxLuggage = maxLuggage; }

    public Double getPerMinuteRate() { return perMinuteRate; }
    public void setPerMinuteRate(Double perMinuteRate) { this.perMinuteRate = perMinuteRate; }

    public Double getDriverAllowance() { return driverAllowance; }
    public void setDriverAllowance(Double driverAllowance) { this.driverAllowance = driverAllowance; }

    public Double getHelperRate() { return helperRate; }
    public void setHelperRate(Double helperRate) { this.helperRate = helperRate; }

    public Double getVolume() { return volume; }
    public void setVolume(Double volume) { this.volume = volume; }

    public Double getMinFare() { return minFare; }
    public void setMinFare(Double minFare) { this.minFare = minFare; }

    public Double getMaxFare() { return maxFare; }
    public void setMaxFare(Double maxFare) { this.maxFare = maxFare; }

    public Double getMinDistance() { return minDistance; }
    public void setMinDistance(Double minDistance) { this.minDistance = minDistance; }

    public Double getMaxDistance() { return maxDistance; }
    public void setMaxDistance(Double maxDistance) { this.maxDistance = maxDistance; }

    public Double getCommissionPercentage() { return commissionPercentage; }
    public void setCommissionPercentage(Double commissionPercentage) { this.commissionPercentage = commissionPercentage; }

    public Double getGstPercentage() { return gstPercentage; }
    public void setGstPercentage(Double gstPercentage) { this.gstPercentage = gstPercentage; }

    public Boolean getCustomerAppVisible() { return customerAppVisible; }
    public void setCustomerAppVisible(Boolean customerAppVisible) { this.customerAppVisible = customerAppVisible; }

    public String getAvailableCities() { return availableCities; }
    public void setAvailableCities(String availableCities) { this.availableCities = availableCities; }
}
