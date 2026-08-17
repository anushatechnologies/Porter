package com.anushaporter.backend.controller;

import com.anushaporter.backend.model.GlobalSettings;
import com.anushaporter.backend.repository.GlobalSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Autowired
    private GlobalSettingsRepository settingsRepo;

    @GetMapping
    public Map<String, String> getSettings() {
        Map<String, String> result = new java.util.HashMap<>();
        for (GlobalSettings s : settingsRepo.findAll()) {
            if (s.getSettingKey() != null) {
                result.put(s.getSettingKey(), s.getSettingValue() != null ? s.getSettingValue() : "");
            }
        }
        return result;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody Map<String, String> settings) {
        settings.forEach((key, value) -> {
            Optional<GlobalSettings> existing = settingsRepo.findBySettingKey(key);
            GlobalSettings s = existing.orElseGet(GlobalSettings::new);
            s.setSettingKey(key);
            s.setSettingValue(value);
            settingsRepo.save(s);
        });
        return ResponseEntity.ok(Map.of("success", (Object) true));
    }
}
