package com.example.cache.impl;

import com.example.cache.CachedResponse;
import com.example.cache.ResponseCache;
import com.example.config.AppConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryResponseCache implements ResponseCache {

    @Inject
    AppConfig appConfig;

    private Duration ttl;
    private int maxSize;

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        this.ttl = appConfig.cache().ttl();
        this.maxSize = appConfig.cache().maxSize();
    }

    @Override
    public Optional<CachedResponse> get(String key) {
        CachedResponse cachedResponse = cache.get(key);
        if (cachedResponse == null) {
            return Optional.empty();
        }

        // Проверяем TTL: если запись устарела — удаляем и возвращаем empty
        Duration age = Duration.between(cachedResponse.getCachedAt(), Instant.now());
        if (age.compareTo(ttl) >= 0) {
            cache.remove(key);
            return Optional.empty();
        }

        return Optional.of(cachedResponse);
    }

    @Override
    public void put(String key, CachedResponse response) {
        // Если кеш полон — удаляем самую старую запись
        if (cache.size() >= maxSize) {
            evictOldest();
        }

        response.setCachedAt(Instant.now());
        cache.put(key, response);
    }

    @Override
    public void evict(String key) {
        cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    /**
     * Удаляет самую старую запись из кеша (с минимальным cachedAt)
     */
    private void evictOldest() {
        String oldestKey = null;
        Instant oldestTime = null;

        for (Map.Entry<String, CachedResponse> entry : cache.entrySet()) {
            Instant cachedAt = entry.getValue().getCachedAt();
            if (cachedAt != null && (oldestTime == null || cachedAt.isBefore(oldestTime))) {
                oldestTime = cachedAt;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }
}
