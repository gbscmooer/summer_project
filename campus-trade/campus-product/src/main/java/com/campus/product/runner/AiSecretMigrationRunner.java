package com.campus.product.runner;

import com.campus.product.ai.service.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Encrypts any legacy plaintext AI key during startup, before serving requests. */
@Component
@Order(0)
@RequiredArgsConstructor
public class AiSecretMigrationRunner implements CommandLineRunner {

    private final AiSettingsService aiSettingsService;

    @Override
    public void run(String... args) {
        aiSettingsService.resolve();
    }
}
