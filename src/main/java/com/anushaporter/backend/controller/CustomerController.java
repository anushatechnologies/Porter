package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Customer;
import com.anushaporter.backend.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/customers")

public class CustomerController {
    @Autowired
    private CustomerRepository repository;

    @GetMapping
    public List<Customer> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Customer create(@RequestBody Customer entity) {
        return repository.save(entity);
    }

    @PostMapping("/{id}/topup")
    public ResponseEntity<Customer> topupCustomer(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
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
            return ResponseEntity.ok(repository.save(customer));
        }).orElse(ResponseEntity.notFound().build());
    }
}
