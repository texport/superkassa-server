# Руководство по развертыванию, конфигурации и запуску Superkassa Server

Этот документ содержит исчерпывающее руководство по установке, конфигурированию и запуску `superkassa-server` в различных режимах эксплуатации: от локального запуска через JAR-файл до масштабируемого отказоустойчивого кластера в Kubernetes с внешними СУБД.

---

## 1. Запуск через исполняемый JAR-файл (Локальный запуск)

Этот способ подходит для локальной отладки или развертывания на отдельных кассовых компьютерах.

### Предварительные требования:
* Установленная Java Runtime Environment (JRE) версии 17 или выше.
* Синхронизированное системное время по NTP (критично для фискализации).

### Сборка JAR-файла из исходников:
Если вы не скачиваете готовый релиз с GitHub, соберите проект локально:
```bash
./gradlew :server:bootJar
```
Исполняемый архив будет находиться по пути: `server/build/libs/server-1.0.1.jar`.

### Настройка конфигурации (`application.yml` / Системные свойства):
При запуске через JAR конфигурацию можно переопределить через переменные окружения или системные свойства Java (`-D`):
* `server.port` — порт сервера (по умолчанию `8080`).
* `spring.datasource.url` — путь к базе данных SQLite (по умолчанию `jdbc:sqlite:data/superkassa.db`).
* `superkassa.debug-cache` — включение отладочного кэширования для работы без ОФД (по умолчанию `false`).

### Команда запуска:
```bash
java -jar -Dserver.port=8080 -Dsuperkassa.debug-cache=true server-1.0.1.jar
```

---

## 2. Запуск через Docker в режиме DESKTOP

Режим `DESKTOP` используется для локальных POS-терминалов. Вся база данных хранится в файле SQLite, а настройки ядра — в локальном файле `core-settings.json`.

### Сборка Docker-образа:
В корне проекта выполните:
```bash
docker build -t superkassa-server .
```

### Запуск контейнера:
Для сохранения базы данных SQLite и настроек при перезапуске контейнера необходимо монтировать локальный том (volume):
```bash
docker run -d \
  -p 8080:8080 \
  -v superkassa-data:/app/data \
  -e SUPERKASSA_DEBUG_CACHE=true \
  --name superkassa \
  superkassa-server
```

* **Примечание**: База данных SQLite будет находиться по пути `/app/data/superkassa.db` внутри контейнера (и сохранится в томе `superkassa-data`).

---

## 3. Развертывание в режиме SERVER (Кластерный режим)

Режим `SERVER` предназначен для развертывания в облаке банка, ОФД или крупного ритейлера. Конфигурация касс хранится во внешней базе данных, а нагрузка распределяется между несколькими нодами.

### 3.1. Подготовка и настройка внешних СУБД (PostgreSQL / MySQL)
Перед запуском кассового сервера необходимо подготовить кластер базы данных.

#### А. Настройка PostgreSQL (Рекомендуется)
1. Создайте базу данных и пользователя:
   ```sql
   CREATE USER superkassa_user WITH PASSWORD 'secure_password';
   CREATE DATABASE superkassa_db OWNER superkassa_user;
   ```
2. Настройте пул подключений (HikariCP конфигурируется автоматически в Spring Boot). Для высоконагруженных систем рекомендуется установить размер пула минимум в 20 соединений.

#### Б. Настройка MySQL
1. Создайте базу данных с поддержкой UTF-8:
   ```sql
   CREATE DATABASE superkassa_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'superkassa_user'@'%' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON superkassa_db.* TO 'superkassa_user'@'%';
   FLUSH PRIVILEGES;
   ```

---

### 3.2. Настройка режима многонодовости (Clustering / Multi-node)
При запуске нескольких экземпляров сервера для распределения нагрузки координация их работы происходит через базу данных.

#### Уникальный Node ID
Каждая запущенная нода должна получить уникальный идентификатор `nodeId` через переменную окружения `SUPERKASSA_NODE_ID`. Это необходимо для корректной работы распределенной очереди задач отправки документов в ОФД.

#### Запуск кластера через Docker Compose (Пример)
Ниже представлен пример конфигурации `docker-compose.yml` для запуска двух нод кассового сервера за балансировщиком Nginx и одной базой PostgreSQL:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: superkassa_db
      POSTGRES_USER: superkassa_user
      POSTGRES_PASSWORD: secure_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  superkassa-node1:
    image: superkassa-server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/superkassa_db
      - SPRING_DATASOURCE_USERNAME=superkassa_user
      - SPRING_DATASOURCE_PASSWORD=secure_password
      - SUPERKASSA_NODE_ID=node-1
    depends_on:
      - postgres

  superkassa-node2:
    image: superkassa-server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/superkassa_db
      - SPRING_DATASOURCE_USERNAME=superkassa_user
      - SPRING_DATASOURCE_PASSWORD=secure_password
      - SUPERKASSA_NODE_ID=node-2
    depends_on:
      - postgres

volumes:
  pgdata:
```

---

### 3.3. Развертывание в Kubernetes (k8s)

Для промышленного запуска в облаке используется Kubernetes. Ниже приведен пример манифеста для развертывания кластера `superkassa-server` из двух реплик с автоматическим пробросом имени пода как `SUPERKASSA_NODE_ID`.

#### Манифест развертывания (`superkassa-k8s.yaml`):
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: superkassa-server
  labels:
    app: superkassa
spec:
  replicas: 2
  selector:
    matchLabels:
      app: superkassa
  template:
    metadata:
      labels:
        app: superkassa
    spec:
      containers:
      - name: superkassa
        image: texport/superkassa-server:latest
        ports:
        - containerPort: 8080
        env:
        # Подключение к внешней базе данных
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres-service:5432/superkassa_db"
        - name: SPRING_DATASOURCE_USERNAME
          value: "superkassa_user"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "secure_password"
        # Динамическое получение имени пода для Node ID очереди
        - name: SUPERKASSA_NODE_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        # Настройки Health Probes для мониторинга
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 15
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: superkassa-service
spec:
  selector:
    app: superkassa
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP
```

---

## 4. Доступ к API и авторизация методов

После запуска сервера в любом из режимов:
1. Документация API (Swagger UI) доступна по адресу: `http://<host>:<port>/swagger-ui/index.html`
2. Спецификация OpenAPI доступна в формате JSON: `http://<host>:<port>/v3/api-docs`
3. Авторизация всех закрытых методов осуществляется путем передачи ПИН-кода в HTTP-заголовке:
   `Authorization: Bearer <PIN>` (например, `Authorization: Bearer 8888`).
