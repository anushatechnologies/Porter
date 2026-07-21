package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PricingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricingHistoryRepository extends JpaRepository<PricingHistory, Long> {
    List<PricingHistory> findAllByOrderByUpdatedTimeDesc();
}
