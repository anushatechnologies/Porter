package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.AppUser;
import com.anushaporter.backend.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appusers")

public class AppUserController {
    @Autowired
    private AppUserRepository repository;

    @GetMapping
    public List<AppUser> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public AppUser create(@RequestBody AppUser entity) {
        return repository.save(entity);
    }
}
