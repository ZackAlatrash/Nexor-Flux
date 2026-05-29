# Testing

## Commands
```bash
JAVA_HOME="$PWD/.jdks/amazon-corretto-17.jdk/Contents/Home" ANDROID_SDK_ROOT="$PWD/.android-sdk" ./gradlew test
JAVA_HOME="$PWD/.jdks/amazon-corretto-17.jdk/Contents/Home" ANDROID_SDK_ROOT="$PWD/.android-sdk" ./gradlew lint
JAVA_HOME="$PWD/.jdks/amazon-corretto-17.jdk/Contents/Home" ANDROID_SDK_ROOT="$PWD/.android-sdk" ./gradlew assembleDebug
JAVA_HOME="$PWD/.jdks/amazon-corretto-17.jdk/Contents/Home" ANDROID_SDK_ROOT="$PWD/.android-sdk" ./gradlew connectedAndroidTest
```

## Coverage
- Unit tests cover moving averages, trend slope, adherence, performance/recovery trend, and calorie adjustment verdicts.
- Instrumentation tests cover Room inserts and Today empty-state rendering.
- Manual checks should cover logging today's metrics, adding/deleting meals, saving foods/meals, quick-adding saved entries, viewing dashboard verdicts, changing plan targets, exporting/importing backup JSON, and resetting data.
