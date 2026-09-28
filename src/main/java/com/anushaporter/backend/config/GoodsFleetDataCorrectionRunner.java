package com.anushaporter.backend.config;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.model.PricingVehicle;
import com.anushaporter.backend.model.PorterService;
import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.model.VehicleType;
import com.anushaporter.backend.repository.DriverRepository;
import com.anushaporter.backend.repository.PricingVehicleRepository;
import com.anushaporter.backend.repository.PorterServiceRepository;
import com.anushaporter.backend.repository.VehicleRepository;
import com.anushaporter.backend.repository.VehicleTypeRepository;
import com.anushaporter.backend.service.FleetSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup runner to scan for and purge backend-seeded legacy entries,
 * dummy test vehicles (Scooter Model, scooty, etc.), and non-vehicle category artifacts
 * from Goods & Services across all database tables (vehicles, pricing_vehicles, services, vehicle_types).
 * Guarantees that only Admin-added vehicles appear for drivers and users.
 */
@Component
@Order(6)
@Slf4j
public class GoodsFleetDataCorrectionRunner implements CommandLineRunner {

    @Autowired(required = false)
    private VehicleRepository vehicleRepository;

    @Autowired(required = false)
    private PricingVehicleRepository pricingVehicleRepository;

    @Autowired(required = false)
    private PorterServiceRepository porterServiceRepository;

    @Autowired(required = false)
    private VehicleTypeRepository vehicleTypeRepository;

    @Autowired(required = false)
    private DriverRepository driverRepository;

    @Override
    public void run(String... args) {
        try {
            cleanupLegacyVehicles();
            cleanupLegacyPricingVehicles();
            cleanupLegacyPorterServices();
            cleanupLegacyVehicleTypes();
            sanitizeDriverVehicles();
            restoreGoodsServices();
        } catch (Exception e) {
            log.warn("[GoodsDataMigration] Goods fleet cleanup completed with notice: {}", e.getMessage());
        }
    }

    /**
     * Purge dummy/test vehicles from MySQL vehicles table (e.g., Scooter Model, scooty, test plates)
     */
    public void cleanupLegacyVehicles() {
        if (vehicleRepository == null) return;
        try {
            List<Vehicle> all = vehicleRepository.findAll();
            List<Vehicle> toDelete = all.stream().filter(v -> {
                String model = v.getModel() != null ? v.getModel().trim() : "";
                String type = v.getType() != null ? v.getType().trim() : "";
                String plate = v.getPlate() != null ? v.getPlate().trim() : "";
                String owner = v.getOwner() != null ? v.getOwner().trim() : "";
                String idStr = v.getId() != null ? String.valueOf(v.getId()) : "";

                // Check artifact helper
                if (FleetSyncService.isNonVehicleArtifact(model, type)
                        || FleetSyncService.isNonVehicleArtifact(model, idStr)
                        || FleetSyncService.isNonVehicleArtifact(type, idStr)) {
                    return true;
                }

                // Check known test records
                String cleanModel = model.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (cleanModel.equals("scootermodel") || cleanModel.equals("scooty")
                        || cleanModel.equals("bikemodel") || cleanModel.equals("vehicle")) {
                    return true;
                }

                if (plate.equalsIgnoreCase("TG63737383882") || plate.equalsIgnoreCase("TG63728282929")) {
                    return true;
                }

                if (owner.equalsIgnoreCase("Unassigned") && (plate.isEmpty() || cleanModel.equals("scooty"))) {
                    return true;
                }

                return false;
            }).toList();

            if (!toDelete.isEmpty()) {
                vehicleRepository.deleteAll(toDelete);
                log.info("[GoodsDataMigration] Purged {} legacy test vehicle record(s) from vehicles table.", toDelete.size());
            }
        } catch (Exception e) {
            log.warn("[GoodsDataMigration] Notice while purging legacy vehicles: {}", e.getMessage());
        }
    }

    /**
     * Purge legacy artifacts from MySQL pricing_vehicles table
     */
    public void cleanupLegacyPricingVehicles() {
        if (pricingVehicleRepository == null) return;
        try {
            List<PricingVehicle> all = pricingVehicleRepository.findAll();
            List<PricingVehicle> toDelete = all.stream().filter(pv ->
                    FleetSyncService.isNonVehicleArtifact(pv.getName(), pv.getVehicleId())
            ).toList();

            if (!toDelete.isEmpty()) {
                pricingVehicleRepository.deleteAll(toDelete);
                log.info("[GoodsDataMigration] Purged {} legacy artifact record(s) from pricing_vehicles table.", toDelete.size());
            }
        } catch (Exception e) {
            log.warn("[GoodsDataMigration] Notice while purging pricing vehicles: {}", e.getMessage());
        }
    }

