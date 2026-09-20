package com.anushaporter.backend.config;

import com.anushaporter.backend.model.Driver;
import com.anushaporter.backend.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup runner to patch existing driver records where vehicle or vehicleType is null/empty.
 * Ensures the Admin Panel and Customer/Driver apps always have synchronized vehicle fields.
 */
@Component
@Order(10)
public class DriverVehicleDataCorrectionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DriverVehicleDataCorrectionRunner.class);

    @Autowired
    private DriverRepository driverRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.VehicleTypeRepository vehicleTypeRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.PricingVehicleRepository pricingVehicleRepository;

    @Override
    public void run(String... args) {
        try {
            List<Driver> drivers = driverRepository.findAll();
            int patchedCount = 0;

            for (Driver d : drivers) {
                boolean modified = false;
                String vehicle = d.getVehicle();
                String vehicleType = d.getVehicleType();

                boolean vehicleEmpty = (vehicle == null || vehicle.trim().isEmpty());
                boolean vehicleTypeEmpty = (vehicleType == null || vehicleType.trim().isEmpty());

                if (vehicleEmpty && !vehicleTypeEmpty) {
                    d.setVehicle(vehicleType.trim());
                    modified = true;
                } else if (vehicleTypeEmpty && !vehicleEmpty) {
                    d.setVehicleType(vehicle.trim());
                    modified = true;
                } else if (vehicleEmpty && vehicleTypeEmpty) {
                    d.setVehicle("Scooter");
                    d.setVehicleType("Scooter");
                    modified = true;
                }

                // Synchronize serviceType (OUR_SERVICES vs PASSENGER)
                String sType = d.getServiceType();
                if (sType == null || sType.isBlank() || "BOTH".equalsIgnoreCase(sType)) {
                    String vStr = (d.getVehicleType() != null ? d.getVehicleType() : (d.getVehicle() != null ? d.getVehicle() : "")).toLowerCase();
                    if (vStr.contains("cab") || vStr.contains("car") || vStr.contains("taxi") || vStr.contains("sedan")
                            || vStr.contains("suv") || vStr.contains("biketaxi") || vStr.contains("autotaxi") || vStr.startsWith("pass")) {
                        d.setServiceType("PASSENGER");
                    } else {
                        d.setServiceType("OUR_SERVICES");
                    }
                    modified = true;
                }

                if (modified) {
                    driverRepository.save(d);
                    patchedCount++;
                }
            }

            if (patchedCount > 0) {
                log.info("[DriverVehicleMigration] Successfully patched {} existing driver record(s) with synchronized vehicle fields.", patchedCount);
            } else {
                log.info("[DriverVehicleMigration] Driver vehicle records are consistent. No uninitialized vehicle fields found.");
            }

            // Patch empty or broken imageUrl in VehicleType table
            if (vehicleTypeRepository != null) {
                var vTypes = vehicleTypeRepository.findAll();
                for (var vt : vTypes) {
                    String img = vt.getImageUrl();
                    if (img == null || img.trim().isEmpty() || img.endsWith("/vehicles/bike.png") || img.endsWith("/vehicles/auto.png") || img.contains("s3.amazonaws.com/vehicles/")) {
                        String corrected = com.anushaporter.backend.controller.VehicleTypeController.resolveDefaultVehicleImageUrl(vt.getType(), vt.getName(), vt.getId());
                        vt.setImageUrl(corrected);
                        vehicleTypeRepository.save(vt);
                        log.info("[DriverVehicleMigration] Updated vehicle type '{}' ({}) with image URL: {}", vt.getId(), vt.getName(), corrected);
                    }
                }
            }

            // Patch empty or broken imageUrl in PricingVehicle table
            if (pricingVehicleRepository != null) {
                var pVehicles = pricingVehicleRepository.findAll();
                for (var pv : pVehicles) {
                    String img = pv.getImageUrl();
                    if (img == null || img.trim().isEmpty() || img.endsWith("/vehicles/bike.png") || img.endsWith("/vehicles/auto.png") || img.contains("s3.amazonaws.com/vehicles/")) {
                        String corrected = com.anushaporter.backend.controller.VehicleTypeController.resolveDefaultVehicleImageUrl(pv.getVehicleId(), pv.getName(), pv.getId() != null ? String.valueOf(pv.getId()) : "");
                        pv.setImageUrl(corrected);
                        pricingVehicleRepository.save(pv);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DriverVehicleMigration] Note: Driver vehicle data migration completed with message: {}", e.getMessage());
        }
    }
}
