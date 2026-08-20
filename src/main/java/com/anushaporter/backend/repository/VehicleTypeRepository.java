package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleTypeRepository extends JpaRepository<VehicleType, String> {

    /** Returns only active vehicle types, sorted by priority ascending */
    List<VehicleType> findByStatusOrderByPriorityAsc(String status);

    /** Find by type slug or id and status */
    Optional<VehicleType> findByIdAndStatus(String id, String status);

    /** Find by type slug */
    Optional<VehicleType> findByType(String type);

    /** Returns all vehicle types sorted by priority ascending */
    List<VehicleType> findAllByOrderByPriorityAsc();
}
