package com.example.proxy;

import com.example.circuitbreaker.CircuitBreaker;
import com.example.client.HttpClient;
import com.example.client.HttpClientException;
import com.example.client.HttpResponse;
import com.example.health.HealthChecker;
import com.example.health.impl.ScheduledHealthCheckService;
import com.example.loadbalancer.LoadBalancer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class ProxyResourceRetryTest {

    @InjectMock
    HttpClient httpClient;

    @InjectMock
    LoadBalancer loadBalancer;

    @InjectMock
    ScheduledHealthCheckService scheduledHealthCheckService;

    @InjectMock
    HealthChecker healthChecker;

    @InjectMock
    CircuitBreaker circuitBreaker;

    @Inject
    ProxyResource proxyResource;

    private HttpHeaders mockHeaders;
    private HttpServerRequest mockRequest;

    @BeforeEach
    void setUp() throws HttpClientException {
        Mockito.reset(httpClient, loadBalancer, scheduledHealthCheckService, healthChecker, circuitBreaker);

        // Mock HttpHeaders
        mockHeaders = mock(HttpHeaders.class);
        when(mockHeaders.getRequestHeaders()).thenReturn(new MultivaluedHashMap<>());

        // Mock HttpServerRequest
        mockRequest = mock(HttpServerRequest.class);
        SocketAddress mockAddress = mock(SocketAddress.class);
        when(mockAddress.host()).thenReturn("127.0.0.1");
        when(mockRequest.remoteAddress()).thenReturn(mockAddress);

        // Mock CircuitBreaker - по умолчанию circuit закрыт
        when(circuitBreaker.isOpen(anyString())).thenReturn(false);
    }

    @Test
    void testSuccessfulRequestOnFirstAttempt() throws HttpClientException {
        // Given: первый сервер отвечает успешно
        when(loadBalancer.selectServer()).thenReturn("http://server1:8080");
        when(scheduledHealthCheckService.getCachedHealthyServers())
                .thenReturn(List.of("http://server1:8080"));
        when(httpClient.get(anyString(), any())).thenReturn(createResponse(200, "Success"));

        // When
        var response = proxyResource.proxyGet("", mockHeaders, mockRequest);

        // Then
        assertEquals(200, response.getStatus());
        assertEquals("Success", response.getEntity());
        verify(healthChecker, never()).markUnhealthy(anyString());
    }

    @Test
    void testRetryOnFailure() throws HttpClientException {
        // Given: первый сервер падает, второй отвечает
        when(loadBalancer.selectServer())
                .thenReturn("http://server1:8080")
                .thenReturn("http://server2:8080");
        when(scheduledHealthCheckService.getCachedHealthyServers())
                .thenReturn(List.of("http://server1:8080", "http://server2:8080"));
        when(httpClient.get(eq("http://server1:8080"), any()))
                .thenThrow(new HttpClientException("Connection refused"));
        when(httpClient.get(eq("http://server2:8080"), any()))
                .thenReturn(createResponse(200, "Success from server2"));

        // When
        var response = proxyResource.proxyGet("", mockHeaders, mockRequest);

        // Then
        assertEquals(200, response.getStatus());
        assertEquals("Success from server2", response.getEntity());
        verify(healthChecker).markUnhealthy("http://server1:8080");
    }

    @Test
    void testAllServersDown_Returns503() throws HttpClientException {
        // Given: все серверы падают
        when(loadBalancer.selectServer())
                .thenReturn("http://server1:8080")
                .thenReturn("http://server2:8080");
        when(scheduledHealthCheckService.getCachedHealthyServers())
                .thenReturn(List.of("http://server1:8080", "http://server2:8080"));
        when(httpClient.get(anyString(), any()))
                .thenThrow(new HttpClientException("Connection refused"));

        // When
        var response = proxyResource.proxyGet("", mockHeaders, mockRequest);

        // Then
        assertEquals(503, response.getStatus());
        verify(healthChecker, times(2)).markUnhealthy(anyString());
    }

    @Test
    void testNoAvailableServers_Returns503() throws HttpClientException {
        // Given: loadBalancer не может выбрать сервер
        when(loadBalancer.selectServer())
                .thenThrow(new HttpClientException("No live servers found"));

        // When
        var response = proxyResource.proxyGet("", mockHeaders, mockRequest);

        // Then
        assertEquals(503, response.getStatus());
    }

    @Test
    void testSkipAlreadyTriedServer() throws HttpClientException {
        // Given: loadBalancer возвращает тот же сервер повторно
        when(loadBalancer.selectServer())
                .thenReturn("http://server1:8080")  // первая попытка
                .thenReturn("http://server1:8080")  // тот же сервер (должен пропустить)
                .thenReturn("http://server2:8080"); // другой сервер
        // maxAttempts должен быть >= 3 чтобы было достаточно итераций
        when(scheduledHealthCheckService.getCachedHealthyServers())
                .thenReturn(List.of("http://server1:8080", "http://server2:8080", "http://server3:8080"));
        when(httpClient.get(eq("http://server1:8080"), any()))
                .thenThrow(new HttpClientException("Connection refused"));
        when(httpClient.get(eq("http://server2:8080"), any()))
                .thenReturn(createResponse(200, "Success from server2"));

        // When
        var response = proxyResource.proxyGet("", mockHeaders, mockRequest);

        // Then
        assertEquals(200, response.getStatus());
        // server1 вызван 1 раз, не 2
        verify(httpClient, times(1)).get(eq("http://server1:8080"), any());
        verify(httpClient, times(1)).get(eq("http://server2:8080"), any());
    }

    private HttpResponse createResponse(int statusCode, String body) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(statusCode);
        response.setBody(body);
        response.setHeaders(Map.of());
        return response;
    }
}
