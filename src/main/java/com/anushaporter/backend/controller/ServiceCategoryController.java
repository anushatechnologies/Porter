package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.ServiceCategory;
import com.anushaporter.backend.repository.ServiceCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/categories", "/api/admin/categories"})
public class ServiceCategoryController {

    @Autowired
    private ServiceCategoryRepository categoryRepository;

    /**
     * GET /api/categories OR GET /api/admin/categories
     * Returns list of service categories sorted by display order.
     */
    @GetMapping
    public ResponseEntity<?> getCategories(@RequestParam(required = false, defaultValue = "false") boolean all) {
        List<ServiceCategory> categories;
        if (all) {
            categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        } else {
            categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        }

        if (categories.isEmpty()) {
            categories = CustomerServiceController.getDefaultCategories();
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", categories.size(),
                "categories", categories
        ));
    }

    /**
     * GET /api/categories/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCategoryById(@PathVariable String id) {
        ServiceCategory category = findByIdOrSlug(id);
        if (category == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "Category not found: " + id
            ));
        }
        return ResponseEntity.ok(Map.of("success", true, "category", category));
    }

    /**
     * POST /api/admin/categories
     */
    @PostMapping
    public ResponseEntity<?> createCategory(@RequestBody ServiceCategory category) {
        if (category.getSlug() == null || category.getSlug().isBlank()) {
            if (category.getName() != null) {
                category.setSlug(category.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", ""));
            }
        }
        ServiceCategory saved = categoryRepository.save(category);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Category created successfully",
                "category", saved
        ));
    }

    /**
     * PUT /api/admin/categories/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable String id, @RequestBody ServiceCategory updated) {
        ServiceCategory existing = findByIdOrSlug(id);
        if (existing == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Category not found: " + id));
        }

        if (updated.getName() != null) existing.setName(updated.getName());
        if (updated.getSlug() != null) existing.setSlug(updated.getSlug());
        if (updated.getIcon() != null) existing.setIcon(updated.getIcon());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getDisplayOrder() != null) existing.setDisplayOrder(updated.getDisplayOrder());
        if (updated.getIsActive() != null) existing.setIsActive(updated.getIsActive());

        ServiceCategory saved = categoryRepository.save(existing);
        return ResponseEntity.ok(Map.of("success", true, "message", "Category updated successfully", "category", saved));
    }

    /**
     * PATCH /api/admin/categories/{id}/toggle-status
     */
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<?> toggleCategoryStatus(@PathVariable String id) {
        ServiceCategory existing = findByIdOrSlug(id);
        if (existing == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Category not found: " + id));
        }

        existing.setIsActive(!Boolean.TRUE.equals(existing.getIsActive()));
        ServiceCategory saved = categoryRepository.save(existing);
        return ResponseEntity.ok(Map.of("success", true, "isActive", saved.getIsActive(), "category", saved));
    }

    /**
     * DELETE /api/admin/categories/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable String id) {
        ServiceCategory existing = findByIdOrSlug(id);
        if (existing == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Category not found: " + id));
        }
        categoryRepository.delete(existing);
        return ResponseEntity.ok(Map.of("success", true, "message", "Category deleted successfully"));
    }

    private ServiceCategory findByIdOrSlug(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        try {
            Long numId = Long.parseLong(identifier);
            Optional<ServiceCategory> opt = categoryRepository.findById(numId);
            if (opt.isPresent()) return opt.get();
        } catch (NumberFormatException ignored) {}

        return categoryRepository.findFirstBySlugIgnoreCase(identifier).orElse(null);
    }
}
