package br.com.companheirofala

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class OpenAiConversationService(private val baseUrl: String = BuildConfig.AI_BACKEND_URL) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    fun reply(message: String, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        executor.execute {
            val result = runCatching {
                val connection = (URL("$baseUrl/api/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write(JSONObject().put("message", message).toString().toByteArray()) }
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                JSONObject(body).optString("reply").takeIf { it.isNotBlank() }
                    ?: error("Backend response did not contain a reply")
            }
            mainHandler.post { result.onSuccess(onSuccess).onFailure { onFailure() } }
        }
    }
}
