# superkassa-server

[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/superkassa-server/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/superkassa-server/actions)
[![Version](https://img.shields.io/badge/Version-1.0.5-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../LICENSE)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Executable Spring Boot REST API application for the **Superkassa** fiscalization system. Integrates core logic with JDBC database storage, delivery notification pipelines, NTP clock validator, and receipt renders.

### Key Features
- **REST Controllers**: Endpoints for all cashbox operations (sale, buy, return, deposit, withdraw).
- **Interactive OpenAPI**: Integrates Swagger UI for route testing and integration documentation.
- **Robust Transaction Management**: Aspect-based transaction and logging management.
- **Environment Checks**: Verification profiles that strictly block running server mode using SQLite database.

---

### Installation

Use the executable jar in your deployment:

---

### Usage Example

Running the executable application:

```bash
java -jar superkassa-server-1.0.5.jar --spring.profiles.active=prod
```

### Logging Configuration

The application uses **SLF4J + Logback** for internal logging. You have full control over the logging level and target logging tools/destinations without modifying the codebase:

*   **Configuring via Spring Boot properties**:
    Adjust the levels and file targets directly using command line arguments or `application.yml`:
    ```bash
    java -jar superkassa-server-1.0.5.jar --logging.level.root=WARN --logging.file.name=logs/server.log
    ```
*   **External Custom Logging Config**:
    To completely customize logging (e.g. forward logs to **Graylog**, **ELK**, **Syslog**, etc.), specify an external Logback configuration file at startup:
    ```bash
    java -Dlogging.config=/path/to/custom-logback.xml -jar superkassa-server-1.0.5.jar
    ```

---

## Документация на русском языке

Исполняемое Spring Boot REST API приложение для системы фискализации **Superkassa**. Интегрирует бизнес-логику ядра с JDBC-хранилищем, каналами доставки уведомлений, NTP-валидатором времени и рендерерами чеков.

### Ключевые возможности
- **REST Контроллеры**: Конечные точки для всех операций (продажа, покупка, возврат, внесение, изъятие).
- **Интерактивный OpenAPI**: Интеграция Swagger UI для тестирования роутов и документации.
- **Транзакционность и логирование**: Аспектное логирование и транзакционность операций.
- **Проверки окружения**: Проверка профилей запуска, блокирующая связку server-режима на базе SQLite.

---

### Установка

Используйте исполняемый JAR в вашей сборке:

---

### Пример использования

Запуск исполняемого приложения:

```bash
java -jar superkassa-server-1.0.5.jar --spring.profiles.active=prod
```

### Настройка логирования

Приложение использует связку **SLF4J + Logback** для логирования. Вы можете гибко управлять уровнями логирования и инструментами отправки логов без изменения исходного кода:

*   **Настройка через свойства Spring Boot**:
    Уровни логирования и запись в локальные файлы можно задавать прямо в аргументах запуска или в файле `application.yml`:
    ```bash
    java -jar superkassa-server-1.0.5.jar --logging.level.root=WARN --logging.file.name=logs/server.log
    ```
*   **Использование внешней конфигурации**:
    Для полной кастомизации (например, отправки логов в **Graylog**, **ELK**, **Syslog** и т.д.) укажите внешний файл настроек Logback при старте:
    ```bash
    java -Dlogging.config=/path/to/custom-logback.xml -jar superkassa-server-1.0.5.jar
    ```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../LICENSE).
