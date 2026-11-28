# tech-updates-android

[![CI](https://github.com/agent7jacob-7177/tech-updates-android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/agent7jacob-7177/tech-updates-android/actions/workflows/ci.yml)

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

### UI and screenshot tests

Compose UI tests and [Roborazzi](https://github.com/takahirom/roborazzi) screenshot tests run on the
JVM with Robolectric (no emulator) as part of `testDebugUnitTest`, which compares screenshots with the
goldens in `app/src/test/screenshots`. After an intended UI change, re-record and commit them:

```sh
./gradlew recordRoborazziDebug
```

On a mismatch, comparison images are written to `app/build/outputs/roborazzi` (CI uploads them as the
`screenshot-diffs` artifact). Goldens are recorded on Linux x86_64, like CI; other platforms may render
slightly differently.

Dependency versions live in `gradle/libs.versions.toml`.

## License

[MIT](LICENSE)
