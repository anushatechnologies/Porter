package com.anushaporter.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "referrals")
public class Referral {

    @Id
    private String id;

    private String referralCode;
    private Integer totalInvites = 0;
    private Double totalRewards = 0.0;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public Integer getTotalInvites() {
        return totalInvites;
    }

    public void setTotalInvites(Integer totalInvites) {
        this.totalInvites = totalInvites;
    }

    public Double getTotalRewards() {
        return totalRewards;
    }

    public void setTotalRewards(Double totalRewards) {
        this.totalRewards = totalRewards;
    }
}
