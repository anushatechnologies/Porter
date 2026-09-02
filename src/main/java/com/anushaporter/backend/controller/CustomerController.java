package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Customer;
import com.anushaporter.backend.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    @Autowired
    private CustomerRepository repository;

    /**
     * GET /api/customers
     * Returns formatted list of customers for Admin Customers Directory module.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Customer> customers = repository.findAll();

        List<Map<String, Object>> items = customers.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName() != null ? c.getName() : "Customer");
            map.put("email", c.getEmail() != null ? c.getEmail() : "");
            map.put("phone", c.getPhone() != null ? c.getPhone() : "");
            map.put("walletBalance", c.getWallet() != null ? c.getWallet() : 0.0);
            map.put("wallet", c.getWallet() != null ? c.getWallet() : 0.0);
            map.put("totalOrders", c.getTotalOrders() != null ? c.getTotalOrders() : 0);
            map.put("avatar", "https://api.dicebear.com/7.x/initials/svg?seed=" + (c.getName() != null ? c.getName().replace(" ", "%20") : "Customer"));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    @PostMapping
    public Customer create(@RequestBody Customer entity) {
        return repository.save(entity);
    }

    @PostMapping("/{id}/topup")
    public ResponseEntity<Map<String, Object>> topupCustomer(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return repository.findById(id).map(customer -> {
            if (payload.containsKey("amount")) {
                try {
                    Double amount = Double.valueOf(payload.get("amount").toString());
                    Double currentWallet = customer.getWallet();
                    if (currentWallet == null) {
                        currentWallet = 0.0;
                    }
                    customer.setWallet(currentWallet + amount);
                } catch (NumberFormatException e) {
                    // Ignore parsing error
                }
            }
            Customer saved = repository.save(customer);
            return ResponseEntity.ok(Map.of("success", (Object) true, "wallet", (Object) (saved.getWallet() != null ? saved.getWallet() : 0.0)));
        }).orElse(ResponseEntity.notFound().build());
    }
}
