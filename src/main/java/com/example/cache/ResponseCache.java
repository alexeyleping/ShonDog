package com.example.cache;

import java.util.Optional;

public interface ResponseCache {
    /**
     * Получает из кеша ответа от backend
     * @param key HTTP метод + URL path
     * @return
     */
    Optional<CachedResponse> get(String key);

    /**
     * Положить ответ в кеш
     * @param key
     * @param response
     */
    void put(String key, CachedResponse response);

    /**
     * Удалить конкретную запись
     * @param key
     */
    void evict(String key);

    /**
     * Очистить весь кеш
     */
    void clear();
}
