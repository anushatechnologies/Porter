package com.anushaporter.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.*;

@RestController
public class RootController {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.DriverRepository driverRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.AppUserRepository appUserRepository;

    @Autowired(required = false)
    private com.anushaporter.backend.repository.OrderRepository orderRepository;

    @GetMapping({"/", "/api", "/health", "/api/health", "/status", "/api/status"})
    public Map<String, Object> root() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "online");
        response.put("service", "Anusha Porter Backend API");
        response.put("version", "1.0.0");
        response.put("message", "Anusha Porter Backend API is running successfully on port 8080.");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("endpoints", java.util.List.of(
                "/api/health/db",
                "/api/drivers",
                "/api/driver/wallet",
                "/api/orders",
                "/api/payments",
                "/api/customer",
                "/api/settings/wallet"
        ));
        return response;
    }

    /**
     * Public DB Diagnostics endpoint
     * Tests live connection to AWS RDS / MySQL and reports table record counts.
     */
    @GetMapping({"/health/db", "/api/health/db", "/status/db", "/api/status/db"})
    public ResponseEntity<Map<String, Object>> dbHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            boolean isConnected = false;
            String dbProduct = "Unknown";
            String dbUrl = "Unknown";

            if (dataSource != null) {
                try (Connection conn = dataSource.getConnection()) {
                    isConnected = !conn.isClosed();
                    DatabaseMetaData meta = conn.getMetaData();
                    dbProduct = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
                    dbUrl = meta.getURL();
                    if (dbUrl != null && dbUrl.contains("password=")) {
                        dbUrl = dbUrl.replaceAll("password=[^&;]+", "password=****");
                    }
                }
            }

            long driverCount = driverRepository != null ? driverRepository.count() : 0;
            long userCount = appUserRepository != null ? appUserRepository.count() : 0;
            long orderCount = orderRepository != null ? orderRepository.count() : 0;

            response.put("status", isConnected ? "CONNECTED" : "DISCONNECTED");
            response.put("success", isConnected);
            response.put("databaseProduct", dbProduct);
            response.put("databaseUrl", dbUrl);
            response.put("totalDriversInDB", driverCount);
            response.put("totalUsersInDB", userCount);
            response.put("totalOrdersInDB", orderCount);
            response.put("timestamp", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            return ResponseEntity.status(500).body(response);
        }
    }
}

