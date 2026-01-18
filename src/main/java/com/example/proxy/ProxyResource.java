package com.example.proxy;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/proxy")
public class ProxyResource {

    @Inject
    HttpClient httpClient;

    @Inject
    AppConfig appConfig;


    /**
     * Проксирует GET запрос
     * URL backend сервера (например "http://example.com/api")
     * @return тело ответа от backend
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyGet(@QueryParam("path") @DefaultValue("") String path) throws HttpClientException {
        String url =  appConfig.backends().getFirst().url();
        return httpClient.get(url + path);
    }
}
