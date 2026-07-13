# superkassa-storage-jdbc

[![CI Build](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![Version](https://img.shields.io/badge/Version-1.0.4-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-85%25--100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../LICENSE)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Infrastructure JDBC database storage adapters for the **Superkassa** fiscalization system. Implements repository interfaces defined in the core to persist application data in relational databases.

### Key Features
- **Supported Engines**: PostgreSQL, MySQL, SQLite.
- **`StorageConfig`**: Database connection credentials and pooling config.
- **`StorageBootstrap`**: Automated database schema migration support using direct migration scripts catalog.
- **Repositories**: Direct JDBC implementations of KKM users, counters, cashboxes, shifts, fiscal documents, outbox events, and offline queues.

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:storage-jdbc:1.0.4")
}
```

---

### Usage Example

```kotlin
import io.github.texport.superkassa.jvm.storage.impl.application.bootstrap.StorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig

// Run migrations on startup
storageBootstrap.bootstrap()
```

---

## Документация на русском языке

Инфраструктурные JDBC-адаптеры базы данных для системы фискализации **Superkassa**. Реализует интерфейсы репозиториев, определенные в ядре, для персистентного хранения данных в реляционных базах данных.

### Ключевые возможности
- **Поддерживаемые СУБД**: PostgreSQL, MySQL, SQLite.
- **`StorageConfig`**: Конфигурирование подключения и параметров пула соединений БД.
- **`StorageBootstrap`**: Автоматическое применение миграций схемы БД через каталог скриптов миграции.
- **Репозитории**: Прямые JDBC-реализации для пользователей кассы, счетчиков, кассовых аппаратов, смен, фискальных документов, событий outbox и офлайн-очереди.

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:storage-jdbc:1.0.4")
}
```

---

### Пример использования

```kotlin
import io.github.texport.superkassa.jvm.storage.impl.application.bootstrap.StorageBootstrap
import io.github.texport.superkassa.jvm.storage.impl.domain.config.StorageConfig

// Запуск миграций при старте
storageBootstrap.bootstrap()
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../LICENSE).
