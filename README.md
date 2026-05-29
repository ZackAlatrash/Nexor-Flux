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

Android builds require Java 17 and an Android SDK with API 36 installed. This workspace can use the local `.android-sdk` and `.jdks` directories when present:

```bash
JAVA_HOME="$PWD/.jdks/amazon-corretto-17.jdk/Contents/Home" ANDROID_SDK_ROOT="$PWD/.android-sdk" ./gradlew assembleDebug
```
