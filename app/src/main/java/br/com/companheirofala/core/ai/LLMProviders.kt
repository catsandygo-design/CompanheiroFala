package br.com.companheirofala.core.ai

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import br.com.companheirofala.BuildConfig
import br.com.companheirofala.core.conversation.ConversationState
import br.com.companheirofala.core.conversation.LLMProvider
import br.com.companheirofala.core.conversation.LLMResult
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import kotlin.coroutines.resume

interface LocalLLMProvider { suspend fun generate(prompt: String, context: ConversationState): LLMResult; fun isAvailable(): Boolean; suspend fun loadModel(): Boolean; fun unloadModel() }
interface RemoteLLMProvider { suspend fun generate(prompt: String, context: ConversationState): LLMResult; fun isEnabled(): Boolean }

/** A chave e o provider remoto não fazem parte do APK; esta preferência só habilita a tentativa futura. */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    var remoteAiEnabled: Boolean
        get() = prefs.getBoolean("remote_ai_enabled", false)
        set(value) = prefs.edit().putBoolean("remote_ai_enabled", value).apply()
}

/** JNI boundary. Native library is loaded only when an arm64 llama.cpp build is packaged. */
class NativeLlamaBridge {
    private var nativeReady = false
    var loadError: String? = null; private set
    init {
        Log.i(TAG, "JNI_LOAD_START")
        nativeReady = runCatching { System.loadLibrary("companion_llama"); true }.onFailure {
            loadError = it.message ?: it.javaClass.simpleName
            Log.e(TAG, "JNI_LOAD_ERROR", it)
        }.getOrDefault(false)
        if (nativeReady) Log.i(TAG, "JNI_LOAD_OK")
    }
    fun isReady() = nativeReady
    external fun nativeLoadModel(path: String): Boolean
    external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    external fun nativeUnloadModel()
    external fun nativeIsLoaded(): Boolean
    external fun nativeLastError(): String
    external fun nativeGeneratedTokenCount(): Int
    external fun nativeCancelGeneration()
    fun lastNativeError(): String = if (!nativeReady) loadError.orEmpty() else runCatching { nativeLastError() }.getOrElse { it.message.orEmpty() }
    companion object { private const val TAG = "CompanheiroLLM" }
}

class LlamaCppLocalProvider(private val appContext: Context) : LocalLLMProvider {
    private val modelFiles = ModelFileManager(appContext)
    private val bridge = NativeLlamaBridge()
    private val loadingMutex = Mutex()
    private var loaded = false
    var lastError: String? = null; private set
    var lastLoadLatencyMs: Long = 0; private set
    var lastGenerationLatencyMs: Long = 0; private set
    var lastGeneratedTokenCount: Int = 0; private set
    private fun model(): File? = modelFiles.selectedModel()
    override fun isAvailable() = loaded && bridge.isReady() && runCatching { bridge.nativeIsLoaded() }.getOrDefault(false)
    override suspend fun loadModel(): Boolean = withContext(Dispatchers.Default) {
        loadingMutex.withLock {
        if (isAvailable()) return@withLock true
        Log.i(TAG, "MODEL_SEARCH_START")
        val file = model() ?: run { lastError = "Nenhum modelo GGUF foi encontrado. Use SELECIONAR MODELO GGUF."; Log.e(TAG, "MODEL_LOAD_ERROR: $lastError"); return@withLock false }
        Log.i(TAG, "MODEL_PATH=${file.absolutePath} MODEL_EXISTS=${file.isFile} MODEL_SIZE=${file.length()}")
        if (!file.isFile || file.length() <= 1024L) { lastError = "GGUF inválido ou inacessível: ${file.absolutePath}"; Log.e(TAG, "MODEL_LOAD_ERROR: $lastError"); return@withLock false }
        if (!bridge.isReady()) { lastError = "Biblioteca nativa companion_llama não carregou: ${bridge.loadError ?: "erro desconhecido"}"; Log.e(TAG, "MODEL_LOAD_ERROR: $lastError"); return@withLock false }
        val memory = ActivityManager.MemoryInfo().also { info -> (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info) }
        Log.i(TAG, "MEMORY total=${memory.totalMem} available=${memory.availMem} lowMemory=${memory.lowMemory}")
        Log.i(TAG, "MODEL_LOAD_START")
        val started = System.currentTimeMillis()
        val result = runCatching { bridge.nativeLoadModel(file.absolutePath) }
        lastLoadLatencyMs = System.currentTimeMillis() - started
        loaded = result.getOrDefault(false) && runCatching { bridge.nativeIsLoaded() }.getOrDefault(false)
        lastError = if (loaded) null else result.exceptionOrNull()?.message ?: bridge.lastNativeError().ifBlank { "llama.cpp recusou o GGUF" }
        if (loaded) Log.i(TAG, "MODEL_LOAD_OK CONTEXT_CREATE_OK load_ms=${lastLoadLatencyMs}") else Log.e(TAG, "MODEL_LOAD_ERROR: $lastError")
        loaded
        }
    }
    override fun unloadModel() { if (loaded) runCatching { bridge.nativeUnloadModel() }; loaded = false }
    override suspend fun generate(prompt: String, context: ConversationState): LLMResult = withContext(Dispatchers.Default) {
        if (!isAvailable() && !loadModel()) {
            LLMResult("", 0f, LLMProvider.LOCAL, 0, false, lastError ?: "IA LOCAL NÃO CARREGADA", false)
        } else {
        val started = System.currentTimeMillis()
        Log.i(TAG, "GENERATION_START prompt_chars=${prompt.length}")
        // Uma criança precisa de respostas rápidas; 12 tokens bastam para uma frase curta.
        val call = runCatching { bridge.nativeGenerate(prompt, 12, .4f) }
        lastGenerationLatencyMs = System.currentTimeMillis() - started
        lastGeneratedTokenCount = runCatching { bridge.nativeGeneratedTokenCount() }.getOrDefault(0)
        val text = call.getOrNull().orEmpty().take(350)
        val error = if (text.isBlank()) call.exceptionOrNull()?.message ?: bridge.lastNativeError().ifBlank { "llama.cpp não produziu tokens" } else null
        lastError = error
        if (text.isNotBlank()) Log.i(TAG, "GENERATION_END tokens=$lastGeneratedTokenCount latency_ms=${lastGenerationLatencyMs}") else Log.e(TAG, "GENERATION_ERROR: $error")
        LLMResult(text, if (text.isBlank()) 0f else .7f, LLMProvider.LOCAL, lastGenerationLatencyMs, text.isNotBlank(), error, call.isSuccess)
        }
    }
    fun modelPath() = model()?.absolutePath ?: File(appContext.filesDir, "models").absolutePath
    fun modelName() = model()?.name ?: "Sem modelo"
    fun modelSize() = model()?.length() ?: 0L
    fun isJniReady() = bridge.isReady()
    fun isContextCreated() = isAvailable()
    /** Pode ser chamado pela UI para não deixar uma resposta longa prender a conversa. */
    fun interruptGeneration() { if (bridge.isReady()) runCatching { bridge.nativeCancelGeneration() } }
    companion object { private const val TAG = "CompanheiroLLM" }
}

