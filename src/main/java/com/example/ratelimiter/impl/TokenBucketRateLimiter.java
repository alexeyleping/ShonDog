package com.example.ratelimiter.impl;

import com.example.config.AppConfig;
import com.example.ratelimiter.RateLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TokenBucketRateLimiter implements RateLimiter {

    @Inject
    AppConfig config;

    private int requestsPerMinute;
    private double tokensPerSecond;

    @PostConstruct
    void init() {
        this.requestsPerMinute = config.rateLimit().requestsPerMinute();
        this.tokensPerSecond = requestsPerMinute / 60.0;
    }

    ConcurrentHashMap<String, ClientHealth> clientStates = new ConcurrentHashMap<>();

    @Override
    public boolean allowRequest(String clientId) {
        ClientHealth clientHealth = clientStates.computeIfAbsent(
                clientId,
                k -> new ClientHealth(requestsPerMinute)
        );

        ClientHealth.Bucket bucket = clientHealth.getBucket();
        refill(bucket, tokensPerSecond);

        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return true;
        }

        return false;
    }

    @Override
    public int getRemaining(String clientId) {
        ClientHealth clientHealth = clientStates.get(clientId);
        if (clientHealth == null) {
            return requestsPerMinute;
        }

        ClientHealth.Bucket bucket = clientHealth.getBucket();
        refill(bucket, tokensPerSecond);

        return (int) Math.floor(bucket.tokens);
    }

    @Override
    public long getResetTime(String clientId) {
        ClientHealth clientHealth = clientStates.get(clientId);
        if (clientHealth == null) {
            return Instant.now().getEpochSecond();
        }

        ClientHealth.Bucket bucket = clientHealth.getBucket();
        refill(bucket, tokensPerSecond);

        if (bucket.tokens >= bucket.capacity) {
            return Instant.now().getEpochSecond();
        }

        double tokensNeeded = bucket.capacity - bucket.tokens;
        double secondsToFill = tokensNeeded / tokensPerSecond;

        return Instant.now().getEpochSecond() + (long) Math.ceil(secondsToFill);
    }


    private void refill(ClientHealth.Bucket bucket, double tokensPerSecond) {
        long now = System.currentTimeMillis();
        long timePassed = now - bucket.lastRefillTime;
        double tokensToAdd = (timePassed / 1000.0) * tokensPerSecond;

        bucket.tokens = Math.min(bucket.capacity, bucket.tokens + tokensToAdd);
        bucket.lastRefillTime = now;
    }


    private static class ClientHealth {
        private final Bucket bucket;

        public ClientHealth(int capacity) {
            this.bucket = new Bucket(capacity);
        }

        public Bucket getBucket() {
            return bucket;
        }

        private static class Bucket {
            private double tokens;
            private long lastRefillTime;
            private final int capacity;

            public Bucket(int capacity) {
                this.capacity = capacity;
                this.tokens = capacity; // Начинаем с полной корзины
                this.lastRefillTime = System.currentTimeMillis();
            }

            public double getTokens() {
                return tokens;
            }

            public void setTokens(double tokens) {
                this.tokens = tokens;
            }

            public long getLastRefillTime() {
                return lastRefillTime;
            }

            public void setLastRefillTime(long lastRefillTime) {
                this.lastRefillTime = lastRefillTime;
            }

            public int getCapacity() {
                return capacity;
            }
        }
    }

}
