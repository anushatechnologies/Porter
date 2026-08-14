package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    Optional<ReconciliationRecord> findByPaymentId(String paymentId);
    List<ReconciliationRecord> findByMatchStatus(String matchStatus);
    List<ReconciliationRecord> findByReconciliationDate(String date);
    List<ReconciliationRecord> findAllByOrderByReconciledAtDesc();
}
