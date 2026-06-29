# Walkthrough - Clean Architecture Refactoring, Coverage, & Documentation

We have completed the refactoring, unit testing, and documentation tasks for `superkassa-core` and verified its integration with `superkassa-server`.

## Changes Made

### 1. Adapter Class Renaming & Consistency
- **Standardized Naming**: Renamed all data adapters in `:core-data` and `:storage-jdbc` to end strictly with `Adapter` rather than `Port` or `PortAdapter` suffixes:
  - `Base64TokenCodecPort` -> `Base64TokenCodecAdapter`
  - `DeliveryPortAdapter` -> `DeliveryServiceAdapter` (renamed to avoid conflict with `superkassa-delivery` library's `DeliveryAdapter` interface)
  - `OfdConfigPortAdapter` -> `OfdConfigAdapter`
  - `OfflineQueuePortAdapter` -> `OfflineQueueAdapter`
  - `ResilienceOfdManagerPortAdapter` -> `ResilienceOfdManagerAdapter`
  - `Sha256PinHasherPort` -> `Sha256PinHasherAdapter`
  - `StoragePortAdapter` -> `StorageAdapter`
- **Updated Wirings**: Refactored [AdaptersConfig.kt](file:///Users/sergeyivanov/superkassa-server/server/src/main/kotlin/kz/mybrain/superkassa/core/config/AdaptersConfig.kt), [ServicesConfig.kt](file:///Users/sergeyivanov/superkassa-server/server/src/main/kotlin/kz/mybrain/superkassa/core/config/ServicesConfig.kt), [CoreIntegrationTest.kt](file:///Users/sergeyivanov/superkassa-server/storage-jdbc/src/test/kotlin/kz/mybrain/superkassa/core/CoreIntegrationTest.kt), and [QueueLockTest.kt](file:///Users/sergeyivanov/superkassa-server/storage-jdbc/src/test/kotlin/kz/mybrain/superkassa/core/QueueLockTest.kt) to match the new class names.

### 2. Comprehensive Code Documentation
- Added detailed KotlinDoc and inline comments inside all 10 adapters in the `core-data` layer (`Base64TokenCodecAdapter`, `DeliveryServiceAdapter`, `OfdConfigAdapter`, `OfflineQueueAdapter`, `ResilienceOfdManagerAdapter`, `Sha256PinHasherAdapter`, `StorageBackedLeaseLockAdapter`, `StorageBackedQueueStorageAdapter`, `UuidGeneratorAdapter`).
- Documented method purposes, parameters, return types, circuit-breaker rules, and thread-safety details.

### 3. Mapped Technical Errors in Data Layer
- Created [DataErrorMessages.kt](file:///Users/sergeyivanov/superkassa-core/core-data/src/main/kotlin/kz/mybrain/superkassa/core/data/exception/DataErrorMessages.kt) to encapsulate all technical, trilingual error formatting for the infrastructure layer.
- Refactored [OfdManagerAdapter.kt](file:///Users/sergeyivanov/superkassa-core/core-data/src/main/kotlin/kz/mybrain/superkassa/core/data/adapter/OfdManagerAdapter.kt) to call `DataErrorMessages.ofdRequestFailed(...)`, completely removing raw string hardcoding of errors in the adapter.

### 4. Strict JaCoCo Coverage Gates & Adapter Unit Tests
- **Verification Rule**: Configured strict JaCoCo verification limits in [build.gradle.kts](file:///Users/sergeyivanov/superkassa-core/core-data/build.gradle.kts) to enforce a minimum of 85% instructions coverage for all adapter classes (`kz.mybrain.superkassa.core.data.adapter.*`).
- **New Unit Tests**: Created comprehensive, isolated unit tests:
  - `Base64TokenCodecAdapterTest` (valid/invalid base64, null checks, format parsing).
  - `Sha256PinHasherAdapterTest` (PIN string hashing matching).
  - `UuidGeneratorAdapterTest` (UUID generation and Kazakh year-based factory number generation checks).
  - `OfdConfigAdapterTest` (environments: `"PROD"` and `"TEST"`, case-insensitivity checks).
  - `DeliveryServiceAdapterTest` (moking `DeliveryService` success/fail channels).
  - `StorageBackedLeaseLockAdapterTest` & `StorageBackedQueueStorageAdapterTest` (verifying database delegation via mocks).
  - `OfflineQueueAdapterTest` (enqueuing commands, checking types mapping, processing batches).
  - `ResilienceOfdManagerAdapterTest` (testing Retry and Circuit Breaker transition gates).
  - `OfdManagerAdapterTest` (verifying network connection timeout exception handling via coroutines time provider simulation, throttling logic, and JSON response parsing).

## Verification Results

We verified both projects by running all tests and checks:
1. `superkassa-core` build verification:
   ```bash
   ./gradlew clean test check publishToMavenLocal
   ```
   - **Result**: `BUILD SUCCESSFUL` (all unit, integration, detekt rules, and JaCoCo coverage rules pass).
2. `superkassa-server` build verification:
   ```bash
   ./gradlew clean test check
   ```
   - **Result**: `BUILD SUCCESSFUL` (all integration and API tests pass).
