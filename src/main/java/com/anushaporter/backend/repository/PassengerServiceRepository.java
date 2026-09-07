package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PassengerServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PassengerServiceRepository extends JpaRepository<PassengerServiceEntity, Long> {
    Optional<PassengerServiceEntity> findByServiceCode(String serviceCode);
    List<PassengerServiceEntity> findByActiveTrueOrderByDisplayOrderAsc();
    List<PassengerServiceEntity> findAllByOrderByDisplayOrderAsc();
}
