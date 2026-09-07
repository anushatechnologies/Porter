package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.SurgeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurgeRuleRepository extends JpaRepository<SurgeRule, Long> {
    List<SurgeRule> findByActiveTrue();
    Optional<SurgeRule> findBySurgeTypeAndActiveTrue(String surgeType);
}
