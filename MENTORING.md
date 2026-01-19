# ShonDog - Менторинг проект

## Цель проекта
Написать веб-сервер на Quarkus, который может заменить nginx рядом с Java-приложением:
- Обратный прокси (reverse proxy)
- Балансировка нагрузки (load balancing)
- Минимальный набор кода для понимания принципов работы

## Процесс разработки

### Роли
- **Ментор (Claude)**: даёт задания, теорию, делает код-ревью
- **Разработчик (Alexey)**: пишет код самостоятельно

### Формат задания
1. **Теория** - зачем это нужно, как работает
2. **Задание** - конкретное описание что сделать
3. **Acceptance Criteria** - критерии приёмки
4. **Hints** - подсказки (опционально)

### Процесс
1. Ментор даёт теорию и задание
2. Разработчик пишет код
3. Разработчик говорит "готово" или задаёт вопросы
4. Ментор делает код-ревью
5. Если есть замечания - итерация
6. Задание закрыто - переход к следующему

### Принципы кода
- Код должен быть покрываем тестами (dependency injection, интерфейсы)
- Маленькие методы с одной ответственностью
- Понятные имена переменных и методов
- Без магических чисел и строк

---

## Архитектура (будет дополняться)

```
[Client] --> [ShonDog Proxy] --> [Backend Server 1]
                             --> [Backend Server 2]
                             --> [Backend Server N]
```

### Компоненты (план)
1. **HTTP Server** - приём входящих запросов (Quarkus уже даёт)
2. **Router** - маршрутизация запросов
3. **HTTP Client** - отправка запросов на backend
4. **Load Balancer** - выбор backend сервера
5. **Health Checker** - проверка доступности backend
6. **Configuration** - конфигурация серверов

---

## История заданий

### Задание #1 - HTTP Client ✅
**Статус**: Завершено
**Ветка**: task/01-http-client (merged)
**Файлы**:
- `com.example.client.HttpClient` - интерфейс
- `com.example.client.HttpClientException` - исключение
- `com.example.client.impl.SimpleHttpClient` - реализация

### Задание #2 - Proxy Endpoint ✅
**Статус**: Завершено
**Ветка**: task/02-proxy-endpoint (merged)
**Файлы**:
- `com.example.proxy.ProxyResource` - REST endpoint для проксирования GET запросов
**Чему научились**:
- Dependency Injection через `@Inject`
- JAX-RS аннотации: `@Path`, `@GET`, `@Produces`, `@QueryParam`
- Связали компоненты: ProxyResource использует HttpClient

### Задание #3 - Конфигурация backend серверов ✅
**Статус**: Завершено
**Ветка**: task/03-backend-server-configuration
**Файлы**:
- `com.example.config.AppConfig` - интерфейс конфигурации с `@ConfigMapping`
- Обновлён `com.example.proxy.ProxyResource` - использует URL из конфигурации
- `application.properties` - список backend серверов
**Чему научились**:
- `@ConfigMapping` для типобезопасной конфигурации
- Вложенные интерфейсы для структурированных настроек
- `@DefaultValue` для значений по умолчанию в query параметрах

### Задание #4 - Балансировка нагрузки ✅
**Статус**: Завершено
**Ветка**: task/04-load-balancer
**Файлы**:
- `com.example.loadbalancer.LoadBalancer` - интерфейс балансировщика
- `com.example.loadbalancer.impl.RoundRobinLoadBalancer` - реализация Round Robin
- Обновлён `com.example.proxy.ProxyResource` - использует LoadBalancer
**Чему научились**:
- Round Robin алгоритм балансировки
- `AtomicInteger` для потокобезопасного счётчика
- Правильная организация пакетов: интерфейсы в корне, реализации в `impl`

### Задание #5 - Health Check ✅
**Статус**: Завершено
**Ветка**: task/04-load-balancer
**Файлы**:
- `com.example.health.HealthChecker` - интерфейс проверки здоровья
- `com.example.health.impl.SimpleHealthChecker` - реализация проверки
- Обновлён `com.example.config.AppConfig` - добавлена конфигурация health endpoint
- Обновлён `com.example.loadbalancer.impl.RoundRobinLoadBalancer` - использует HealthChecker
- Обновлён `com.example.client.impl.SimpleHttpClient` - бросает исключение при не-2xx статусе
**Чему научились**:
- Health Check паттерн для проверки доступности серверов
- `ConcurrentHashMap` для потокобезопасного хранения состояния
- Try-catch для определения живых/мёртвых серверов
- Проверка HTTP статус кода (2xx = успех)

---

## Структура пакетов

```
com.example
├── client                  # HTTP клиент
│   ├── HttpClient.java
│   ├── HttpClientException.java
│   └── impl
│       └── SimpleHttpClient.java
├── config                  # Конфигурация
│   └── AppConfig.java
├── health                  # Health Check
│   ├── HealthChecker.java
│   └── impl
│       └── SimpleHealthChecker.java
├── loadbalancer            # Балансировка нагрузки
│   ├── LoadBalancer.java
│   └── impl
│       └── RoundRobinLoadBalancer.java
└── proxy                   # REST endpoints
    └── ProxyResource.java
```

---

## Текущий статус
- **Фаза**: Разработка core компонентов
- **Последнее задание**: #5
- **Следующий шаг**: Задание #6 - Scheduled Health Check
