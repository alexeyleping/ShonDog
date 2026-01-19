package com.example.client.impl;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@ApplicationScoped
public class SimpleHttpClient implements HttpClient {

    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

    @Override
    public String get(String url) throws HttpClientException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = null;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new HttpClientException("Failed to execute GET request to " + url, e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpClientException("HTTP error: " + response.statusCode());
        }

        return  response.body();
    }
}
