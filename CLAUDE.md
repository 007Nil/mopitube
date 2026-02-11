# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Mopitube is an Android music client for the [Mopidy](https://mopidy.com/) music server. It connects to a Mopidy instance over WebSocket (JSON-RPC 2.0) and provides a Material Design 3 UI for browsing, searching, and playing music.

- **Package**: `com.nil.mopitube`
- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose with Material3
- **Min SDK**: 26 (Android 8) / **Target SDK**: 36
- **Single-module** Android app (`app/`)

## Build Commands

```bash
./gradlew assembleDebug       # Build debug APK
./gradlew assembleRelease     # Build release APK
./gradlew clean               # Clean build artifacts
./gradlew build               # Full build
```

No tests are currently configured (test directories exist but are empty).

## Architecture

**Pattern**: MVVM + Repository, no DI framework (manual dependency wiring).

### Data Flow

```
Compose Screens → AppNav (navigation) → AppNavViewModel → MopidyClient
                                                              ├── MopidyWebSocket (OkHttp3)
                                                              ├── MopidyRpcClient (JSON-RPC 2.0)
                                                              ├── MopidyRepository (business logic)
                                                              ├── QueueManager (internal playback queue)
                                                              └── Room Database + DataStore
```

### Key Layers

- **`mopidy/`** — Core client layer. `MopidyClient` orchestrates the WebSocket connection, RPC client, and repository. `MopidyWebSocket` handles connection with exponential-backoff reconnection. `MopidyRpcClient` manages JSON-RPC request/response correlation with atomic IDs. `MopidyRepository` wraps RPC calls with caching, likes, play history, and queue management.

- **`navigation/`** — `AppNav.kt` is the root composable containing the full NavHost, bottom navigation (Home/Search/Library), drawer, scaffold, and all route definitions. `AppNavViewModel` owns the `MopidyClient` lifecycle. Routes are split into connection-independent (startup, settings, player) and connection-dependent (home, search, songs, albums, etc.) groups.

- **`database/`** — Room database (version 4, destructive migration). Entities: `Track`, `LikedTrack`, `PlayHistoryEntry`, `ArtworkCacheEntry`. Single DAO (`MopitubeDao`) for all queries.

- **`data/`** — `UserPreferencesRepository` uses DataStore for server host/port preferences.

- **`ui/screens/`** — One composable per screen. Screens receive `MopidyRepository` directly (no per-screen ViewModels).

- **`ui/components/`** — Reusable composables (track items, album cards, carousel, mini player, etc.).

### Navigation Routes

Routes use URL-encoded URIs as path parameters (e.g., `album/{albumUri}`, `playlist/{playlistUri}`). The startup screen is the NavHost start destination; after connection, a `LaunchedEffect` navigates to `home` exactly once.

### Connection Model

`MopidyWebSocket` exposes `connectionState` as a `StateFlow<ConnectionState>` with states: `Idle`, `Connecting`, `Connected`, `Disconnected(reason)`. The RPC client waits for `Connected` before sending requests. Reconnection uses exponential backoff (1s to 30s).

### Network Security

Cleartext traffic is permitted for `192.168.1.50` and `archive.org` via `network_security_config.xml`.

## Dependencies

Managed via version catalog (`gradle/libs.versions.toml`). Key libraries: OkHttp3 (WebSocket), kotlinx-serialization (JSON), Room (database), Coil (image loading), Navigation Compose, DataStore (preferences). Annotation processing uses `kapt` (for Room compiler).
