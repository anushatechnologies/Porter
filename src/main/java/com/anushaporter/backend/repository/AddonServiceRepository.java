package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.AddonService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddonServiceRepository extends JpaRepository<AddonService, Long> {
    Optional<AddonService> findByAddonId(String addonId);
    List<AddonService> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<AddonService> findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(String category);
    List<AddonService> findAllByOrderByDisplayOrderAsc();
}
