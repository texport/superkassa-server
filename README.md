# Superkassa Server

[![Build Status](https://github.com/texport/superkassa-server/actions/workflows/ci.yml/badge.svg)](https://github.com/texport/superkassa-server/actions)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.0.4-blue.svg)]()

The server-side component of the Superkassa online cashbox system. It provides a RESTful HTTP API to manage organization cashboxes, record shifts, process cash operations, queue tax notifications, and render/print receipts.

## Submodules
- **`:server`**: The executable Spring Boot application hosting HTTP REST API.
- **`:server-settings`**: Settings adapters (File-based and Central Database-based).
- **`:server-delivery`**: Adapters for notification channels (SMS, WhatsApp, Email, Telegram, physical Java Print Service printing).
- **`:server-converter`**: Receipt formatting converters (PDF, PNG, ESC/POS CP866 bytes, ZXing QR generation).
- **`:storage-jdbc`**: Relational database storage adapter (PostgreSQL, MySQL, SQLite) with migration files.
- **`:server-time`**: NTP-based clock synchronizer and validator.
- **`:shared-strings`**: Shared localized resources module.

---

## Integration with superkassa-core

This server integrates with `superkassa-core` Kotlin Multiplatform library to execute core fiscal operations:

### JVM / Gradle
To use `superkassa-core` in your JVM Gradle project, add the dependency from Maven Local or your repository:
```kotlin
dependencies {
    implementation("io.github.texport.superkassa:core-presentation:1.1.0")
}
```

---

## Architecture Boundary

The server project acts as the JVM host and integration layer:
- **Core Business Logic:** Handled strictly by the `superkassa-core` library.
- **Platform Adapters:** Local storage (`storage-jdbc`), networking (`ofd-network-client`), and API entry point (`server`) are implemented as separate JVM modules and wired via composition root in `ServicesConfig.kt`.

---

## License / Лицензия

This project is licensed under the Apache License 2.0.
