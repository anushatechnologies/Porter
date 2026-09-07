package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PassengerPricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerPricingRuleRepository extends JpaRepository<PassengerPricingRule, Long> {
    List<PassengerPricingRule> findByPricingVersionId(String pricingVersionId);
    Optional<PassengerPricingRule> findByPricingVersionIdAndServiceCodeAndVehicleCategoryCode(
            String pricingVersionId, String serviceCode, String vehicleCategoryCode);
    Optional<PassengerPricingRule> findFirstByServiceCodeAndVehicleCategoryCodeOrderByIdDesc(
            String serviceCode, String vehicleCategoryCode);
}
