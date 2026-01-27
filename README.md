# ShonDog

Lightweight reverse proxy server built with Quarkus (Java 21). Designed as a minimal replacement for nginx when running alongside Java applications.

## Features

- **Reverse Proxy** — proxies GET, POST, PUT, DELETE requests to backend servers
- **Load Balancing** — Round Robin distribution across multiple backends
- **Health Checks** — periodic health monitoring with automatic removal/recovery of backends
- **Retry & Failover** — automatic retry on the next healthy server when a backend fails
- **Circuit Breaker** — prevents cascading failures by temporarily blocking requests to failing servers (CLOSED / OPEN / HALF_OPEN states)
- **Rate Limiting** — Token Bucket algorithm, per-client IP, configurable requests per minute
- **Response Caching** — in-memory cache for GET responses with TTL and max-size eviction
- **Header Propagation** — forwards request/response headers, adds `X-Forwarded-For`
- **Configurable Timeouts** — connection and request timeouts
- **Request Logging** — structured logs with method, path, backend, status code, and latency

## Architecture

```
[Client] --> [ShonDog Proxy :8080] --> [Backend Server 1 :8081]
                                   --> [Backend Server 2 :8082]
                                   --> [Backend Server N]
```

## Requirements

- Java 21+
- Gradle

## Quick Start

Start backend servers (example with Python):

```sh
# Terminal 1
python3 -m http.server 8081

# Terminal 2
python3 -m http.server 8082
```

Run ShonDog:

```sh
./gradlew quarkusDev
```

Test proxying:

```sh
curl "http://localhost:8080/proxy?path=/"
```

See [TESTING.md](TESTING.md) for detailed testing scenarios.

## Configuration

All settings are in `src/main/resources/application.properties`:

| Setting | Default | Description |
|---|---|---|
| `app.backends.urls` | `localhost:8081, :8082` | Backend server URLs |
| `app.health.endpoint` | `/health` | Health check path on backends |
| `app.health.interval` | `10s` | Health check interval |
| `app.timeout.connect` | `5s` | Connection timeout |
| `app.timeout.request` | `30s` | Request timeout |
| `app.circuit-breaker.failure-threshold` | `3` | Failures before circuit opens |
| `app.circuit-breaker.open-duration` | `30s` | Time circuit stays open |
| `app.rate-limit.requests-per-minute` | `60` | Max requests per client per minute |
| `app.rate-limit.enabled` | `true` | Enable/disable rate limiting |
| `app.cache.ttl` | `60s` | Cache entry time-to-live |
| `app.cache.max-size` | `100` | Max cached responses |
| `app.cache.enabled` | `true` | Enable/disable response caching |

## API

All endpoints are under `/proxy`. The `path` query parameter specifies the backend path.

```sh
# GET
curl "http://localhost:8080/proxy?path=/api/data"

# POST
curl -X POST "http://localhost:8080/proxy?path=/api/data" -d '{"key":"value"}'

# PUT
curl -X PUT "http://localhost:8080/proxy?path=/api/data/1" -d '{"key":"updated"}'

# DELETE
curl -X DELETE "http://localhost:8080/proxy?path=/api/data/1"
```

### Response Headers

| Header | Description |
|---|---|
| `X-Cache` | `HIT` if served from cache, `MISS` otherwise |
| `Age` | Seconds since the response was cached (on cache HIT) |
| `X-RateLimit-Limit` | Max requests per minute |
| `X-RateLimit-Remaining` | Remaining requests in current window |
| `X-RateLimit-Reset` | Unix timestamp when the limit resets |

## Project Structure

```
com.example
├── cache/                 # Response caching
│   ├── ResponseCache          (interface)
│   ├── CachedResponse         (data class)
│   └── impl/InMemoryResponseCache
├── circuitbreaker/        # Circuit breaker pattern
│   ├── CircuitBreaker         (interface)
│   ├── CircuitState           (enum)
│   └── impl/SimpleCircuitBreaker
├── client/                # HTTP client
│   ├── HttpClient             (interface)
│   ├── HttpResponse           (data class)
│   ├── HttpClientException    (exception)
│   └── impl/SimpleHttpClient
├── config/                # Configuration
│   └── AppConfig              (@ConfigMapping)
├── health/                # Health checking
│   ├── HealthChecker          (interface)
│   └── impl/
│       ├── SimpleHealthChecker
│       └── ScheduledHealthCheckService
├── loadbalancer/          # Load balancing
│   ├── LoadBalancer           (interface)
│   └── impl/RoundRobinLoadBalancer
├── ratelimiter/           # Rate limiting
│   ├── RateLimiter            (interface)
│   └── impl/TokenBucketRateLimiter
└── proxy/                 # REST endpoint
    └── ProxyResource
```

## Build & Test

```sh
# Run tests
./gradlew test

# Build JAR
./gradlew build

# Run
java -jar build/quarkus-app/quarkus-run.jar

# Build uber-jar
./gradlew build -Dquarkus.package.jar.type=uber-jar
java -jar build/*-runner.jar
```

## Tech Stack

- **Quarkus 3.x** — runtime framework
- **Java 21** — language
- **Gradle** — build system
- **JUnit 5 + Mockito** — testing
- **java.net.http.HttpClient** — built-in HTTP client (no external HTTP libraries)
