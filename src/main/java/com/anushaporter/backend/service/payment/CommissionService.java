package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.CommissionRule;
import com.anushaporter.backend.repository.CommissionRuleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CommissionService {

    @Autowired
    private CommissionRuleRepository ruleRepository;

    @PostConstruct
    public void seedDefaultCommissionRules() {
        if (ruleRepository.count() == 0) {
            CommissionRule defaultRule = new CommissionRule();
            defaultRule.setRuleId("RULE_DEFAULT_10");
            defaultRule.setServiceCategory("ALL");
            defaultRule.setCommissionType("PERCENTAGE");
            defaultRule.setPercentageRate(10.0); // 10% platform commission
            defaultRule.setFixedAmount(0.0);
            defaultRule.setMinCommission(5.0);
            defaultRule.setMaxCommission(1500.0);
            defaultRule.setTaxPercentage(18.0);
            defaultRule.setIsActive(true);
            ruleRepository.save(defaultRule);
        }
    }

    /**
     * Calculates platform commission and net driver earnings
     */
    public Map<String, Object> calculateCommission(double grossFare, String serviceCategory) {
        CommissionRule rule = null;
        if (serviceCategory != null && !serviceCategory.isBlank()) {
            rule = ruleRepository.findFirstByServiceCategoryIgnoreCaseAndIsActiveTrueOrderByIdDesc(serviceCategory).orElse(null);
        }
        if (rule == null) {
            rule = ruleRepository.findFirstByServiceCategoryIgnoreCaseAndIsActiveTrue("ALL")
                    .orElseGet(() -> ruleRepository.findAll().stream().findFirst().orElse(null));
        }

        double percentage = rule != null && rule.getPercentageRate() != null ? rule.getPercentageRate() : 10.0;
        double fixed = rule != null && rule.getFixedAmount() != null ? rule.getFixedAmount() : 0.0;
        double minComm = rule != null && rule.getMinCommission() != null ? rule.getMinCommission() : 0.0;
        double maxComm = rule != null && rule.getMaxCommission() != null ? rule.getMaxCommission() : 10000.0;
        String type = rule != null && rule.getCommissionType() != null ? rule.getCommissionType() : "PERCENTAGE";

        double calculatedCommission = 0.0;
        if ("FIXED".equalsIgnoreCase(type)) {
            calculatedCommission = fixed;
        } else if ("HYBRID".equalsIgnoreCase(type)) {
            calculatedCommission = (grossFare * (percentage / 100.0)) + fixed;
        } else {
            // PERCENTAGE default
            calculatedCommission = grossFare * (percentage / 100.0);
        }

        // Apply min/max clamps
        if (calculatedCommission < minComm && grossFare > minComm) {
            calculatedCommission = minComm;
        }
        if (calculatedCommission > maxComm) {
            calculatedCommission = maxComm;
        }
        if (calculatedCommission > grossFare) {
            calculatedCommission = grossFare; // Never exceed gross fare
        }

        BigDecimal commBd = BigDecimal.valueOf(calculatedCommission).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossBd = BigDecimal.valueOf(grossFare).setScale(2, RoundingMode.HALF_UP);
        BigDecimal driverNetBd = grossBd.subtract(commBd).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grossFare", grossBd.doubleValue());
        result.put("platformCommission", commBd.doubleValue());
        result.put("driverNetEarning", driverNetBd.doubleValue());
        result.put("commissionPercentage", percentage);
        result.put("ruleId", rule != null ? rule.getRuleId() : "DEFAULT_10");
        return result;
    }
}
