package com.anushaporter.backend.service;

import com.anushaporter.backend.model.PassengerPricingRule;
import com.anushaporter.backend.model.PassengerPricingVersion;
import com.anushaporter.backend.model.PricingAuditLog;
import com.anushaporter.backend.repository.PassengerPricingRuleRepository;
import com.anushaporter.backend.repository.PassengerPricingVersionRepository;
import com.anushaporter.backend.repository.PricingAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassengerPricingVersionService {

    private final PassengerPricingVersionRepository versionRepository;
    private final PassengerPricingRuleRepository ruleRepository;
    private final PricingAuditLogRepository auditLogRepository;

    public PassengerPricingVersion getActiveVersion() {
        return versionRepository.findFirstByStatusOrderByCreatedAtDesc("ACTIVE")
                .orElseGet(this::createInitialVersion);
    }

    @Transactional
    public PassengerPricingVersion createInitialVersion() {
        String versionNumber = generateVersionNumber();
        PassengerPricingVersion v = PassengerPricingVersion.builder()
                .versionNumber(versionNumber)
                .status("ACTIVE")
                .effectiveFrom(LocalDateTime.now())
                .createdBy("System Initializer")
                .notes("Initial baseline pricing version")
                .build();
        return versionRepository.save(v);
    }

    @Transactional
    public PassengerPricingVersion publishNewVersion(String adminName, String adminEmail, String notes) {
        PassengerPricingVersion currentActive = getActiveVersion();
        if (currentActive != null) {
            currentActive.setStatus("SUPERSEDED");
            currentActive.setEffectiveUntil(LocalDateTime.now());
            versionRepository.save(currentActive);
        }

        String nextVersionNumber = generateVersionNumber();
        PassengerPricingVersion newVersion = PassengerPricingVersion.builder()
                .versionNumber(nextVersionNumber)
                .status("ACTIVE")
                .effectiveFrom(LocalDateTime.now())
                .createdBy(adminName != null ? adminName : "Admin")
                .notes(notes)
                .build();
        newVersion = versionRepository.save(newVersion);

        // Copy existing active rules into new version if any
        if (currentActive != null) {
            List<PassengerPricingRule> currentRules = ruleRepository.findByPricingVersionId(currentActive.getVersionNumber());
            for (PassengerPricingRule r : currentRules) {
                PassengerPricingRule cloned = PassengerPricingRule.builder()
                        .pricingVersionId(nextVersionNumber)
                        .serviceCode(r.getServiceCode())
                        .vehicleCategoryCode(r.getVehicleCategoryCode())
                        .baseFare(r.getBaseFare())
                        .minimumKm(r.getMinimumKm())
                        .perKmRate(r.getPerKmRate())
                        .minimumFare(r.getMinimumFare())
                        .perMinuteRate(r.getPerMinuteRate())
                        .driverAllowance(r.getDriverAllowance())
                        .freeWaitingMinutes(r.getFreeWaitingMinutes())
                        .waitingChargePer15Min(r.getWaitingChargePer15Min())
                        .waitingChargePerHour(r.getWaitingChargePerHour())
                        .nightChargeFixed(r.getNightChargeFixed())
                        .nightChargePercentage(r.getNightChargePercentage())
                        .nightChargePerKm(r.getNightChargePerKm())
                        .nightStartHour(r.getNightStartHour())
                        .nightEndHour(r.getNightEndHour())
                        .firstStopFree(r.getFirstStopFree())
                        .additionalStopCharge(r.getAdditionalStopCharge())
                        .tollHandling(r.getTollHandling())
                        .fixedTollAmount(r.getFixedTollAmount())
                        .parkingHandling(r.getParkingHandling())
                        .fixedParkingAmount(r.getFixedParkingAmount())
                        .driverCommissionPercentage(r.getDriverCommissionPercentage())
                        .taxPercentage(r.getTaxPercentage())
                        .build();
                ruleRepository.save(cloned);
            }
        }

        logAudit(nextVersionNumber, "PRICING_VERSION", newVersion.getId(), adminName, adminEmail,
                "PUBLISH", "versionNumber", currentActive != null ? currentActive.getVersionNumber() : "NONE",
                nextVersionNumber, notes);

        return newVersion;
    }

    @Transactional
    public void logAudit(
            String versionId, String entityType, Long entityId,
            String adminName, String adminEmail, String changeType,
            String fieldName, String oldValue, String newValue, String reason
    ) {
        PricingAuditLog audit = PricingAuditLog.builder()
                .pricingVersionId(versionId)
                .entityType(entityType)
                .entityId(entityId)
                .adminName(adminName != null ? adminName : "Admin")
                .adminEmail(adminEmail != null ? adminEmail : "admin@anushaporter.com")
                .changeType(changeType)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .build();
        auditLogRepository.save(audit);
    }

    private String generateVersionNumber() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        long count = versionRepository.count() + 1;
        return String.format("PV-%s-%02d", today, count);
    }
}
