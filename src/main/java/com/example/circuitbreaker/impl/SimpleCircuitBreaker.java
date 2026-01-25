package com.example.circuitbreaker.impl;

import com.example.circuitbreaker.CircuitBreaker;
import com.example.circuitbreaker.CircuitState;
import com.example.config.AppConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SimpleCircuitBreaker implements CircuitBreaker {

    @Inject
    AppConfig appConfig;

    private int failureThreshold;
    private Duration openDuration;

    @PostConstruct
    void init() {
        failureThreshold = appConfig.circuitBreaker().failureThreshold();
        openDuration = appConfig.circuitBreaker().openDuration();
    }

    ConcurrentHashMap<String, ServerCircuitState> serverStates = new ConcurrentHashMap<>();

    @Override
    public boolean isOpen(String serverUrl) {
        ServerCircuitState serverCircuitState = serverStates.get(serverUrl);
        if (serverCircuitState == null) {
            serverCircuitState = new ServerCircuitState();
            serverCircuitState.setFailureCount(0);
            serverCircuitState.setState(CircuitState.CLOSED);
            serverStates.put(serverUrl, serverCircuitState);
            return false;
        }

        if (serverCircuitState.getState() == CircuitState.OPEN) {
            Duration elapsed = Duration.between(serverCircuitState.getLastFailureTime(), LocalDateTime.now());
            if (elapsed.compareTo(openDuration) >= 0) {
                serverCircuitState.setState(CircuitState.HALF_OPEN);
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void recordSuccess(String serverUrl) {
        ServerCircuitState serverCircuitState = serverStates.get(serverUrl);
        serverCircuitState.setState(CircuitState.CLOSED);
        serverCircuitState.setFailureCount(0);
        serverStates.put(serverUrl, serverCircuitState);
    }

    @Override
    public void recordFailure(String serverUrl) {
        ServerCircuitState serverCircuitState = serverStates.get(serverUrl);
        int failureCount = serverCircuitState.getFailureCount();
        failureCount++;
        serverCircuitState.setFailureCount(failureCount);
        serverCircuitState.setLastFailureTime(LocalDateTime.now());
        if (failureCount >= failureThreshold) {
            serverCircuitState.setState(CircuitState.OPEN);
        }
        serverStates.put(serverUrl, serverCircuitState);
    }

    @Override
    public CircuitState getState(String serverUrl) {
        ServerCircuitState serverCircuitState = serverStates.get(serverUrl);
        return serverCircuitState.getState();
    }

    private static class ServerCircuitState {
        CircuitState state;
        int failureCount;
        LocalDateTime lastFailureTime;

        public CircuitState getState() {
            return state;
        }

        public void setState(CircuitState state) {
            this.state = state;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public void setFailureCount(int failureCount) {
            this.failureCount = failureCount;
        }

        public LocalDateTime getLastFailureTime() {
            return lastFailureTime;
        }

        public void setLastFailureTime(LocalDateTime lastFailureTime) {
            this.lastFailureTime = lastFailureTime;
        }
    }
}
