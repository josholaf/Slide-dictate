# Slide Dictate

Type with your voice, in any Android app.

A small handle sits on the edge of your screen. Slide it out, tap Start, speak — your words appear in whatever text field you were using. No switching apps, no cloud, no keyboard needed.

## Why use it

- Works everywhere: messages, notes, email, search — any text field.
- Private by design: speech recognition runs on your phone with free offline Whisper models.
- Fast: slide, speak, done. Your text is typed with the cursor left where you stopped.

## Use it in 3 steps

1. Install the app, allow the microphone, and turn on Slide Dictate under Accessibility settings.
2. Download the Tiny model (75 MB, good enough to start). The first download selects itself; tap Use only to switch models later.
3. Open any app, tap a text field, slide out the edge handle, tap Start, speak, tap Stop.

That's it — your speech is typed in.

## What you need

- Android 8 or newer (Android 8.0 / API 26+).
- About 100 MB free for the Tiny model (Base 142 MB and Small 466 MB sound better and are optional).
- Internet only once, to download the model. Dictation itself is fully offline.

## Build it yourself

No wrapper is checked in, and everything is plain Android + Kotlin — no extra dependencies.

Easy way on Windows (installs the SDK packages and builds the APK):

```powershell
.\build-apk.ps1
```

Manual way:

```sh
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

The finished app is at `slide-dictate/app/build/outputs/apk/debug/app-debug.apk`.

For real on-device transcription, check out whisper.cpp into `slide-dictate/app/src/main/cpp/whisper.cpp` and rebuild. Without it the app still runs end to end with a test message so you can try the full flow.

Full technical details: see [slide-dictate/README.md](slide-dictate/README.md).

## Honest status

Early prototype that works, not yet polished: the handle is slim, online mode isn't built (leave Offline only on), and a few apps without standard text fields get the text on the clipboard instead. See the roadmap in the technical README if you want to help.
