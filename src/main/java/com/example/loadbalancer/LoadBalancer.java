package com.example.loadbalancer;

import com.example.client.HttpClientException;

public interface LoadBalancer {

    /**
     * Выбирает backend сервер для запроса
     */
    String selectServer() throws HttpClientException;
}
