#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

static whisper_context* g_ctx = nullptr;

extern "C"
JNIEXPORT jstring JNICALL
Java_com_voxcommander_app_domain_engine_whisper_WhisperNative_getVersion(
        JNIEnv *env,
        jobject /* this */) {

    std::string version = whisper_print_system_info();

    return env->NewStringUTF(version.c_str());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_voxcommander_app_domain_engine_whisper_WhisperNative_loadModel(
        JNIEnv* env,
        jobject,
        jstring modelPath) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    whisper_context_params params = whisper_context_default_params();

    g_ctx = whisper_init_from_file_with_params(path, params);

    env->ReleaseStringUTFChars(modelPath, path);

    if (g_ctx != nullptr) {
        std::string sys_info = whisper_print_system_info();
        __android_log_print(ANDROID_LOG_DEBUG, "WhisperNative", "System info: %s", sys_info.c_str());
    }

    return g_ctx != nullptr;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_voxcommander_app_domain_engine_whisper_WhisperNative_freeModel(
        JNIEnv* env,
        jobject) {

    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_voxcommander_app_domain_engine_whisper_WhisperNative_transcribe(
        JNIEnv* env,
        jobject,
        jfloatArray audioData) {

    __android_log_print(ANDROID_LOG_DEBUG, "WhisperNative", "transcribe called");

    if (g_ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, "WhisperNative", "Model not loaded");
        return env->NewStringUTF("Error: Model not loaded");
    }

    jfloat* audio = env->GetFloatArrayElements(audioData, nullptr);
    jsize length = env->GetArrayLength(audioData);

    __android_log_print(ANDROID_LOG_DEBUG, "WhisperNative", "Audio length: %d", length);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    
    // Aggressive optimization for speed (commands)
    params.language = "en";  // Set language explicitly
    params.n_threads = 8;    // Use more threads (8 cores on modern phones)
    params.temperature = 0.0f;  // Lower temperature for more deterministic results
    params.max_len = 128;    // Much shorter max length for commands (was 224)
    params.token_timestamps = false;  // Disable token timestamps for speed
    params.single_segment = true;  // Treat as single segment for short audio
    params.no_timestamps = true;  // Disable timestamps for speed

    __android_log_print(ANDROID_LOG_DEBUG, "WhisperNative", "Calling whisper_full with language=%s, n_threads=%d", params.language, params.n_threads);

    int result = whisper_full(g_ctx, params, audio, length);

    __android_log_print(ANDROID_LOG_DEBUG, "WhisperNative", "whisper_full returned: %d", result);

    env->ReleaseFloatArrayElements(audioData, audio, 0);

    if (result != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "WhisperNative", "Transcription failed with code: %d", result);
        return env->NewStringUTF("Error: Transcription failed");
    }

    int n = whisper_full_n_segments(g_ctx);
    __android_log_print(ANDROID_LOG_DEBUG, "WhisperNative", "Segments: %d", n);

    std::string text;

    for (int i = 0; i < n; ++i) {
        const char* segment = whisper_full_get_segment_text(g_ctx, i);
        if (segment) {
            text += segment;
        }
    }

    __android_log_print(ANDROID_LOG_DEBUG, "WhisperNative", "Result: %s", text.c_str());

    return env->NewStringUTF(text.c_str());
}
