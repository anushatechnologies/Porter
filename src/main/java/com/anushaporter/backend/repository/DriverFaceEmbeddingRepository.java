package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.DriverFaceEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverFaceEmbeddingRepository extends JpaRepository<DriverFaceEmbedding, Long> {

    Optional<DriverFaceEmbedding> findFirstByDriverIdAndStatusOrderByIdDesc(Long driverId, String status);

    List<DriverFaceEmbedding> findAllByDriverId(Long driverId);

    List<DriverFaceEmbedding> findAllByStatus(String status);

    void deleteAllByDriverId(Long driverId);
}
