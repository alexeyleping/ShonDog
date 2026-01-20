# ShonDog - Инструкция по запуску и тестированию

## Содержание
1. [Подготовка](#подготовка)
2. [Запуск Mock Backend Серверов](#запуск-mock-backend-серверов)
3. [Запуск ShonDog](#запуск-shondog)
4. [Тестирование функциональности](#тестирование-функциональности)
5. [Полезные команды](#полезные-команды)

---

## Подготовка

### Требования
- Java 21
- Gradle
- Python 3 (для mock серверов)
- curl (для тестирования)

### Проверка портов
Перед запуском убедись, что порты свободны:
```bash
# Проверить какие процессы используют порты
lsof -i :8080  # ShonDog
lsof -i :8081  # Backend 1
lsof -i :8082  # Backend 2
```

Если порты заняты, убей процессы:
```bash
# Найти процесс и убить
kill -9 $(lsof -t -i:8080)
kill -9 $(lsof -t -i:8081)
kill -9 $(lsof -t -i:8082)
```

---

## Запуск Mock Backend Серверов

### Шаг 1: Создать mock сервер

Создай файл `/tmp/backend_server.py`:

```python
#!/usr/bin/env python3
from http.server import HTTPServer, BaseHTTPRequestHandler
import sys

class HealthHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()
            self.wfile.write(b'OK')
        else:
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()
            message = f'Hello from backend on port {port}'
            self.wfile.write(message.encode())

    def log_message(self, format, *args):
        # Suppress logs
        pass

if __name__ == '__main__':
    port = int(sys.argv[1])
    server = HTTPServer(('localhost', port), HealthHandler)
    print(f'Backend server started on port {port}')
    server.serve_forever()
```

### Шаг 2: Запустить backend серверы

**Вариант 1: В отдельных терминалах (рекомендуется для обучения)**
```bash
# Терминал 1
python3 /tmp/backend_server.py 8081

# Терминал 2
python3 /tmp/backend_server.py 8082
```

**Вариант 2: В фоне**
```bash
python3 /tmp/backend_server.py 8081 > /tmp/backend_8081.log 2>&1 &
python3 /tmp/backend_server.py 8082 > /tmp/backend_8082.log 2>&1 &
```

### Шаг 3: Проверить что серверы работают
```bash
# Проверить health endpoint
curl http://localhost:8081/health
# Ожидаемый ответ: OK

curl http://localhost:8082/health
# Ожидаемый ответ: OK

# Проверить основной endpoint
curl http://localhost:8081/
# Ожидаемый ответ: Hello from backend on port 8081

curl http://localhost:8082/
# Ожидаемый ответ: Hello from backend on port 8082
```

---

## Запуск ShonDog

### Вариант 1: Dev режим с консолью (рекомендуется для обучения)
```bash
./gradlew quarkusDev
```

**Что увидишь:**
- Логи запуска Quarkus
- Сообщения о scheduled health check каждые 10 секунд
- Живое обновление кода (hot reload) при изменениях

**Остановка:** `Ctrl+C`

### Вариант 2: Dev режим в фоне
```bash
./gradlew quarkusDev > /tmp/shondog.log 2>&1 &

# Смотреть логи
tail -f /tmp/shondog.log
```

### Проверка что ShonDog запустился
```bash
# Должен вернуть 200 и ответ от backend
curl http://localhost:8080/proxy
```

---

## Тестирование функциональности

### 1. Тест базового проксирования
```bash
curl http://localhost:8080/proxy
# Ожидаемый ответ: Hello from backend on port 8081 или 8082
```

### 2. Тест балансировки нагрузки (Round Robin)
```bash
# Сделать 6 запросов подряд
for i in {1..6}; do
  echo "Request $i:"
  curl -s http://localhost:8080/proxy
  echo ""
done
```

**Ожидаемый результат:**
```
Request 1:
Hello from backend on port 8081
Request 2:
Hello from backend on port 8082
Request 3:
Hello from backend on port 8081
Request 4:
Hello from backend on port 8082
Request 5:
Hello from backend on port 8081
Request 6:
Hello from backend on port 8082
```

### 3. Тест Scheduled Health Check

**Что проверяем:** Health check запускается автоматически каждые 10 секунд

**Как проверить:**
1. Смотри логи ShonDog:
   ```bash
   # В консоли где запущен quarkusDev увидишь каждые 10 сек:
   # INFO  Running scheduled health check...
   # INFO  Healthy servers: [http://localhost:8081, http://localhost:8082]
   ```

### 4. Тест Failover (автоматическое исключение упавшего сервера)

**Сценарий:** Останавливаем один сервер и проверяем, что прокси перестает на него отправлять запросы

**Шаги:**
```bash
# 1. Найти процесс backend сервера 8081
ps aux | grep "backend_server.py 8081" | grep -v grep

# 2. Убить процесс (подставь свой PID)
kill <PID>

# Или одной командой:
kill $(ps aux | grep "backend_server.py 8081" | grep -v grep | awk '{print $2}')

# 3. Подождать health check цикла (10+ секунд)
sleep 12

# 4. Проверить что все запросы идут только на 8082
for i in {1..4}; do
  curl -s http://localhost:8080/proxy
  echo ""
done
```

**Ожидаемый результат:** Все 4 ответа будут "Hello from backend on port 8082"

### 5. Тест Recovery (автоматическое восстановление сервера)

**Сценарий:** Запускаем упавший сервер обратно

**Шаги:**
```bash
# 1. Запустить сервер 8081 снова
python3 /tmp/backend_server.py 8081 &

# 2. Проверить что он работает
curl http://localhost:8081/health

# 3. Подождать health check цикла (10+ секунд)
sleep 12

# 4. Проверить балансировку снова
for i in {1..6}; do
  curl -s http://localhost:8080/proxy | grep -o "port [0-9]*"
done
```

**Ожидаемый результат:** Чередование port 8081 и port 8082

### 6. Тест с query параметром path
```bash
# Отправить запрос с параметром path
curl "http://localhost:8080/proxy?path=/some/path"

# Если backend поддерживает путь, он вернет ответ для этого пути
# В нашем mock сервере вернется просто "Hello from backend..."
```

---

## Полезные команды

### Управление процессами

**Посмотреть запущенные процессы:**
```bash
# ShonDog
ps aux | grep quarkus

# Backend серверы
ps aux | grep backend_server.py

# Все вместе
ps aux | grep -E "(quarkus|backend_server)"
```

**Убить все процессы:**
```bash
# Убить все backend серверы
pkill -f backend_server.py

# Убить Quarkus (если запущен в фоне)
pkill -f quarkus
```

### Проверка логов

**ShonDog логи (если запущен в фоне):**
```bash
tail -f /tmp/shondog.log
```

**Backend логи (если запущены в фоне):**
```bash
tail -f /tmp/backend_8081.log
tail -f /tmp/backend_8082.log
```

### Проверка конфигурации

**Посмотреть текущую конфигурацию:**
```bash
cat src/main/resources/application.properties
```

**Изменить интервал health check:**
```bash
# Отредактировать application.properties
# app.health.interval=5s  # проверка каждые 5 секунд
# app.health.interval=30s # проверка каждые 30 секунд
```

### Тестирование с curl

**Показать HTTP заголовки:**
```bash
curl -v http://localhost:8080/proxy
```

**Показать время ответа:**
```bash
curl -w "\nTime: %{time_total}s\n" http://localhost:8080/proxy
```

**Отправить несколько запросов параллельно:**
```bash
# 10 запросов параллельно
for i in {1..10}; do
  curl -s http://localhost:8080/proxy &
done
wait
```

### Отладка проблем

**Проблема: "Connection refused"**
```bash
# Проверить что серверы запущены
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8080/proxy
```

**Проблема: "No live servers found"**
```bash
# 1. Проверить что backend серверы работают
curl http://localhost:8081/health
curl http://localhost:8082/health

# 2. Проверить конфигурацию в application.properties
cat src/main/resources/application.properties | grep backends

# 3. Подождать health check цикла
sleep 12

# 4. Попробовать снова
curl http://localhost:8080/proxy
```

**Проблема: Порт занят**
```bash
# Найти процесс на порту
lsof -i :8080

# Убить процесс
kill -9 <PID>
```

---

## Быстрый старт (все вместе)

Полный процесс запуска всего проекта:

```bash
# 1. Очистить старые процессы
pkill -f backend_server.py
pkill -f quarkus

# 2. Создать mock сервер (если еще не создан)
cat > /tmp/backend_server.py << 'EOF'
#!/usr/bin/env python3
from http.server import HTTPServer, BaseHTTPRequestHandler
import sys

class HealthHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/health':
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()
            self.wfile.write(b'OK')
        else:
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()
            message = f'Hello from backend on port {port}'
            self.wfile.write(message.encode())

    def log_message(self, format, *args):
        pass

if __name__ == '__main__':
    port = int(sys.argv[1])
    server = HTTPServer(('localhost', port), HealthHandler)
    print(f'Backend server started on port {port}')
    server.serve_forever()
EOF

# 3. Запустить backend серверы
python3 /tmp/backend_server.py 8081 &
python3 /tmp/backend_server.py 8082 &

# 4. Проверить backend серверы
sleep 2
curl http://localhost:8081/health && echo " ✓ Server 8081 OK"
curl http://localhost:8082/health && echo " ✓ Server 8082 OK"

# 5. Запустить ShonDog
./gradlew quarkusDev
```

После запуска в другом терминале:
```bash
# Подождать запуска (15-20 секунд)
sleep 15

# Тестировать балансировку
for i in {1..6}; do
  curl -s http://localhost:8080/proxy | grep -o "port [0-9]*"
done
```

---

## Следующие шаги

После того как научишься запускать и тестировать:
1. Попробуй изменить интервал health check в `application.properties`
2. Попробуй добавить третий backend сервер на порт 8083
3. Попробуй сделать stress тест с большим количеством запросов
4. Изучи логи и пойми как работает scheduled task

Удачи! 🚀
