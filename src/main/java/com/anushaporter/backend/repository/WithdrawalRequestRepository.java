package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, String> {
    List<WithdrawalRequest> findByDriverIdOrderByRequestedAtDesc(String driverId);
    List<WithdrawalRequest> findByDriverIdAndStatus(String driverId, String status);
    List<WithdrawalRequest> findByStatus(String status);
}
