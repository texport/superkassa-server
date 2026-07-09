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
- **`:offline-queue`**: Pure Kotlin Multiplatform queue orchestration module.

---

## Getting Started / Integration

### JVM / Gradle
To use the offline queue KMP module in your JVM Gradle project, add the following dependency:
```kotlin
dependencies {
    implementation("kz.mybrain.superkassa:offline-queue:1.0.3")
}
```

### iOS / Swift Package Manager
To integrate the offline queue module into an iOS client, add the Swift package dependency:
- **Repository URL:** `https://github.com/texport/superkassa-offline-queue.git`
- **Target Name:** `OfflineQueue`

---

## Quick Start / Usage

Here is a quick example of initializing `QueueService` and processing pending commands:

```kotlin
import kz.mybrain.superkassa.offline_queue.application.service.QueueService
import kz.mybrain.superkassa.offline_queue.application.service.QueueCommandHandler
import kz.mybrain.superkassa.offline_queue.application.model.DispatchResult
import kz.mybrain.superkassa.offline_queue.application.model.DispatchStatus

// 1. Implement Command Handler
val handler = QueueCommandHandler { command, renewLock ->
    // Process command logic
    DispatchResult(DispatchStatus.SENT)
}

// 2. Initialize Queue Service
val queueService = QueueService(
    storage = storagePort,
    lockPort = leaseLockPort,
    handler = handler,
    backoffPolicy = defaultBackoffPolicy,
    ownerId = "node-1"
)

// 3. Process next pending command
queueService.processNext(cashboxId = "kkm-123", lane = QueueLane.OFFLINE)
```

---

## Architecture Boundary

The `offline-queue` module contains pure multiplatform business logic for queue management and lease lock synchronization:
- **Core (commonMain):** Fully decoupled from Spring, JDBC, or platform-specific dependencies.
- **Platform Adapters:** Local storage (`storage-jdbc`), networking (`ofd-network-client`), and scheduler/worker logic (`server`) are implemented in separate JVM modules and wired via composition root.

---

## License / Лицензия

This project is licensed under the Apache License 2.0. See [LICENSE](time-java/LICENSE) for details.

Этот проект распространяется под лицензией Apache License 2.0. Подробности см. в файле [LICENSE](time-java/LICENSE).
