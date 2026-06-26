# superkassa-server

[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/superkassa-server/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/superkassa-server/actions)
[![Version](https://img.shields.io/badge/Version-1.0-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../time-java/LICENSE)

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

Add the dependency or use the executable jar in your deployment:

```kotlin
dependencies {
    implementation("kz.mybrain:superkassa-server:1.0")
}
```

---

### Usage Example

Running the executable application:

```bash
java -jar superkassa-server-1.0.jar --spring.profiles.active=prod
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

Добавьте зависимость или используйте исполняемый JAR в вашей сборке:

```kotlin
dependencies {
    implementation("kz.mybrain:superkassa-server:1.0")
}
```

---

### Пример использования

Запуск исполняемого приложения:

```bash
java -jar superkassa-server-1.0.jar --spring.profiles.active=prod
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../time-java/LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../time-java/LICENSE).
