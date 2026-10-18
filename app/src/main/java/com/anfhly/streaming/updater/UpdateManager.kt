package com.anfhly.streaming.updater

import android.app.DownloadManager
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class UpdateManager(private val context: Context) {

    private val TAG = "UpdateManager"
    // URL del archivo update.json en Google Drive
    private val UPDATE_URL = "https://drive.google.com/uc?export=download&id=1cIgIxd0tyQI9Efy8Cht84_nTxDff65Lr"
    // El APK URL se obtiene dinámicamente desde el update.json

    private var downloadId: Long = -1
    private var progressDialog: ProgressDialog? = null

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String,
        val buildDate: String, // Fecha de build para detectar cambios sin cambiar versionCode
        val fileHash: String? = null // Opcional: hash del archivo para verificación
    )

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates... Current version: $currentVersionCode")

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(UPDATE_URL)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to check update: HTTP ${response.code}")
                    return@withContext null
                }

                val jsonString = response.body?.string() ?: return@withContext null
                val json = JSONObject(jsonString)

                val versionCode = json.getInt("versionCode")
                val versionName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val changelog = json.optString("changelog", "")
                val buildDate = json.optString("buildDate", "")
                val fileHash = json.optString("fileHash", null)

                Log.d(TAG, "Remote version: $versionCode ($versionName), buildDate: $buildDate")

                // Verificar siempre que haya una actualización disponible
                // Incluso con el mismo versionCode, si el buildDate es diferente, hay nueva versión
                val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
                val lastBuildDate = prefs.getString("last_build_date", "")

                // Siempre detectar como nueva versión si:
                // 1. versionCode es mayor, O
                // 2. buildDate es diferente (incluso con mismo versionCode)
                val isNewVersion = when {
                    versionCode > currentVersionCode -> {
                        Log.d(TAG, "New versionCode detected: $versionCode > $currentVersionCode")
                        true
                    }
                    buildDate.isNotEmpty() && buildDate != lastBuildDate -> {
                        Log.d(TAG, "New buildDate detected: $buildDate != $lastBuildDate")
                        true
                    }
                    else -> {
                        Log.d(TAG, "No update - versionCode: $versionCode, buildDate: $buildDate, last: $lastBuildDate")
                        false
                    }
                }

                if (isNewVersion) {
                    Log.d(TAG, "Update available! (buildDate: $buildDate vs last: $lastBuildDate)")
                    return@withContext UpdateInfo(versionCode, versionName, apkUrl, changelog, buildDate, fileHash)
                } else {
                    Log.d(TAG, "No update needed")
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}", e)
            return@withContext null
        }
    }

    fun downloadAndInstall(updateInfo: UpdateInfo) {
        Log.d(TAG, "Starting download from: ${updateInfo.apkUrl}")

        // Mostrar progreso
        progressDialog = ProgressDialog(context).apply {
            setMessage("Descargando actualización...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = false
            setCancelable(false)
            show()
        }

        // Crear directorio de descargas si no existe
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Updates")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        // Nombre del archivo
        val fileName = "app-update-v${updateInfo.versionName}.apk"
        val destinationFile = File(downloadDir, fileName)

        // Eliminar archivo anterior si existe
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        // Configurar descarga con DownloadManager
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl)).apply {
            setTitle("Actualización ${updateInfo.versionName}")
            setDescription("Descargando APK...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        // Registrar receiver para cuando termine la descarga
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                if (id == downloadId) {
                    Log.d(TAG, "Download completed: $destinationFile")
                    progressDialog?.dismiss()
                    context?.unregisterReceiver(this)
                    installApk(destinationFile)
                }
            }
        }

        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        downloadId = downloadManager.enqueue(request)
    }

    private fun installApk(apkFile: File) {
        Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ requiere FileProvider
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
    }

    fun showUpdateDialog(updateInfo: UpdateInfo, onUpdate: () -> Unit, onCancel: () -> Unit) {
        android.app.AlertDialog.Builder(context)
            .setTitle("Nueva versión disponible: ${updateInfo.versionName}")
            .setMessage("${updateInfo.changelog}\n\n¿Deseas actualizar ahora?")
            .setPositiveButton("Actualizar") { _, _ -> onUpdate() }
            .setNegativeButton("Más tarde") { _, _ -> onCancel() }
            .setCancelable(false)
            .show()
    }

    fun markUpdateInstalled(buildDate: String) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        prefs.edit().putString("last_build_date", buildDate).apply()
        Log.d(TAG, "Marked update as installed: $buildDate")
    }
}
