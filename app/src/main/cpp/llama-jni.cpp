#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <unistd.h>
#include <android/log.h>

#include "llama.h"
#include "chat.h"
#include "common.h"

#define TAG "offlinebot-llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    common_chat_templates_ptr chat_templates;
    llama_batch batch{};
    bool batch_inited = false;
    bool loaded = false;
    bool backend_init = false;
};

static LlamaState g_state;

static void ensure_backend() {
    if (!g_state.backend_init) {
        llama_backend_init();
        g_state.backend_init = true;
        LOGI("llama backend initialized");
    }
}

static int thread_count() {
    const int n = (int) sysconf(_SC_NPROCESSORS_ONLN);
    return std::max(2, std::min(4, n > 2 ? n - 2 : n));
}

static bool tokenize_prompt(const std::string &prompt, std::vector<llama_token> &out) {
    const bool is_first = llama_memory_seq_pos_max(llama_get_memory(g_state.ctx), 0) < 0;
    const int n = -llama_tokenize(
        g_state.vocab, prompt.c_str(), (int32_t) prompt.size(),
        nullptr, 0, is_first, true);
    if (n <= 0) {
        return false;
    }
    out.resize(n);
    if (llama_tokenize(
            g_state.vocab, prompt.c_str(), (int32_t) prompt.size(),
            out.data(), n, is_first, true) < 0) {
        return false;
    }
    return true;
}

