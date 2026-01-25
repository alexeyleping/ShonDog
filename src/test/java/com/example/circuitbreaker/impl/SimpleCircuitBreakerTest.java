package com.example.circuitbreaker.impl;

import com.example.circuitbreaker.CircuitState;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SimpleCircuitBreakerTest {

    @Inject
    SimpleCircuitBreaker circuitBreaker;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private String uniqueUrl() {
        return "http://test-server-" + counter.incrementAndGet() + ":8080";
    }

    @Test
    void testInitialStateIsClosed() {
        String url = uniqueUrl();

        // When: проверяем новый сервер
        boolean isOpen = circuitBreaker.isOpen(url);

        // Then: circuit закрыт
        assertFalse(isOpen);
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState(url));
    }

    @Test
    void testCircuitStaysClosedBeforeThreshold() {
        String url = uniqueUrl();

        // Given: сервер в состоянии CLOSED
        circuitBreaker.isOpen(url);

        // When: записываем 2 ошибки (меньше threshold=3)
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);

        // Then: circuit всё ещё закрыт
        assertFalse(circuitBreaker.isOpen(url));
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState(url));
    }

    @Test
    void testCircuitOpensAfterThresholdFailures() {
        String url = uniqueUrl();

        // Given: сервер в состоянии CLOSED
        circuitBreaker.isOpen(url);

        // When: записываем 3 ошибки (= threshold)
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);

        // Then: circuit открыт
        assertEquals(CircuitState.OPEN, circuitBreaker.getState(url));
    }

    @Test
    void testOpenCircuitBlocksRequests() {
        String url = uniqueUrl();

        // Given: circuit открыт
        circuitBreaker.isOpen(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);

        // When: проверяем isOpen сразу после открытия
        boolean isOpen = circuitBreaker.isOpen(url);

        // Then: запросы блокируются
        assertTrue(isOpen);
    }

    @Test
    void testCircuitTransitionsToHalfOpenAfterTimeout() throws InterruptedException {
        String url = uniqueUrl();

        // Given: circuit открыт
        circuitBreaker.isOpen(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        assertEquals(CircuitState.OPEN, circuitBreaker.getState(url));

        // When: ждём таймаут (в тестах 100ms)
        Thread.sleep(150);
        boolean isOpen = circuitBreaker.isOpen(url);

        // Then: circuit перешёл в HALF_OPEN, запросы разрешены
        assertFalse(isOpen);
        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState(url));
    }

    @Test
    void testSuccessInHalfOpenCloses() throws InterruptedException {
        String url = uniqueUrl();

        // Given: circuit в состоянии HALF_OPEN
        circuitBreaker.isOpen(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        Thread.sleep(150);
        circuitBreaker.isOpen(url); // триггерим переход в HALF_OPEN
        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState(url));

        // When: записываем успех
        circuitBreaker.recordSuccess(url);

        // Then: circuit закрыт
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState(url));
        assertFalse(circuitBreaker.isOpen(url));
    }

    @Test
    void testFailureInHalfOpenOpens() throws InterruptedException {
        String url = uniqueUrl();

        // Given: circuit в состоянии HALF_OPEN
        circuitBreaker.isOpen(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        Thread.sleep(150);
        circuitBreaker.isOpen(url);
        assertEquals(CircuitState.HALF_OPEN, circuitBreaker.getState(url));

        // When: записываем ошибку
        circuitBreaker.recordFailure(url);

        // Then: circuit снова открыт
        assertEquals(CircuitState.OPEN, circuitBreaker.getState(url));
    }

    @Test
    void testSuccessResetsFailureCount() {
        String url = uniqueUrl();

        // Given: сервер с 2 ошибками
        circuitBreaker.isOpen(url);
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);

        // When: записываем успех
        circuitBreaker.recordSuccess(url);

        // Then: счётчик сброшен, нужно снова 3 ошибки для открытия
        circuitBreaker.recordFailure(url);
        circuitBreaker.recordFailure(url);
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState(url));

        circuitBreaker.recordFailure(url);
        assertEquals(CircuitState.OPEN, circuitBreaker.getState(url));
    }
}
