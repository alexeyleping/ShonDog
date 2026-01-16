package com.example.client;

public class HttpClientException extends Exception {

        public HttpClientException(String message) {
            super(message);
        }

        public HttpClientException(String message, Throwable cause) {
            super(message, cause);
        }
}
