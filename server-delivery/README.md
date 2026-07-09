# superkassa-delivery

[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/superkassa-server/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/superkassa-server/actions)
[![Version](https://img.shields.io/badge/Version-1.0-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../time-java/LICENSE)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Infrastructure delivery adapters for the **Superkassa** fiscalization system. Implements delivery ports defined in the core to send receipts, tickets, and notifications through various channels.

### Key Features
- **`PrintDeliveryAdapter` & `JpsPrintDeliveryAdapter`**: Printing support for POS terminals and hardware receipt printers.
- **`SmsDeliveryAdapter`**: Send notifications over SMS gateways.
- **`WhatsAppDeliveryAdapter`**: Deliver receipts directly to customers via WhatsApp Business API.
- **`TelegramDeliveryAdapter`**: Deliver notifications to Telegram chats/bots.
- **`EmailDeliveryAdapter`**: SMTP/HTTP email dispatching.
- **`BaseHttpDeliveryAdapter`**: Reusable base class for HTTP-based notification gateways.

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-delivery:1.0")
}
```

---

### Usage Example

```kotlin
import io.github.texport.superkassa.jvm.delivery.impl.TelegramDeliveryAdapter

val telegramAdapter = TelegramDeliveryAdapter(httpClient, config)
telegramAdapter.send(document)
```

---

## Документация на русском языке

Инфраструктурные адаптеры отправки и печати для системы фискализации **Superkassa**. Реализует порты отправки, объявленные в ядре системы, для доставки чеков, билетов и уведомлений через различные каналы связи.

### Ключевые возможности
- **`PrintDeliveryAdapter` и `JpsPrintDeliveryAdapter`**: Поддержка печати чеков на POS-терминалах и аппаратных принтерах чеков.
- **`SmsDeliveryAdapter`**: Отправка уведомлений через SMS-шлюзы.
- **`WhatsAppDeliveryAdapter`**: Доставка чеков клиентам через WhatsApp Business API.
- **`TelegramDeliveryAdapter`**: Доставка уведомлений в Telegram чаты и боты.
- **`EmailDeliveryAdapter`**: Отправка писем по электронной почте через SMTP/HTTP.
- **`BaseHttpDeliveryAdapter`**: Базовый абстрактный класс для переиспользования логики HTTP-шлюзов доставки.

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:superkassa-delivery:1.0")
}
```

---

### Пример использования

```kotlin
import io.github.texport.superkassa.jvm.delivery.impl.TelegramDeliveryAdapter

val telegramAdapter = TelegramDeliveryAdapter(httpClient, config)
telegramAdapter.send(document)
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../time-java/LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../time-java/LICENSE).
