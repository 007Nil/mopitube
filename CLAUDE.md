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
./gradlew installDebug        # Build + install on connected device
./gradlew clean               # Clean build artifacts
./gradlew build               # Full build
```

Build, install, launch, and stream logcat (VS Code task also available):
```bash
./gradlew installDebug && adb shell am start -n com.nil.mopitube/.MainActivity && until PID=$(adb shell pidof -s com.nil.mopitube); do sleep 0.5; done && adb logcat --pid=$PID
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

- **`mopidy/`** — Core client layer. `MopidyClient` orchestrates the WebSocket connection, RPC client, and repository. `MopidyWebSocket` handles connection with exponential-backoff reconnection (1s to 30s, 15s ping interval). `MopidyRpcClient` manages JSON-RPC request/response correlation with `AtomicInteger` IDs and `ConcurrentHashMap` for pending requests (5s timeout). `MopidyRepository` wraps RPC calls with caching, likes, play history, and tracklist management. `MusicBrainzClient` fetches artist artwork from TheAudioDB API.

- **`navigation/`** — `AppNav.kt` is the root composable containing the full NavHost, bottom navigation (Home/Search/Liked Songs), drawer, scaffold, and all route definitions. `AppNavViewModel` owns the `MopidyClient` lifecycle and a `hasNavigatedFromStartup` flag. Routes are split into connection-independent (startup, settings, player) and connection-dependent (home, search, songs, albums, etc.) groups.

- **`database/`** — Room database (version 4, destructive migration — no proper migrations). Entities: `Track`, `LikedTrack`, `PlayHistoryEntry`, `ArtworkCacheEntry`. Single DAO (`MopitubeDao`) with singleton pattern (`INSTANCE` volatile field).

- **`data/`** — `UserPreferencesRepository` uses DataStore for server host/port preferences (datastore name: `"settings"`).

- **`ui/screens/`** — One composable per screen. Screens receive `MopidyRepository` directly (no per-screen ViewModels). Settings screens are in `ui/screens/settings/`.

- **`ui/components/`** — Reusable composables (track items, album cards, carousel, mini player, etc.).

### Nullable Client Components

`MopidyClient` exposes `rpc` and `repo` as nullable Compose `mutableStateOf` properties. They are `null` until `start()` is called and set back to `null` on `shutdown()`. UI code must null-check these before use — connection-dependent routes only render when `repo != null`.

### JSON-RPC Serialization

Mopidy responses are parsed dynamically using `kotlinx.serialization.json` types (`JsonObject`, `JsonArray`, `JsonElement`, `JsonPrimitive`). There are no typed data classes for RPC responses — all parsing is inline via extension properties like `.jsonObject`, `.jsonPrimitive?.content`. Requests are built with `buildJsonObject { }` / `buildJsonArray { }`.

### Navigation Routes

Routes use URL-encoded URIs as path parameters (e.g., `album/{albumUri}`, `playlist/{playlistUri}`), decoded with `URLDecoder.decode(it, "UTF-8")`. The startup screen is the NavHost start destination; after connection, a `LaunchedEffect` navigates to `home` exactly once via the `hasNavigatedFromStartup` flag.

Track click handling in `AppNav`: clears tracklist → adds selected track → fetches 19 random tracks for autoplay (filtering duplicates) → logs play history → navigates to player.

### Connection Model

`MopidyWebSocket` exposes `connectionState` as a `StateFlow<ConnectionState>` with states: `Idle`, `Connecting`, `Connected`, `Disconnected(reason)`. The RPC client waits for `Connected` before sending requests. Pending requests are cancelled on disconnect to prevent indefinite blocking. Reconnection uses exponential backoff (1s to 30s).

### Caching Strategy

- **Track cache**: 24-hour TTL, all tracks stored in Room. Timestamp tracked via SharedPreferences (not Room). Manual refresh available from settings.
- **Artwork cache (3-tier)**: In-memory LRU (300 entries, `ArtworkProvider` with `Mutex`) → Room database (`ArtworkCacheEntry`) → Mopidy RPC (`core.library.get_images`).
- **Play history**: 90-day retention, auto-pruned when new entries are logged.
- **Coil disk cache**: 100 MB in `cacheDir/image_cache`, configured in `App.kt`.

### Coroutine Patterns

- `MopidyClient` uses `CoroutineScope(Dispatchers.IO + SupervisorJob())` for proper cancellation isolation.
- Database operations use `withContext(Dispatchers.IO)`.
- `HomeScreen` uses `async` for concurrent data fetching.
- `PlayerScreen` polls Mopidy tracklist state on 1-second intervals and auto-appends random tracks when the queue is running low.
- `PlayerScreen` and `MiniPlayer` use `lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)` to only poll when foregrounded.

### Network Security

Cleartext traffic is permitted for `192.168.1.50` and `archive.org` via `network_security_config.xml`.

### Manifest Services

`PlaybackService` is declared with `foregroundServiceType="mediaPlayback"` and media session intent filters, but the implementation class does not yet exist — playback is currently managed entirely via Mopidy RPC calls from the UI layer. The app requests `INTERNET`, `FOREGROUND_SERVICE`, and `POST_NOTIFICATIONS` permissions.

## Known Legacy Code

- `mopidy/ArtworkCache.kt` defines a separate `ArtworkDatabase` that is not actively used. Artwork caching is handled by `MopitubeDatabase`'s `ArtworkCacheEntry` entity instead.

## Dependencies

Managed in `app/build.gradle.kts` with a Compose BOM (`2024.10.00`). Key libraries: OkHttp3 4.12.0 (WebSocket), kotlinx-serialization 1.7.3 (JSON), Room 2.6.1 (database), Coil 2.6.0 (image loading), Navigation Compose 2.8.3, DataStore 1.1.1 (preferences). Annotation processing uses `kapt` (Room compiler). JVM target: 11.
