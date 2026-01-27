package com.example.proxy;

import com.example.cache.CachedResponse;
import com.example.cache.ResponseCache;
import com.example.circuitbreaker.CircuitBreaker;
import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import com.example.config.AppConfig;
import com.example.health.HealthChecker;
import com.example.health.impl.ScheduledHealthCheckService;
import com.example.loadbalancer.LoadBalancer;
import com.example.ratelimiter.RateLimiter;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import jakarta.ws.rs.core.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
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

    @Inject
    RateLimiter rateLimiter;

    @Inject
    ResponseCache responseCache;

    @Inject
    AppConfig config;

    private static final Logger LOG = Logger.getLogger(ProxyResource.class);

    /**
     * Проксирует GET запрос
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyGet(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                             @Context HttpServerRequest request) {
        String clientIp = request.remoteAddress().host();

        // Проверка rate limit
        Response rateLimitResponse = checkRateLimit(clientIp);
        if (rateLimitResponse != null) {
            return rateLimitResponse;
        }

        // Проверяем кеш для GET-запросов
        if (config.cache().enabled()) {
            Optional<CachedResponse> cached = responseCache.get(path);
            if (cached.isPresent()) {
                LOG.infof("<-- GET %s [CACHE HIT]", path);
                Response response = buildCachedResponse(cached.get());
                return addRateLimitHeaders(response, clientIp);
            }
        }

        Map<String, String> headersMap = createHeaders(headers, request);
        Response response = executeWithRetry("GET", path, headersMap, (url, h) -> httpClient.get(url, h));

        // Сохраняем успешный ответ в кеш
        if (config.cache().enabled() && response.getStatus() >= 200 && response.getStatus() < 300) {
            cacheResponse(path, response);
        }

        response = addCacheHeader(response, "MISS");
        return addRateLimitHeaders(response, clientIp);
    }

    /**
     * Проксирует POST запрос
     */
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyPost(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                              @Context HttpServerRequest request) {
        String clientIp = request.remoteAddress().host();

        // Проверка rate limit
        Response rateLimitResponse = checkRateLimit(clientIp);
        if (rateLimitResponse != null) {
            return rateLimitResponse;
        }

        // Инвалидация кеша — данные изменились
        if (config.cache().enabled()) {
            responseCache.evict(path);
        }

        Map<String, String> headersMap = createHeaders(headers, request);
        Response response = executeWithRetry("POST", path, headersMap, (url, h) -> httpClient.post(url, body, h));
        return addRateLimitHeaders(response, clientIp);
    }

    /**
     * Проксирует PUT запрос
     */
    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyPut(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                             @Context HttpServerRequest request) {
        String clientIp = request.remoteAddress().host();

        // Проверка rate limit
        Response rateLimitResponse = checkRateLimit(clientIp);
        if (rateLimitResponse != null) {
            return rateLimitResponse;
        }

        // Инвалидация кеша — данные изменились
        if (config.cache().enabled()) {
            responseCache.evict(path);
        }

        Map<String, String> headersMap = createHeaders(headers, request);
        Response response = executeWithRetry("PUT", path, headersMap, (url, h) -> httpClient.put(url, body, h));
        return addRateLimitHeaders(response, clientIp);
    }

    /**
     * Проксирует DELETE запрос
     */
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response proxyDelete(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                                @Context HttpServerRequest request) {
        String clientIp = request.remoteAddress().host();

        // Проверка rate limit
        Response rateLimitResponse = checkRateLimit(clientIp);
        if (rateLimitResponse != null) {
            return rateLimitResponse;
        }

        // Инвалидация кеша — данные изменились
        if (config.cache().enabled()) {
            responseCache.evict(path);
        }

        Map<String, String> headersMap = createHeaders(headers, request);
        Response response = executeWithRetry("DELETE", path, headersMap, (url, h) -> httpClient.delete(url, h));
        return addRateLimitHeaders(response, clientIp);
    }

    /**
     * Проверяет rate limit для клиента
     * @param clientIp IP адрес клиента
     * @return Response с кодом 429 если лимит превышен, иначе null
     */
    private Response checkRateLimit(String clientIp) {
        if (!config.rateLimit().enabled()) {
            return null;
        }

        if (!rateLimiter.allowRequest(clientIp)) {
            LOG.warnf("Rate limit exceeded for client: %s", clientIp);

            long resetTime = rateLimiter.getResetTime(clientIp);
            long retryAfter = resetTime - (System.currentTimeMillis() / 1000);

            return Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Limit", config.rateLimit().requestsPerMinute())
                    .header("X-RateLimit-Remaining", "0")
                    .header("X-RateLimit-Reset", resetTime)
                    .header("Retry-After", Math.max(1, retryAfter))
                    .entity("Rate limit exceeded. Please try again later.")
                    .build();
        }

        return null;
    }

    /**
     * Добавляет заголовки rate limit в ответ
     * @param response оригинальный ответ
     * @param clientIp IP адрес клиента
     * @return ответ с добавленными заголовками
     */
    private Response addRateLimitHeaders(Response response, String clientIp) {
        if (!config.rateLimit().enabled()) {
            return response;
        }

        Response.ResponseBuilder builder = Response.fromResponse(response);
        builder.header("X-RateLimit-Limit", config.rateLimit().requestsPerMinute());
        builder.header("X-RateLimit-Remaining", rateLimiter.getRemaining(clientIp));
        builder.header("X-RateLimit-Reset", rateLimiter.getResetTime(clientIp));

        return builder.build();
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

    /**
     * Строит Response из кешированного ответа с заголовками X-Cache: HIT и Age
     */
    private Response buildCachedResponse(CachedResponse cached) {
        Response.ResponseBuilder builder = Response.status(cached.getStatusCode());
        cached.getHeaders().forEach(builder::header);
        long ageSeconds = Duration.between(cached.getCachedAt(), Instant.now()).getSeconds();
        builder.header("X-Cache", "HIT");
        builder.header("Age", ageSeconds);
        return builder.entity(cached.getBody()).build();
    }

    /**
     * Сохраняет ответ в кеш
     */
    private void cacheResponse(String path, Response response) {
        CachedResponse cached = new CachedResponse();
        cached.setBody((String) response.getEntity());
        cached.setStatusCode(response.getStatus());
        Map<String, String> headers = new HashMap<>();
        response.getHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headers.put(key, values.get(0).toString());
            }
        });
        cached.setHeaders(headers);
        responseCache.put(path, cached);
    }

    /**
     * Добавляет заголовок X-Cache в ответ
     */
    private Response addCacheHeader(Response response, String value) {
        if (!config.cache().enabled()) {
            return response;
        }
        Response.ResponseBuilder builder = Response.fromResponse(response);
        builder.header("X-Cache", value);
        return builder.build();
    }

    @FunctionalInterface
    interface HttpOperation {
        com.example.client.HttpResponse execute(String url, Map<String, String> headers) throws HttpClientException;
    }

}
