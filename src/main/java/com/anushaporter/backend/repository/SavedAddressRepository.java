package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.SavedAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedAddressRepository extends JpaRepository<SavedAddress, Long> {
    List<SavedAddress> findByUserEmailOrderByCreatedAtDesc(String userEmail);
}
