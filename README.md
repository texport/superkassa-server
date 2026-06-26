# Superkassa Server

[![Build Status](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](time-java/LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()

*Read this in other languages: [English](README.md), [Русский](README.ru.md)*

The server-side component of the Superkassa online cashbox system. It provides a RESTful HTTP API to manage organization cashboxes, record shifts, process cash operations, queue tax notifications, and render/print receipts.

## Submodules
- **`:server`**: The executable Spring Boot application hosting HTTP REST API.
- **`:server-settings`**: Settings adapters (File-based and Central Database-based).
- **`:server-delivery`**: Adapters for notification channels (SMS, WhatsApp, Email, Telegram, physical Java Print Service printing).
- **`:server-converter`**: Receipt formatting converters (PDF, PNG, ESC/POS CP866 bytes, ZXing QR generation).
- **`:storage-jdbc`**: Relational database storage adapter (PostgreSQL, MySQL, SQLite) with migration files.
- **`:time-java`**: NTP-based clock synchronizer and validator.

---

# Сервер Superkassa

[![Build Status](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](time-java/LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0-blue.svg)]()
[![Coverage](https://img.shields.io/badge/Coverage-100%25-brightgreen.svg)]()

*Читать на других языках: [English](README.md), [Русский](README.ru.md)*

Серверная часть системы онлайн-кассы Superkassa, предоставляющая HTTP REST API для управления кассами организации, учета смен, проведения кассовых операций, оффлайн-доставки фискальных документов в ОФД и печати/рендеринга чеков.

## Субмодули
- **`:server`**: Исполняемое приложение Spring Boot, предоставляющее REST API.
- **`:server-settings`**: Адаптеры настроек (локальный файл и реляционная СУБД).
- **`:server-delivery`**: Адаптеры каналов отправки (SMS, WhatsApp, Email, Telegram, физическая печать JPS).
- **`:server-converter`**: Конвертеры форматов чеков (PDF, PNG, ESC/POS байты CP866, генератор QR-кодов).
- **`:storage-jdbc`**: Репозиторий хранения в СУБД (PostgreSQL, MySQL, SQLite) с файлами миграций.
- **`:time-java`**: Синхронизатор и валидатор времени по NTP.

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](time-java/LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](time-java/LICENSE).
