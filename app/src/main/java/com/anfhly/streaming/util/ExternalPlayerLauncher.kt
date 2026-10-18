package com.anfhly.streaming.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

/**
 * Utility para lanzar reproductores externos como Just Player
 */
object ExternalPlayerLauncher {
    
    private const val TAG = "ExternalPlayer"
    
    // Just Player package name
    private const val JUST_PLAYER_PACKAGE = "com.brouken.player"
    
    // MX Player packages (alternativa popular)
    private const val MX_PLAYER_PACKAGE = "com.mxtech.videoplayer.ad"
    private const val MX_PLAYER_PRO_PACKAGE = "com.mxtech.videoplayer.pro"
    
    // VLC
    private const val VLC_PACKAGE = "org.videolan.vlc"
    
    /**
     * Verifica si Just Player está instalado
     */
    fun isJustPlayerInstalled(context: Context): Boolean {
        return isPackageInstalled(context, JUST_PLAYER_PACKAGE)
    }
    
    /**
     * Verifica si algún reproductor externo está disponible
     */
    fun getAvailableExternalPlayer(context: Context): String? {
        val players = listOf(
            JUST_PLAYER_PACKAGE,
            MX_PLAYER_PRO_PACKAGE,
            MX_PLAYER_PACKAGE,
            VLC_PACKAGE
        )
        
        return players.firstOrNull { isPackageInstalled(context, it) }
    }
    
    /**
     * Lanza Just Player con la URL del video
     */
    fun launchJustPlayer(
        activity: Activity,
        videoUrl: String,
        title: String? = null,
        subtitleUrl: String? = null
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(JUST_PLAYER_PACKAGE)
                setDataAndType(videoUrl.toUri(), "video/*")
                
                // Título
                title?.let {
                    putExtra("title", it)
                }
                
                // Subtítulos externos
                subtitleUrl?.let {
                    putExtra("subs", arrayOf(Uri.parse(it)))
                    putExtra("subs.name", arrayOf("Subtítulos"))
                    putExtra("subs.enable", arrayOf(0))
                }
                
                // Flags para Android TV
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            activity.startActivity(intent)
            Log.d(TAG, "Just Player launched successfully")
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Just Player not installed", e)
            false
        }
    }
    
    /**
     * Lanza el mejor reproductor disponible (Just Player > MX Player > VLC)
     */
    fun launchBestPlayer(
        activity: Activity,
        videoUrl: String,
        title: String? = null,
        subtitleUrl: String? = null
    ): Boolean {
        val availablePlayer = getAvailableExternalPlayer(activity)
        
        return when (availablePlayer) {
            JUST_PLAYER_PACKAGE -> launchJustPlayer(activity, videoUrl, title, subtitleUrl)
            MX_PLAYER_PACKAGE, MX_PLAYER_PRO_PACKAGE -> launchMxPlayer(activity, videoUrl, title, subtitleUrl)
            VLC_PACKAGE -> launchVlc(activity, videoUrl, title, subtitleUrl)
            else -> {
                Log.w(TAG, "No external player available, launching system picker")
                launchSystemPlayer(activity, videoUrl, title)
            }
        }
    }
    
    /**
     * Lanza MX Player
     */
    private fun launchMxPlayer(
        activity: Activity,
        videoUrl: String,
        title: String? = null,
        subtitleUrl: String? = null
    ): Boolean {
        return try {
            val packageName = if (isPackageInstalled(activity, MX_PLAYER_PRO_PACKAGE)) {
                MX_PLAYER_PRO_PACKAGE
            } else {
                MX_PLAYER_PACKAGE
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(packageName)
                setDataAndType(videoUrl.toUri(), "video/*")
                putExtra("title", title)
                subtitleUrl?.let {
                    putExtra("subs", it)
                }
            }
            
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
    
    /**
     * Lanza VLC
     */
    private fun launchVlc(
        activity: Activity,
        videoUrl: String,
        title: String? = null,
        subtitleUrl: String? = null
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(VLC_PACKAGE)
                setDataAndType(videoUrl.toUri(), "video/*")
                putExtra("title", title)
                subtitleUrl?.let {
                    putExtra("subtitles_location", it)
                }
            }
            
            activity.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
    
    /**
     * Lanza el selector de apps del sistema
     */
    private fun launchSystemPlayer(activity: Activity, videoUrl: String, title: String? = null): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(videoUrl.toUri(), "video/*")
                putExtra("title", title)
            }
            
            val chooser = Intent.createChooser(intent, "Reproducir video")
            activity.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No player available", e)
            false
        }
    }
    
    /**
     * Lanza un reproductor específico por clave (vlc, mxplayer, xplayer, etc.)
     */
    fun launchByKey(activity: Activity, playerKey: String, videoUrl: String, title: String? = null, subtitleUrl: String? = null): Boolean {
        return when (playerKey) {
            "vlc" -> {
                if (isPackageInstalled(activity, VLC_PACKAGE)) {
                    launchVlc(activity, videoUrl, title, subtitleUrl)
                } else {
                    launchSystemPlayer(activity, videoUrl, title)
                }
            }
            "mxplayer" -> {
                if (isPackageInstalled(activity, MX_PLAYER_PACKAGE) || isPackageInstalled(activity, MX_PLAYER_PRO_PACKAGE)) {
                    launchMxPlayer(activity, videoUrl, title, subtitleUrl)
                } else {
                    launchSystemPlayer(activity, videoUrl, title)
                }
            }
            "xplayer" -> launchSystemPlayer(activity, videoUrl, title)
            else -> launchBestPlayer(activity, videoUrl, title, subtitleUrl)
        }
    }

    /**
     * Verifica si un paquete está instalado
     */
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
