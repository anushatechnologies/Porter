package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "driver_face_embeddings", indexes = {
    @Index(name = "idx_face_driver_id", columnList = "driver_id"),
    @Index(name = "idx_face_status", columnList = "status")
})
public class DriverFaceEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Lob
    @Column(name = "embedding_vector", columnDefinition = "CLOB")
    private String embeddingVector;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "face_hash", length = 64)
    private String faceHash;

    @Column(name = "liveness_score")
    private Double livenessScore;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt = LocalDateTime.now();

    @Column(name = "last_authenticated_at")
    private LocalDateTime lastAuthenticatedAt;

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "photo_url")
    private String photoUrl;

    public DriverFaceEmbedding() {}

    public DriverFaceEmbedding(Long driverId, String embeddingVector, Integer embeddingDimension, String faceHash, Double livenessScore, String photoUrl) {
        this.driverId = driverId;
        this.embeddingVector = embeddingVector;
        this.embeddingDimension = embeddingDimension;
        this.faceHash = faceHash;
        this.livenessScore = livenessScore;
        this.photoUrl = photoUrl;
        this.status = "ACTIVE";
        this.registeredAt = LocalDateTime.now();
    }
}
