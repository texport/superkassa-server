# superkassa-server-converter

[![CI Build](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![Version](https://img.shields.io/badge/Version-1.0.5-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](../LICENSE)

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

Infrastructure converter module for the **Superkassa** fiscalization system. Converts domain documents, receipts, and invoices into formats ready for rendering and client delivery.

### Key Features
- **`DocumentConvertAdapter`**: Main implementation of the `DocumentConvertPort`.
- **`QrCodeDataUriGenerator`**: Generates QR codes encoded as Data URIs for embedding in HTML/PDF receipts.
- Supports CP866 encoding conversions and three-language error/document handling.

---

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:server-converter:1.0.5")
}
```

---

### Usage Example

```kotlin
import io.github.texport.superkassa.jvm.receipt.impl.DocumentConvertAdapter

val convertAdapter = DocumentConvertAdapter()
val resultBytes = convertAdapter.convert(document)
```

---

## Документация на русском языке

Инфраструктурный модуль конвертации данных для системы фискализации **Superkassa**. Преобразует доменные документы, чеки и счета в форматы, готовые к визуализации, печати и отправке клиентам.

### Ключевые возможности
- **`DocumentConvertAdapter`**: Основная реализация порта `DocumentConvertPort`.
- **`QrCodeDataUriGenerator`**: Генерация QR-кодов в формате Data URI для встраивания в печатные формы HTML/PDF.
- Поддержка кодировки CP866 и трехъязычного перевода ошибок и текста документов.

---

### Установка

Добавьте зависимость в ваш `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.texport:server-converter:1.0.5")
}
```

---

### Пример использования

```kotlin
import io.github.texport.superkassa.jvm.receipt.impl.DocumentConvertAdapter

val convertAdapter = DocumentConvertAdapter()
val resultBytes = convertAdapter.convert(document)
```

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](../LICENSE).
