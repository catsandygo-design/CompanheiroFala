package br.com.companheirofala.core.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

class ModelFileManager(private val context: Context) {
    private val directory = File(context.filesDir, "models").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    fun selectedModel(): File? {
        val persisted = prefs.getString("selected_path", null)?.let(::File)
        if (persisted?.isFile == true && persisted.extension.equals("gguf", true)) return persisted
        return directory.listFiles()?.firstOrNull { it.extension.equals("gguf", true) && it.length() > 1024L }
    }
    fun import(uri: Uri, onProgress: (Int) -> Unit = {}): Result<File> = runCatching {
        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')
        if (displayName.isNullOrBlank() || !displayName.endsWith(".gguf", true)) throw IllegalArgumentException("Escolha um arquivo .gguf")
        val target = File(directory, displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")); val temporary = File(directory, "$displayName.part")
        context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { output ->
            val total = context.contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L; var copied = 0L; val buffer = ByteArray(64 * 1024); var read: Int
            while (input.read(buffer).also { read = it } >= 0) { output.write(buffer, 0, read); copied += read; if (total > 0) onProgress(((copied * 100) / total).toInt()) }
        }} ?: throw IllegalArgumentException("Não foi possível abrir o arquivo")
        if (temporary.length() <= 1024L) throw IllegalArgumentException("Arquivo GGUF inválido ou vazio")
        target.delete(); if (!temporary.renameTo(target)) throw IllegalStateException("Não foi possível salvar o modelo")
        prefs.edit().putString("selected_path", target.absolutePath).apply()
        target
    }
    fun remove(): Boolean { val removed = selectedModel()?.delete() ?: false; prefs.edit().remove("selected_path").apply(); return removed }
}
