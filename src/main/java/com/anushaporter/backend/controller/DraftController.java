package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.BookingDraft;
import com.anushaporter.backend.repository.BookingDraftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    @Autowired
    private BookingDraftRepository repository;

    @GetMapping("/{id}")
    public ResponseEntity<BookingDraft> getDraft(@PathVariable String id) {
        Optional<BookingDraft> draft = repository.findById(id);
        return draft.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<BookingDraft> createDraft(@RequestBody Map<String, Object> payload) {
        BookingDraft draft = new BookingDraft();
        draft.setId("draft_" + System.currentTimeMillis());
        draft.setStatus("draft");
        // Store the rest of the payload if needed, currently frontend just expects draftId and status
        return ResponseEntity.ok(repository.save(draft));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookingDraft> updateDraft(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        Optional<BookingDraft> draftOpt = repository.findById(id);
        if (draftOpt.isPresent()) {
            BookingDraft draft = draftOpt.get();
            // Update logic here
            return ResponseEntity.ok(repository.save(draft));
        }
        return ResponseEntity.notFound().build();
    }
}
