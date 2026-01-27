package com.example.cache.impl;

import com.example.cache.CachedResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class InMemoryResponseCacheTest {

    @Inject
    InMemoryResponseCache cache;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        cache.clear();
    }

    private String uniqueKey() {
        return "/api/resource/" + counter.incrementAndGet();
    }

    private CachedResponse createCachedResponse(int statusCode, String body) {
        CachedResponse response = new CachedResponse();
        response.setStatusCode(statusCode);
        response.setBody(body);
        response.setHeaders(Map.of("Content-Type", "text/plain"));
        return response;
    }

    @Test
    void testPutAndGet() {
        // Given
        String key = uniqueKey();
        CachedResponse response = createCachedResponse(200, "Hello");

        // When
        cache.put(key, response);
        Optional<CachedResponse> result = cache.get(key);

        // Then
        assertTrue(result.isPresent());
        assertEquals(200, result.get().getStatusCode());
        assertEquals("Hello", result.get().getBody());
        assertEquals("text/plain", result.get().getHeaders().get("Content-Type"));
    }

    @Test
    void testGetNonExistentKey_ReturnsEmpty() {
        // Given
        String key = uniqueKey();

        // When
        Optional<CachedResponse> result = cache.get(key);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testTtlExpired_ReturnsEmpty() throws InterruptedException {
        // Given: TTL в тестах = 2s
        String key = uniqueKey();
        CachedResponse response = createCachedResponse(200, "Expiring");
        cache.put(key, response);

        // When: ждём дольше TTL
        Thread.sleep(2100);
        Optional<CachedResponse> result = cache.get(key);

        // Then: запись устарела
        assertTrue(result.isEmpty());
    }

    @Test
    void testTtlNotExpired_ReturnsValue() throws InterruptedException {
        // Given: TTL в тестах = 2s
        String key = uniqueKey();
        CachedResponse response = createCachedResponse(200, "Fresh");
        cache.put(key, response);

        // When: проверяем до истечения TTL
        Thread.sleep(500);
        Optional<CachedResponse> result = cache.get(key);

        // Then: запись ещё актуальна
        assertTrue(result.isPresent());
        assertEquals("Fresh", result.get().getBody());
    }

    @Test
    void testEvict_RemovesEntry() {
        // Given
        String key = uniqueKey();
        cache.put(key, createCachedResponse(200, "To be evicted"));

        // When
        cache.evict(key);
        Optional<CachedResponse> result = cache.get(key);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testEvict_NonExistentKey_NoError() {
        // Given
        String key = uniqueKey();

        // When & Then: не бросает исключение
        assertDoesNotThrow(() -> cache.evict(key));
    }

    @Test
    void testClear_RemovesAllEntries() {
        // Given
        String key1 = uniqueKey();
        String key2 = uniqueKey();
        cache.put(key1, createCachedResponse(200, "First"));
        cache.put(key2, createCachedResponse(200, "Second"));

        // When
        cache.clear();

        // Then
        assertTrue(cache.get(key1).isEmpty());
        assertTrue(cache.get(key2).isEmpty());
    }

    @Test
    void testMaxSize_EvictsOldestEntry() throws InterruptedException {
        // Given: заполняем кеш до максимума (100 в тестах)
        String oldestKey = uniqueKey();
        cache.put(oldestKey, createCachedResponse(200, "Oldest"));
        Thread.sleep(10); // чтобы cachedAt отличался

        for (int i = 1; i < 100; i++) {
            cache.put(uniqueKey(), createCachedResponse(200, "Entry " + i));
        }

        // When: добавляем 101-ю запись
        String newKey = uniqueKey();
        cache.put(newKey, createCachedResponse(200, "New entry"));

        // Then: самая старая запись вытеснена, новая есть
        assertTrue(cache.get(oldestKey).isEmpty(), "Oldest entry should be evicted");
        assertTrue(cache.get(newKey).isPresent(), "New entry should be present");
    }

    @Test
    void testPutSetsTimestamp() {
        // Given
        String key = uniqueKey();
        CachedResponse response = createCachedResponse(200, "Timestamped");

        // When
        cache.put(key, response);
        Optional<CachedResponse> result = cache.get(key);

        // Then
        assertTrue(result.isPresent());
        assertNotNull(result.get().getCachedAt());
    }

    @Test
    void testPutOverwritesExistingEntry() {
        // Given
        String key = uniqueKey();
        cache.put(key, createCachedResponse(200, "Original"));

        // When
        cache.put(key, createCachedResponse(200, "Updated"));
        Optional<CachedResponse> result = cache.get(key);

        // Then
        assertTrue(result.isPresent());
        assertEquals("Updated", result.get().getBody());
    }
}
