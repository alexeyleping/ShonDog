package com.example.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;

@ConfigMapping(prefix = "app")
public interface AppConfig {
    List<BackendConfig> backends();

    interface BackendConfig {
        String url();
    }
}
