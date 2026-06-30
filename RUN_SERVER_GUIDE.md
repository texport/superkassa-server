# Руководство по развертыванию Superkassa Server в режиме SERVER (Кластерный режим)

Этот документ содержит руководство по установке, конфигурированию и запуску `superkassa-server` в масштабируемом и отказоустойчивом режиме **SERVER**. 

В режиме **SERVER** конфигурация касс хранится во внешней базе данных, а нагрузка распределяется между несколькими нодами кассового сервера за балансировщиком нагрузки.

---

## ШАГ 1. Настройка внешних СУБД (PostgreSQL / MySQL)

Перед запуском кассового сервера необходимо настроить внешнюю СУБД.

### Вариант А. Настройка PostgreSQL (Рекомендуется)
1. Создайте базу данных и пользователя:
   ```sql
   CREATE USER superkassa_user WITH PASSWORD 'secure_password';
   CREATE DATABASE superkassa_db OWNER superkassa_user;
   ```

### Вариант Б. Настройка MySQL
1. Создайте базу данных с поддержкой UTF-8 и пользователя:
   ```sql
   CREATE DATABASE superkassa_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'superkassa_user'@'%' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON superkassa_db.* TO 'superkassa_user'@'%';
   FLUSH PRIVILEGES;
   ```

---

## ШАГ 2. Настройка Node ID для кластера

При запуске нескольких экземпляров сервера координация их работы происходит через общую БД. Каждая запущенная нода должна получить уникальный идентификатор через переменную окружения `SUPERKASSA_NODE_ID` (или свойство `-Dsuperkassa.node-id`). Это необходимо для бесконфликтной работы распределенной очереди задач отправки документов в ОФД.

---

## ШАГ 3. Варианты запуска (Выберите наиболее удобный)

Скачанный файл `server-1.0.1.jar` можно запустить напрямую в операционной системе, упаковать в Docker-образ, запустить в связке с БД через Docker Compose или развернуть в Kubernetes.

---

### Вариант 3.1. Запуск напрямую в операционной системе (через Java 21)
Этот способ подходит, если внешняя база данных запущена на отдельном сервере, а вы запускаете JAR-файл приложения напрямую.

1. Убедитесь, что установлена JRE 21.
2. Запустите сервер, передав параметры подключения к вашей СУБД в качестве системных свойств:
   * **Для PostgreSQL**:
     ```bash
     java -jar \
       -Dserver.port=8080 \
       -Dspring.datasource.url=jdbc:postgresql://<db_host>:5432/superkassa_db \
       -Dspring.datasource.username=superkassa_user \
       -Dspring.datasource.password=secure_password \
       -Dsuperkassa.node-id=node-1 \
       server-1.0.1.jar
     ```
   * **Для MySQL**:
     ```bash
     java -jar \
       -Dserver.port=8080 \
       -Dspring.datasource.url=jdbc:mysql://<db_host>:3306/superkassa_db \
       -Dspring.datasource.username=superkassa_user \
       -Dspring.datasource.password=secure_password \
       -Dsuperkassa.node-id=node-1 \
       server-1.0.1.jar
     ```

---

### Вариант 3.2. Запуск одного контейнера (через Docker)
Если вы упаковали скачанный JAR в Docker-образ (как описано в руководстве по Desktop-версии), вы можете запустить его с пробросом переменных окружения для подключения к БД:

```bash
docker run -d \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<db_host>:5432/superkassa_db \
  -e SPRING_DATASOURCE_USERNAME=superkassa_user \
  -e SPRING_DATASOURCE_PASSWORD=secure_password \
  -e SUPERKASSA_NODE_ID=node-1 \
  --name superkassa \
  superkassa-server
```

---

### Вариант 3.3. Запуск готового кластера (через Docker Compose)
Пример файла `docker-compose.yml` для автоматического развертывания БД и двух нод сервера кассы:

```yaml
version: '3.8'

services:
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

  superkassa-node1:
    image: superkassa-server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-db:5432/superkassa_db
      - SPRING_DATASOURCE_USERNAME=superkassa_user
      - SPRING_DATASOURCE_PASSWORD=secure_password
      - SUPERKASSA_NODE_ID=node-1
    ports:
      - "8081:8080"
    depends_on:
      - postgres-db

  superkassa-node2:
    image: superkassa-server:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-db:5432/superkassa_db
      - SPRING_DATASOURCE_USERNAME=superkassa_user
      - SPRING_DATASOURCE_PASSWORD=secure_password
      - SUPERKASSA_NODE_ID=node-2
    ports:
      - "8082:8080"
    depends_on:
      - postgres-db

volumes:
  pgdata:
```

Запустите кластер:
```bash
docker-compose up -d
```

---

### Вариант 3.4. Развертывание в Kubernetes (k8s)
Пример файла `superkassa-k8s.yaml` для запуска кластера в Kubernetes с автоматическим пробросом имени пода как `SUPERKASSA_NODE_ID` через Downward API:

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
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:postgresql://postgres-service:5432/superkassa_db"
        - name: SPRING_DATASOURCE_USERNAME
          value: "superkassa_user"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "secure_password"
        # Динамическое получение имени пода для Node ID очереди:
        - name: SUPERKASSA_NODE_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
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
```

Примените в кластер:
```bash
kubectl apply -f superkassa-k8s.yaml
```

---

## ШАГ 4. Доступ к API и авторизация методов

После развертывания кластера:
1. Документация API (Swagger UI) доступна по адресу: `http://<load-balancer-ip>/swagger-ui/index.html`
2. Спецификация OpenAPI доступна в формате JSON: `http://<load-balancer-ip>/v3/api-docs`
3. Авторизация всех методов осуществляется путем передачи ПИН-кода в HTTP-заголовке:
   `Authorization: Bearer <PIN>` (например, `Authorization: Bearer 8888`).
