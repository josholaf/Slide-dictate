# Slide Dictate

Android app: a handle on the screen edge; slide it out, tap the mic, speak, and the text is typed into the focused field. Speech recognition runs on the phone with downloaded open-source Whisper models. Same idea as Handy on desktop.

## Status

Not compiled yet. The sandbox it was written in could not reach Google's Maven repository, so nobody has run `assembleDebug`. Expect small compile errors on the first build and fix them.

The flow works end to end with a stub engine: the stub returns "[stub: N s of audio, model X]" instead of real text. Real transcription is step 2 below.

## How it is built

- Zero dependencies beyond the Android SDK and Kotlin. `minSdk 26`, `compileSdk 34`, Kotlin 1.9.24, AGP 8.5.2.
- `DictationService` is an AccessibilityService. It draws the handle and panel as accessibility overlays (no "draw over other apps" permission), records audio, and inserts text with `ACTION_SET_TEXT` on the focused input node. If that fails it falls back to clipboard plus `ACTION_PASTE`.
- `Recorder`: `AudioRecord`, 16 kHz mono PCM16, whole recording returned on stop (tap to start, tap to stop).
- `ModelStore`: downloads ggml Whisper models (tiny.en, base.en, small.en) from Hugging Face into `filesDir/models`.
- `Transcriber` / `Engines.load()`: the seam where the real engine plugs in.
- `MainActivity`: plain Views. Microphone permission, link to accessibility settings, offline switch, model download and select.
- `Prefs`: offline-only flag and selected model id.

## Next steps, in order

1. Build. Run `gradle wrapper --gradle-version 8.7` once (no wrapper is checked in), then `./gradlew assembleDebug`. Install on a phone, enable the service under Settings > Accessibility, download Tiny, dictate. You should see the stub text typed into a field.
2. Replace the stub. Recommended: whisper.cpp through the NDK (CMake + a small JNI wrapper with `init(path)`, `transcribe(float[])`, `free()`). The downloaded files are already ggml, so no conversion. Convert PCM16 to float in [-1, 1] before the call. Use `arm64-v8a` first. Alternative: sherpa-onnx prebuilt libraries, but that needs ONNX-format models and different URLs in `ModelStore`.
3. Check microphone access from the accessibility service on Android 14 and later. If capture is blocked while another app is in front, start a foreground service of type `microphone` when the panel opens and record from there.
4. Long dictation: add voice activity detection and transcribe in chunks, so text appears while speaking.
5. Online mode: the "Offline only" switch off currently just shows a message. Either wire `android.speech.SpeechRecognizer` or remove the off position.
6. Handle polish: wider invisible touch area, vertical position setting, a Quick Settings tile to show or hide the handle.
7. Models: delete, resume interrupted downloads, SHA-256 check, multilingual models (the current three are English only).
8. Release: app icon, signing config, ProGuard/R8 check for the JNI layer.

## Known gaps

- Handle touch area is 22 dp wide. Fine for a prototype, small for thumbs.
- Text insertion depends on the target app exposing a standard input node. Some apps (games, some WebViews) will take the clipboard fallback or nothing.
- UI is built in code with no layouts or themes, on purpose, to keep the first build small.
