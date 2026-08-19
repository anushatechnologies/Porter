package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    List<WalletTransaction> findByDriverIdOrderByCreatedAtDesc(String driverId);
    List<WalletTransaction> findByDriverId(String driverId);
    List<WalletTransaction> findByOrderId(String orderId);
    List<WalletTransaction> findByTransactionType(String transactionType);
    Optional<WalletTransaction> findFirstByDriverIdAndOrderIdAndTransactionType(String driverId, String orderId, String transactionType);
    boolean existsByDriverIdAndOrderIdAndTransactionType(String driverId, String orderId, String transactionType);
}

