package com.example.client.impl;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@ApplicationScoped
public class SimpleHttpClient implements HttpClient {

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

    @Override
    public String get(String url, Map<String, String> headers) throws HttpClientException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        headers.forEach(builder::header);

        HttpRequest request = builder.GET().build();

        HttpResponse<String> response = null;
        response = execute(request, "GET", url);

        return  response.body();
    }

    @Override
    public String post(String url, String body, Map<String, String> headers) throws HttpClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        headers.forEach(builder::header);

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();

        HttpResponse<String> response = null;
        response = execute(request, "POST", url);

        return  response.body();
    }

    @Override
    public String put(String url, String body, Map<String, String> headers) throws HttpClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        headers.forEach(builder::header);

        HttpRequest request = builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();

        HttpResponse<String> response = null;
        response = execute(request, "PUT", url);

        return  response.body();
    }

    @Override
    public String delete(String url, Map<String, String> headers) throws HttpClientException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        headers.forEach(builder::header);

        HttpRequest request = builder.DELETE().build();

        HttpResponse<String> response = null;
        response = execute(request, "DELETE", url);

        return  response.body();
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
}
