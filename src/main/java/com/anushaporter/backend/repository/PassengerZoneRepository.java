package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.PassengerZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PassengerZoneRepository extends JpaRepository<PassengerZone, Long> {
    List<PassengerZone> findByCityAndActiveTrue(String city);
    List<PassengerZone> findByActiveTrue();
    List<PassengerZone> findByZoneTypeAndActiveTrue(String zoneType);
}
