package com.example.health;

import com.example.client.HttpClientException;
import com.example.client.HttpResponse;

import java.util.List;

public interface HealthChecker {

    /**
     * Возвращает список живых серверов
     */
    List<String> getHealthyServers();

    /**
     * Проверяет жив ли сервер
     */
    HttpResponse checkHealth(String serverUrl) throws HttpClientException;

    /**
     * Помечает сервер как неработающий
     */
    void markUnhealthy(String url);
}
