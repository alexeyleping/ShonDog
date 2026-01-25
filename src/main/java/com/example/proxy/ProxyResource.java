package com.example.proxy;

import com.example.circuitbreaker.CircuitBreaker;
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
import org.jboss.logging.Logger;

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

    @Inject
    CircuitBreaker circuitBreaker;

    private static final Logger LOG = Logger.getLogger(ProxyResource.class);

    /**
     * Проксирует GET запрос
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyGet(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                             @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry("GET", path, headersMap, (url, h) -> httpClient.get(url, h));
    }

    /**
     * Проксирует POST запрос
     */
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyPost(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                              @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry("POST", path, headersMap, (url, h) -> httpClient.post(url, body, h));
    }

    /**
     * Проксирует PUT запрос
     */
    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyPut(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                             @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry("PUT", path, headersMap, (url, h) -> httpClient.put(url, body, h));
    }

    /**
     * Проксирует DELETE запрос
     */
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyDelete(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                                @Context HttpServerRequest request) {
        Map<String, String> headersMap = createHeaders(headers, request);
        return executeWithRetry("DELETE", path, headersMap, (url, h) -> httpClient.delete(url, h));
    }

    /**
     * Создает карту headers
     *
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
    private Response executeWithRetry(String method, String path, Map<String, String> headers,
                                      HttpOperation operation) {
        long start = System.currentTimeMillis();

        Set<String> triedServers = new HashSet<>();
        int maxAttempts = scheduledHealthCheckService.getCachedHealthyServers().size();

        for (int i = 0; i < maxAttempts; i++) {
            String url;
            try {
                url = loadBalancer.selectServer();
            } catch (HttpClientException e) {
                break;  // Нет доступных серверов
            }

            // Пропускаем уже опробованные
            if (triedServers.contains(url)) {
                continue;
            }
            triedServers.add(url);

            // Circuit Breaker: пропускаем если circuit открыт
            if (circuitBreaker.isOpen(url)) {
                LOG.warnf("    %s %s -> %s [SKIPPED: Circuit Open]", method, path, url);
                continue;
            }

            LOG.infof("--> %s %s -> %s", method, path, url);

            try {
                com.example.client.HttpResponse response = operation.execute(url + path, headers);
                long duration = System.currentTimeMillis() - start;
                LOG.infof("<-- %s %s -> %s [%d] %dms", method, path, url, response.getStatusCode(), duration);

                // Circuit Breaker: успех
                circuitBreaker.recordSuccess(url);

                Response.ResponseBuilder builder = Response.status(response.getStatusCode());
                response.getHeaders().forEach(builder::header);
                return builder.entity(response.getBody()).build();
            } catch (HttpClientException e) {
                LOG.warnf("    %s %s -> %s [FAILED: %s] retrying...", method, path, url, e.getMessage());

                // Circuit Breaker: ошибка
                circuitBreaker.recordFailure(url);
                healthChecker.markUnhealthy(url);
            }
        }

        long duration = System.currentTimeMillis() - start;
        LOG.errorf("<-- %s %s [FAILED: All servers unavailable] %dms", method, path, duration);
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("All backend servers are unavailable")
                .build();
    }

    @FunctionalInterface
    interface HttpOperation {
        com.example.client.HttpResponse execute(String url, Map<String, String> headers) throws HttpClientException;
    }
}
