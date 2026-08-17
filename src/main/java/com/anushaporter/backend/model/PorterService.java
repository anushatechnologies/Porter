package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "services")
public class PorterService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String serviceId;

    private String name;
    private String label;
    private String category; // 'vehicle', 'two_wheeler', 'packers', 'intercity', 'how_it_works'
    private String categoryId; // Foreign category ID e.g. "1", "2" or slug
    private String categoryName; // Category Name e.g. "Porter Trucks & Fleet"
    private Boolean customerAppVisible = true;
    
    @Column(length = 1000)
    private String subtitle;

    @Column(length = 2000)
    private String description;

    private Double baseFare;
    private Double baseKm;
    private Double perKmRate;
    private Double helperRate;

    private Integer capacityKg;
    private String capacityLabel;

    @Column(length = 1000)
    private String dimensions; // JSON bed dimensions: {"length":"7 ft","width":"4.5 ft","height":"5 ft"}

    private String etaLabel;
    
    @Column(length = 1000)
    private String iconUrl;

    private String bgTint;
    private Boolean isActive = true;
    private Integer displayOrder = 1;

    @Column(length = 1000)
    private String availableCities; // JSON array: ["Hyderabad", "Secunderabad"] or ["ALL"]

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.customerAppVisible == null) {
            this.customerAppVisible = true;
        }
        if (this.displayOrder == null) {
            this.displayOrder = 1;
        }
        if (this.serviceId == null && this.name != null) {
            this.serviceId = this.name.toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-+|-+$", "");
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public Boolean getCustomerAppVisible() { return customerAppVisible; }
    public void setCustomerAppVisible(Boolean customerAppVisible) { this.customerAppVisible = customerAppVisible; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getBaseFare() { return baseFare; }
    public void setBaseFare(Double baseFare) { this.baseFare = baseFare; }

    public Double getBaseKm() { return baseKm; }
    public void setBaseKm(Double baseKm) { this.baseKm = baseKm; }

    public Double getPerKmRate() { return perKmRate; }
    public void setPerKmRate(Double perKmRate) { this.perKmRate = perKmRate; }

    public Double getHelperRate() { return helperRate; }
    public void setHelperRate(Double helperRate) { this.helperRate = helperRate; }

    public Integer getCapacityKg() { return capacityKg; }
    public void setCapacityKg(Integer capacityKg) { this.capacityKg = capacityKg; }

    public String getCapacityLabel() { return capacityLabel; }
    public void setCapacityLabel(String capacityLabel) { this.capacityLabel = capacityLabel; }

    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }

    public String getEtaLabel() { return etaLabel; }
    public void setEtaLabel(String etaLabel) { this.etaLabel = etaLabel; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public String getBgTint() { return bgTint; }
    public void setBgTint(String bgTint) { this.bgTint = bgTint; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getAvailableCities() { return availableCities; }
    public void setAvailableCities(String availableCities) { this.availableCities = availableCities; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
