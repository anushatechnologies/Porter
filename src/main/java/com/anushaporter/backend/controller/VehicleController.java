package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.Vehicle;
import com.anushaporter.backend.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")

public class VehicleController {
    @Autowired
    private VehicleRepository repository;

    @GetMapping
    public List<Vehicle> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Vehicle create(@RequestBody Vehicle entity) {
        return repository.save(entity);
    }
}
