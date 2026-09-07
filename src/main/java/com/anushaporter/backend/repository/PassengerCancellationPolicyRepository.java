package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PassengerCancellationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PassengerCancellationPolicyRepository extends JpaRepository<PassengerCancellationPolicy, Long> {
    Optional<PassengerCancellationPolicy> findFirstByServiceCodeAndActiveTrue(String serviceCode);
    Optional<PassengerCancellationPolicy> findFirstByActiveTrue();
}
