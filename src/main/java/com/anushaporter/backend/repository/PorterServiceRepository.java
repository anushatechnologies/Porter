package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PorterService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PorterServiceRepository extends JpaRepository<PorterService, Long> {

    Optional<PorterService> findByServiceId(String serviceId);

    Optional<PorterService> findFirstByServiceIdIgnoreCase(String serviceId);

    List<PorterService> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<PorterService> findByIsActiveTrueAndCustomerAppVisibleTrueOrderByDisplayOrderAsc();

    List<PorterService> findAllByOrderByDisplayOrderAsc();

    List<PorterService> findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(String category);

    List<PorterService> findByCategoryIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc(String category);

    List<PorterService> findByCategoryIgnoreCaseAndIsActiveTrueAndCustomerAppVisibleTrueOrderByDisplayOrderAsc(String category);

    List<PorterService> findByCategoryIdAndIsActiveTrueAndCustomerAppVisibleTrueOrderByDisplayOrderAsc(String categoryId);

    List<PorterService> findByCategoryIgnoreCaseOrderByDisplayOrderAsc(String category);

    List<PorterService> findByNameContainingIgnoreCaseOrServiceIdContainingIgnoreCase(String name, String serviceId);
}
