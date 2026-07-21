package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Franchise;
import com.anushaporter.backend.repository.FranchiseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/franchises")

public class FranchiseController {
    @Autowired
    private FranchiseRepository repository;

    @GetMapping
    public List<Franchise> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Franchise create(@RequestBody Franchise entity) {
        return repository.save(entity);
    }
}
