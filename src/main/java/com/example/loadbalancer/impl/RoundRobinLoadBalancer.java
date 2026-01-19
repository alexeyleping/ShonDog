package com.example.loadbalancer.impl;

import com.example.client.HttpClientException;
import com.example.health.HealthChecker;
import com.example.loadbalancer.LoadBalancer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);


    @Inject
    HealthChecker healthChecker;

    @Override
    public String selectServer() throws HttpClientException {
        List<String> liveServers = healthChecker.getHealthyServers();
        if(liveServers.isEmpty()) {
            throw new HttpClientException("No live servers found");
        }
        int index = counter.getAndIncrement() % liveServers.size();
        return liveServers.get(index);
    }
}
