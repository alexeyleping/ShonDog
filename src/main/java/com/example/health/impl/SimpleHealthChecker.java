package com.example.health.impl;

import com.example.client.HttpClientException;
import com.example.config.AppConfig;
import com.example.health.HealthChecker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SimpleHealthChecker implements HealthChecker {

    private ConcurrentHashMap<String, Boolean> liveServers = new ConcurrentHashMap<>();

    @Inject
    AppConfig appConfig;

    @Inject
    com.example.client.HttpClient httpClient;

    @Override
    public List<String> getHealthyServers() {
        List<String> backendConfigList = appConfig.backends().urls();
        for (String url : backendConfigList) {
            String fullUrl = url + appConfig.health().endpoint();
            try {
                checkHealth(fullUrl);
                liveServers.put(url, true);
            } catch (HttpClientException e) {
                liveServers.put(url, false);
            }
        }
        return liveServers.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
    }

    @Override
    public String checkHealth(String serverUrl) throws HttpClientException {
        return httpClient.get(serverUrl);
    }
}
