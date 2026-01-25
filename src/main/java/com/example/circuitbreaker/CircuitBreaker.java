package com.example.circuitbreaker;

public interface CircuitBreaker {

    /**
     * Проверяет открыт ли circuit для сервера
     */
    boolean isOpen(String serverUrl);

    /**
     * Записывает успешный запрос
     */
    void recordSuccess(String serverUrl);

    /**
     * Записывает не успешный запрос
     */
    void recordFailure(String serverUrl);

    /**
     * Получает текущее состояние сервера
     */
    CircuitState getState(String serverUrl);
}
