package com.anushaporter.backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonAlias;

@Entity
@Table(name = "gst_details")
public class GstDetails {

    @Id
    private String id;

    private String gstin;
    @JsonAlias("companyName")
    private String businessName;
    @JsonAlias("registeredAddress")
    private String billingAddress;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
    }
}
