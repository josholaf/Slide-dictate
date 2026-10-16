#include <jni.h>
#include <string>

#ifdef HAVE_WHISPER_CPP
#include "whisper.h"
#include <vector>

struct Ctx {
    struct whisper_context* ctx = nullptr;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_slidedictate_WhisperEngine_nativeInit(JNIEnv* env, jobject, jstring path_) {
    const char* path = env->GetStringUTFChars(path_, nullptr);
    struct whisper_context_params params = whisper_context_default_params();
    struct whisper_context* ctx = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(path_, path);
    if (!ctx) return 0;
    Ctx* holder = new Ctx();
    holder->ctx = ctx;
    return reinterpret_cast<jlong>(holder);
}

JNIEXPORT jstring JNICALL
Java_app_slidedictate_WhisperEngine_nativeTranscribe(JNIEnv* env, jobject, jlong handle, jfloatArray samples_) {
    Ctx* holder = reinterpret_cast<Ctx*>(handle);
    if (!holder || !holder->ctx) return env->NewStringUTF("");
    jsize n = env->GetArrayLength(samples_);
    if (n <= 0) return env->NewStringUTF("");
    std::vector<float> samples((size_t)n);
    env->GetFloatArrayRegion(samples_, 0, n, samples.data());

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress = false;
    wparams.print_special = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.translate = false;
    wparams.language = "en";
    wparams.n_threads = 4;

    if (whisper_full(holder->ctx, wparams, samples.data(), (int)samples.size()) != 0) {
        return env->NewStringUTF("");
    }
    std::string out;
    int segments = whisper_full_n_segments(holder->ctx);
    for (int i = 0; i < segments; ++i) {
        const char* text = whisper_full_get_segment_text(holder->ctx, i);
        if (text) {
            if (!out.empty()) out += " ";
            out += text;
        }
    }
    // Trim leading space whisper.cpp emits per segment.
    size_t start = out.find_first_not_of(" \t\r\n");
    if (start != std::string::npos) out = out.substr(start);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT void JNICALL
Java_app_slidedictate_WhisperEngine_nativeFree(JNIEnv*, jobject, jlong handle) {
    Ctx* holder = reinterpret_cast<Ctx*>(handle);
    if (!holder) return;
    if (holder->ctx) whisper_free(holder->ctx);
    delete holder;
}

} // extern "C"

#else // HAVE_WHISPER_CPP — whisper.cpp sources not checked out yet; build still succeeds.

// Build stays green without whisper.cpp so assembleDebug works on any machine.
// WhisperEngine.nativeInit returns 0, Kotlin falls back to the stub transcriber.
extern "C" {

JNIEXPORT jlong JNICALL
Java_app_slidedictate_WhisperEngine_nativeInit(JNIEnv*, jobject, jstring) {
    return 0;
}

JNIEXPORT jstring JNICALL
Java_app_slidedictate_WhisperEngine_nativeTranscribe(JNIEnv* env, jobject, jlong, jfloatArray) {
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_app_slidedictate_WhisperEngine_nativeFree(JNIEnv*, jobject, jlong) {}

} // extern "C"

#endif
