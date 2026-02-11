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
                                                              └── Room Database + DataStore
```

### Key Layers

- **`mopidy/`** — Core client layer. `MopidyClient` orchestrates the WebSocket connection, RPC client, and repository. `MopidyWebSocket` handles connection with exponential-backoff reconnection (1s to 30s, 15s ping interval). `MopidyRpcClient` manages JSON-RPC request/response correlation with `AtomicInteger` IDs and `ConcurrentHashMap` for pending requests (5s timeout). `MopidyRepository` wraps RPC calls with caching, likes, play history, and tracklist management.

- **`navigation/`** — `AppNav.kt` is the root composable containing the full NavHost, bottom navigation (Home/Search/Liked Songs), drawer, scaffold, and all route definitions. `AppNavViewModel` owns the `MopidyClient` lifecycle and a `hasNavigatedFromStartup` flag. Routes are split into connection-independent (startup, settings, player) and connection-dependent (home, search, songs, albums, etc.) groups.

- **`database/`** — Room database (version 4, destructive migration). Entities: `Track`, `LikedTrack`, `PlayHistoryEntry`, `ArtworkCacheEntry`. Single DAO (`MopitubeDao`) with singleton pattern (`INSTANCE` volatile field).

- **`data/`** — `UserPreferencesRepository` uses DataStore for server host/port preferences (datastore name: `"settings"`).

- **`ui/screens/`** — One composable per screen. Screens receive `MopidyRepository` directly (no per-screen ViewModels).

- **`ui/components/`** — Reusable composables (track items, album cards, carousel, mini player, etc.).

### Nullable Client Components

`MopidyClient` exposes `rpc` and `repo` as nullable Compose `mutableStateOf` properties. They are `null` until `start()` is called and set back to `null` on `shutdown()`. UI code must null-check these before use — connection-dependent routes only render when `repo != null`.

### Navigation Routes

Routes use URL-encoded URIs as path parameters (e.g., `album/{albumUri}`, `playlist/{playlistUri}`). The startup screen is the NavHost start destination; after connection, a `LaunchedEffect` navigates to `home` exactly once via the `hasNavigatedFromStartup` flag.

Track click handling in `AppNav`: clears tracklist → adds selected track → fetches random tracks for autoplay (filtering duplicates) → logs play history → navigates to player.

### Connection Model

`MopidyWebSocket` exposes `connectionState` as a `StateFlow<ConnectionState>` with states: `Idle`, `Connecting`, `Connected`, `Disconnected(reason)`. The RPC client waits for `Connected` before sending requests. Reconnection uses exponential backoff (1s to 30s).

### Coroutine Patterns

- `MopidyClient` uses `CoroutineScope(Dispatchers.IO + SupervisorJob())` for proper cancellation isolation.
- Database operations use `withContext(Dispatchers.IO)`.
- `HomeScreen` uses `async` for concurrent data fetching.
- `PlayerScreen` polls Mopidy tracklist state on 1-second intervals and auto-appends random tracks when the queue is running low.
- `ArtworkProvider` uses `Mutex` for thread-safe in-memory image URL caching.

### Network Security

Cleartext traffic is permitted for `192.168.1.50` and `archive.org` via `network_security_config.xml`.

### Manifest Services

`PlaybackService` is declared with `foregroundServiceType="mediaPlayback"` and media session intent filters. The app also requests `INTERNET`, `FOREGROUND_SERVICE`, and `POST_NOTIFICATIONS` permissions.

## Dependencies

Managed in `app/build.gradle.kts` with a Compose BOM (`2024.10.00`). Key libraries: OkHttp3 4.12.0 (WebSocket), kotlinx-serialization 1.7.3 (JSON), Room 2.6.1 (database), Coil 2.6.0 (image loading), Navigation Compose 2.8.3, DataStore 1.1.1 (preferences). Annotation processing uses `kapt` (Room compiler). JVM target: 11.
