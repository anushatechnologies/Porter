package com.anushaporter.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "booking_drafts")
public class BookingDraft {

    @Id
    private String id; // draftId

    private String status;

    @Column(columnDefinition = "TEXT")
    private String payload;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
