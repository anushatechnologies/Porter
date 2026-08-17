package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    List<ServiceCategory> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<ServiceCategory> findAllByOrderByDisplayOrderAsc();

    Optional<ServiceCategory> findBySlug(String slug);

    Optional<ServiceCategory> findFirstBySlugIgnoreCase(String slug);
}
