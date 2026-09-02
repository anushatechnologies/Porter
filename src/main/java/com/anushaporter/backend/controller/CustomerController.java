package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.model.Customer;
import com.anushaporter.backend.repository.AppUserRepository;
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

    @Autowired
    private AppUserRepository appUserRepository;

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

    /**
     * PUT/POST/PATCH /api/customers/{id}/wallet
     * Admin modifies customer wallet balance.
     * Supports action: "set" (default), "credit", "debit", "adjust".
     */
    @RequestMapping(value = {"/{id}/wallet", "/{id}/balance"}, method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> modifyCustomerWallet(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        Double amount = extractAmount(payload);
        if (amount == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Wallet amount is required"));
        }

        String action = (String) payload.getOrDefault("action", "set");
        Optional<Customer> customerOpt = repository.findById(id);
        Optional<AppUser> userOpt = appUserRepository.findById(id);

        if (customerOpt.isEmpty() && userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        double prev = 0.0;
        if (customerOpt.isPresent() && customerOpt.get().getWallet() != null) {
            prev = customerOpt.get().getWallet();
        } else if (userOpt.isPresent() && userOpt.get().getWalletBalance() != null) {
            prev = userOpt.get().getWalletBalance();
        }

        double newBalance = calculateNewBalance(prev, amount, action);

        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setWallet(newBalance);
            repository.save(customer);

            if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
                appUserRepository.findFirstByEmailOrderByIdDesc(customer.getEmail()).ifPresent(u -> {
                    u.setWalletBalance(newBalance);
                    appUserRepository.save(u);
                });
            }
        }

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            user.setWalletBalance(newBalance);
            appUserRepository.save(user);

            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                repository.findByEmail(user.getEmail()).ifPresent(c -> {
                    c.setWallet(newBalance);
                    repository.save(c);
                });
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", id,
                "action", action,
                "previousBalance", prev,
                "walletBalance", newBalance,
                "wallet", newBalance,
                "message", "Customer wallet updated successfully"
        ));
    }

    /**
     * POST /api/customers/{id}/topup
     * Top-up or adjust customer wallet. Supports explicit action or defaults to addition.
     */
    @PostMapping("/{id}/topup")
    public ResponseEntity<Map<String, Object>> topupCustomer(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<Customer> customerOpt = repository.findById(id);
        Optional<AppUser> userOpt = appUserRepository.findById(id);

        if (customerOpt.isEmpty() && userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Double amount = extractAmount(payload);
        if (amount == null) {
            amount = 0.0;
        }

        String action = (String) payload.getOrDefault("action", "credit");
        double prev = 0.0;
        if (customerOpt.isPresent() && customerOpt.get().getWallet() != null) {
            prev = customerOpt.get().getWallet();
        } else if (userOpt.isPresent() && userOpt.get().getWalletBalance() != null) {
            prev = userOpt.get().getWalletBalance();
        }

        double newBalance = calculateNewBalance(prev, amount, action);

        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            customer.setWallet(newBalance);
            repository.save(customer);

            if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
                appUserRepository.findFirstByEmailOrderByIdDesc(customer.getEmail()).ifPresent(u -> {
                    u.setWalletBalance(newBalance);
                    appUserRepository.save(u);
                });
            }
        }

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            user.setWalletBalance(newBalance);
            appUserRepository.save(user);

            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                repository.findByEmail(user.getEmail()).ifPresent(c -> {
                    c.setWallet(newBalance);
                    repository.save(c);
                });
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", id,
                "wallet", newBalance,
                "walletBalance", newBalance
        ));
    }

    private Double extractAmount(Map<String, Object> payload) {
        if (payload == null) return null;
        Object val = payload.get("walletBalance");
        if (val == null) val = payload.get("balance");
        if (val == null) val = payload.get("amount");
        if (val == null) val = payload.get("newBalance");
        if (val == null) val = payload.get("wallet");
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private double calculateNewBalance(double previousBalance, double amount, String action) {
        String act = (action != null && !action.isBlank()) ? action.trim().toLowerCase() : "set";
        double newBal;
        switch (act) {
            case "credit":
            case "add":
                newBal = previousBalance + Math.abs(amount);
                break;
            case "debit":
            case "deduct":
                newBal = previousBalance - Math.abs(amount);
                break;
            case "adjust":
                newBal = previousBalance + amount;
                break;
            case "set":
            default:
                newBal = amount;
                break;
        }
        return Math.round(newBal * 100.0) / 100.0;
    }
}

