# Slide Dictate

Edge-handle voice typing for Android. Slide out the handle on the screen edge, tap Start, speak, and the text is typed into the focused field. Same idea as Handy on desktop.

Speech recognition runs fully on-device with downloaded open-source Whisper (ggml) models. No audio leaves the phone.

## How it works

1. `DictationService` (AccessibilityService) draws a 22x96dp handle on the right screen edge as a `TYPE_ACCESSIBILITY_OVERLAY` — no "draw over other apps" permission needed.
2. Slide left / tap the handle to open a 300dp panel (`Start dictation`, status, `Offline only` switch).
3. Tap Start: `Recorder` captures 16 kHz mono PCM16 via `AudioRecord` (`VOICE_RECOGNITION`), splits speech on 1.2 s pauses and emits each chunk live (`onChunk`); `stop()` returns the un-emitted tail. A `MicForegroundService` (type `microphone`) holds mic access so Android 14+ doesn't block background capture. Tap Stop to finish.
4. `WhisperEngine` (whisper.cpp via JNI `libwhisper_jni`) transcribes on a background thread: PCM16 → float in [-1, 1] → `nativeTranscribe`. Language is `en` for `.en` models, auto-detect otherwise. Without the native lib it falls back to `StubTranscriber`.
5. Text is inserted into the focused input node via `ACTION_SET_TEXT` (cursor preserved, smart leading space), fallback to clipboard + `ACTION_PASTE`, else copied with a "no field focused" message. Each finished chunk is inserted as it arrives, so text appears while speaking.
6. Online mode: with Offline only off, dictation uses Google voice typing (`SpeechRecognizer`, needs Google services) with live partial results in the status line.

## Setup

1. Open the app, allow microphone.
2. Enable Slide Dictate under Settings > Accessibility (button in app opens it).
3. Download a model (Tiny 75 MB to start), tap `Use`.
4. Focus any text field in any app, slide out the handle, dictate.

## Models

Downloaded from Hugging Face (`ggerganov/whisper.cpp`) into app-private `filesDir/models`:

| ID | File | Size |
|----|------|------|
| tiny | `ggml-tiny.en.bin` | 75 MB |
| base | `ggml-base.en.bin` | 142 MB |
| small | `ggml-small.en.bin` | 466 MB |
| tiny-multi | `ggml-tiny.bin` | 75 MB |
| base-multi | `ggml-base.bin` | 142 MB |
| small-multi | `ggml-small.bin` | 466 MB |
| tiny.en-q5_1 | `ggml-tiny.en-q5_1.bin` | 31 MB |
| base.en-q5_1 | `ggml-base.en-q5_1.bin` | 57 MB |
| small.en-q5_1 | `ggml-small.en-q5_1.bin` | 181 MB |
| tiny-q5_1 | `ggml-tiny-q5_1.bin` | 31 MB |
| base-q5_1 | `ggml-base-q5_1.bin` | 57 MB |
| small-q5_1 | `ggml-small-q5_1.bin` | 181 MB |
| medium.en | `ggml-medium.en.bin` | 1.4 GB |
| medium.en-q5_0 | `ggml-medium.en-q5_0.bin` | 514 MB |
| medium-multi | `ggml-medium.bin` | 1.4 GB |
| medium-multi-q5_0 | `ggml-medium-q5_0.bin` | 514 MB |

English-only and multilingual side by side. Download writes to `.part`, resumes with HTTP Range, then renames on completion. The finished file's SHA-256 is verified against the pinned hash; mismatches are deleted and fail the download.

## Build

Requirements: Android SDK, Kotlin 1.9.24, AGP 8.5.2, `minSdk 26`, `compileSdk/targetSdk 34`.

```sh
cd slide-dictate
gradle wrapper --gradle-version 8.7   # once, no wrapper is checked in
./gradlew assembleDebug
```

CI (`.github/workflows/android.yml`) builds debug and release APKs on every push and attaches them to the GitHub release page on `v*` tags. Set `SLIDE_KEYSTORE`, `SLIDE_KEYSTORE_PASSWORD`, `SLIDE_KEY_ALIAS` and `SLIDE_KEY_PASSWORD` as repository secrets to get a signed release APK; without them CI produces a debug-key-signed APK for sideloading.

Install the APK, enable the accessibility service, download Tiny, dictate. With only the stub you will see `[stub: N s of audio, model X]` typed into the field.

For real transcription: whisper.cpp v1.5.4 is vendored at `app/src/main/cpp/whisper.cpp` (flat tree the JNI expects; re-fetch with `tools/fetch-whisper.ps1`). Rebuild and `Engines.load()` uses it, stub otherwise. `CMakeLists.txt` builds the JNI lib if present and still builds a stub otherwise, so `assembleDebug` stays green either way. Start with `arm64-v8a`.

## Project layout

```
app/src/main/
  java/app/slidedictate/
    DictationService.kt    # overlay handle + panel, record, insert text
    Recorder.kt            # AudioRecord 16kHz mono PCM16
    WhisperEngine.kt       # JNI engine (init/transcribe/free)
    Transcriber.kt         # Transcriber interface + Engines.load() + stub
    ModelStore.kt          # model list + Hugging Face downloader
    MainActivity.kt        # setup screen (permission, service, models)
    MicForegroundService.kt# microphone foreground hold
    Prefs.kt               # offline flag + selected model + handle prefs
  cpp/whisper_jni.cpp, CMakeLists.txt
  AndroidManifest.xml
  res/xml/accessibility_service_config.xml
```

Zero dependencies beyond the Android SDK and Kotlin. UI is built in code (no layouts/themes) to keep the first build small.

## Permissions

- `RECORD_AUDIO` — dictation
- `INTERNET` — model downloads only
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` — mic hold while panel is open
- `POST_NOTIFICATIONS` — Android 13+ mic-recording indicator (requested in-app)
- Accessibility service binding — draw handle/panel, find focused field, set text

## Status and limits

- Works end to end with the stub engine; real whisper.cpp wiring is the next step.
- `Offline only` off currently just shows "Online mode is not built yet."
- Handle is a 22dp pill with a wide invisible grab area; position adjustable from the app.
- Text insertion: SET_TEXT, then paste, then clipboard. When nothing reports input focus, the active window is scanned for an editable node first (helps WebViews). Games with custom canvases still get clipboard only.

## Roadmap

1. Real engine: done (whisper.cpp v1.5.4 vendored, JNI `init(path, language)` / `transcribe(float[])` / `free()`, stub fallback).
2. Mic capture on Android 14+: done (foreground service of type `microphone`).
3. VAD + chunked transcription: done (chunks transcribed and inserted in order while speaking).
4. Online mode: done (`SpeechRecognizer` with partial results; clear message without Google services).
5. Handle polish: done (wide grab area, vertical position, Quick Settings tile).
6. Models: done — q5_1 quantized tiny/base/small plus Medium (plain and q5_0), all SHA-256 pinned. large-v3-turbo is deliberately excluded until the vendored whisper.cpp is upgraded (needs >= 1.7).
7. Release: done pending keystore (conditional signing, ProGuard rules, Lucide icons wired).
