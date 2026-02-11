# GEMINI.md

This document serves as a comprehensive guide to the Mopitube project for the Gemini CLI agent, providing essential context for future interactions and development tasks.

## Project Overview

Mopitube is an Android music client designed for the [Mopidy](https://mopidy.com/) music server. It establishes a connection to a Mopidy instance via WebSocket (JSON-RPC 2.0) and presents a modern Material Design 3 user interface for browsing, searching, and playing music.

- **Package**: `com.nil.mopitube`
- **Language**: Kotlin (version 2.0.21)
- **UI Framework**: Jetpack Compose with Material3
- **Minimum SDK**: 26 (Android 8)
- **Target SDK**: 36
- **Project Structure**: Single-module Android application located in the `app/` directory.

### Architecture Highlights

The project follows an MVVM (Model-View-ViewModel) + Repository pattern, with manual dependency wiring (no explicit Dependency Injection framework).

**Key Data Flow**:
`Compose Screens` → `AppNav` (navigation) → `AppNavViewModel` → `MopidyClient`
`MopidyClient` orchestrates:
  - `MopidyWebSocket` (using OkHttp3)
  - `MopidyRpcClient` (JSON-RPC 2.0)
  - `MopidyRepository` (business logic, caching, likes, play history, tracklist management)
  - Integration with `Room Database` and `DataStore`

### Core Components

- **`mopidy/`**: Contains the core client logic, including WebSocket communication, RPC handling, and the main repository for Mopidy interactions. Features exponential-backoff reconnection and robust JSON-RPC request management.
- **`navigation/`**: Manages the application's navigation flow, including `NavHost`, bottom navigation, drawer, and route definitions. `AppNavViewModel` manages the `MopidyClient` lifecycle.
- **`database/`**: Implements local data persistence using Room, storing `Track`, `LikedTrack`, `PlayHistoryEntry`, and `ArtworkCacheEntry`.
- **`data/`**: Handles user preferences, specifically server host/port, using DataStore.
- **`ui/screens/`**: Houses individual screen composables.
- **`ui/components/`**: Provides reusable UI components.

## Building and Running

The project uses Gradle for its build system. The following commands can be executed from the project root directory:

-   **Build Debug APK**: `./gradlew assembleDebug`
-   **Build Release APK**: `./gradlew assembleRelease`
-   **Clean Build Artifacts**: `./gradlew clean`
-   **Perform Full Build**: `./gradlew build`

To run the application, you would typically build an APK and install it on an Android device or emulator.

## Development Conventions

### Dependencies

Dependencies are managed in `app/build.gradle.kts` and utilize a Compose Bill of Materials (BOM) version `2024.10.00`. Notable libraries include:
-   OkHttp3 4.12.0 (for WebSocket communication)
-   kotlinx-serialization 1.7.3 (for JSON serialization/deserialization)
-   Room 2.6.1 (for local database persistence)
-   Coil 2.6.0 (for image loading)
-   Navigation Compose 2.8.3
-   DataStore 1.1.1 (for preferences)
Annotation processing uses `kapt` for the Room compiler. The JVM target is 11.

### Testing Practices

The project currently has test directories (`app/src/androidTest`, `app/src/test`) but no tests are configured or present. This implies that testing is not yet a primary focus or is handled manually.

### Code Style

The project uses Kotlin and Jetpack Compose. Given the nature of Android development, it adheres to standard Kotlin coding conventions and Compose best practices (e.g., state management, composable design). The `CLAUDE.md` also indicates specific coroutine patterns and nullable client component handling.

### Network Security

The `network_security_config.xml` allows cleartext traffic for `192.168.1.50` and `archive.org`.

### Permissions

The application requests `INTERNET`, `FOREGROUND_SERVICE`, and `POST_NOTIFICATIONS` permissions. It also declares `PlaybackService` with `foregroundServiceType="mediaPlayback"` and media session intent filters in the Android Manifest.
