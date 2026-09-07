package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PricingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingAuditLogRepository extends JpaRepository<PricingAuditLog, Long> {
    List<PricingAuditLog> findAllByOrderByTimestampDesc();
    List<PricingAuditLog> findByPricingVersionIdOrderByTimestampDesc(String pricingVersionId);
}
