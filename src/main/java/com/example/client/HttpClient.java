package com.example.client;

import java.util.Map;

public interface HttpClient {
    /**
     * Выполняет GET запрос на указанный URL
     * @param url полный URL (например "http://example.com/api")
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String get(String url, Map<String, String> headers) throws HttpClientException;

    /**
     * Выполняет POST запрос на указанный URL
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String post(String url, String body, Map<String, String> headers) throws HttpClientException;

    /**
     * Выполняет PUT запрос на указанный URL
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String put(String url, String body, Map<String, String> headers) throws HttpClientException;

    /**
     * Выполняет DELETE запрос на указанный URL
     * @return тело ответа как строка
     * @throws HttpClientException если запрос не удался
     */
    String delete(String url, Map<String, String> headers) throws HttpClientException;
}
