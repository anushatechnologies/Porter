package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.*;
import com.anushaporter.backend.repository.*;
import com.anushaporter.backend.service.payment.ReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminPaymentController {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private LedgerEntryRepository ledgerRepository;

    @Autowired
    private DriverPayoutRecordRepository payoutRecordRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    /**
     * GET /api/admin/payments
     * Admin view of all customer payments and transactions.
     */
    @GetMapping("/payments")
    public ResponseEntity<?> getPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String customerId
    ) {
        List<PaymentOrder> orders;
        if (status != null && !status.isBlank()) {
            try {
                orders = paymentOrderRepository.findByStatusOrderByCreatedAtDesc(PaymentStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                orders = paymentOrderRepository.findAllByOrderByCreatedAtDesc();
            }
        } else if (driverId != null && !driverId.isBlank()) {
            orders = paymentOrderRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
        } else if (customerId != null && !customerId.isBlank()) {
            orders = paymentOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        } else {
            orders = paymentOrderRepository.findAllByOrderByCreatedAtDesc();
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", orders.size(),
                "payments", orders
        ));
    }

    /**
     * GET /api/admin/commissions
     * Admin analytics on gross platform revenue, commission deducted, taxes, and net driver pay.
     */
    @GetMapping("/commissions")
    public ResponseEntity<?> getCommissionMetrics() {
        Double grossRevenue = ledgerRepository.sumTotalGrossRevenue();
        Double totalCommission = ledgerRepository.sumTotalPlatformCommission();

        double gross = grossRevenue != null ? grossRevenue : 0.0;
        double comm = totalCommission != null ? totalCommission : 0.0;
        double driverEarnings = Math.max(0.0, gross - comm);
        double estGst = Math.round(comm * 0.18 * 100.0) / 100.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("grossRevenue", Math.round(gross * 100.0) / 100.0);
        metrics.put("platformCommissionEarned", Math.round(comm * 100.0) / 100.0);
        metrics.put("driverPayableTotal", Math.round(driverEarnings * 100.0) / 100.0);
        metrics.put("estimatedGstPayable", estGst);
        metrics.put("effectiveCommissionPercentage", gross > 0 ? Math.round((comm / gross) * 1000.0) / 10.0 : 10.0);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "metrics", metrics
        ));
    }

    /**
     * GET /api/admin/payouts
     * Returns all driver payout records.
     */
    @GetMapping("/payouts")
    public ResponseEntity<?> getPayouts(@RequestParam(required = false) String status) {
        List<DriverPayoutRecord> payouts;
        if (status != null && !status.isBlank()) {
            try {
                payouts = payoutRecordRepository.findByStatusOrderByRequestedAtDesc(PayoutStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                payouts = payoutRecordRepository.findAllByOrderByRequestedAtDesc();
            }
        } else {
            payouts = payoutRecordRepository.findAllByOrderByRequestedAtDesc();
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", payouts.size(),
                "payouts", payouts
        ));
    }

    /**
     * POST /api/admin/payouts/:id/process
     * Manually process / release a pending payout.
     */
    @PostMapping("/payouts/{id}/process")
    public ResponseEntity<?> processPayout(@PathVariable Long id) {
        return payoutRecordRepository.findById(id).map(p -> {
            p.setStatus(PayoutStatus.SUCCESS);
            if (p.getUtr() == null || p.getUtr().isBlank()) {
                p.setUtr("ADM_UTR" + System.currentTimeMillis());
            }
            p.setSettledAt(java.time.LocalDateTime.now());
            payoutRecordRepository.save(p);
            return ResponseEntity.ok(Map.of("success", true, "message", "Payout settled successfully", "payout", p));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/admin/payouts/:id/reject
     * Rejects a pending payout.
     */
    @PostMapping("/payouts/{id}/reject")
    public ResponseEntity<?> rejectPayout(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return payoutRecordRepository.findById(id).map(p -> {
            p.setStatus(PayoutStatus.FAILED);
            p.setFailureReason(body != null && body.get("reason") != null ? body.get("reason") : "Rejected by Administrator");
            payoutRecordRepository.save(p);
            return ResponseEntity.ok(Map.of("success", true, "message", "Payout rejected", "payout", p));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/admin/reconciliation
     * Audits and displays discrepancy records.
     */
    @GetMapping("/reconciliation")
    public ResponseEntity<?> getReconciliationReport() {
        Map<String, Object> report = reconciliationService.runReconciliationAudit();
        return ResponseEntity.ok(report);
    }

    /**
     * POST /api/admin/reconciliation/run
     * Triggers a live audit run.
     */
    @PostMapping("/reconciliation/run")
    public ResponseEntity<?> triggerReconciliation() {
        Map<String, Object> report = reconciliationService.runReconciliationAudit();
        return ResponseEntity.ok(report);
    }
}
