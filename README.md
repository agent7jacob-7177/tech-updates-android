# tech-updates-android

Code7 Tech Updates — a native Android (Kotlin + Jetpack Compose, Material 3) Hacker News reader.
It is a port of the [Code7 Tech Updates web app](https://agent7jacob-7177.github.io/code7/).

## Requirements

- JDK 21 (used to run Gradle; the app targets JVM 17 bytecode)
- Android SDK with platform `android-37` (compileSdk / targetSdk 37), minSdk 26
- No local Gradle install needed — use the included wrapper (Gradle 9.9.3)

Point Gradle at the SDK via `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=/path/to/Android/Sdk`.

## Build & test

```sh
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew lintDebug            # Android lint
./gradlew ktlintCheck          # Kotlin style (ktlintFormat to auto-fix)
./gradlew detekt               # static analysis (config/detekt/detekt.yml)
```

All checks at once:

```sh
./gradlew assembleDebug lintDebug ktlintCheck detekt testDebugUnitTest
```

Dependency versions live in `gradle/libs.versions.toml`.

## License

[MIT](LICENSE)
