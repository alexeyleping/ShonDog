package com.example.proxy;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import com.example.loadbalancer.LoadBalancer;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import jakarta.ws.rs.core.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import java.util.HashMap;
import java.util.Map;

@Path("/proxy")
public class ProxyResource {

    @Inject
    HttpClient httpClient;

    @Inject
    LoadBalancer loadBalancer;

    /**
     * Проксирует GET запрос
     * URL backend сервера (например "http://example.com/api")
     * @return тело ответа от backend
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyGet(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                           @Context HttpServerRequest request) throws HttpClientException {
        String url = loadBalancer.selectServer();
        Map<String, String> headersMap = createHeaders(headers, request);
        return httpClient.get(url + path, headersMap);
    }

    /**
     * Проксирует POST запрос
     * @return тело ответа от backend
     */
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyPost(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                            @Context HttpServerRequest request) throws HttpClientException {
        String url = loadBalancer.selectServer();
        Map<String, String> headersMap = createHeaders(headers, request);
        return httpClient.post(url + path, body, headersMap);
    }

    /**
     * Проксирует PUT запрос
     * @return тело ответа от backend
     */
    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyPut(@QueryParam("path") @DefaultValue("") String path, String body, @Context HttpHeaders headers,
                           @Context HttpServerRequest request) throws HttpClientException {
        String url = loadBalancer.selectServer();
        Map<String, String> headersMap = createHeaders(headers, request);
        return httpClient.put(url + path, body, headersMap);
    }

    /**
     * Проксирует DELETE запрос
     * @return тело ответа от backend
     */
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyDelete(@QueryParam("path") @DefaultValue("") String path, @Context HttpHeaders headers,
                              @Context HttpServerRequest request) throws HttpClientException {
        String url = loadBalancer.selectServer();
        Map<String, String> headersMap = createHeaders(headers, request);
        return httpClient.delete(url + path, headersMap);
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
}
