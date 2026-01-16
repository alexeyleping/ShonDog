package com.example.client;

public interface HttpClient {
    /**
     * Выполняет GET запрос на указанный URL
     * @param url полный URL (например "http://example.com/api")
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String get(String url) throws HttpClientException;
}
