package com.example.proxy;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/proxy")
public class ProxyResource {

    @Inject
    HttpClient httpClient;

    /**
     * Проксирует GET запрос на указанный target URL
     * @param target URL backend сервера (например "http://example.com/api")
     * Для проверки сделать curl "http://localhost:8080/proxy?target=https://httpbin.org/get"
     * @return тело ответа от backend
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyGet(@QueryParam("target") String target) throws HttpClientException {
        String response = httpClient.get(target);
        return response;
    }
}
