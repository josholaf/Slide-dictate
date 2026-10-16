# whisper.cpp JNI: native methods are invoked from C++, so R8 must not rename
# or strip them (or their host class) when minification is on.
-keep class app.slidedictate.WhisperEngine {
    native <methods>;
}
