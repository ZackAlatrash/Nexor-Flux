# Recomp Tracker

Recomp Tracker is a fully offline Android MVP for logging nutrition, body metrics, recovery, marker lifts, and weekly calorie-adjustment decisions.

## Stack
- Kotlin
- Jetpack Compose and Material 3
- Single-activity architecture with Compose Navigation
- Room for local app data
- DataStore Preferences for plan settings
- Vico for progress charts
- kotlinx.serialization for local JSON backups

## Common commands
```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew connectedAndroidTest
```

## Local food libraries
- Import an official NEVO CSV export from Settings to search Dutch generic foods offline. When loaded, the app displays the required RIVM attribution.
- Export or merge-import personal foods as a separate JSON file without replacing logs or plan settings.
- With Health Connect nutrition and full-history permissions, scan the last 365 days of named nutrition records and review selected foods before saving them locally.

NEVO data are not bundled in this repository or APK. Download the official dataset or CSV export from RIVM after accepting its conditions, then import the local file through Settings.

Android builds require Java 17 and an Android SDK with API 36 installed. This workspace can use the local `.android-sdk` and `.jdks` directories when present:

```bash
JAVA_HOME="$PWD/.jdks/amazon-corretto-17.jdk/Contents/Home" ANDROID_SDK_ROOT="$PWD/.android-sdk" ./gradlew assembleDebug
```
