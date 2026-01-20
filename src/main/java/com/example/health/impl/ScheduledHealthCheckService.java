package com.example.health.impl;

import com.example.client.HttpClientException;
import com.example.config.AppConfig;
import com.example.health.HealthChecker;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ScheduledHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledHealthCheckService.class);
    @Inject
    HealthChecker healthChecker;

    @Inject
    AppConfig appConfig;

    private volatile List<String> cachedHealthyServers = new ArrayList<>();

    public List<String> getCachedHealthyServers() {
        return cachedHealthyServers;
    }

    @Scheduled(every = "{app.health.interval}")
    public void checkHealth() {
        log.info("Running scheduled health check...");
        List<String> healthyServers = new ArrayList<>();
        List<String> backendConfigList = appConfig.backends().urls();
        for (String url : backendConfigList) {
            String fullUrl = url + appConfig.health().endpoint();
            try {
                healthChecker.checkHealth(fullUrl);
                healthyServers.add(url);
            } catch (HttpClientException ignored) {
            }
        }
        if(healthyServers.isEmpty()) {
            log.warn("No live servers found");
        } else {
            log.info("Healthy servers: {}", healthyServers);
        }
        cachedHealthyServers = List.copyOf(healthyServers);
    }
}
