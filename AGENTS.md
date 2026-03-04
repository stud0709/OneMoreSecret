# Workspace Setup Notes (Linux-native clone)

## Repository
- Path: `/home/ydzh/workspace/OneMoreSecret-linux`
- Remote: `https://github.com/stud0709/OneMoreSecret.git`
- Active branch used: `kotlin1`

## Java setup (user-level)
- Installed via SDKMAN.
- JDK: Temurin `17.0.11`.
- Environment in `~/.profile`:
  - `JAVA_HOME=$HOME/.sdkman/candidates/java/current`
  - `PATH=$JAVA_HOME/bin:$PATH`

## Android SDK setup (user-level)
- SDK root: `/home/ydzh/Android/Sdk`
- Command-line tools installed under:
  - `/home/ydzh/Android/Sdk/cmdline-tools/latest`
- Installed packages:
  - `platform-tools`
  - `platforms;android-36`
  - `build-tools;36.0.0`
- Licenses accepted with `sdkmanager --licenses`.
- Environment in `~/.profile`:
  - `ANDROID_HOME=$HOME/Android/Sdk`
  - `ANDROID_SDK_ROOT=$ANDROID_HOME`
  - `PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH`

## Verified build command
Run from clone root:

```bash
source "$HOME/.profile"
bash ./gradlew :app:compileStandardDebugKotlin --no-daemon --console=plain
```

Status last run: **BUILD SUCCESSFUL**.

## Gradle wrapper permission workaround
- In this workspace, `./gradlew` can fail with `Permission denied`.
- Use `bash gradlew <tasks>` (or `bash ./gradlew <tasks>`) as the default workaround.

## Current code state in this clone
- WiFi pairing migrated to Compose/Kotlin:
  - `WiFiPairingFragment.java` -> `WiFiPairingFragment.kt`
  - `MsgPluginWiFiPairing.java` -> `MsgPluginWiFiPairing.kt`
  - Added `app/src/main/java/com/onemoresecret/composable/WiFiPairing.kt`
  - Removed `app/src/main/res/layout/fragment_wi_fi_pairing.xml`
- `MessageFragment.kt` updated to require non-null message bytes in `onMessage()`:
  - `requireNotNull(requireArguments().getByteArray(QRFragment.ARG_MESSAGE))`
