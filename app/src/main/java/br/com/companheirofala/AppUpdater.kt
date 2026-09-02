package br.com.companheirofala

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AppUpdater(private val activity: Activity) {
    private val apiUrl = "https://api.github.com/repos/catsandygo-design/CompanheiroFala/releases/latest"
    private var downloadId = -1L
    private var pendingInstallUri: Uri? = null

    fun checkAndUpdate(onStatus: (String) -> Unit) {
        thread {
            try {
                val connection = URL(apiUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 7000
                connection.readTimeout = 7000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "CompanheiroFala-Android")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v")
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val localVersion = packageInfo.versionName ?: "0.0.0"
                if (apkUrl != null && isNewer(tag, localVersion)) {
                    activity.runOnUiThread {
                        onStatus("Nova versão $tag encontrada. Baixando atualização...")
                        downloadAndInstall(apkUrl, tag, onStatus)
                    }
                }
            } catch (_: Exception) {
                // O app continua funcionando normalmente quando estiver offline.
            }
        }
    }

    fun onResume() {
        val uri = pendingInstallUri ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()) {
            pendingInstallUri = null
            launchInstaller(uri)
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        val size = maxOf(r.size, l.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun downloadAndInstall(url: String, version: String, onStatus: (String) -> Unit) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Atualizando Companheiro Fala")
            .setDescription("Baixando versão $version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, "updates", "CompanheiroFala-$version.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                try { activity.unregisterReceiver(this) } catch (_: Exception) {}
                val uri = manager.getUriForDownloadedFile(downloadId) ?: return
                activity.runOnUiThread { onStatus("Atualização pronta. Confirme a instalação.") }
                install(uri)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") activity.registerReceiver(receiver, filter)
    }

    private fun install(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            pendingInstallUri = uri
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        launchInstaller(uri)
    }

    private fun launchInstaller(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
