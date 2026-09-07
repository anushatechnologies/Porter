package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.ZonePricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZonePricingRuleRepository extends JpaRepository<ZonePricingRule, Long> {
    List<ZonePricingRule> findByZoneIdAndActiveTrue(Long zoneId);
    Optional<ZonePricingRule> findByZoneIdAndVehicleCategoryCodeAndServiceCodeAndActiveTrue(
            Long zoneId, String vehicleCategoryCode, String serviceCode);
}
