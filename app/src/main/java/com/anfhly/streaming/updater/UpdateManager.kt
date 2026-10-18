package com.anfhly.streaming.updater

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_OWNER = "Adriavc15"
        private const val GITHUB_REPO = "ANFHLYTV"

        // URL directa al manifiesto de actualización en el repositorio GitHub
        private const val GITHUB_RAW_MANIFEST_URL =
            "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/app/update.json"

        // URL a la API de GitHub Releases para última versión
        private const val GITHUB_RELEASES_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

        // URL por defecto del APK publicado en GitHub Releases
        private const val DEFAULT_RELEASE_APK_URL =
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest/download/Anfhly%20Tv.apk"
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String,
        val buildDate: String = "",
        val fileHash: String? = null
    )

    /**
     * Consulta actualizaciones desde GitHub (manifiesto raw o GitHub Releases).
     */
    suspend fun checkForUpdate(
        currentVersionCode: Int,
        currentVersionName: String = ""
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        Log.d(TAG, "Buscando actualizaciones en GitHub... Versión actual: $currentVersionName (código: $currentVersionCode)")

        // 1. Intentar obtener el manifiesto raw desde GitHub
        var update = fetchFromManifest(client, currentVersionCode, currentVersionName)

        // 2. Si no hay manifiesto o no arrojó actualización, verificar con la API de Releases de GitHub
        if (update == null) {
            update = fetchFromGithubReleases(client, currentVersionCode, currentVersionName)
        }

        update
    }

    private fun fetchFromManifest(
        client: OkHttpClient,
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateInfo? {
        try {
            val request = Request.Builder()
                .url(GITHUB_RAW_MANIFEST_URL)
                .header("User-Agent", "AnfhlyTV-App")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "Manifiesto GitHub no disponible (HTTP ${response.code})")
                    return null
                }

                val jsonString = response.body?.string() ?: return null
                val json = JSONObject(jsonString)

                val remoteVersionCode = json.optInt("versionCode", 0)
                val remoteVersionName = json.optString("versionName", "")
                var apkUrl = json.optString("apkUrl", "")
                val changelog = json.optString("changelog", "Mejoras y correcciones generales")
                val buildDate = json.optString("buildDate", "")
                val fileHash = if (json.has("fileHash")) json.getString("fileHash") else null

                if (apkUrl.isBlank()) {
                    apkUrl = DEFAULT_RELEASE_APK_URL
                }

                val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
                val lastBuildDate = prefs.getString("last_build_date", "")

                val isNewer = when {
                    remoteVersionCode > currentVersionCode -> true
                    remoteVersionName.isNotEmpty() && currentVersionName.isNotEmpty() &&
                            compareSemVer(remoteVersionName, currentVersionName) > 0 -> true
                    buildDate.isNotEmpty() && buildDate != lastBuildDate && remoteVersionCode == currentVersionCode -> true
                    else -> false
                }

                if (isNewer) {
                    Log.d(TAG, "Nueva versión encontrada en manifiesto GitHub: $remoteVersionName ($remoteVersionCode)")
                    return UpdateInfo(remoteVersionCode, remoteVersionName, apkUrl, changelog, buildDate, fileHash)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo manifiesto de GitHub: ${e.message}")
        }
        return null
    }

    private fun fetchFromGithubReleases(
        client: OkHttpClient,
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateInfo? {
        try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_API_URL)
                .header("User-Agent", "AnfhlyTV-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "GitHub Releases API retornó HTTP ${response.code}")
                    return null
                }

                val jsonString = response.body?.string() ?: return null
                val json = JSONObject(jsonString)

                val tagName = json.optString("tag_name", "").trim()
                val cleanTag = tagName.removePrefix("v").removePrefix("V")
                val changelog = json.optString("body", "Nueva versión disponible en GitHub")
                val publishedAt = json.optString("published_at", "")

                // Buscar el asset APK
                var apkUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            if (name.contains("Anfhly", ignoreCase = true)) {
                                break // Priorizar Anfhly Tv.apk
                            }
                        }
                    }
                }

                if (apkUrl.isBlank()) {
                    apkUrl = DEFAULT_RELEASE_APK_URL
                }

                val isNewer = if (cleanTag.isNotEmpty() && currentVersionName.isNotEmpty()) {
                    compareSemVer(cleanTag, currentVersionName) > 0
                } else false

                if (isNewer) {
                    Log.d(TAG, "Nueva versión encontrada en GitHub Releases: $cleanTag")
                    return UpdateInfo(
                        versionCode = currentVersionCode + 1,
                        versionName = cleanTag,
                        apkUrl = apkUrl,
                        changelog = changelog,
                        buildDate = publishedAt
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando GitHub Releases: ${e.message}")
        }
        return null
    }

    /**
     * Descarga el APK con indicador de progreso y lanza el instalador.
     */
    fun downloadAndInstall(
        updateInfo: UpdateInfo,
        scope: CoroutineScope,
        onProgress: ((Int) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        Log.d(TAG, "Iniciando descarga de actualización desde: ${updateInfo.apkUrl}")

        val progressDialog = ProgressDialog(context).apply {
            setTitle("Descargando actualización")
            setMessage("Descargando Anfhly TV v${updateInfo.versionName}...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            max = 100
            setCancelable(false)
            show()
        }

        scope.launch(Dispatchers.IO) {
            try {
                // Directorio seguro para descarga de APK
                val downloadDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
                    "Updates"
                )
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val destinationFile = File(downloadDir, "Anfhly Tv.apk")
                if (destinationFile.exists()) destinationFile.delete()

                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url(updateInfo.apkUrl)
                    .header("User-Agent", "AnfhlyTV-App")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw java.io.IOException("Error en el servidor: HTTP ${response.code}")
                    }

                    val body = response.body ?: throw java.io.IOException("Respuesta vacía del servidor")
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    val outputStream = destinationFile.outputStream()

                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalBytesRead: Long = 0

                    inputStream.use { input ->
                        outputStream.use { output ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    val percent = ((totalBytesRead * 100) / contentLength).toInt()
                                    withContext(Dispatchers.Main) {
                                        progressDialog.progress = percent
                                        onProgress?.invoke(percent)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        if (updateInfo.buildDate.isNotEmpty()) {
                            markUpdateInstalled(updateInfo.buildDate)
                        }
                        installApk(destinationFile)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error descargando APK: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    onError?.invoke(e.message ?: "Error desconocido")
                    Toast.makeText(
                        context,
                        "Error al descargar la actualización: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Lanza el intent del sistema para instalar el archivo APK descargado.
     */
    fun installApk(apkFile: File) {
        try {
            Log.d(TAG, "Instalando APK: ${apkFile.absolutePath}")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

                val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }

                setDataAndType(apkUri, "application/vnd.android.package-archive")
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando instalador de APK: ${e.message}", e)
            Toast.makeText(
                context,
                "No se pudo iniciar el instalador: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Muestra diálogo amigable para Android TV informando la nueva versión.
     */
    fun showUpdateDialog(
        updateInfo: UpdateInfo,
        onUpdate: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        AlertDialog.Builder(context)
            .setTitle("Nueva actualización disponible (v${updateInfo.versionName})")
            .setMessage("${updateInfo.changelog}\n\n¿Deseas descargar e instalar la actualización ahora?")
            .setPositiveButton("Actualizar ahora") { _, _ -> onUpdate() }
            .setNegativeButton("Más tarde") { _, _ -> onCancel() }
            .setCancelable(true)
            .show()
    }

    fun markUpdateInstalled(buildDate: String) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        prefs.edit().putString("last_build_date", buildDate).apply()
        Log.d(TAG, "Actualización marcada como instalada: $buildDate")
    }

    private fun compareSemVer(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
