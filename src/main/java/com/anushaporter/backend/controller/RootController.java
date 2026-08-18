package com.anushaporter.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping({"/", "/api", "/health", "/api/health", "/status", "/api/status"})
    public Map<String, Object> root() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "online");
        response.put("service", "Anusha Porter Backend API");
        response.put("version", "1.0.0");
        response.put("message", "Anusha Porter Backend API is running successfully on port 8080.");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        response.put("endpoints", java.util.List.of(
                "/api/drivers",
                "/api/driver/wallet",
                "/api/orders",
                "/api/payments",
                "/api/customer",
                "/api/settings/wallet"
        ));
        return response;
    }
}
