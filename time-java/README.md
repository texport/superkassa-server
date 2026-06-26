# superkassa-time-java

[![Maven Central](https://img.shields.io/maven-central/v/io.github.texport/superkassa-time-java.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.texport/superkassa-time-java)
[![Version](https://img.shields.io/badge/Version-1.0.1-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/superkassa-server/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/superkassa-server/actions)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Standard Java JVM time provider and validation guard for the **Superkassa** fiscalization system.

### Key Features
- **`SystemClock`**: Implements `ClockPort` to fetch standard system epoch millisecond timestamps.
- **`SystemTimeGuard`**: Implements `TimeValidatorPort`. Ensures system time safety by verifying:
  1. The time falls within a realistic range (years 2020 to 2100).
  2. The system clock did not jump/rewind unexpectedly relative to CPU monotonic time (`nanoTime`).
  3. The clock is synchronized with external web servers via `HTTP Date` headers fetch.

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-time-java:1.0.1")
}
```

---

### Usage Example

```kotlin
import kz.mybrain.superkassa.core.application.policy.SystemTimeGuard
import kz.mybrain.superkassa.core.application.policy.SystemClock

val result = SystemTimeGuard.validate(SystemClock)
if (result.ok) {
    println("Time is synchronized and valid")
} else {
    println("Time error: ${result.trilingualMessage()}")
}
```

---

## Документация на русском языке

Стандартный JVM провайдер времени и валидатор системных часов для системы фискализации **Superkassa**.

### Ключевые возможности
- **`SystemClock`**: Реализует порт `ClockPort` для получения текущего системного времени в миллисекундах.
- **`SystemTimeGuard`**: Реализует порт `TimeValidatorPort`. Проверяет корректность системного времени по критериям:
  1. Нахождение времени в разумном интервале (с 2020 по 2100 годы).
  2. Отсутствие скачков или перевода системных часов назад относительно монотонного таймера CPU (`nanoTime`).
  3. Сверка времени с эталонными веб-серверами через заголовки `HTTP Date`.

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-time-java:1.0.1")
}
```

---

### Пример использования

```kotlin
import kz.mybrain.superkassa.core.application.policy.SystemTimeGuard
import kz.mybrain.superkassa.core.application.policy.SystemClock

val result = SystemTimeGuard.validate(SystemClock)
if (result.ok) {
    println("Время синхронизировано и корректно")
} else {
    println("Ошибка времени: ${result.trilingualMessage()}")
}
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](LICENSE).
