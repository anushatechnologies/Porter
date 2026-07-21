package com.anushaporter.backend.repository;

import com.anushaporter.backend.model.GlobalSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlobalSettingsRepository extends JpaRepository<GlobalSettings, Long> {
    Optional<GlobalSettings> findBySettingKey(String settingKey);
}
