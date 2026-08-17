package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.DriverWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverWalletRepository extends JpaRepository<DriverWallet, String> {
    Optional<DriverWallet> findByDriverId(String driverId);
}
