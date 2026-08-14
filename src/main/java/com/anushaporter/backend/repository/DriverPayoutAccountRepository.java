package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.DriverPayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverPayoutAccountRepository extends JpaRepository<DriverPayoutAccount, Long> {
    Optional<DriverPayoutAccount> findByDriverId(String driverId);
    Optional<DriverPayoutAccount> findByDriverIdAndIsPrimaryTrue(String driverId);
}
