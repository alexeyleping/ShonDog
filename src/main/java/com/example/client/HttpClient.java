package com.example.client;

public interface HttpClient {
    /**
     * Выполняет GET запрос на указанный URL
     * @param url полный URL (например "http://example.com/api")
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String get(String url) throws HttpClientException;

    /**
     * Выполняет POST запрос на указанный URL
     * @param url полный URL (например "http://example.com/api")
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String post(String url, String body) throws HttpClientException;

    /**
     * Выполняет PUT запрос на указанный URL
     * @param url полный URL (например "http://example.com/api")
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String put(String url, String body) throws HttpClientException;

    /**
     * Выполняет DELETE запрос на указанный URL
     * @param url полный URL (например "http://example.com/api")
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String delete(String url) throws HttpClientException;
}
