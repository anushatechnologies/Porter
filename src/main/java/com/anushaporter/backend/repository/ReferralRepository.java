package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, String> {
}
