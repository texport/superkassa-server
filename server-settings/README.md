# superkassa-server-settings

[![CI Build](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../time-java/LICENSE)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Infrastructure settings repository implementation for the **Superkassa** fiscalization system. Implements `CoreSettingsRepository` port defined in the application core.

### Key Features

- **`FileCoreSettingsRepository`**: JSON file storage optimized for desktop mode. 
  - *Thread-safe:* Uses synchronization locks to handle concurrent access.
  - *Atomic Writes:* Employs secure file saving using temporary files and atomic replacements (`ATOMIC_MOVE`) to prevent data corruption.
- **`DatabaseCoreSettingsRepository`**: Relational database storage using JDBC for multi-node deployments.
  - *Flexible Setup:* Supports direct connection metadata extraction to avoid duplicate URL configs when a `DataSource` is supplied.
  - *DDL Management:* Custom `createTable` flag disables automated table creation for secure production environments.
- **`CoreSettingsValidator`**: Centralized validation rules that check core configurations and block invalid configurations (e.g., using SQLite in `SERVER` mode).

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("kz.mybrain:superkassa-core:1.0")
    implementation("io.github.texport:superkassa-server-settings:1.0")
}
```

---

### Usage Example

#### 1. File-based Repository Configuration (Desktop Mode)
```kotlin
import io.github.texport.superkassa.jvm.settings.FileCoreSettingsRepository
import kz.mybrain.superkassa.core.application.model.CoreSettings
import java.nio.file.Paths

val repository = FileCoreSettingsRepository(Paths.get("config/core-settings.json"))

// Load settings or write defaults if missing
val settings = repository.loadOrCreate(defaults)
```

#### 2. Database-based Repository Configuration (Server Mode)
```kotlin
import kz.mybrain.superkassa.core.data.adapter.DatabaseCoreSettingsRepository
import javax.sql.DataSource

val dataSource: DataSource = getDataSource()

val repository = DatabaseCoreSettingsRepository(
    dataSource = dataSource,
    createTable = false // Disable DDL migrations at start if pre-provisioned
)

val settings = repository.load()
```

---

## Документация на русском языке

Инфраструктурная реализация хранилища настроек для системы фискализации **Superkassa**. Реализует порт `CoreSettingsRepository`, объявленный в ядре системы.

### Ключевые возможности

- **`FileCoreSettingsRepository`**: Хранилище в локальном JSON-файле для одиночных инстансов (desktop-режим).
  - *Потокобезопасность:* Защищено блоками синхронизации от конкурентного чтения/записи.
  - *Атомарная запись:* Сохранение данных сначала во временный файл с последующей атомарной заменой оригинального файла (`ATOMIC_MOVE`) для защиты конфигурации от повреждения.
- **`DatabaseCoreSettingsRepository`**: Хранилище в реляционной базе данных через JDBC/DataSource для многонодовых кластеров.
  - *Гибкая конфигурация:* Может автоматически извлекать строку подключения из метаданных `DataSource`, исключая дублирование настроек.
  - *Управление DDL:* Флаг `createTable` позволяет отключать автоматическое выполнение DDL-запросов (создание таблиц) при запуске приложения в продакшене.
- **`CoreSettingsValidator`**: Единый модуль валидации конфигураций, блокирующий запуск небезопасных сред (например, использование базы SQLite в серверном режиме `SERVER`).

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("kz.mybrain:superkassa-core:1.0")
    implementation("io.github.texport:superkassa-server-settings:1.0")
}
```

---

### Примеры использования

#### 1. Инициализация файлового хранилища (Desktop режим)
```kotlin
import io.github.texport.superkassa.jvm.settings.FileCoreSettingsRepository
import java.nio.file.Paths

val repository = FileCoreSettingsRepository(Paths.get("config/core-settings.json"))

// Загрузить текущие настройки или создать дефолтные в случае их отсутствия
val settings = repository.loadOrCreate(defaultSettings)
```

#### 2. Инициализация хранилища в БД (Server режим)
```kotlin
import kz.mybrain.superkassa.core.data.adapter.DatabaseCoreSettingsRepository
import javax.sql.DataSource

val dataSource: DataSource = obtainDataSource()

val repository = DatabaseCoreSettingsRepository(
    dataSource = dataSource,
    createTable = true // Автоматически создать таблицу при запуске, если она отсутствует
)

val settings = repository.load()
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../time-java/LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../time-java/LICENSE).
