package com.example.loadbalancer.impl;

import com.example.config.AppConfig;
import com.example.loadbalancer.LoadBalancer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger counter = new AtomicInteger(0);

    @Inject
    AppConfig appConfig;

    @Override
    public String selectServer() {
        List<AppConfig.BackendConfig> backendConfigList = appConfig.backends();
        int index = counter.getAndIncrement() % backendConfigList.size();
        return backendConfigList.get(index).url();
    }
}
