package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    List<WalletTransaction> findByDriverIdOrderByCreatedAtDesc(String driverId);
}
