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
        } catch (Exception e) {
            log.warn("[DriverVehicleMigration] Note: Driver vehicle data migration completed with message: {}", e.getMessage());
        }
    }
}
