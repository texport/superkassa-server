# superkassa-server-delivery

[![CI Build](https://img.shields.io/github/actions/workflow/status/texport/superkassa-server/ci.yml?branch=main&label=CI%20Build)](https://github.com/texport/superkassa-server/actions)
[![Version](https://img.shields.io/badge/Version-1.0.5-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../LICENSE)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Printing adapters of the **Superkassa** node. They implement the delivery port of the core for receipt printers.

SMS, Telegram, WhatsApp and email are not here: the node assembles them from the core module `superkassa-delivery-channels`, the same channels the embedded cashbox uses.

### Key Features
- **`PrintDeliveryAdapter`**: ESC/POS bytes to a network receipt printer over a raw TCP socket.
- **`JpsPrintDeliveryAdapter`**: printing to a printer registered in the operating system (Java Print Service).

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:server-delivery:1.0.5")
}
```

---

### Usage Example

```kotlin
import io.github.texport.superkassa.jvm.delivery.impl.PrintDeliveryAdapter

val printer = PrintDeliveryAdapter(host = "192.168.1.50", port = 9100)
printer.send(request)
```

---

## Документация на русском языке

Печатные адаптеры узла **Superkassa**. Реализуют порт доставки ядра для принтеров чеков.

SMS, Telegram, WhatsApp и почты здесь нет: узел собирает их из модуля ядра `superkassa-delivery-channels` — те же каналы, что у встраиваемой кассы.

### Ключевые возможности
- **`PrintDeliveryAdapter`**: байты ESC/POS на сетевой принтер чеков через TCP-сокет.
- **`JpsPrintDeliveryAdapter`**: печать на принтер, зарегистрированный в операционной системе (Java Print Service).

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:server-delivery:1.0.5")
}
```

---

### Пример использования

```kotlin
import io.github.texport.superkassa.jvm.delivery.impl.PrintDeliveryAdapter

val printer = PrintDeliveryAdapter(host = "192.168.1.50", port = 9100)
printer.send(request)
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../LICENSE).
