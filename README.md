# Veste-AI — Virtual Dressing Room for Android

**Veste-AI** is a native Android application that turns your phone into an on‑demand virtual dressing room. Digitise your wardrobe, take a full‑body baseline photo, and instantly preview realistic outfit combinations on your own image.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose (Material 3) |
| DI | Hilt (Dagger) |
| Database | SQLite via Room Persistence Library |
| State management | Navigation Compose |
| Serialisation | kotlinx.serialization |
| Testing | JUnit 4 + Robolectric |

## Prerequisites

- **Java 17** (JDK) – the build toolchain targets JVM 17.
- **Android SDK** – installed and pointed to in `local.properties`:
  ```
  sdk.dir=/path/to/android-sdk
  ```
  The project requires at least SDK 37 for compilation and targets API levels 26–36.

- **Gradle** – the wrapper (`gradlew` / `gradlew.bat`) bootstraps the correct version automatically. A standalone Gradle installation is not required.

## Building

All commands are run from the repository root.

### Debug build (default)

```bash
./gradlew app:assembleDebug
```

The debug variant includes additional tooling (UI manifest, HTTP logging) and is **unminified**.

### Release build

```bash
./gradlew app:assembleRelease
```

The release variant is minified with ProGuard and excludes debug-only assets.

### Clean build artefacts

```bash
./gradlew clean
```

## Running

### On a connected Android device or emulator

After a successful `assembleDebug` (or `assembleRelease`), install the produced APK with `adb`:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

For the release variant, replace `debug` with `release` (the release APK is unsigned; sign it before distribution).

### Compose Preview (IDE only)

The debug variant includes Compose Preview support for use inside JetBrains IDEs. The release variant deliberately disables unit tests, which blocks Compose Preview – this is expected and does not affect builds or device runs.

> **Note:** `local.properties` must point to an Android SDK that matches your device's API level. The project currently targets API levels 26 (minimum) through 36 (target), with compilation SDK 37.

### Using an IDE

The project ships with JetBrains IDEA configuration (`.idea/`). Open the root directory as a Gradle project:

1. **File → Open…** → select the repository root.
2. Let IDEA index the project and sync Gradle dependencies.
3. Select the `app` run configuration or create a new Android Run Configuration targeting your device/emulator.

An Android SDK must still be configured in **File → Settings → Build, Execution, Deployment → Android SDK**.

## Testing

### Unit tests

```bash
./gradlew app:test
```

Tests run with Robolectric (no device or emulator needed). UI tests use Jetpack Compose's JUnit 4 test manifest.

### Accessibility tests

```bash
./gradlew app:test --tests "*A11yTest*"
```

## Project structure

```
veste-ai/
├── app/
│   ├── src/
│   │   ├── main/java/com/vesteai/     # Application sources
│   │   ├── test/java/com/vesteai/     # Unit & UI tests
│   │   ├── debug/java/com/vesteai/    # Debug-only modules
│   │   └── testDebug/java/com/vesteai/ # Tests for debug modules
│   ├── schemas/                       # Room database schemas (exported)
│   └── build.gradle.kts               # App module build config
├── gradle/
│   ├── libs.versions.toml             # Centralised dependency versions
│   └── wrapper/                       # Gradle wrapper files
├── build.gradle.kts                   # Root build config (plugin aliases)
├── settings.gradle.kts                # Project settings & repositories
├── gradle.properties                  # Global Gradle properties
├── gradlew                            # Unix Gradle wrapper
├── gradlew.bat                        # Windows Gradle wrapper
├── local.properties                   # **Local config** – SDK path (not checked in)
└── PRD.md                             # Product Requirements Document
```

## Key dependencies (as defined in `gradle/libs.versions.toml`)

| Dependency | Version |
|---|---|
| Android Gradle Plugin | 9.5.0 |
| Kotlin | 2.4.21 |
| Jetpack Compose BOM | 2026.09.00 |
| Room (SQLite) | 2.8.5 |
| Hilt | 2.60.1 |
| Navigation Compose | 2.10.2 |
| Camera | 1.6.2 |
| OkHttp | 5.5.1 |
| Robolectric | 4.17 |

## License

See [LICENSE](LICENSE).