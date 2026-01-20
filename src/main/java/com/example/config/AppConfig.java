package com.example.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.List;

@ConfigMapping(prefix = "app")
public interface AppConfig {
    Backends backends();

    Health health();

    interface Backends {
        List<String> urls();
    }

    interface Health {
        @WithDefault("/health")
        String endpoint();

        @WithDefault("10s")
        Duration interval();
    }
}
