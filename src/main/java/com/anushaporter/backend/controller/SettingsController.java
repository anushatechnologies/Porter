package com.anushaporter.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @GetMapping
    public List<Object> getSettings() {
        return Collections.emptyList();
    }
}
