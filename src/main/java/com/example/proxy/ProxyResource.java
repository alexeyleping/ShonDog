package com.example.proxy;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import com.example.health.HealthChecker;
import com.example.health.impl.ScheduledHealthCheckService;
import com.example.loadbalancer.LoadBalancer;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import jakarta.ws.rs.core.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Response;

import java.util.*;

@Path("/proxy")
public class ProxyResource {

    @Inject
    HttpClient httpClient;

    @Inject
    LoadBalancer loadBalancer;

    @Inject
    ScheduledHealthCheckService scheduledHealthCheckService;

    @Inject
    HealthChecker healthChecker;

    /**
     * Проксирует GET запрос
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyGet(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                             @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry(path, headersMap, (url, h) -> httpClient.get(url, h));
    }

    /**
     * Проксирует POST запрос
     */
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyPost(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                              @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry(path, headersMap, (url, h) -> httpClient.post(url, body, h));
    }

    /**
     * Проксирует PUT запрос
     */
    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyPut(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                             @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry(path, headersMap, (url, h) -> httpClient.put(url, body, h));
    }

    /**
     * Проксирует DELETE запрос
     */
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyDelete(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                                @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry(path, headersMap, (url, h) -> httpClient.delete(url, h));
    }

    /**
     * Создает карту headers
     * @return Map<String, String>
     */
    private Map<String, String> createHeaders(HttpHeaders headers, HttpServerRequest request) {
        Map<String, String> headersMap = new HashMap<>();
        headers.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headersMap.put(key, values.get(0));
            }
        });
        headersMap.put("X-Forwarded-For", request.remoteAddress().host());
        return headersMap;
    }

    /**
     * Выполняет HTTP операцию с retry и failover
     */
    private Response executeWithRetry(String path, Map<String, String> headers,
                                      HttpOperation operation) {
        String url;
        try {
            url = loadBalancer.selectServer();
        } catch (HttpClientException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("No available servers")
                    .build();
        }

        Set<String> triedServers = new HashSet<>();
        triedServers.add(url);

        // Первая попытка
        try {
            String response = operation.execute(url + path, headers);
            return Response.ok(response).build();
        } catch (HttpClientException e) {
            healthChecker.markUnhealthy(url);
        }

        // Retry на других серверах
        int maxAttempts = scheduledHealthCheckService.getCachedHealthyServers().size();

        for (int i = 0; i < maxAttempts; i++) {
            String newUrl;
            try {
                newUrl = loadBalancer.selectServer();
            } catch (HttpClientException e) {
                break;  // Нет доступных серверов
            }

            if (triedServers.contains(newUrl)) {
                continue;
            }
            triedServers.add(newUrl);

            try {
                String response = operation.execute(newUrl + path, headers);
                return Response.ok(response).build();
            } catch (HttpClientException ignored) {
                healthChecker.markUnhealthy(newUrl);
            }
        }

        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("All backend servers are unavailable")
                .build();
    }

    @FunctionalInterface
    interface HttpOperation {
        String execute(String url, Map<String, String> headers) throws HttpClientException;
    }
}
