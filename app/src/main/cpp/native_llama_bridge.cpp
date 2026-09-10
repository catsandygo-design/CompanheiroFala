#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "CompanheiroLLM", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CompanheiroLLM", __VA_ARGS__)

namespace {
std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
llama_sampler * g_sampler = nullptr;
bool g_backend_initialized = false;
std::string g_last_error;
int g_generated_tokens = 0;
std::atomic_bool g_cancel_requested { false };

void set_error(const std::string & error) {
    g_last_error = error;
    LOGE("%s", error.c_str());
}

void unload() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text) {
    std::vector<llama_token> tokens(text.size() + 8);
    int32_t count = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (count < 0) { tokens.resize(static_cast<size_t>(-count)); count = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), tokens.data(), static_cast<int32_t>(tokens.size()), true, true); }
    if (count < 0) return {};
    tokens.resize(count); return tokens;
}

std::string piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(256);
    int32_t size = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    if (size < 0) { buffer.resize(static_cast<size_t>(-size)); size = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false); }
    return size > 0 ? std::string(buffer.data(), size) : std::string();
}

std::string format_chat_prompt(const std::string & input) {
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    if (!tmpl || !*tmpl) return input;
    llama_chat_message message { "user", input.c_str() };
    int32_t needed = llama_chat_apply_template(tmpl, &message, 1, true, nullptr, 0);
    if (needed <= 0) return input;
    std::vector<char> buffer(static_cast<size_t>(needed) + 1);
    int32_t written = llama_chat_apply_template(tmpl, &message, 1, true, buffer.data(), static_cast<int32_t>(buffer.size()));
    return written > 0 ? std::string(buffer.data(), static_cast<size_t>(written)) : input;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_br_com_companheirofala_core_ai_NativeLlamaBridge_nativeLoadModel(JNIEnv * env, jobject, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex); unload();
    g_last_error.clear(); g_generated_tokens = 0;
    if (!g_backend_initialized) { llama_backend_init(); g_backend_initialized = true; }
    const char * raw = env->GetStringUTFChars(path, nullptr);
    if (!raw) { set_error("Não foi possível ler o caminho do GGUF"); return JNI_FALSE; }
    LOGI("MODEL_LOAD_START path=%s", raw);
    llama_model_params params = llama_model_default_params();
    g_model = llama_model_load_from_file(raw, params);
    env->ReleaseStringUTFChars(path, raw);
    if (!g_model) { set_error("llama_model_load_from_file falhou ao abrir o GGUF"); return JNI_FALSE; }
    llama_context_params contextParams = llama_context_default_params();
    // A conversa infantil usa respostas curtas: menos contexto reduz pressão de RAM no celular.
    contextParams.n_ctx = std::min<uint32_t>(512, llama_model_n_ctx_train(g_model));
    contextParams.n_batch = 128;
    contextParams.n_ubatch = 128;
    // Deixa núcleos livres para a UI, TTS e reconhecimento de voz do Android.
    contextParams.n_threads = 2;
    contextParams.n_threads_batch = contextParams.n_threads;
    LOGI("CONTEXT_CREATE_START n_ctx=%u n_batch=%u threads=%d", contextParams.n_ctx, contextParams.n_batch, contextParams.n_threads);
    g_context = llama_init_from_model(g_model, contextParams);
    if (!g_context) { unload(); set_error("llama_init_from_model falhou ao criar o contexto"); return JNI_FALSE; }
    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(samplerParams);
    if (!g_sampler) { unload(); set_error("Falha ao criar o sampler llama.cpp"); return JNI_FALSE; }
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.5f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(1234));
    LOGI("MODEL_LOAD_OK CONTEXT_CREATE_OK n_ctx=%u", contextParams.n_ctx);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_companheirofala_core_ai_NativeLlamaBridge_nativeGenerate(JNIEnv * env, jobject, jstring prompt, jint maxTokens, jfloat) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_last_error.clear(); g_generated_tokens = 0; g_cancel_requested.store(false);
    if (!g_model || !g_context || !g_sampler) { set_error("Modelo, contexto ou sampler não inicializado"); return env->NewStringUTF(""); }
    const char * raw = env->GetStringUTFChars(prompt, nullptr);
    if (!raw) { set_error("Não foi possível ler o prompt UTF-8"); return env->NewStringUTF(""); }
    std::string input(raw); env->ReleaseStringUTFChars(prompt, raw);
    input = format_chat_prompt(input);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    auto tokens = tokenize(vocab, input); if (tokens.empty()) { set_error("llama_tokenize não produziu tokens"); return env->NewStringUTF(""); }
    if (tokens.size() >= llama_n_ctx(g_context)) { set_error("Prompt excede o contexto do modelo"); return env->NewStringUTF(""); }
    LOGI("TOKENIZE_OK count=%zu", tokens.size());
    llama_memory_clear(llama_get_memory(g_context), false); llama_sampler_reset(g_sampler);
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    LOGI("DECODE_START");
    if (llama_decode(g_context, batch) != 0) { set_error("llama_decode falhou ao processar o prompt"); return env->NewStringUTF(""); }
    std::string output;
    for (int i = 0; i < std::min(12, static_cast<int>(maxTokens)); ++i) {
        if (g_cancel_requested.load()) { set_error("Geração interrompida para manter o aplicativo responsivo"); break; }
        llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        output += piece(vocab, token); g_generated_tokens++;
        if (g_generated_tokens == 1) LOGI("FIRST_TOKEN");
        llama_sampler_accept(g_sampler, token);
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_context, batch) != 0) { set_error("llama_decode falhou durante a geração"); break; }
    }
    if (output.empty() && g_last_error.empty()) set_error("O modelo encerrou sem gerar tokens");
    LOGI("GENERATION_END tokens=%d", g_generated_tokens);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_companheirofala_core_ai_NativeLlamaBridge_nativeUnloadModel(JNIEnv *, jobject) { std::lock_guard<std::mutex> lock(g_mutex); unload(); }

extern "C" JNIEXPORT jboolean JNICALL
Java_br_com_companheirofala_core_ai_NativeLlamaBridge_nativeIsLoaded(JNIEnv *, jobject) { std::lock_guard<std::mutex> lock(g_mutex); return g_model && g_context ? JNI_TRUE : JNI_FALSE; }

extern "C" JNIEXPORT jstring JNICALL
Java_br_com_companheirofala_core_ai_NativeLlamaBridge_nativeLastError(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex); return env->NewStringUTF(g_last_error.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_br_com_companheirofala_core_ai_NativeLlamaBridge_nativeGeneratedTokenCount(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex); return g_generated_tokens;
}

extern "C" JNIEXPORT void JNICALL
Java_br_com_companheirofala_core_ai_NativeLlamaBridge_nativeCancelGeneration(JNIEnv *, jobject) {
    // Não usa g_mutex: precisa conseguir sinalizar enquanto nativeGenerate está executando.
    g_cancel_requested.store(true);
    LOGI("Generation cancellation requested");
}
