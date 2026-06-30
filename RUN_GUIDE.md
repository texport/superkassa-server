# Подробное пошаговое руководство по запуску и настройке Superkassa Server

Это руководство написано простым языком и содержит пошаговые инструкции для различных операционных систем (**Windows**, **macOS** и **Linux**). Следуя ему, вы сможете запустить и настроить сервер, даже если делаете это впервые.

---

## ШАГ 1. Установка необходимых программ (Подготовка)

Перед запуском сервера на ваш компьютер нужно установить две программы: **Java 21** (для запуска напрямую) и **Docker** (для запуска в контейнере).

### 1.1. Установка Java 21
Сервер написан на языке Kotlin/Java, поэтому для его работы обязательна виртуальная машина Java версии 21.

* **Для Windows**:
  1. Скачайте установщик Java 21 (выберите `x64 Installer` (.msi)) с официального сайта: [Скачать Java 21 (Temurin)](https://adoptium.net/temurin/releases/?version=21).
  2. Запустите скачанный файл `.msi` или `.exe` и нажимайте кнопку **Далее (Next)** до завершения установки. Во время установки убедитесь, что включены галочки `Set JAVA_HOME` и `Associate .jar`.
  3. Проверьте установку: откройте командную строку (нажмите клавиши `Win + R`, введите `cmd` и нажмите Enter) и введите команду:
     ```cmd
     java -version
     ```
     Вы должны увидеть текст, содержащий `openjdk version "21...`.

* **Для macOS**:
  1. Скачайте установщик для macOS (выберите `.pkg` файл под ваш процессор — `x64` для Intel или `aarch64` для Apple M1/M2/M3) с сайта adoptium.net (выбрав версию 21).
  2. Запустите `.pkg` файл и следуйте стандартным шагам установки на Mac.
  3. Проверьте установку через Терминал:
     ```bash
     java -version
     ```

* **Для Linux (Ubuntu/Debian)**:
  Откройте терминал и выполните команды:
  ```bash
  sudo apt update
  sudo apt install -y openjdk-21-jre
  ```

---

### 1.2. Установка Docker
Docker нужен, чтобы запускать сервер в изолированном контейнере со всеми его зависимостями «в один клик».

* **Для Windows и macOS**:
  1. Скачайте программу **Docker Desktop**: [Скачать Docker Desktop](https://www.docker.com/products/docker-desktop/).
  2. Установите её как обычную программу. На Windows в процессе установки согласитесь на установку компонентов WSL 2 (если потребуется).
  3. После установки запустите Docker Desktop. В нижнем левом углу программы должна загореться зеленая иконка (это означает, что Docker запущен и готов к работе).

* **Для Linux**:
  Установите Docker через терминал:
  ```bash
  sudo apt update
  sudo apt install -y docker.io docker-compose
  sudo systemctl start docker
  sudo systemctl enable docker
  ```

---

## ШАГ 2. Скачивание готового файла сервера (Release JAR)

Вам не нужно компилировать код из исходников. Мы уже собрали готовый файл программы.

1. Откройте страницу релизов проекта в браузере: [Релизы Superkassa Server на GitHub](https://github.com/texport/superkassa-server/releases).
2. Найдите последний релиз (например, `v1.0.1`).
3. В разделе **Assets** (в самом низу описания релиза) кликните мышкой на файл **`server-1.0.1.jar`** и сохраните его на компьютер (например, в созданную папку `C:\superkassa` на Windows или `/Users/имя_пользователя/superkassa` на Mac).

---

## ШАГ 3. Варианты запуска сервера (Выберите один)

Ниже описаны три разных способа запуска сервера. Для простой проверки выберите **Вариант 3.1** или **Вариант 3.2**.

---

### Вариант 3.1. Простой запуск напрямую через Java (без Docker)
Этот способ запускает сервер прямо на вашем компьютере. База данных SQLite автоматически создастся в той же папке, куда вы скачали JAR-файл.

1. Откройте командную строку/терминал и перейдите в папку со скачанным файлом:
   * **На Windows**:
     ```cmd
     cd C:\superkassa
     ```
   * **На macOS / Linux**:
     ```bash
     cd ~/superkassa
     ```
2. Запустите сервер следующей командой (порт сервера `8080`, включен режим отладки без реального ОФД):
   ```bash
   java -jar -Dserver.port=8080 -Dsuperkassa.debug-cache=true server-1.0.1.jar
   ```
3. Вы увидите много текстовых логов на экране. Когда появится строчка `Started ServerApplicationKt in ... seconds`, сервер готов к работе.
4. **Как остановить**: Чтобы выключить сервер, просто нажмите комбинацию клавиш `Ctrl + C` в этом же окне командной строки.

---

### Вариант 3.2. Запуск через Docker в режиме DESKTOP (Локальный режим)
Этот режим изолирует сервер в контейнере, но сохраняет базу данных SQLite на вашем диске, чтобы при перезапуске данные не стирались.

1. Создайте в папке проекта пустую структуру папок `server/build/libs/` и скопируйте туда скачанный ранее файл `server-1.0.1.jar`:
   * **На Windows (в PowerShell)**:
     ```powershell
     New-Item -ItemType Directory -Force -Path .\server\build\libs
     Copy-Item .\server-1.0.1.jar .\server\build\libs\server-1.0.1.jar
     ```
   * **На macOS / Linux**:
     ```bash
     mkdir -p server/build/libs && cp server-1.0.1.jar server/build/libs/
     ```
2. Соберите Docker-образ кассы:
   ```bash
   docker build -t superkassa-server .
   ```
3. Запустите контейнер:
   ```bash
   docker run -d -p 8080:8080 -v superkassa-data:/app/data -e SUPERKASSA_DEBUG_CACHE=true --name superkassa superkassa-server
   ```
   *Параметр `-v superkassa-data:/app/data` указывает Docker сохранять файлы базы данных SQLite на вашем компьютере.*
4. **Как остановить**:
   ```bash
   docker stop superkassa
   ```
5. **Как запустить остановленный сервер снова**:
   ```bash
   docker start superkassa
   ```

---

### Вариант 3.3. Развертывание в режиме SERVER (Кластерный режим)
Этот режим используется для промышленной эксплуатации. Здесь сервер не хранит данные локально, а подключается к внешнему кластеру баз данных (PostgreSQL или MySQL), а нагрузка может распределяться между несколькими серверами (нодами).

#### Шаг А. Настройка СУБД (Базы данных)
Перед запуском касс вам нужна база данных. Зайдите в консоль управления вашей СУБД и выполните настройки:

* **Для PostgreSQL**:
  1. Создайте пользователя и БД:
     ```sql
     CREATE USER superkassa_user WITH PASSWORD 'secure_password';
     CREATE DATABASE superkassa_db OWNER superkassa_user;
     ```
* **Для MySQL**:
  1. Создайте базу данных с поддержкой UTF-8 и пользователя:
     ```sql
     CREATE DATABASE superkassa_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
     CREATE USER 'superkassa_user'@'%' IDENTIFIED BY 'secure_password';
     GRANT ALL PRIVILEGES ON superkassa_db.* TO 'superkassa_user'@'%';
     FLUSH PRIVILEGES;
     ```

#### Шаг Б. Запуск кластера из двух нод через Docker Compose
Создайте в любой папке файл с именем `docker-compose.yml` и запишите туда следующий текст (он автоматически настроит базу PostgreSQL и запустит две копии кассового сервера):

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

Запустите этот кластер одной командой в терминале:
```bash
docker-compose up -d
```

---

#### Шаг В. Развертывание в Kubernetes (k8s)
Если вы хотите запустить сервер в Kubernetes:

1. Создайте файл конфигурации `superkassa-k8s.yaml`:
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
           # Автоматически прокидываем уникальное имя пода как Node ID:
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
2. Примените манифест в ваш кластер:
   ```bash
   kubectl apply -f superkassa-k8s.yaml
   ```

---

## ШАГ 4. Проверка работоспособности и авторизация

Как только сервер запустился (любым из способов выше на порту `8080`):

1. Откройте браузер и перейдите по адресу:
   👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**
   *Вы увидите интерактивное меню со списком всех доступных API-методов кассы.*

2. **Как войти в панель администратора кассы (Авторизация)**:
   * Нажмите кнопку **Authorize** (зеленый замок) в правом верхнем углу страницы Swagger.
   * В поле **Value** введите стандартный ПИН-код администратора: `8888`.
   * Нажмите кнопку **Authorize**, затем **Close**.
   * Теперь вы можете выполнять любые запросы (например, инициализацию кассы через `/kkm/init` или открытие смены `/kkm/{kkmId}/shift/open`).
