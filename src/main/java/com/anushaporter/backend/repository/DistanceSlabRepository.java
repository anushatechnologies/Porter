package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.DistanceSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DistanceSlabRepository extends JpaRepository<DistanceSlab, Long> {
    List<DistanceSlab> findByCityAndVehicleIdOrderByFromKmAsc(String city, String vehicleId);
}