class DisabledRemoteLLMProvider(private val enabled: Boolean = false) : RemoteLLMProvider {
    override fun isEnabled() = enabled
    override suspend fun generate(prompt: String, context: ConversationState) = LLMResult("", 0f, LLMProvider.REMOTE, 0, false, "IA remota desativada")
}

/** Contrato mínimo enviado ao endpoint remoto. O histórico já está embutido em [message]. */
data class GabiChatRequest(val message: String)
data class GabiSseChunk(val text: String)

/**
 * Cliente SSE da conversa remota. A biblioteca local/JNI continua no projeto, mas não é usada
 * pelo fluxo principal. Os fragmentos ficam disponíveis em [chunks] para um consumidor de voz.
 */
class VercelRemoteLLMProvider(
    private val endpoint: String = BuildConfig.GABI_API_URL,
    private val bearerToken: String = BuildConfig.GABI_API_TOKEN,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) : RemoteLLMProvider {
    private val _chunks = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val chunks: SharedFlow<String> = _chunks.asSharedFlow()

    override fun isEnabled(): Boolean = endpoint.startsWith("https://")

    override suspend fun generate(prompt: String, context: ConversationState): LLMResult = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            return@withContext LLMResult("", 0f, LLMProvider.REMOTE, 0, false, "IA remota não configurada")
        }

        val requestJson = JSONObject().put("message", GabiChatRequest(prompt).message).toString()
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("Accept", "text/event-stream")
            .post(requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (bearerToken.isNotBlank()) requestBuilder.header("Authorization", "Bearer $bearerToken")
        val request = requestBuilder.build()

        suspendCancellableCoroutine { continuation ->
            val startedAt = System.currentTimeMillis()
            val responseText = StringBuilder()
            val completed = AtomicBoolean(false)

            fun complete(result: LLMResult) {
                if (completed.compareAndSet(false, true) && continuation.isActive) continuation.resume(result)
            }

            fun successResult(): LLMResult {
                val text = responseText.toString().trim()
                val latency = System.currentTimeMillis() - startedAt
                return if (text.isNotBlank()) {
                    LLMResult(text, .8f, LLMProvider.REMOTE, latency, true, null, true)
                } else {
                    LLMResult("", 0f, LLMProvider.REMOTE, latency, false, "SSE encerrou sem resposta do modelo", true)
                }
            }

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    Log.i(TAG, "REMOTE_SSE_OPEN")
                }

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data.trim() == "[DONE]") {
                        complete(successResult())
                        eventSource.cancel()
                        return
                    }
                    val text = runCatching { JSONObject(data).optString("text") }.getOrDefault("")
                    if (text.isNotEmpty()) {
                        responseText.append(text)
                        _chunks.tryEmit(GabiSseChunk(text).text)
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    complete(successResult())
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val latency = System.currentTimeMillis() - startedAt
                    val detail = when {
                        response != null -> "Servidor remoto retornou HTTP ${response.code}"
                        t != null -> t.message ?: t.javaClass.simpleName
                        else -> "Conexão SSE encerrada inesperadamente"
                    }
                    response?.close()
                    Log.e(TAG, "REMOTE_SSE_ERROR: $detail", t ?: IOException(detail))
                    complete(LLMResult("", 0f, LLMProvider.REMOTE, latency, false, detail, true))
                }
            }
            val source = EventSources.createFactory(client).newEventSource(request, listener)
            continuation.invokeOnCancellation { source.cancel() }
        }
    }

    companion object { private const val TAG = "CompanheiroRemoteLLM" }
}
