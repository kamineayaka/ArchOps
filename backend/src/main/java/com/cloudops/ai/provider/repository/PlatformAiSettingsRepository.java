package com.cloudops.ai.provider.repository;

import com.cloudops.ai.provider.domain.PlatformAiSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAiSettingsRepository extends JpaRepository<PlatformAiSettings, Short> {}
