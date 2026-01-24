package com.example.client.impl;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SimpleHttpClient implements HttpClient {

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of("Connection", "Keep-Alive", "Transfer-Encoding", "Proxy-Authenticate",
            "Proxy-Authorization", "TE", "Trailer", "Upgrade");


    @Override
    public com.example.client.HttpResponse get(String url, Map<String, String> headers) throws HttpClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach(builder::header);
        HttpRequest request = builder.GET().build();
        HttpResponse<String> response = execute(request, "GET", url);
        return createResponse(response);
    }

    @Override
    public com.example.client.HttpResponse post(String url, String body, Map<String, String> headers) throws HttpClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach(builder::header);
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = execute(request, "POST", url);
        return createResponse(response);
    }

    @Override
    public com.example.client.HttpResponse put(String url, String body, Map<String, String> headers) throws HttpClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach(builder::header);
        HttpRequest request = builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = execute(request, "PUT", url);
        return createResponse(response);
    }

    @Override
    public com.example.client.HttpResponse delete(String url, Map<String, String> headers) throws HttpClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        headers.forEach(builder::header);
        HttpRequest request = builder.DELETE().build();
        HttpResponse<String> response = execute(request, "DELETE", url);
        return createResponse(response);
    }

    private HttpResponse<String> execute(HttpRequest request, String method, String url) throws HttpClientException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpClientException("HTTP error: " + response.statusCode());
            }
            return response;
        } catch (IOException | InterruptedException e) {
            throw new HttpClientException("Failed to execute " + method + " request to " + url, e);
        }
    }

    private com.example.client.HttpResponse createResponse(HttpResponse<String> response) {
        com.example.client.HttpResponse httpResponse = new com.example.client.HttpResponse();
        httpResponse.setBody(response.body());
        HttpHeaders httpHeaders = response.headers();
        Map <String, List<String>> headersMap = httpHeaders.map();
        Map<String, String> newHeaders = new HashMap<>();
        for (String key : headersMap.keySet()) {
            List<String> values = headersMap.get(key);
            String value = values.get(0);
            newHeaders.put(key, value);
        }
        httpResponse.setHeaders(filterHeaders(newHeaders));
        httpResponse.setStatusCode(response.statusCode());
        return httpResponse;
    }

    private Map<String, String> filterHeaders(Map<String, String> headers) {
        Map<String, String> newHeaders = new HashMap<>();
        for (String key : headers.keySet()) {
            String value = headers.get(key);
            if (!HOP_BY_HOP_HEADERS.contains(key)) {
                newHeaders.put(key, value);
            }
        }
        return newHeaders;
    }
}
