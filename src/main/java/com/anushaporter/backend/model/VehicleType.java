package com.anushaporter.backend.model;

import jakarta.persistence.*;

/**
 * Represents a vehicle type available for driver registration.
 * Controlled by the Admin Portal — drivers see only "active" entries.
 * Separate from the fleet Vehicle entity (which tracks plates/trips/owners).
 */
@Entity
@Table(name = "vehicle_types")
public class VehicleType {

    @Id
    @Column(name = "id", length = 64)
    private String id;               // e.g. "veh_bike_01"

    @Column(nullable = false)
    private String name;             // Display name: "Bike", "Tata Ace"

    @Column(nullable = false)
    private String type;             // Icon/slug: "bike", "truck-delivery"

    private String capacity;         // "Load: Up to 20kg"

    @Column(nullable = false)
    private String status = "active"; // "active" | "inactive"

    private Integer priority = 1;    // Sort order (lower = first)

    private String imageUrl;         // Optional icon URL

    // ── Getters & Setters ────────────────────────────────────────────────────
    public String getId()                        { return id; }
    public void setId(String id)                 { this.id = id; }

    public String getName()                      { return name; }
    public void setName(String name)             { this.name = name; }

    public String getType()                      { return type; }
    public void setType(String type)             { this.type = type; }

    public String getCapacity()                  { return capacity; }
    public void setCapacity(String capacity)     { this.capacity = capacity; }

    public String getStatus()                    { return status; }
    public void setStatus(String status)         { this.status = status; }

    public Integer getPriority()                 { return priority; }
    public void setPriority(Integer priority)    { this.priority = priority; }

    public String getImageUrl()                  { return imageUrl; }
    public void setImageUrl(String imageUrl)     { this.imageUrl = imageUrl; }
}
