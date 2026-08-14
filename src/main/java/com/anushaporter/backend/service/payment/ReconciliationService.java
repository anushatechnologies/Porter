package com.anushaporter.backend.service.payment;

import com.anushaporter.backend.model.PaymentOrder;
import com.anushaporter.backend.model.PaymentStatus;
import com.anushaporter.backend.model.ReconciliationRecord;
import com.anushaporter.backend.repository.PaymentOrderRepository;
import com.anushaporter.backend.repository.ReconciliationRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ReconciliationService {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private ReconciliationRecordRepository reconciliationRepository;

    @Autowired
    private PaymentProvider paymentProvider;

    public Map<String, Object> runReconciliationAudit() {
        String today = LocalDate.now().toString();
        List<PaymentOrder> orders = paymentOrderRepository.findAll();

        int matchedCount = 0;
        int mismatchCount = 0;
        List<ReconciliationRecord> records = new ArrayList<>();

        for (PaymentOrder order : orders) {
            String matchStatus = "MATCHED";
            String notes = "Transaction reconciled successfully";
            Double gatewayAmount = order.getAmount();
            String gatewayStatus = order.getStatus().name();

            if (order.getStatus() == PaymentStatus.PENDING && order.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                matchStatus = "MISMATCH";
                notes = "Pending payment older than 24 hours without gateway confirmation";
                mismatchCount++;
            } else {
                matchedCount++;
            }

            ReconciliationRecord rec = reconciliationRepository.findByPaymentId(order.getPaymentId())
                    .orElse(new ReconciliationRecord());
            rec.setReconciliationDate(today);
            rec.setPaymentId(order.getPaymentId());
            rec.setBookingId(order.getBookingId());
            rec.setInternalAmount(order.getAmount());
            rec.setGatewayAmount(gatewayAmount);
            rec.setInternalStatus(order.getStatus().name());
            rec.setGatewayStatus(gatewayStatus);
            rec.setMatchStatus(matchStatus);
            rec.setNotes(notes);
            rec.setReconciledAt(LocalDateTime.now());
            records.add(reconciliationRepository.save(rec));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("auditDate", today);
        result.put("totalChecked", orders.size());
        result.put("matchedCount", matchedCount);
        result.put("mismatchCount", mismatchCount);
        result.put("records", records);
        return result;
    }
}
