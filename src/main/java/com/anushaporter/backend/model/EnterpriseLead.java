package com.anushaporter.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "enterprise_leads")
public class EnterpriseLead {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String source;
    private String companyName;
    private String contactPerson;
    private String phone;
    private String email;
    private LocalDateTime createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getSource() { return source; } public void setSource(String v) { source = v; }
    public String getCompanyName() { return companyName; } public void setCompanyName(String v) { companyName = v; }
    public String getContactPerson() { return contactPerson; } public void setContactPerson(String v) { contactPerson = v; }
    public String getPhone() { return phone; } public void setPhone(String v) { phone = v; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}
