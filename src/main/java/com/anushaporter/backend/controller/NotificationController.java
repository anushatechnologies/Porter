package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Notification;
import com.anushaporter.backend.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/notifications")

public class NotificationController {
    @Autowired
    private NotificationRepository repository;

    @GetMapping
    public List<Notification> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Notification create(@RequestBody Notification entity) {
        return repository.save(entity);
    }

    @PostMapping("/broadcast")
    public ResponseEntity<java.util.Map<String, Object>> broadcast(@RequestBody Notification entity) {
        Notification saved = repository.save(entity);
        return ResponseEntity.ok(java.util.Map.of("success", (Object) true, "notification", (Object) saved));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<java.util.Map<String, Object>> markRead(@PathVariable Long id) {
        return repository.findById(id).map(notification -> {
            notification.setReadStatus(true);
            Notification saved = repository.save(notification);
            return ResponseEntity.ok(java.util.Map.of("success", (Object) true, "notification", (Object) saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/read-all")
    public ResponseEntity<java.util.Map<String, Object>> markAllRead() {
        List<Notification> all = repository.findAll();
        for (Notification n : all) {
            n.setReadStatus(true);
        }
        repository.saveAll(all);
        return ResponseEntity.ok(java.util.Map.of("success", (Object) true));
    }
}
