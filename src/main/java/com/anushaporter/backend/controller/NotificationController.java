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
    public ResponseEntity<Notification> broadcast(@RequestBody Notification entity) {
        return ResponseEntity.ok(repository.save(entity));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Notification> markRead(@PathVariable Long id) {
        return repository.findById(id).map(notification -> {
            // Assume there is a boolean or string status field. We'll just return it.
            return ResponseEntity.ok(repository.save(notification));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        // Just mock it for now
        return ResponseEntity.ok().build();
    }
}
