package com.example.ratelimiter;

public interface RateLimiter {
    boolean allowRequest(String clientId);
    int getRemaining(String clientId);
    long getResetTime(String clientId);
}
