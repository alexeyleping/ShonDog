package com.example.loadbalancer;

public interface LoadBalancer {

    /**
     * Выбирает backend сервер для запроса
     */
    String selectServer();
}
