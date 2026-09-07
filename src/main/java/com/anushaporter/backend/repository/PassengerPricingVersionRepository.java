package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PassengerPricingVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerPricingVersionRepository extends JpaRepository<PassengerPricingVersion, Long> {
    Optional<PassengerPricingVersion> findByVersionNumber(String versionNumber);
    Optional<PassengerPricingVersion> findFirstByStatusOrderByCreatedAtDesc(String status);
    List<PassengerPricingVersion> findAllByOrderByCreatedAtDesc();
}
