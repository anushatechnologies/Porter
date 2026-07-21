package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.WeightSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeightSlabRepository extends JpaRepository<WeightSlab, Long> {
    List<WeightSlab> findByVehicleIdOrderByFromKgAsc(String vehicleId);
}
