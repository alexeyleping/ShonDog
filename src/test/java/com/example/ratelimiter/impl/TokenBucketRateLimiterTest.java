package com.example.ratelimiter.impl;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TokenBucketRateLimiterTest {

    @Inject
    TokenBucketRateLimiter rateLimiter;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private String uniqueClientId() {
        return "192.168.1." + counter.incrementAndGet();
    }

    @Test
    void testAllowRequest_FirstRequestAllowed() {
        // Given
        String clientId = uniqueClientId();

        // When
        boolean allowed = rateLimiter.allowRequest(clientId);

        // Then
        assertTrue(allowed);
    }

    @Test
    void testAllowRequest_MultipleRequestsAllowed() {
        // Given
        String clientId = uniqueClientId();
        int requestsPerMinute = 60;

        // When & Then
        for (int i = 0; i < requestsPerMinute; i++) {
            assertTrue(rateLimiter.allowRequest(clientId), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void testAllowRequest_ExceedLimitBlocked() {
        // Given
        String clientId = uniqueClientId();
        int requestsPerMinute = 60;

        // When
        for (int i = 0; i < requestsPerMinute; i++) {
            rateLimiter.allowRequest(clientId);
        }

        // Then
        boolean allowed = rateLimiter.allowRequest(clientId);
        assertFalse(allowed, "Request exceeding limit should be blocked");
    }

    @Test
    void testGetRemaining_InitiallyFullCapacity() {
        // Given
        String clientId = uniqueClientId();

        // When
        int remaining = rateLimiter.getRemaining(clientId);

        // Then
        assertEquals(60, remaining);
    }

    @Test
    void testGetRemaining_DecreasesAfterRequest() {
        // Given
        String clientId = uniqueClientId();

        // When
        rateLimiter.allowRequest(clientId);
        int remaining = rateLimiter.getRemaining(clientId);

        // Then
        assertEquals(59, remaining);
    }

    @Test
    void testGetRemaining_AfterMultipleRequests() {
        // Given
        String clientId = uniqueClientId();

        // When
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest(clientId);
        }
        int remaining = rateLimiter.getRemaining(clientId);

        // Then
        assertEquals(50, remaining);
    }

    @Test
    void testGetRemaining_ZeroAfterExceedingLimit() {
        // Given
        String clientId = uniqueClientId();
        int requestsPerMinute = 60;

        // When
        for (int i = 0; i < requestsPerMinute + 10; i++) {
            rateLimiter.allowRequest(clientId);
        }
        int remaining = rateLimiter.getRemaining(clientId);

        // Then
        assertEquals(0, remaining);
    }

    @Test
    void testGetResetTime_ReturnsValidTimestamp() {
        // Given
        String clientId = uniqueClientId();
        long now = System.currentTimeMillis() / 1000;

        // When
        rateLimiter.allowRequest(clientId);
        long resetTime = rateLimiter.getResetTime(clientId);

        // Then
        assertTrue(resetTime >= now, "Reset time should be in the future or now");
        assertTrue(resetTime <= now + 60, "Reset time should be within 60 seconds");
    }

    @Test
    void testGetResetTime_InFutureWhenTokensExhausted() {
        // Given
        String clientId = uniqueClientId();
        int requestsPerMinute = 60;

        // When
        for (int i = 0; i < requestsPerMinute; i++) {
            rateLimiter.allowRequest(clientId);
        }
        long resetTime = rateLimiter.getResetTime(clientId);
        long now = System.currentTimeMillis() / 1000;

        // Then
        assertTrue(resetTime > now, "Reset time should be in the future when tokens are exhausted");
    }

    @Test
    void testTokenRefill_AllowsRequestsAfterWait() throws InterruptedException {
        // Given
        String clientId = uniqueClientId();
        // С конфигурацией 60 requests/minute = 1 token/sec

        // When: используем все токены
        for (int i = 0; i < 60; i++) {
            rateLimiter.allowRequest(clientId);
        }
        assertFalse(rateLimiter.allowRequest(clientId), "Should be blocked initially");

        // Wait for ~1.1 seconds to refill ~1.1 tokens (enough for 1 request)
        Thread.sleep(1100);

        // Then
        assertTrue(rateLimiter.allowRequest(clientId), "Should allow request after refill");
    }

    @Test
    void testDifferentClients_IndependentLimits() {
        // Given
        String client1 = uniqueClientId();
        String client2 = uniqueClientId();
        int requestsPerMinute = 60;

        // When
        for (int i = 0; i < requestsPerMinute; i++) {
            rateLimiter.allowRequest(client1);
        }

        // Then
        assertFalse(rateLimiter.allowRequest(client1), "Client 1 should be blocked");
        assertTrue(rateLimiter.allowRequest(client2), "Client 2 should still be allowed");
    }

    @Test
    void testGetRemaining_NewClientReturnsFullCapacity() {
        // Given
        String clientId = uniqueClientId();

        // When
        int remaining = rateLimiter.getRemaining(clientId);

        // Then
        assertEquals(60, remaining);
    }

    @Test
    void testGetResetTime_NewClientReturnsCurrentTime() {
        // Given
        String clientId = uniqueClientId();
        long now = System.currentTimeMillis() / 1000;

        // When
        long resetTime = rateLimiter.getResetTime(clientId);

        // Then
        assertTrue(Math.abs(resetTime - now) <= 1, "Reset time for new client should be approximately now");
    }
}
