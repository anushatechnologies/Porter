package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.BookingDraft;
import com.anushaporter.backend.repository.BookingDraftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    @Autowired
    private BookingDraftRepository repository;

    @Autowired private ObjectMapper objectMapper;

    @GetMapping("/{id}")
    public ResponseEntity<BookingDraft> getDraft(@PathVariable String id) {
        Optional<BookingDraft> draft = repository.findById(id);
        return draft.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createDraft(@RequestBody Map<String, Object> payload) {
        BookingDraft draft = new BookingDraft();
        draft.setId("draft_" + System.currentTimeMillis());
        draft.setStatus("draft");
        draft.setExpiresAt(LocalDateTime.now().plusHours(24));
        draft.setPayload(writePayload(payload));
        repository.save(draft);
        return ResponseEntity.ok(Map.of("draftId", draft.getId(), "expiresAt", draft.getExpiresAt(), "status", draft.getStatus()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDraft(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        Optional<BookingDraft> draftOpt = repository.findById(id);
        if (draftOpt.isPresent()) {
            BookingDraft draft = draftOpt.get();
            draft.setPayload(writePayload(payload));
            draft.setExpiresAt(LocalDateTime.now().plusHours(24));
            repository.save(draft);
            return ResponseEntity.ok(Map.of("draftId", draft.getId(), "expiresAt", draft.getExpiresAt(), "status", draft.getStatus()));
        }
        return ResponseEntity.notFound().build();
    }

    private String writePayload(Map<String, Object> payload) {
        try { return objectMapper.writeValueAsString(payload); } catch (Exception e) { return "{}"; }
    }
}
