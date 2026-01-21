package com.example.proxy;

import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import com.example.loadbalancer.LoadBalancer;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

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
    public String proxyGet(@QueryParam("path") @DefaultValue("") String path) throws HttpClientException {
        String url =  loadBalancer.selectServer();
        return httpClient.get(url + path);
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyPost(@QueryParam("path") @DefaultValue("") String path, String body) throws HttpClientException {
        String url =  loadBalancer.selectServer();
        return httpClient.post(url + path, body);
    }

    @PUT
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyPut(@QueryParam("path") @DefaultValue("") String path, String body) throws HttpClientException {
        String url =  loadBalancer.selectServer();
        return httpClient.put(url + path, body);
    }

    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public String proxyDelete(@QueryParam("path") @DefaultValue("") String path) throws HttpClientException {
        String url =  loadBalancer.selectServer();
        return httpClient.delete(url + path);
    }
}