static int decode_tokens_batched(const std::vector<llama_token> &tokens, llama_pos start_pos) {
    const int n_batch = (int) llama_n_batch(g_state.ctx);
    for (int i = 0; i < (int) tokens.size(); i += n_batch) {
        const int cur = std::min(n_batch, (int) tokens.size() - i);
        common_batch_clear(g_state.batch);
        for (int j = 0; j < cur; j++) {
            const bool want_logits = (i + j == (int) tokens.size() - 1);
            common_batch_add(
                g_state.batch,
                tokens[i + j],
                start_pos + i + j,
                {0},
                want_logits);
        }
        if (llama_decode(g_state.ctx, g_state.batch) != 0) {
            LOGE("llama_decode failed at batch offset %d", i);
            return -1;
        }
    }
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_offlinebot_ai_llm_LlamaCppEngine_nativeLoadModel(
    JNIEnv *env, jobject, jstring modelPath, jint nCtx) {

    ensure_backend();

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) return JNI_FALSE;

    LOGI("Loading model: %s", path);

    if (g_state.loaded) {
        if (g_state.batch_inited) {
            llama_batch_free(g_state.batch);
            g_state.batch_inited = false;
        }
        g_state.chat_templates.reset();
        if (g_state.ctx) llama_free(g_state.ctx);
        if (g_state.model) llama_model_free(g_state.model);
        g_state.loaded = false;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    g_state.model = llama_model_load_from_file(path, mparams);

    if (!g_state.model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    const int trained_ctx = llama_model_n_ctx_train(g_state.model);
    int ctx_size = nCtx > 0 ? nCtx : 2048;
    if (trained_ctx > 0 && ctx_size > trained_ctx) {
        ctx_size = trained_ctx;
    }

    const int threads = thread_count();
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = ctx_size;
    cparams.n_batch = std::min(512, ctx_size);
    cparams.n_ubatch = cparams.n_batch;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    cparams.no_perf = true;

    g_state.ctx = llama_init_from_model(g_state.model, cparams);

    if (!g_state.ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_state.model);
        g_state.model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    g_state.vocab = llama_model_get_vocab(g_state.model);
    g_state.chat_templates = common_chat_templates_init(g_state.model, "");
    g_state.batch = llama_batch_init(cparams.n_batch, 0, 1);
    g_state.batch_inited = true;
    g_state.loaded = true;

    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("Model loaded (ctx=%d, batch=%d, threads=%d)", ctx_size, cparams.n_batch, threads);
    return JNI_TRUE;
}

static std::string run_chat_internal(
    const std::string &system_text,
    const std::string &user_text,
    int max_tokens,
    float temperature,
    float top_p) {

    std::vector<common_chat_msg> messages;
    if (!system_text.empty()) {
        common_chat_msg system_msg;
        system_msg.role = "system";
        system_msg.content = system_text;
        messages.push_back(std::move(system_msg));
    }
    common_chat_msg user_msg;
    user_msg.role = "user";
    user_msg.content = user_text;
    messages.push_back(std::move(user_msg));

    common_chat_templates_inputs inputs;
    inputs.messages = std::move(messages);
    inputs.add_generation_prompt = true;
    inputs.use_jinja = true;
    inputs.enable_thinking = false;

    common_chat_params chat_params;
    try {
        chat_params = common_chat_templates_apply(g_state.chat_templates.get(), inputs);
    } catch (const std::exception &e) {
        LOGE("Chat template apply failed: %s", e.what());
        return "[Template error]";
    }

    if (chat_params.prompt.empty()) {
        return "[Template error]";
    }

    llama_memory_clear(llama_get_memory(g_state.ctx), true);

    std::vector<llama_token> prompt_tokens;
    if (!tokenize_prompt(chat_params.prompt, prompt_tokens)) {
        return "[Tokenization error]";
    }

    const int n_ctx = llama_n_ctx(g_state.ctx);
    const int n_predict = max_tokens > 0 ? max_tokens : 256;
    const int max_prompt = std::max(32, n_ctx - n_predict - 16);
    if ((int) prompt_tokens.size() > max_prompt) {
        LOGI("Truncating prompt %zu -> %d tokens", prompt_tokens.size(), max_prompt);
        prompt_tokens.erase(prompt_tokens.begin(), prompt_tokens.end() - max_prompt);
    }

    if (decode_tokens_batched(prompt_tokens, 0) != 0) {
        LOGE("Prompt decode failed (%zu tokens, n_ctx=%d)", prompt_tokens.size(), n_ctx);
        return "[Decode error]";
    }

    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    std::string result;
    llama_pos pos = (llama_pos) prompt_tokens.size();

    for (int i = 0; i < n_predict; i++) {
        llama_token new_token = llama_sampler_sample(smpl, g_state.ctx, -1);
        llama_sampler_accept(smpl, new_token);

        if (llama_vocab_is_eog(g_state.vocab, new_token)) {
            break;
        }

        char piece[256];
        const int len = llama_token_to_piece(
            g_state.vocab, new_token, piece, sizeof(piece), 0, true);
        if (len > 0) {
            result.append(piece, len);
        }

        if (result.find("<turn|>") != std::string::npos ||
            result.find("<|turn>") != std::string::npos) {
            break;
        }

        common_batch_clear(g_state.batch);
        common_batch_add(g_state.batch, new_token, pos, {0}, true);
        if (llama_decode(g_state.ctx, g_state.batch) != 0) {
            LOGE("Token decode failed at pos %d", (int) pos);
            break;
        }
        pos++;
    }

    llama_sampler_free(smpl);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offlinebot_ai_llm_LlamaCppEngine_nativeChat(
    JNIEnv *env, jobject,
    jstring jSystemPrompt,
    jstring jUserMessage,
    jint maxTokens,
    jfloat temperature,
    jfloat topP) {

    if (!g_state.loaded || !g_state.ctx || !g_state.vocab || !g_state.chat_templates) {
        return env->NewStringUTF("[Model not loaded]");
    }

    const char *systemText = env->GetStringUTFChars(jSystemPrompt, nullptr);
    const char *userText = env->GetStringUTFChars(jUserMessage, nullptr);

    const std::string system_str = systemText ? systemText : "";
    const std::string user_str = userText ? userText : "";

    env->ReleaseStringUTFChars(jSystemPrompt, systemText);
    env->ReleaseStringUTFChars(jUserMessage, userText);

    std::string result = run_chat_internal(
        system_str, user_str, maxTokens, temperature, topP);

    LOGI("Generated %zu chars", result.size());
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_offlinebot_ai_llm_LlamaCppEngine_nativeWarmup(JNIEnv *, jobject) {
    if (!g_state.loaded) return JNI_FALSE;
    std::string out = run_chat_internal(
        "You are a helpful assistant.",
        "Say hello in one short sentence.",
        32, 0.7f, 0.9f);
    const bool ok = !out.empty() &&
        out.find("[Decode error]") == std::string::npos &&
        out.find("[Template error]") == std::string::npos &&
        out.find("[Tokenization error]") == std::string::npos;
    LOGI("Warmup %s (%zu chars)", ok ? "ok" : "failed", out.size());
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlinebot_ai_llm_LlamaCppEngine_nativeFree(JNIEnv *, jobject) {
    if (g_state.loaded) {
        if (g_state.batch_inited) {
            llama_batch_free(g_state.batch);
            g_state.batch_inited = false;
        }
        g_state.chat_templates.reset();
        if (g_state.ctx) { llama_free(g_state.ctx); g_state.ctx = nullptr; }
        if (g_state.model) { llama_model_free(g_state.model); g_state.model = nullptr; }
        g_state.vocab = nullptr;
        g_state.loaded = false;
        LOGI("Model unloaded");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_offlinebot_ai_llm_LlamaCppEngine_nativeIsLoaded(JNIEnv *, jobject) {
    return g_state.loaded ? JNI_TRUE : JNI_FALSE;
}
