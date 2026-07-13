# Superkassa Server

[![Build Status](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.5-blue.svg)]()

---

### [Documentation in English](#documentation-in-english) &middot; [Документация на русском языке](#документация-на-русском-языке)

---

## Documentation in English

The server-side component of the Superkassa online cashbox system. It provides a RESTful HTTP API to manage organization cashboxes, record shifts, process cash operations, queue tax notifications, and render/print receipts.

### Submodules
- **`:server`**: The executable Spring Boot application hosting HTTP REST API.
- **`:server-settings`**: Settings adapters (File-based and Central Database-based).
- **`:server-delivery`**: Adapters for notification channels (SMS, WhatsApp, Email, Telegram, physical Java Print Service printing).
- **`:server-converter`**: Receipt formatting converters (PDF, PNG, ESC/POS CP866 bytes, ZXing QR generation).
- **`:storage-jdbc`**: Relational database storage adapter (PostgreSQL, MySQL, SQLite) with migration files.
- **`:server-time`**: NTP-based clock synchronizer and validator.
- **`:shared-strings`**: Shared localized resources module.

### Architecture Boundary

The server project acts as the JVM host and integration layer:
- **Core Business Logic:** Handled strictly by the `superkassa-core` library.
- **Platform Adapters:** Local storage (`storage-jdbc`), networking (`ofd-network-client`), and API entry point (`server`) are implemented as separate JVM modules and wired via composition root in `ServicesConfig.kt`.

---

## Документация на русском языке

Серверный компонент системы онлайн-касс Superkassa. Предоставляет REST HTTP API для управления кассами организации, учета смен, фискализации операций, отправки уведомлений в ОФД и печати чеков.

### Подмодули
- **`:server`**: Исполняемое Spring Boot приложение, предоставляющее HTTP REST API.
- **`:server-settings`**: Адаптеры настроек (файловый и на базе центральной БД).
- **`:server-delivery`**: Адаптеры каналов отправки уведомлений (SMS, WhatsApp, Email, Telegram, печать через Java Print Service).
- **`:server-converter`**: Конвертеры форматирования чеков (PDF, PNG, ESC/POS байты CP866, генерация QR-кодов ZXing).
- **`:storage-jdbc`**: Адаптер реляционных баз данных (PostgreSQL, MySQL, SQLite) с файлами миграций.
- **`:server-time`**: Валидатор и синхронизатор времени на базе NTP.
- **`:shared-strings`**: Общий модуль локализованных строковых ресурсов.

### Границы архитектуры

Серверный проект выступает в качестве JVM-хоста и слоя интеграции:
- **Ядро бизнес-логики:** Реализовано исключительно в рамках подключаемой библиотеки `superkassa-core`.
- **Адаптеры платформы:** Локальное хранилище (`storage-jdbc`), сеть (`ofd-network-client`) и точка входа API (`server`) выделены в отдельные JVM-модули и связываются через композиционный корень в `ServicesConfig.kt`.

---

## License / Лицензия

This project is licensed under the Apache License 2.0.
