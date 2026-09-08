package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.ServiceableArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceableAreaRepository extends JpaRepository<ServiceableArea, Long> {

    List<ServiceableArea> findByCityIgnoreCaseOrderByAreaNameAsc(String city);

    List<ServiceableArea> findByCityIgnoreCaseAndIsServiceableTrueOrderByAreaNameAsc(String city);

    List<ServiceableArea> findByIsServiceableTrue();

    Optional<ServiceableArea> findByPincodeAndIsServiceableTrue(String pincode);

    List<ServiceableArea> findAllByPincode(String pincode);

    Optional<ServiceableArea> findFirstByCityIgnoreCaseAndPincode(String city, String pincode);

    boolean existsByPincodeAndIsServiceableTrue(String pincode);
}
