package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PassengerVehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerVehicleCategoryRepository extends JpaRepository<PassengerVehicleCategory, Long> {
    Optional<PassengerVehicleCategory> findByCategoryCode(String categoryCode);
    List<PassengerVehicleCategory> findByActiveTrueOrderByDisplayOrderAsc();
    List<PassengerVehicleCategory> findAllByOrderByDisplayOrderAsc();
    List<PassengerVehicleCategory> findByActiveTrueAndPassengerCapacityGreaterThanEqualOrderByDisplayOrderAsc(Integer passengerCapacity);
}
