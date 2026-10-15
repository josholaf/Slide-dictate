# Slide Dictate

Edge-handle voice typing for Android. Slide out the handle on the screen edge, tap Start, speak, and the text is typed into the focused field. Same idea as Handy on desktop.

Speech recognition runs fully on-device with downloaded open-source Whisper (ggml) models. No audio leaves the phone.

## How it works

1. `DictationService` (AccessibilityService) draws a 22x96dp handle on the right screen edge as a `TYPE_ACCESSIBILITY_OVERLAY` — no "draw over other apps" permission needed.
2. Slide left / tap the handle to open a 300dp panel (`Start dictation`, status, `Offline only` switch).
3. Tap Start: `Recorder` captures 16 kHz mono PCM16 via `AudioRecord` (`VOICE_RECOGNITION`). A `MicForegroundService` (type `microphone`) holds mic access so Android 14+ doesn't block background capture. Tap Stop to finish.
4. `WhisperEngine` (whisper.cpp via JNI `libwhisper_jni`) transcribes on a background thread: PCM16 → float in [-1, 1] → `nativeTranscribe`. Without the native lib it falls back to `StubTranscriber`.
5. Text is inserted into the focused input node via `ACTION_SET_TEXT` (cursor preserved, smart leading space), fallback to clipboard + `ACTION_PASTE`, else copied with a "no field focused" message.

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

English-only for now. Download writes to `.part` then renames on completion.

## Build

Requirements: Android SDK, Kotlin 1.9.24, AGP 8.5.2, `minSdk 26`, `compileSdk/targetSdk 34`.

```sh
cd slide-dictate
gradle wrapper --gradle-version 8.7   # once, no wrapper is checked in
./gradlew assembleDebug
```

Install the APK, enable the accessibility service, download Tiny, dictate. With only the stub you will see `[stub: N s of audio, model X]` typed into the field.

For real transcription: check out whisper.cpp at `app/src/main/cpp/whisper.cpp` (tag matching the ggml models) and rebuild. `CMakeLists.txt` builds the JNI lib if present and still builds a stub otherwise, so `assembleDebug` stays green either way. Start with `arm64-v8a`.

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
- Handle touch area is 22dp wide — small for thumbs, wider invisible area planned.
- Text insertion needs a standard input node; games / some WebViews get clipboard fallback or nothing.

## Roadmap

1. Real engine (whisper.cpp NDK: `init(path)`, `transcribe(float[])`, `free()`; alt: sherpa-onnx with ONNX models).
2. Verify mic capture from the service on Android 14+.
3. VAD + chunked transcription so text appears while speaking.
4. Online mode (`SpeechRecognizer`) or remove the off position.
5. Handle polish: touch area, vertical position, Quick Settings tile.
6. Models: resume interrupted downloads, SHA-256 check, multilingual (delete is done).
7. Release: icon, signing, ProGuard/R8 check for JNI.
