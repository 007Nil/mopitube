# Mopitube 🎵
_Beta Stage_

Mopitube is an Android music client for the [Mopidy](https://mopidy.com/) music server. It provides a modern Material Design 3 UI for browsing, searching, and playing music from your Mopidy instance.

## 🚀 Project Overview

-   **Language**: Kotlin 2.0.21
-   **UI Framework**: Jetpack Compose with Material3
-   **Architecture**: MVVM + Repository pattern, manual dependency wiring
-   **Minimum SDK**: 26 (Android 8)
-   **Target SDK**: 36
-   **Features**: WebSocket communication with Mopidy, JSON-RPC 2.0, local Room database for caching, DataStore for user preferences, Coil for image loading.

## ⚙️ Building and Running

This project uses Gradle. You can build and run the application using the following commands:

### ▶️ Standard Gradle Commands

These commands are executed from the project's root directory:

-   **Build Debug APK**: `./gradlew assembleDebug`
-   **Build Release APK**: `./gradlew assembleRelease`
-   **Clean Build Artifacts**: `./gradlew clean`
-   **Perform Full Build**: `./gradlew build`

### 💻 VS Code Task (Build, Run, and Logcat)

If you are using VS Code, there is a predefined task to build, install, launch the debug APK, and stream logcat output for the app's process.

```bash
echo '▶ Building & installing...'; ./gradlew installDebug; echo '▶ Launching app...'; adb shell am start -n com.nil.mopitube/.MainActivity; echo '▶ Waiting for app PID...'; until PID=$(adb shell pidof -s com.nil.mopitube); do sleep 0.5; done; echo "▶ PID=$PID"; adb logcat --pid=$PID
```

This command performs the following steps:
1.  Builds and installs the debug APK (`./gradlew installDebug`).
2.  Launches the application on a connected device or emulator (`adb shell am start ...`).
3.  Waits for the app's process ID (PID).
4.  Streams the logcat output filtered by the app's PID (`adb logcat --pid=$PID`).

## 📦 Dependencies

Key dependencies are managed in `app/build.gradle.kts` and include:

-   OkHttp3 (WebSocket communication)
-   kotlinx-serialization (JSON)
-   Room (local database)
-   Coil (image loading)
-   Navigation Compose
-   DataStore (preferences)

## 🛠️ Development Environment

-   **JVM Target**: 11
-   **Annotation Processing**: `kapt` for Room compiler.

## 🔒 Network Security

The `network_security_config.xml` is configured to allow cleartext traffic for `192.168.1.50` and `archive.org`.

## 🔑 Permissions

The application requests `INTERNET`, `FOREGROUND_SERVICE`, and `POST_NOTIFICATIONS` permissions. It also utilizes `PlaybackService` with `foregroundServiceType="mediaPlayback"` for media playback capabilities.