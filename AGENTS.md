# AGENTS.md — Slide Dictate

Read this before building. Repo root holds the Windows build script; the Gradle project lives in `slide-dictate/`.

## Layout

- `build-apk.ps1` — Windows one-shot build: installs SDK packages + Gradle, generates wrapper, runs `assembleDebug`.
- `slide-dictate/` — Gradle project (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`).
- `slide-dictate/app/` — the app (`build.gradle.kts`, `src/main/`).
- `slide-dictate/app/src/main/java/app/slidedictate/` — all Kotlin: `DictationService`, `Recorder`, `WhisperEngine`, `Transcriber`, `ModelStore`, `MainActivity`, `MicForegroundService`, `Prefs`.
- `slide-dictate/app/src/main/cpp/` — `whisper_jni.cpp` + `CMakeLists.txt`. Builds a stub if `cpp/whisper.cpp/` checkout is absent.
- `slide-dictate/README.md` — technical readme. `README.md` (here) — user-facing front page.

## Build (pick one)

Windows (needs ~1–2 GB SDK download on first run):

```powershell
.\build-apk.ps1
```

Manual (any OS with Android SDK installed):

```sh
cd slide-dictate
gradle wrapper --gradle-version 8.7   # once, no wrapper is checked in
./gradlew assembleDebug
```

APK out: `slide-dictate/app/build/outputs/apk/debug/app-debug.apk`.

Toolchain: Kotlin 1.9.24, AGP 8.5.2, `minSdk 26`, `compileSdk/targetSdk 34`, Java 17, NDK 26.1.10909125, CMake 3.22.1. Zero dependencies beyond Android SDK + Kotlin — keep it that way.

## Verify

1. `assembleDebug` must stay green with AND without the whisper.cpp checkout (CMake stub path covers the absent case — do not break it).
2. On a phone: install APK, enable Slide Dictate under Settings > Accessibility, download Tiny model, focus a text field, slide out the edge handle, dictate. With stub only, expect `[stub: N s of audio, model X]` typed into the field.
3. After touching JNI: confirm `System.loadLibrary("whisper_jni")` still resolves and `Engines.load()` falls back to stub instead of crashing when the native lib/model is missing.

## Rules for agents

- Smallest diff that builds. No new dependencies without asking.
- UI stays code-built Views (no XML layouts/themes) until the build is green on a real device.
- `DictationService` owns overlay + recording + insertion; `Recorder` stays 16 kHz mono PCM16; `ModelStore` URLs stay ggml unless the engine change requires otherwise.
- Do not commit: `local.properties`, `.gradle/`, `build/`, `*.apk`, downloaded `.bin` models, keystores (`.jks`/`.keystore`).
- Next work in order: (1) green build, (2) real whisper.cpp engine, (3) Android 14+ mic check, (4) chunked/VAD dictation. Details in `slide-dictate/AGENTS.md`.

## Release signing

`app/build.gradle.kts` signs `release` with `slideRelease` when a key is present,
debug key otherwise (warning logged). Never commit the keystore. One-time setup:

```powershell
keytool -genkeypair -v -keystore $env:USERPROFILE\slide-release.jks -alias slide -keyalg RSA -keysize 2048 -validity 10000
$env:SLIDE_KEYSTORE = "$env:USERPROFILE\slide-release.jks"
$env:SLIDE_KEYSTORE_PASSWORD = "..."
$env:SLIDE_KEY_ALIAS = "slide"
$env:SLIDE_KEY_PASSWORD = "..."
```

Local alternative without env vars: put `slide.keystore`, `slide.keystorePassword`,
`slide.keyAlias`, `slide.keyPassword` in `%USERPROFILE%\.gradle\gradle.properties`.
Then `./gradlew assembleRelease` and smoke-test transcription on the release APK,
since R8 only runs there.
