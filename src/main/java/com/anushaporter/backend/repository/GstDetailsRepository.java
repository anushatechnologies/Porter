package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.GstDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GstDetailsRepository extends JpaRepository<GstDetails, String> {
    Optional<GstDetails> findFirstByUserEmail(String userEmail);
}
