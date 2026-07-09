# time-java

JVM adapter module for Superkassa system time access and validation.

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

`time-java` is an infrastructure adapter module inside `superkassa-server`. It implements time-related ports from `superkassa-core-jvm` and is wired by the executable `server` module.

### Responsibilities

- `SystemClock` implements `ClockPort`.
- `SystemTimeGuard` implements `TimeValidatorPort`.
- Validation result errors are returned through `TimeValidationResult` with `TrilingualMessage` in Russian, Kazakh, and English.
- Startup policy is not implemented in this module. The `server` module calls `ValidateSystemTimeOnStartupUseCase`, which depends on `TimeValidatorPort` and `ClockPort`.

### Validation Rules

`SystemTimeGuard.validate(clock)` checks:

1. Wall-clock time is inside the accepted range: `2020-01-01T00:00:00Z` to `2100-01-01T00:00:00Z`.
2. Wall-clock time has not jumped too far from monotonic `System.nanoTime()`.
3. When reachable, external HTTP reference time from the `Date` header is not skewed by more than five minutes.

If external reference time cannot be fetched or parsed, local validation can still pass. Network/reference failures are treated as degraded reference availability, not as immediate cash register shutdown conditions.

### Current Integration

`server/src/main/kotlin/kz/mybrain/superkassa/core/config/AdaptersConfig.kt` exposes:

```kotlin
@Bean
fun timeValidatorPort(): TimeValidatorPort = SystemTimeGuard

@Bean
fun clockPort(): ClockPort = SystemClock
```

`server` startup validation is handled by:

- `ValidateSystemTimeOnStartupUseCase`
- `StartupTimeValidationRunner`

On startup, invalid time causes `SystemTimeStartupValidationException` and the application fails to start. The exception message includes Russian, Kazakh, and English text.

### Usage Example

```kotlin
import kz.mybrain.superkassa.core.data.adapter.SystemClock
import kz.mybrain.superkassa.core.data.adapter.SystemTimeGuard

val result = SystemTimeGuard.validate(SystemClock)
if (!result.ok) {
    error(result.trilingualMessage() ?: result.reason.orEmpty())
}
```

### Quality Gates

Required checks for this module:

```shell
./gradlew :time-java:test :time-java:detekt :time-java:check
```

The repository-level gate is:

```shell
./gradlew check
```

---

## Документация на русском языке

`time-java` — JVM infrastructure adapter модуль внутри `superkassa-server`. Он реализует порты времени из `superkassa-core-jvm` и подключается исполняемым модулем `server`.

### Ответственность

- `SystemClock` реализует `ClockPort`.
- `SystemTimeGuard` реализует `TimeValidatorPort`.
- Ошибки результата валидации возвращаются через `TimeValidationResult` с `TrilingualMessage` на русском, казахском и английском языках.
- Startup-политика не находится в этом модуле. Модуль `server` вызывает `ValidateSystemTimeOnStartupUseCase`, который зависит от `TimeValidatorPort` и `ClockPort`.

### Правила валидации

`SystemTimeGuard.validate(clock)` проверяет:

1. Системное время находится в допустимом диапазоне: с `2020-01-01T00:00:00Z` до `2100-01-01T00:00:00Z`.
2. Системное время не совершило критический скачок относительно монотонного `System.nanoTime()`.
3. Если внешний HTTP-эталон доступен, его `Date` header не расходится с локальным временем больше чем на пять минут.

Если внешний эталон недоступен или его `Date` header нельзя разобрать, локальная проверка может пройти успешно. Сетевая деградация эталона не считается автоматическим основанием для остановки кассы.

### Текущая интеграция

`server/src/main/kotlin/kz/mybrain/superkassa/core/config/AdaptersConfig.kt` публикует:

```kotlin
@Bean
fun timeValidatorPort(): TimeValidatorPort = SystemTimeGuard

@Bean
fun clockPort(): ClockPort = SystemClock
```

Проверка времени при запуске `server` выполняется через:

- `ValidateSystemTimeOnStartupUseCase`
- `StartupTimeValidationRunner`

Если время некорректно, выбрасывается `SystemTimeStartupValidationException`, и приложение не стартует. Сообщение исключения содержит русский, казахский и английский текст.

### Пример использования

```kotlin
import kz.mybrain.superkassa.core.data.adapter.SystemClock
import kz.mybrain.superkassa.core.data.adapter.SystemTimeGuard

val result = SystemTimeGuard.validate(SystemClock)
if (!result.ok) {
    error(result.trilingualMessage() ?: result.reason.orEmpty())
}
```

### Проверки качества

Обязательные проверки для модуля:

```shell
./gradlew :time-java:test :time-java:detekt :time-java:check
```

Общая проверка репозитория:

```shell
./gradlew check
```
