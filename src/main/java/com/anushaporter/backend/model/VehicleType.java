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
}
