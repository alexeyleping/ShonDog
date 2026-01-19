package com.example.health;

import com.example.client.HttpClientException;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public interface HealthChecker {

    /**
     * Возвращает список живых серверов
     */
    List<String> getHealthyServers();

    /**
     * Проверяет жив ли сервер
     */
    String checkHealth(String serverUrl) throws HttpClientException;
}
