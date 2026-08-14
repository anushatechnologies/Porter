package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.CommissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {
    Optional<CommissionRule> findByRuleId(String ruleId);
    Optional<CommissionRule> findFirstByServiceCategoryIgnoreCaseAndIsActiveTrueOrderByIdDesc(String serviceCategory);
    Optional<CommissionRule> findFirstByServiceCategoryIgnoreCaseAndIsActiveTrue(String serviceCategory);
    List<CommissionRule> findByIsActiveTrue();
}
