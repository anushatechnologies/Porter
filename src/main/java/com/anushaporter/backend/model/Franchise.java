package com.anushaporter.backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "franchises")
public class Franchise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name; private String head; private String address; private Integer driversCount; private Integer dailyOrders; private String revenue; private String status;
}
