package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.RentalPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RentalPackageRepository extends JpaRepository<RentalPackage, Long> {
    List<RentalPackage> findByActiveTrue();
    List<RentalPackage> findByVehicleCategoryCodeAndActiveTrue(String vehicleCategoryCode);
}