    /**
     * Purge category headers or non-vehicle artifacts from services table
     */
    public void cleanupLegacyPorterServices() {
        if (porterServiceRepository == null) return;
        try {
            List<PorterService> all = porterServiceRepository.findAll();
            List<PorterService> toDelete = all.stream().filter(s ->
                    FleetSyncService.isNonVehicleArtifact(s.getName(), s.getServiceId())
            ).toList();

            if (!toDelete.isEmpty()) {
                porterServiceRepository.deleteAll(toDelete);
                log.info("[GoodsDataMigration] Purged {} legacy artifact record(s) from services table.", toDelete.size());
            }
        } catch (Exception e) {
            log.warn("[GoodsDataMigration] Notice while purging services: {}", e.getMessage());
        }
    }

    /**
     * Purge artifacts from vehicle_types table
     */
    public void cleanupLegacyVehicleTypes() {
        if (vehicleTypeRepository == null) return;
        try {
            List<VehicleType> all = vehicleTypeRepository.findAll();
            List<VehicleType> toDelete = all.stream().filter(vt ->
                    FleetSyncService.isNonVehicleArtifact(vt.getName(), vt.getId())
            ).toList();

            if (!toDelete.isEmpty()) {
                vehicleTypeRepository.deleteAll(toDelete);
                log.info("[GoodsDataMigration] Purged {} legacy artifact record(s) from vehicle_types table.", toDelete.size());
            }
        } catch (Exception e) {
            log.warn("[GoodsDataMigration] Notice while purging vehicle types: {}", e.getMessage());
        }
    }

