package com.anushaporter.backend.config;

import com.anushaporter.backend.model.PassengerPricingRule;
import com.anushaporter.backend.model.PassengerVehicleCategory;
import com.anushaporter.backend.repository.PassengerPricingRuleRepository;
import com.anushaporter.backend.repository.PassengerVehicleCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Startup runner to scan for and clean up duplicate rows in passenger_pricing_rules
 * and passenger_vehicle_categories (e.g. duplicate AUTO entries), retaining only the newest record.
 */
@Component
@Order(5)
@Slf4j
public class PassengerPricingDataCorrectionRunner implements CommandLineRunner {

    @Autowired(required = false)
    private PassengerPricingRuleRepository pricingRuleRepository;

    @Autowired(required = false)
    private PassengerVehicleCategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        try {
            cleanupDuplicatePricingRules();
            cleanupDuplicateVehicleCategories();
        } catch (Exception e) {
            log.warn("[PricingDataMigration] Cleanup completed with notice: {}", e.getMessage());
        }
    }

    public int cleanupDuplicatePricingRules() {
        if (pricingRuleRepository == null) return 0;
        List<PassengerPricingRule> allRules = pricingRuleRepository.findAll();
        // Group by (pricingVersionId + ":" + serviceCode + ":" + vehicleCategoryCode)
        Map<String, List<PassengerPricingRule>> grouped = new LinkedHashMap<>();
        for (PassengerPricingRule r : allRules) {
            String vId = r.getPricingVersionId() != null ? r.getPricingVersionId().trim() : "";
            String sCode = r.getServiceCode() != null ? r.getServiceCode().trim().toUpperCase() : "";
            String cCode = r.getVehicleCategoryCode() != null ? r.getVehicleCategoryCode().trim().toUpperCase() : "";
            String key = vId + ":" + sCode + ":" + cCode;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<PassengerPricingRule> toDelete = new ArrayList<>();
        for (Map.Entry<String, List<PassengerPricingRule>> entry : grouped.entrySet()) {
            List<PassengerPricingRule> list = entry.getValue();
            if (list.size() > 1) {
                // Sort by ID descending: keep the highest ID (latest entry)
                list.sort((a, b) -> {
                    Long idA = a.getId() != null ? a.getId() : 0L;
                    Long idB = b.getId() != null ? b.getId() : 0L;
                    return idB.compareTo(idA);
                });
                // Keep index 0, delete indices 1..n
                for (int i = 1; i < list.size(); i++) {
                    toDelete.add(list.get(i));
                }
            }
        }

        if (!toDelete.isEmpty()) {
            pricingRuleRepository.deleteAll(toDelete);
            log.info("[PricingDataMigration] Successfully deleted {} duplicate passenger pricing rule(s).", toDelete.size());
        } else {
            log.info("[PricingDataMigration] Passenger pricing rules are clean. No duplicate rules found.");
        }
        return toDelete.size();
    }

    public int cleanupDuplicateVehicleCategories() {
        if (categoryRepository == null) return 0;
        List<PassengerVehicleCategory> allCategories = categoryRepository.findAll();
        Map<String, List<PassengerVehicleCategory>> grouped = new LinkedHashMap<>();
        for (PassengerVehicleCategory c : allCategories) {
            String code = c.getCategoryCode() != null ? c.getCategoryCode().trim().toUpperCase() : "";
            grouped.computeIfAbsent(code, k -> new ArrayList<>()).add(c);
        }

        List<PassengerVehicleCategory> toDelete = new ArrayList<>();
        for (Map.Entry<String, List<PassengerVehicleCategory>> entry : grouped.entrySet()) {
            List<PassengerVehicleCategory> list = entry.getValue();
            if (list.size() > 1) {
                list.sort((a, b) -> {
                    Long idA = a.getId() != null ? a.getId() : 0L;
                    Long idB = b.getId() != null ? b.getId() : 0L;
                    return idB.compareTo(idA);
                });
                for (int i = 1; i < list.size(); i++) {
                    toDelete.add(list.get(i));
                }
            }
        }

        if (!toDelete.isEmpty()) {
            categoryRepository.deleteAll(toDelete);
            log.info("[PricingDataMigration] Successfully deleted {} duplicate passenger vehicle category record(s).", toDelete.size());
        }
        return toDelete.size();
    }
}
