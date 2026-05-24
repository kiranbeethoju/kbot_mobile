#include <jni.h>
#include <string>
#include <cstdint>

extern "C" {
#include "whisper.h"
}

struct WhisperContext {
    whisper_context *ctx;
    WhisperContext(whisper_context *c) : ctx(c) {}
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_offlinebot_ai_whisper_WhisperCppEngine_nativeInit(
    JNIEnv *env,
    jobject /* this */,
    jstring modelPath) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return 0;

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) return 0;

    return reinterpret_cast<jlong>(new WhisperContext(ctx));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offlinebot_ai_whisper_WhisperCppEngine_nativeTranscribe(
    JNIEnv *env,
    jobject /* this */,
    jlong contextPtr,
    jstring audioPath) {

    auto *wrapper = reinterpret_cast<WhisperContext *>(contextPtr);
    if (!wrapper || !wrapper->ctx) {
        return env->NewStringUTF("");
    }

    const char *path = env->GetStringUTFChars(audioPath, nullptr);
    if (!path) return env->NewStringUTF("");

    // Read WAV file
    std::vector<float> pcmf32;
    {
        FILE *fp = fopen(path, "rb");
        if (!fp) {
            env->ReleaseStringUTFChars(audioPath, path);
            return env->NewStringUTF("");
        }

        // Skip WAV header (44 bytes for standard PCM WAV)
        fseek(fp, 0, SEEK_END);
        long fileSize = ftell(fp);
        fseek(fp, 44, SEEK_SET); // skip header

        size_t dataSize = (fileSize - 44) / 2; // 16-bit samples
        if (dataSize <= 0) {
            fclose(fp);
            env->ReleaseStringUTFChars(audioPath, path);
            return env->NewStringUTF("");
        }

        pcmf32.resize(dataSize);
        std::vector<int16_t> pcm16(dataSize);
        fread(pcm16.data(), sizeof(int16_t), dataSize, fp);
        fclose(fp);

        for (size_t i = 0; i < dataSize; i++) {
            pcmf32[i] = float(pcm16[i]) / 32768.0f;
        }
    }

    env->ReleaseStringUTFChars(audioPath, path);

    // Run whisper inference
    struct whisper_full_params wparams = whisper_full_default_params(
        WHISPER_SAMPLING_GREEDY);

    wparams.print_progress   = false;
    wparams.print_special    = false;
    wparams.print_realtime   = false;
    wparams.print_timestamps = false;
    wparams.translate        = false;
    wparams.language         = "en";
    wparams.n_threads        = 4;
    wparams.offset_ms        = 0;
    wparams.no_context       = true;
    wparams.single_segment   = true;

    if (whisper_full(wrapper->ctx, wparams, pcmf32.data(), pcmf32.size()) != 0) {
        return env->NewStringUTF("[transcription failed]");
    }

    // Collect result
    std::string result;
    const int n_segments = whisper_full_n_segments(wrapper->ctx);
    for (int i = 0; i < n_segments; i++) {
        const char *text = whisper_full_get_segment_text(wrapper->ctx, i);
        if (text && text[0]) {
            if (!result.empty()) result += " ";
            result += text;
        }
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlinebot_ai_whisper_WhisperCppEngine_nativeFree(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong contextPtr) {

    auto *wrapper = reinterpret_cast<WhisperContext *>(contextPtr);
    if (wrapper) {
        if (wrapper->ctx) {
            whisper_free(wrapper->ctx);
        }
        delete wrapper;
    }
}
