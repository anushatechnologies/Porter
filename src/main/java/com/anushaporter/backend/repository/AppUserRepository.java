package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    // Select the newest row while legacy duplicate data is being cleaned up.
    Optional<AppUser> findFirstByEmailOrderByIdDesc(String email);
    Optional<AppUser> findFirstByPhoneOrderByIdDesc(String phone);
    Optional<AppUser> findFirstByOtpOrderByIdDesc(String otp);
    boolean existsByPhone(String phone);
}
