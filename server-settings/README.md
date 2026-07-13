# superkassa-server-settings

[![CI Build](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![Version](https://img.shields.io/badge/Version-1.0.4-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../LICENSE)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Infrastructure settings repository implementation for the **Superkassa** fiscalization system. Implements `CoreSettingsRepository` port defined in the application core.

### Key Features

- **`FileCoreSettingsRepository`**: JSON file storage optimized for desktop mode. 
  - *Thread-safe:* Uses synchronization locks to handle concurrent access.
  - *Atomic Writes:* Employs secure file saving using temporary files and atomic replacements (`ATOMIC_MOVE`) to prevent data corruption.
- **`CoreSettingsValidator`**: Centralized validation rules that check core configurations and block invalid configurations (e.g., using SQLite in `SERVER` mode).

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-core-jvm:1.1.0")
    implementation("io.github.texport:server-settings:1.0.4")
}
```

---

### Usage Example

#### File-based Repository Configuration (Desktop Mode)
```kotlin
import io.github.texport.superkassa.jvm.settings.impl.FileCoreSettingsRepository
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import java.nio.file.Paths

val repository = FileCoreSettingsRepository(Paths.get("config/core-settings.json"))

// Load settings or write defaults if missing
val settings = repository.loadOrCreate(defaults)
```

---

## Документация на русском языке

Инфраструктурная реализация хранилища настроек для системы фискализации **Superkassa**. Реализует порт `CoreSettingsRepository`, объявленный в ядре системы.

### Ключевые возможности

- **`FileCoreSettingsRepository`**: Хранилище в локальном JSON-файле для одиночных инстансов (desktop-режим).
  - *Потокобезопасность:* Защищено блоками синхронизации от конкурентного чтения/записи.
  - *Атомарная запись:* Сохранение данных сначала во временный файл с последующей атомарной заменой оригинального файла (`ATOMIC_MOVE`) для защиты конфигурации от повреждения.
- **`CoreSettingsValidator`**: Единый модуль валидации конфигураций, блокирующий запуск небезопасных сред (например, использование базы SQLite в серверном режиме `SERVER`).

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-core-jvm:1.1.0")
    implementation("io.github.texport:server-settings:1.0.4")
}
```

---

### Примеры использования

#### Инициализация файлового хранилища (Desktop режим)
```kotlin
import io.github.texport.superkassa.jvm.settings.impl.FileCoreSettingsRepository
import io.github.texport.superkassa.core.domain.api.model.settings.CoreSettings
import java.nio.file.Paths

val repository = FileCoreSettingsRepository(Paths.get("config/core-settings.json"))

// Загрузить текущие настройки или создать дефолтные в случае их отсутствия
val settings = repository.loadOrCreate(defaultSettings)
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../LICENSE).
