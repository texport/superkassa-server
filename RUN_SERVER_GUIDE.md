# Руководство по развертыванию Superkassa Server в режиме SERVER (Кластерный режим)

Это руководство предназначено для системных администраторов и DevOps-инженеров по настройке масштабируемого и отказоустойчивого развертывания `superkassa-server` в облачной инфраструктуре (SaaS / Enterprise). 

В режиме **SERVER** конфигурация касс хранится во внешней базе данных, а нагрузка распределяется между несколькими нодами кассового сервера за балансировщиком нагрузки.

---

## 1. Подготовка и настройка внешних СУБД (PostgreSQL / MySQL)

Перед запуском кассового сервера необходимо настроить СУБД для обеспечения транзакционной целостности и блокировок в режиме кластера.

### Вариант А. Настройка PostgreSQL (Рекомендуется)
1. Создайте базу данных и пользователя:
   ```sql
   CREATE USER superkassa_user WITH PASSWORD 'secure_password';
   CREATE DATABASE superkassa_db OWNER superkassa_user;
   ```
2. Убедитесь, что лимит подключений (max_connections) настроен с запасом. Каждая нода использует пул подключений HikariCP (по умолчанию 10-20 соединений на ноду).

### Вариант Б. Настройка MySQL
1. Создайте базу данных с поддержкой UTF-8 и пользователя:
   ```sql
   CREATE DATABASE superkassa_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'superkassa_user'@'%' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON superkassa_db.* TO 'superkassa_user'@'%';
   FLUSH PRIVILEGES;
   ```

---

## 2. Настройка режима многонодовости (Clustering / Multi-node)

Для распределения нагрузки между несколькими экземплярами приложения координация их работы происходит через общую базу данных.

### 2.1. Уникальный Node ID
Каждая запущенная нода должна получить уникальный идентификатор через переменную окружения `SUPERKASSA_NODE_ID`. Это необходимо для того, чтобы распределенная очередь задач отправки документов в ОФД понимала, какая нода заблокировала и обрабатывает конкретный чек.

### 2.2. Запуск кластера через Docker Compose
Ниже представлен пример конфигурации `docker-compose.yml` для запуска двух нод кассового сервера за одной базой PostgreSQL:

```yaml
version: '3.8'

services:
  # Блок Базы Данных PostgreSQL
  postgres-db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: superkassa_db
      POSTGRES_USER: superkassa_user
      POSTGRES_PASSWORD: secure_password
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  # Первая нода кассового сервера
  superkassa-node1:
    image: superkassa-server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-db:5432/superkassa_db
      - SPRING_DATASOURCE_USERNAME=superkassa_user
      - SPRING_DATASOURCE_PASSWORD=secure_password
      - SUPERKASSA_NODE_ID=node-1 # Уникальный ID первой ноды для очереди ОФД
    ports:
      - "8081:8080" # Будет доступна на порту 8081
    depends_on:
      - postgres-db

  # Вторая нода кассового сервера
  superkassa-node2:
    image: superkassa-server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-db:5432/superkassa_db
      - SPRING_DATASOURCE_USERNAME=superkassa_user
      - SPRING_DATASOURCE_PASSWORD=secure_password
      - SUPERKASSA_NODE_ID=node-2 # Уникальный ID второй ноды
    ports:
      - "8082:8080" # Будет доступна на порту 8082
    depends_on:
      - postgres-db

volumes:
  pgdata:
```

Запустите кластер одной командой в терминале:
```bash
docker-compose up -d
```

---

## 3. Развертывание в Kubernetes (k8s)

Ниже приведен пример манифеста для развертывания кластера `superkassa-server` из двух реплик с автоматическим пробросом имени пода как `SUPERKASSA_NODE_ID` через Downward API.

### Манифест развертывания (`superkassa-k8s.yaml`):
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
        # Настройки Health Probes для мониторинга Kubernetes
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

Примените манифест в ваш кластер Kubernetes:
```bash
kubectl apply -f superkassa-k8s.yaml
```

---

## 4. Доступ к API и авторизация методов

После развертывания кластера:
1. Интерактивная документация (Swagger UI) доступна по адресу: `http://<load-balancer-ip>/swagger-ui/index.html`
2. Спецификация OpenAPI доступна в формате JSON: `http://<load-balancer-ip>/v3/api-docs`
3. Авторизация всех методов осуществляется путем передачи ПИН-кода в HTTP-заголовке:
   `Authorization: Bearer <PIN>` (например, `Authorization: Bearer 8888`).
