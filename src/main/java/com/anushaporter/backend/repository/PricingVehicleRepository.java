package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PricingVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PricingVehicleRepository extends JpaRepository<PricingVehicle, Long> {
    PricingVehicle findByVehicleId(String vehicleId);
    java.util.Optional<PricingVehicle> findFirstByVehicleIdIgnoreCase(String vehicleId);
    java.util.List<PricingVehicle> findByStatus(Boolean status);
    void deleteByVehicleId(String vehicleId);
    void deleteByVehicleIdIgnoreCase(String vehicleId);
}
