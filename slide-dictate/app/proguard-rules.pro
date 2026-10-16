# Slide Dictate release rules. Zero third-party dependencies; these keeps cover the
# JNI seam, manifest-referenced components, and model plumbing against R8.
-keep class app.slidedictate.WhisperEngine { *; }
-keep class app.slidedictate.Transcriber { *; }
-keep class app.slidedictate.StubTranscriber { *; }
-keep class app.slidedictate.Engines { *; }
-keep class app.slidedictate.DictationService { *; }
-keep class app.slidedictate.MicForegroundService { *; }
-keep class app.slidedictate.HandleTileService { *; }
-keep class app.slidedictate.MainActivity { *; }
-keep class app.slidedictate.ModelStore { *; }
-keep class app.slidedictate.ModelInfo { *; }
-keep class app.slidedictate.Prefs { *; }
-keep class app.slidedictate.Recorder { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