    /**
     * Restore delivery trucks into services table from vehicle_types / defaults
     * so that Admin Fleet Management and Customers have the full fleet available.
     */
    public void restoreGoodsServices() {
        if (porterServiceRepository == null) return;
        try {
            long goodsTrucksCount = porterServiceRepository.findAll().stream()
                    .filter(s -> "vehicle".equalsIgnoreCase(s.getCategory()) || "1".equals(s.getCategoryId()))
                    .count();

            if (goodsTrucksCount == 0) {
                log.info("[GoodsDataMigration] Goods delivery trucks missing from services table. Restoring delivery fleet...");
                List<PorterService> defaults = com.anushaporter.backend.controller.PorterServiceController.getDefaultFallbackServices();
                for (PorterService s : defaults) {
                    if (s.getServiceId() != null && porterServiceRepository.findByServiceId(s.getServiceId()).isEmpty()) {
                        porterServiceRepository.save(s);
                        log.info("[GoodsDataMigration] Restored goods service: {} ({})", s.getName(), s.getServiceId());
                    }
                }
            }

            // Also ensure any goods vehicles present in vehicle_types table are synchronized into services
            if (vehicleTypeRepository != null) {
                List<VehicleType> vts = vehicleTypeRepository.findAll();
                for (VehicleType vt : vts) {
                    String sType = vt.getServiceType() != null ? vt.getServiceType().toUpperCase() : "";
                    if ("OUR_SERVICES".equals(sType) || "GOODS".equals(sType) || "BOTH".equals(sType)) {
                        String name = vt.getName() != null ? vt.getName() : "";
                        String type = vt.getType() != null ? vt.getType() : "";
                        if (!FleetSyncService.isNonVehicleArtifact(name, vt.getId())) {
                            String slug = resolvePorterServiceSlug(vt.getId(), name);
                            if (slug != null && porterServiceRepository.findByServiceId(slug).isEmpty()
                                    && porterServiceRepository.findFirstByServiceIdIgnoreCase(slug).isEmpty()) {
                                PorterService ps = new PorterService();
                                ps.setServiceId(slug);
                                ps.setName(vt.getName() != null ? vt.getName() : vt.getDisplayName());
                                ps.setLabel(vt.getDisplayName() != null ? vt.getDisplayName() : vt.getName());
                                String cat = (type.toLowerCase().contains("two_wheel") || type.toLowerCase().contains("scooter") || type.toLowerCase().contains("bike")) ? "two_wheeler" : "vehicle";
                                ps.setCategory(cat);
                                ps.setCategoryId(cat.equals("two_wheeler") ? "2" : "1");
                                ps.setCategoryName(cat.equals("two_wheeler") ? "2 Wheeler / Bike" : "Porter Trucks & Fleet");
                                ps.setDescription(vt.getDescription());
                                ps.setSubtitle(vt.getDescription());
                                ps.setBaseFare(vt.getBaseFare() != null ? vt.getBaseFare() : 50.0);
                                ps.setBaseKm(vt.getBaseKm() != null ? vt.getBaseKm() : 1.0);
                                ps.setPerKmRate(vt.getPerKmRate() != null ? vt.getPerKmRate() : 12.0);
                                ps.setHelperRate(vt.getHelperRate() != null ? vt.getHelperRate() : 0.0);
                                ps.setCapacityKg(vt.getCapacityKg() != null ? vt.getCapacityKg() : 20);
                                ps.setCapacityLabel(vt.getCapacity() != null && !vt.getCapacity().isBlank() ? vt.getCapacity() : (vt.getCapacityKg() != null ? vt.getCapacityKg() + " Kg" : ""));
                                ps.setDimensions(vt.getDimensions());
                                ps.setIconUrl(vt.getImageUrl());
                                ps.setCustomerAppVisible(vt.getCustomerAppVisible() != null ? vt.getCustomerAppVisible() : true);
                                ps.setIsActive("active".equalsIgnoreCase(vt.getStatus()));
                                ps.setDisplayOrder(vt.getPriority() != null ? vt.getPriority() : 1);
                                ps.setAvailableCities(vt.getAvailableCities() != null ? vt.getAvailableCities() : "ALL");
                                porterServiceRepository.save(ps);
                                log.info("[GoodsDataMigration] Synchronized vehicle_type into services table: {} ({})", ps.getName(), ps.getServiceId());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[GoodsDataMigration] Notice during restoreGoodsServices: {}", e.getMessage());
        }
    }

    private String resolvePorterServiceSlug(String id, String name) {
        if (id == null && name == null) return "vehicle";
        String s = (id != null ? id : "").trim().toLowerCase();
        if (s.equals("veh_tataace_06") || s.contains("tataace") || s.contains("tata-ace")) return "tata-ace";
        if (s.equals("veh_minitruck_05") || s.contains("minitruck") || s.contains("mini3w")) return "mini-3w";
        if (s.equals("veh_pickup_04") || s.contains("pickup")) return "pickup-8ft";
        if (s.equals("veh_407_07") || s.contains("407") || s.equals("14ft")) return "14ft";
        if (s.equals("veh_lpt1109_08") || s.contains("1109") || s.equals("17ft")) return "17ft";
        if (s.equals("veh_auto_03") || s.contains("auto")) return "3-wheeler";
        if (s.equals("veh_scooter_02") || s.contains("scooter")) return "scooter";
        if (s.equals("2-wheeler") || s.contains("two_wheel")) return "2-wheeler";
        if (s.startsWith("veh_")) {
            String cleanName = (name != null ? name : id).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
            return cleanName.isEmpty() ? s : cleanName;
        }
        return s.replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
    }

    /**
     * Sanitize any driver whose assigned vehicle is a legacy artifact
     */
    public void sanitizeDriverVehicles() {
        if (driverRepository == null) return;
        try {
            List<Driver> drivers = driverRepository.findAll();
            int modifiedCount = 0;
            for (Driver d : drivers) {
                boolean changed = false;
                if (FleetSyncService.isNonVehicleArtifact(d.getVehicle(), d.getVehicle())) {
                    String replacement = "PASSENGER".equalsIgnoreCase(d.getServiceType()) ? "Cab" : "Scooter";
                    d.setVehicle(replacement);
                    changed = true;
                }
                if (FleetSyncService.isNonVehicleArtifact(d.getVehicleType(), d.getVehicleType())) {
                    String replacement = "PASSENGER".equalsIgnoreCase(d.getServiceType()) ? "Cab" : "Scooter";
                    d.setVehicleType(replacement);
                    changed = true;
                }
                if (changed) {
                    driverRepository.save(d);
                    modifiedCount++;
                }
            }
            if (modifiedCount > 0) {
                log.info("[GoodsDataMigration] Sanitized {} driver vehicle assignment(s).", modifiedCount);
            }
        } catch (Exception e) {
            log.warn("[GoodsDataMigration] Notice while sanitizing driver vehicles: {}", e.getMessage());
        }
    }
}
