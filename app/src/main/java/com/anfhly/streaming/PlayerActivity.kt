@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.anfhly.streaming

import android.app.Activity
import android.app.Dialog
import android.app.PictureInPictureParams
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.AudioAttributes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anfhly.streaming.utils.FFmpegManager
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import com.anfhly.streaming.data.local.SettingsDataStore
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.Episode
import com.anfhly.streaming.data.model.Season
import com.anfhly.streaming.extractors.Extractor
import com.anfhly.streaming.data.repository.WatchProgressRepository
import com.anfhly.streaming.utils.DeviceCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PlayerActivity : AppCompatActivity() {
    
    var exoPlayer: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private val tag = "PlayerActivity"
    
    // Watch Progress Repository
    private lateinit var watchProgressRepository: WatchProgressRepository
    private var contentId: String = ""
    private var contentType: String = ""
    private var seasonNumber: Int? = null
    private var episodeNumber: Int? = null
    
    // Device capabilities
    private lateinit var deviceCapabilities: DeviceCapabilities
    
    // Resume playback
    private var resumePositionFromIntent: Long = 0L
    private lateinit var sharedPreferences: SharedPreferences
    
    // Settings DataStore for audio/subtitle/quality preferences
    private lateinit var settingsDataStore: SettingsDataStore
    private var preferredAudioLanguage: String = "es"
    private var preferredSubtitleLanguage: String = "es"
    private var subtitlesEnabled: Boolean = true
    private var preferredQuality: String = "1080p"
    
    // UI elements
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var ivLogo: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnStop: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnAudio: ImageView
    private lateinit var btnSubtitle: ImageView
    private lateinit var btnAspect: ImageView
    private lateinit var btnQuality: ImageView
    private lateinit var llBrightness: LinearLayout
    private lateinit var llVolume: LinearLayout
    private lateinit var pbBrightness: ProgressBar
    private lateinit var pbVolume: ProgressBar
    private lateinit var tvBrightness: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvErrorLog: TextView
    
    private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var isManualZoomMode = false
    private var hasShownResumeDialog = false
    private var isInPictureInPictureMode = false
    
    // FFmpeg Manager
    private lateinit var ffmpegManager: FFmpegManager
    
    // Quality Manager
    private lateinit var qualityManager: com.anfhly.streaming.utils.QualityManager
    
    // Resume playback - guardar posición del video
    private var currentVideoUrl: String = ""
    private val prefName = "video_progress"
    private val positionPrefix = "position_"
    private val durationPrefix = "duration_"
    private val seekResetDelay = 1000L
    
    // URL expiration handling
    private var currentVideoExpiresAt: Long? = null
    private var originalVideoUrl: String? = null
    private var isRefreshingUrl = false
    
    // Calidades manuales del extractor (para videos MP4 directos como Ok.ru)
    private var manualQualities: List<com.anfhly.streaming.data.model.Video.Quality> = emptyList()
    private var manualSubtitles: List<com.anfhly.streaming.data.model.Video.Subtitle> = emptyList()
    private var activeManualSubtitle: com.anfhly.streaming.data.model.Video.Subtitle? = null
    private var manualAudios: List<com.anfhly.streaming.data.model.Video.AudioTrack> = emptyList()
    private var currentHeaders: Map<String, String> = emptyMap()
    private var isManualQuality: Boolean = false
    private var currentVideoMimeType: String? = null
    private var currentStartPosition: Long = -1L
    private var preferredPlayer: String = "exoplayer"
    
    // Custom SubtitleView para evitar zoom con el video
    private var customSubtitleView: androidx.media3.ui.SubtitleView? = null
    private var subtitleListener: androidx.media3.common.Player.Listener? = null
    
    private fun bindCustomSubtitleView(player: androidx.media3.exoplayer.ExoPlayer?) {
        // Remover listener anterior si existe
        subtitleListener?.let { player?.removeListener(it) }
        
        subtitleListener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                Log.d("PlayerActivity", "onCues(cueGroup) called, cue count: ${cueGroup.cues.size}")
                customSubtitleView?.setCues(cueGroup.cues)
                
                // Mostrar customSubtitleView si hay cues activos
                customSubtitleView?.visibility = if (cueGroup.cues.isNotEmpty()) android.view.View.VISIBLE else android.view.View.INVISIBLE
            }
            
            override fun onCues(cues: List<androidx.media3.common.text.Cue>) {
                Log.d("PlayerActivity", "onCues(List<Cue>) called, cue count: ${cues.size}")
                customSubtitleView?.setCues(cues)
                
                customSubtitleView?.visibility = if (cues.isNotEmpty()) android.view.View.VISIBLE else android.view.View.INVISIBLE
            }
        }
        player?.addListener(subtitleListener!!)
        
        // Mantener el SubtitleView interno de PlayerView permanentemente oculto
        playerView?.subtitleView?.visibility = android.view.View.GONE
        
        // Inicializar visibilidad
        customSubtitleView?.visibility = android.view.View.INVISIBLE
    }

    // Navegación entre episodios
    private var currentSeason: Season? = null
    private var currentEpisode: Episode? = null
    private var currentSeries: Content.Series? = null
    private var currentMovie: Content.Movie? = null
    private var currentContent: Content? = null  // Referencia genérica al contenido actual
    
    private val isLiveChannel: Boolean
        get() = contentType == "movie" && currentMovie?.releaseDate.isNullOrBlank()
    
    // Capturador global de errores - guarda en archivo para diagnóstico
    private fun saveErrorToFile(errorType: String, message: String?, stackTrace: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "PlayerActivity_Crash_$timestamp.txt"
            
            // Guardar en directorio de descargas para fácil acceso
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            FileWriter(file).use { writer ->
                writer.write("=== ERROR EN PLAYERACTIVITY ===\n")
                writer.write("Fecha: $timestamp\n")
                writer.write("Tipo: $errorType\n")
                writer.write("Mensaje: $message\n\n")
                writer.write("=== STACK TRACE ===\n")
                writer.write(stackTrace)
                writer.write("\n\n=== FIN DEL REPORTE ===")
            }
            
            Log.d(tag, "Error guardado en: ${file.absolutePath}")
            
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Error guardado en Descargas: $fileName",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "No se pudo guardar el error en archivo: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar contexto del extractor
        Extractor.initializeContext(this)
        
        // Mantener pantalla encendida durante reproducción
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences(prefName, MODE_PRIVATE)
        
        // Inicializar FFmpeg Manager
        FFmpegManager.initialize()
        ffmpegManager = FFmpegManager(this)
        
        // Inicializar Quality Manager
        qualityManager = com.anfhly.streaming.utils.QualityManager(this)
        Log.d(tag, "Quality Manager initialized")
        Log.d(tag, qualityManager.getDeviceInfo())
        
        // Inicializar Device Capabilities
        deviceCapabilities = DeviceCapabilities(this)
        Log.d(tag, "Device Capabilities initialized")
        
        // Capturador global de errores
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(tag, "CRASH no manejado: ${throwable.message}", throwable)
            
            // Guardar error en archivo
            saveErrorToFile(
                throwable.javaClass.simpleName,
                throwable.message,
                throwable.stackTraceToString()
            )
            
            runOnUiThread {
                Toast.makeText(
                    this,
                    "CRASH: ${throwable.javaClass.simpleName}\nRevisa Descargas/PlayerActivity_Crash_*.txt",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            Thread.sleep(5000)
            finish()
        }
        
        try {
            Log.d(tag, "onCreate: START")
            Log.d(tag, "onCreate: intent extras = ${intent.extras}")
            setContentView(R.layout.activity_player_tv)
            Log.d(tag, "onCreate: setContentView done")
            
            // Inicializar WatchProgressRepository
            watchProgressRepository = WatchProgressRepository(this)
            
            // Obtener content y posición de reanudación
            val content = intent.getSerializableExtra(EXTRA_CONTENT) as? Content
            val episode = intent.getSerializableExtra(EXTRA_EPISODE) as? Episode
            resumePositionFromIntent = intent.getLongExtra(EXTRA_RESUME_POSITION, 0L)
            
            Log.d(tag, "onCreate: content=$content, episode=$episode, resumePosition=$resumePositionFromIntent")
            
            if (content == null) {
                Log.e(tag, "No content provided, finishing")
                Toast.makeText(this, "No content provided", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // Guardar info para el progreso
            contentId = content.id
            currentContent = content
            contentType = when (content) {
                is Content.Movie -> {
                    currentMovie = content
                    "movie"
                }
                is Content.Series -> {
                    currentSeries = content
                    "series"
                }
                else -> "unknown"
            }
            currentEpisode = episode
            seasonNumber = episode?.seasonNumber
            episodeNumber = episode?.episodeNumber
            
            Log.d(tag, "onCreate: initializing views...")
            initializeViews()
            Log.d(tag, "onCreate: views initialized")
            
            // Pre-inicializar la instancia del reproductor y asociarla al PlayerView
            // para evitar el bug de escalado/zoom de la superficie (surface_view)
            initializePlayerInstance()
            
            // Inicializar SettingsDataStore y cargar preferencias
            settingsDataStore = SettingsDataStore(this)
            lifecycleScope.launch {
                try {
                    val userSettings = settingsDataStore.userSettings.first()
                    preferredAudioLanguage = userSettings.defaultAudioLanguage
                    preferredSubtitleLanguage = userSettings.defaultSubtitleLanguage
                    subtitlesEnabled = userSettings.subtitlesEnabled
                    val hwMax = deviceCapabilities.getHardwareMaxVideoSize()
                    val isHardware4k = hwMax.second >= 2160
                    val isHardware2k = hwMax.second >= 1440
                    
                    preferredQuality = if (userSettings.autoQuality) {
                        if (isHardware4k) "4k" else if (isHardware2k) "2k" else "auto"
                    } else if (userSettings.defaultVideoQuality == "1080p" && isHardware4k) {
                        "4k"
                    } else if (userSettings.defaultVideoQuality == "1080p" && isHardware2k) {
                        "2k"
                    } else {
                        userSettings.defaultVideoQuality
                    }
                    Log.d(tag, "Loaded settings: audio=$preferredAudioLanguage, subtitle=$preferredSubtitleLanguage, quality=$preferredQuality (hwMax=${hwMax.first}x${hwMax.second}), subtitlesEnabled=$subtitlesEnabled")
                    
                    // Actualizar parámetros del TrackSelector con las preferencias del usuario
                    updatePlayerTrackSelectorParameters()
                } catch (e: Exception) {
                    Log.e(tag, "Error loading settings: ${e.message}")
                }
                
                // Cargar y reproducir el video
                Log.d(tag, "onCreate: initializing player...")
                initializePlayer(content, episode)
                Log.d(tag, "onCreate: player initialized")
            }
        } catch (e: Exception) {
            Log.e(tag, "FATAL ERROR in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Intercepta eventos genéricos de motion (joystick/gamepad) para soporte de Meta Quest 3.
     * Traduce los ejes del thumbstick del mando Quest a eventos de D-pad para controlar el reproductor.
     */
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        val translatedEvent = com.anfhly.streaming.utils.QuestInputHelper.processGenericMotionEvent(event)
        if (translatedEvent != null) {
            Log.d(tag, "Quest joystick -> D-pad in Player: keyCode=${translatedEvent.keyCode}")
            dispatchKeyEvent(translatedEvent)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // === Meta Quest 3 Controller Support ===
        // Traducir botones de gamepad a equivalentes de Android TV
        if (com.anfhly.streaming.utils.QuestInputHelper.isGamepadButton(event)) {
            val translatedEvent = com.anfhly.streaming.utils.QuestInputHelper.translateButtonEvent(event)
            if (translatedEvent != null) {
                Log.d(tag, "Quest button ${event.keyCode} -> ${translatedEvent.keyCode} in Player")
                
                // Manejo especial para botones multimedia en el reproductor
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (translatedEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            exoPlayer?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                            playerView?.showController()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            val seekAmount = 10000L // 10 segundos
                            exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) - seekAmount)
                            playerView?.showController()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            val seekAmount = 10000L // 10 segundos
                            exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) + seekAmount)
                            playerView?.showController()
                            return true
                        }
                    }
                }
                
                return super.dispatchKeyEvent(translatedEvent)
            }
        }
        
        // Handle BACK key to hide controller if visible
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            // Check if controller is visible by checking if any control view has focus
            val controllerViews = listOf(
                R.id.btn_prev, R.id.btn_stop, R.id.btn_play_pause, R.id.btn_next,
                R.id.exo_rew, R.id.exo_ffwd,
                R.id.btn_audio, R.id.btn_subtitle, R.id.btn_quality, R.id.btn_aspect,
                R.id.exo_progress, R.id.exo_position, R.id.exo_duration
            )
            val currentFocus = currentFocus
            val isControllerVisible = currentFocus != null && controllerViews.contains(currentFocus.id)
            
            if (isControllerVisible) {
                Log.d(tag, "dispatchKeyEvent: BACK pressed, hiding controller")
                playerView?.hideController()
                return true
            }
        }
        
        // D-Pad Navigation for player controller
        val bottomControls = listOf(
            R.id.btn_audio, R.id.btn_subtitle, R.id.btn_quality,
            R.id.btn_prev, R.id.exo_rew, R.id.btn_stop, R.id.btn_play_pause, R.id.exo_ffwd, R.id.btn_next,
            R.id.btn_aspect
        )
        val currentFocus = currentFocus
        val progressBar = playerView?.findViewById<android.view.View>(R.id.exo_progress)
            ?: findViewById(R.id.exo_progress)
        val isOnBottomControl = currentFocus != null && bottomControls.contains(currentFocus.id)
        val isOnProgressBar = currentFocus?.id == R.id.exo_progress

        fun getActiveBottomButtons(): List<View> {
            val exoRew = playerView?.findViewById<View>(R.id.exo_rew) ?: findViewById(R.id.exo_rew)
            val exoFfwd = playerView?.findViewById<View>(R.id.exo_ffwd) ?: findViewById(R.id.exo_ffwd)
            val all = listOfNotNull(
                btnAudio, btnSubtitle, btnQuality,
                btnPrev, exoRew, btnStop, btnPlayPause, exoFfwd, btnNext,
                btnAspect
            )
            return all.filter { it.visibility == View.VISIBLE && it.isFocusable && it.isEnabled }
        }

        if (isOnBottomControl) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val buttons = getActiveBottomButtons()
                        val focusedId = currentFocus?.id
                        val idx = buttons.indexOfFirst { it.id == focusedId }
                        if (idx > 0) {
                            buttons[idx - 1].requestFocus()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val buttons = getActiveBottomButtons()
                        val focusedId = currentFocus?.id
                        val idx = buttons.indexOfFirst { it.id == focusedId }
                        if (idx in 0 until buttons.size - 1) {
                            buttons[idx + 1].requestFocus()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (progressBar != null) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            progressBar.requestFocus()
                        }
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    return true // Consume DOWN so it doesn't leave the controller
                }
            }
        }

        if (isOnProgressBar) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val buttons = getActiveBottomButtons()
                        val target = buttons.firstOrNull { it.id == R.id.btn_play_pause }
                            ?: buttons.firstOrNull { it.id == R.id.btn_stop || it.id == R.id.exo_rew || it.id == R.id.btn_prev }
                            ?: buttons.firstOrNull()
                        target?.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        playerView?.hideController()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    
    private fun initializeViews() {
        playerView = findViewById(R.id.player_view)
        
        // Controles personalizados (buscar manualmente)
        tvTitle = playerView?.findViewById(R.id.tv_title) ?: findViewById(R.id.tv_title)
        tvSubtitle = playerView?.findViewById(R.id.tv_subtitle) ?: findViewById(R.id.tv_subtitle)
        ivLogo = playerView?.findViewById(R.id.iv_logo) ?: findViewById(R.id.iv_logo)
        btnPrev = playerView?.findViewById(R.id.btn_prev) ?: findViewById(R.id.btn_prev)
        btnNext = playerView?.findViewById(R.id.btn_next) ?: findViewById(R.id.btn_next)
        btnStop = playerView?.findViewById(R.id.btn_stop) ?: findViewById(R.id.btn_stop)
        btnPlayPause = playerView?.findViewById(R.id.btn_play_pause) ?: findViewById(R.id.btn_play_pause)
        btnAspect = playerView?.findViewById(R.id.btn_aspect) ?: findViewById(R.id.btn_aspect)
        btnQuality = playerView?.findViewById(R.id.btn_quality) ?: findViewById(R.id.btn_quality)
        btnAudio = playerView?.findViewById(R.id.btn_audio) ?: findViewById(R.id.btn_audio)
        btnSubtitle = playerView?.findViewById(R.id.btn_subtitle) ?: findViewById(R.id.btn_subtitle)
        
        // Configurar incremento de búsqueda en la barra de progreso a 10 segundos (evita avanzar demasiado en videos largos)
        val exoProgress = playerView?.findViewById<androidx.media3.ui.DefaultTimeBar>(R.id.exo_progress) ?: findViewById(R.id.exo_progress)
        exoProgress?.setKeyTimeIncrement(10000L)
        
        // Los controles estándar de ExoPlayer (exo_progress, exo_position, exo_duration)
        // son manejados automáticamente por PlayerView - no buscar manualmente
        // PlayerView los encuentra por sus IDs estándar en el controller layout
        
        // Controles de brillo/volumen están en activity layout
        llBrightness = findViewById(R.id.ll_brightness)
        llVolume = findViewById(R.id.ll_volume)
        pbBrightness = findViewById(R.id.pb_brightness)
        pbVolume = findViewById(R.id.pb_volume)
        tvBrightness = findViewById(R.id.tv_brightness_percentage)
        tvVolume = findViewById(R.id.tv_volume_percentage)
        
        Log.d(tag, "Controls - btnPrev: $btnPrev, btnStop: $btnStop, btnPlayPause: $btnPlayPause, btnNext: $btnNext, btnAudio: $btnAudio, btnSubtitle: $btnSubtitle, btnAspect: $btnAspect")
        
        // Configure PlayerView to use custom controller and show it
        playerView?.apply {
            setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == android.view.View.VISIBLE) {
                    // Al mostrar controles, enfocar play/pause para poder navegar con el D-pad
                    btnPlayPause.post {
                        if (btnPlayPause.visibility == android.view.View.VISIBLE) {
                            btnPlayPause.requestFocus()
                        }
                    }
                }
            })
            controllerShowTimeoutMs = 5000 // Show controller for 5 seconds
            controllerHideOnTouch = false // Don't hide on touch for TV
            useController = true // Enable controller
            // Configurar el SubtitleView interno para subtítulos embebidos
            subtitleView?.apply {
                setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_CANVAS)
                setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28f) // Aumentar tamaño
                setFractionalTextSize(0.0533f) // 5.33% de la altura de la pantalla
                val style = androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.parseColor("#CC000000"), // Fondo negro más opaco (75%)
                    android.graphics.Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null
                )
                setStyle(style)
                setPadding(16, 16, 16, 16)
                visibility = android.view.View.GONE
                Log.d("PlayerActivity", "Internal SubtitleView configured and hidden, using customSubtitleView")
            }
        }
        
        customSubtitleView = findViewById(R.id.custom_subtitle_view)
        customSubtitleView?.apply {
            bringToFront()
            setViewType(androidx.media3.ui.SubtitleView.VIEW_TYPE_CANVAS)
            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24f)
            val style = androidx.media3.ui.CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.parseColor("#99000000"), // Fondo negro translúcido (60% opacidad)
                android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.TRANSPARENT,
                null
            )
            setStyle(style)
            // Esconder customSubtitleView por defecto - usar interno para subtítulos embebidos
            visibility = android.view.View.GONE
        }
        
        // Hide controller initially
        playerView?.hideController()
        
        // Audio track button
        btnAudio.setOnClickListener {
            Log.d(tag, "btnAudio clicked")
            try {
                showAudioTrackSelection()
            } catch (e: Exception) {
                val stackTrace = Log.getStackTraceString(e)
                Log.e(tag, "Error showing audio dialog: ${e.message}\n$stackTrace")
                saveErrorToFile("AudioDialogError", e.message, stackTrace)
                Toast.makeText(this, "Error audio: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // Quality button
        btnQuality.setOnClickListener {
            Log.d(tag, "btnQuality clicked")
            try {
                showQualitySelection()
            } catch (e: Exception) {
                val stackTrace = Log.getStackTraceString(e)
                Log.e(tag, "Error showing quality dialog: ${e.message}\n$stackTrace")
                saveErrorToFile("QualityDialogError", e.message, stackTrace)
                Toast.makeText(this, "Error calidad: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // Subtitle button
        btnSubtitle.setOnClickListener {
            Log.d(tag, "btnSubtitle clicked")
            try {
                showSubtitleSelection()
            } catch (e: Exception) {
                val stackTrace = Log.getStackTraceString(e)
                Log.e(tag, "Error showing subtitle dialog: ${e.message}\n$stackTrace")
                saveErrorToFile("SubtitleDialogError", e.message, stackTrace)
                Toast.makeText(this, "Error subtítulos: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        // Aspect ratio button
        btnAspect.setOnClickListener {
            currentResizeMode = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            playerView?.resizeMode = currentResizeMode
            val modeName = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                else -> "Fit"
            }
            Toast.makeText(this, "Aspect: $modeName", Toast.LENGTH_SHORT).show()
        }


        // Stop button
        btnStop.setOnClickListener {
            Log.d(tag, "btnStop clicked")
            try {
                exoPlayer?.let { player ->
                    player.pause()
                    player.seekTo(0)
                }
                playerView?.showController()
                Toast.makeText(this, "Video reiniciado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(tag, "Error resetting player position: ${e.message}", e)
            }
        }
        
        // Play/Pause button
        btnPlayPause.setOnClickListener {
            exoPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    btnPlayPause.setImageResource(R.drawable.play)
                } else {
                    it.play()
                    btnPlayPause.setImageResource(R.drawable.pause)
                }
            }
        }

        // Rewind / Fast-forward (películas)
        val exoRewBtn = playerView?.findViewById<View>(R.id.exo_rew) ?: findViewById(R.id.exo_rew)
        val exoFfwdBtn = playerView?.findViewById<View>(R.id.exo_ffwd) ?: findViewById(R.id.exo_ffwd)
        exoRewBtn?.setOnClickListener {
            val seekAmount = calculateSeekAmount()
            exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) - seekAmount)
            playerView?.showController()
        }
        exoFfwdBtn?.setOnClickListener {
            val seekAmount = calculateSeekAmount()
            exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) + seekAmount)
            playerView?.showController()
        }
        
        // Setup player view key handling
        playerView?.setOnKeyListener { _, keyCode, event ->
            Log.d(tag, "PlayerView onKey: keyCode=$keyCode, action=${event.action}")
            handlePlayerKeyEvent(keyCode, event)
        }
        
        // Ensure PlayerView can receive focus for TV remote
        playerView?.isFocusable = true
        playerView?.isFocusableInTouchMode = true
        playerView?.requestFocus()
        Log.d(tag, "PlayerView focus requested")

        setupControllerFocusNavigation()
        
        // Aplicar modo Live Channel si corresponde
        setupLiveChannelMode()
    }
    
    private fun setupLiveChannelMode() {
        if (isLiveChannel) {
            // Eliminar barra de progreso completamente del layout
            val progressRow = playerView?.findViewById<View>(R.id.exo_progress)?.parent as? ViewGroup
            progressRow?.let { (it.parent as? ViewGroup)?.removeView(it) }
            
            val ivLogo = playerView?.findViewById<ImageView>(R.id.iv_logo)
            val titleContainer = ivLogo?.parent as? ViewGroup
            val leftContainer = playerView?.findViewById<View>(R.id.btn_prev)?.parent as? ViewGroup
            
            // Mover el logo al contenedor izquierdo primero
            if (ivLogo != null && titleContainer != null && leftContainer != null) {
                titleContainer.removeView(ivLogo)
                leftContainer.addView(ivLogo)
                
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (40 * resources.displayMetrics.density).toInt()
                )
                params.marginStart = (8 * resources.displayMetrics.density).toInt()
                params.marginEnd = (8 * resources.displayMetrics.density).toInt()
                ivLogo.layoutParams = params
                ivLogo.visibility = View.VISIBLE
            }
            
            // Eliminar los demás botones del layout para evitar que ExoPlayer fuerce su visibilidad
            val viewsToRemove = listOf(
                R.id.btn_prev, R.id.btn_next, R.id.btn_stop, R.id.btn_play_pause,
                R.id.exo_ffwd, R.id.exo_rew, R.id.btn_audio, R.id.btn_subtitle, R.id.btn_quality
            )
            
            for (id in viewsToRemove) {
                val view = playerView?.findViewById<View>(id)
                view?.let { (it.parent as? ViewGroup)?.removeView(it) }
            }
        }
    }
    
    private fun showErrorOnScreen(error: String) {
        tvErrorLog.text = "ERROR:\n$error"
        tvErrorLog.visibility = android.view.View.VISIBLE
        Log.e(tag, "ERROR EN PANTALLA: $error")
        // También guardar en archivo
        saveErrorToFile("RuntimeError", error, Thread.currentThread().stackTrace.toString())
    }
    
    private fun calculateSeekAmount(): Long = 10000L // 10 seconds constant

    private fun isFocusOnControllerButton(): Boolean {
        val focusedId = currentFocus?.id ?: return false
        return focusedId == R.id.btn_prev ||
            focusedId == R.id.btn_next ||
            focusedId == R.id.btn_stop ||
            focusedId == R.id.btn_play_pause ||
            focusedId == R.id.exo_rew ||
            focusedId == R.id.exo_ffwd ||
            focusedId == R.id.btn_audio ||
            focusedId == R.id.btn_subtitle ||
            focusedId == R.id.btn_quality ||
            focusedId == R.id.btn_aspect ||
            focusedId == R.id.exo_progress
    }

    /**
     * Cablea nextFocus entre botones visibles.
     * En películas se usan rew/ffwd; en series prev/next.
     */
    private fun setupControllerFocusNavigation() {
        val exoRew = playerView?.findViewById<View>(R.id.exo_rew) ?: findViewById(R.id.exo_rew)
        val exoFfwd = playerView?.findViewById<View>(R.id.exo_ffwd) ?: findViewById(R.id.exo_ffwd)
        val exoProgress = playerView?.findViewById<View>(R.id.exo_progress) ?: findViewById(R.id.exo_progress)

        fun View?.isNavVisible(): Boolean =
            this != null && visibility == View.VISIBLE && isFocusable && isEnabled

        val fullChain = listOfNotNull(
            btnAudio, btnSubtitle, btnQuality,
            btnPrev, exoRew, btnStop, btnPlayPause, exoFfwd, btnNext,
            btnAspect
        ).filter { it.isNavVisible() }

        if (fullChain.isEmpty()) return

        for (i in fullChain.indices) {
            val current = fullChain[i]
            val prev = fullChain.getOrElse(i - 1) { fullChain.first() }
            val next = fullChain.getOrElse(i + 1) { fullChain.last() }
            current.nextFocusLeftId = prev.id
            current.nextFocusRightId = next.id
            if (exoProgress != null) {
                current.nextFocusUpId = exoProgress.id
            }
        }

        val downTarget = btnPlayPause.takeIf { it.isNavVisible() }
            ?: fullChain.firstOrNull { it.id == R.id.btn_stop || it.id == R.id.exo_rew || it.id == R.id.btn_prev }
            ?: fullChain.firstOrNull()
        if (exoProgress != null && downTarget != null) {
            exoProgress.nextFocusDownId = downTarget.id
        }

        Log.d(tag, "setupControllerFocusNavigation: chain=${fullChain.map { resources.getResourceEntryName(it.id) }}")
    }
    
    private fun handlePlayerKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        
        Log.d(tag, "handlePlayerKeyEvent: keyCode=$keyCode focus=${currentFocus?.let { resources.getResourceEntryName(it.id) }}")
        
        // Show controller on any key press
        playerView?.showController()

        // Si el foco está en un botón/barra, dejar que Android maneje la navegación
        if (isFocusOnControllerButton()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> return false
            }
        }
        
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isManualZoomMode) {
                    val seekAmount = calculateSeekAmount()
                    exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) - seekAmount)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isManualZoomMode) {
                    val seekAmount = calculateSeekAmount()
                    exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) + seekAmount)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isManualZoomMode) {
                    adjustZoom(0f, 0.01f)
                    return true
                } else {
                    // Hide controller when pressing UP from player surface
                    playerView?.hideController()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isManualZoomMode) {
                    adjustZoom(0f, -0.01f)
                } else {
                    // Toggle controller visibility when pressing DOWN
                    playerView?.let { view ->
                        view.showController()
                        btnPlayPause.post { btnPlayPause.requestFocus() }
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (isManualZoomMode) {
                    exitManualZoomMode()
                    return true
                } else {
                    finish()
                    return true
                }
            }
            KeyEvent.KEYCODE_MENU -> {
                toggleManualZoomMode()
                return true
            }
        }
        return false
    }
    
    private fun initializePlayer(content: Content, episode: Episode?) {
        // Reset manual quality overrides when initializing a new video/episode
        explicitVideoWidth = -1
        explicitVideoHeight = -1
        explicitVideoBitrate = -1

        // Get version parameter from intent
        val version = intent.getStringExtra(EXTRA_VERSION)
        Log.d(tag, "Version parameter: $version")

        preferredPlayer = com.anfhly.streaming.utils.UserPreferences.getPreferredPlayer(this)
        Log.d(tag, "Preferred player: $preferredPlayer")

        val movie = content as? Content.Movie

        // Select appropriate URL based on version for movies
        val videoUrl = when {
            episode != null -> {
                // For episodes, use normal URL (version doesn't apply)
                episode.videoUrl
            }
            movie != null && version == "imax" -> {
                Log.d(tag, "Using IMAX version")
                movie.fileUrlImax ?: movie.videoUrl
            }
            movie != null && version == "openmatte" -> {
                Log.d(tag, "Using Open Matte version")
                movie.fileUrlOpenmatte ?: movie.videoUrl
            }
            movie != null && version == "43" -> {
                Log.d(tag, "Using 4:3 version")
                movie.fileUrl43 ?: movie.videoUrl
            }
            else -> {
                // Default/normal version
                movie?.videoUrl
            }
        }

        var filemoonUrl = episode?.filemoonUrl ?: (content as? Content.Movie)?.filemoonUrl
        val easyvidplayerUrl = episode?.easyvidplayerUrl ?: (content as? Content.Movie)?.easyvidplayerUrl
        val luluvdoUrl = episode?.luluvdoUrl ?: (content as? Content.Movie)?.luluvdoUrl

        // TEST: Detectar y forzar uso de Filemoon para dominios conocidos - DESACTIVADO
        /*
        if (!videoUrl.isNullOrEmpty() && (
            videoUrl.contains("bysesukior.com") ||
            videoUrl.contains("bysejikuar.com") ||
            videoUrl.contains("bysezoxexe.com") ||
            videoUrl.contains("bysebuho.com") ||
            videoUrl.contains("filemoon.sx") ||
            videoUrl.contains("bysekoze.com") ||
            videoUrl.contains("bysesayeveum.com") ||
            videoUrl.contains("bf0skv.org") ||
            videoUrl.contains("moflix-stream.link")
        )) {
            filemoonUrl = videoUrl
            Log.d(tag, "TEST: Detected Filemoon domain in videoUrl, setting filemoonUrl: $filemoonUrl")
        }
        */

        Log.d(tag, "Initializing player - videoUrl: $videoUrl, filemoonUrl: $filemoonUrl, easyvidplayerUrl: $easyvidplayerUrl, luluvdoUrl: $luluvdoUrl")

        var finalVideoUrl = videoUrl

        // Si videoUrl está vacío o contiene easyvidplayer, intentar con filemoonUrl - DESACTIVADO
        // TEST: Siempre usar filemoonUrl si está disponible para pruebas - DESACTIVADO
        /*
        if (!filemoonUrl.isNullOrEmpty()) {
            Log.d(tag, "Using filemoonUrl instead (TEST)")
            finalVideoUrl = filemoonUrl
        } else 
        */
        if (finalVideoUrl.isNullOrEmpty() || finalVideoUrl.contains("easyvidplayer", ignoreCase = true)) {
            // Ya no usar filemoonUrl como fallback
            Log.d(tag, "Filemoon desactivado, buscando otras alternativas")
        }

        // Si videoUrl está vacío o contiene otros extractores, intentar con luluvdoUrl
        if (finalVideoUrl.isNullOrEmpty() || finalVideoUrl.contains("easyvidplayer", ignoreCase = true) || finalVideoUrl.contains("filemoon", ignoreCase = true)) {
            if (!luluvdoUrl.isNullOrEmpty()) {
                Log.d(tag, "Using luluvdoUrl instead")
                finalVideoUrl = luluvdoUrl
            }
        }

        // Si aún no hay URL, intentar con easyvidplayerUrl (último recurso)
        if (finalVideoUrl.isNullOrEmpty()) {
            finalVideoUrl = easyvidplayerUrl
        }

        if (finalVideoUrl.isNullOrEmpty()) {
            Log.e(tag, "No video URL available")
            Toast.makeText(this, "No video URL available", Toast.LENGTH_LONG).show()
            // finish() // ELIMINADO: No cerrar el reproductor
            return
        }

        Log.d(tag, "Final video URL to play: $finalVideoUrl")
        
        // Guardar referencias para navegación entre episodios
        currentEpisode = episode
        currentSeries = content as? Content.Series
        // Buscar la temporada que contiene este episodio
        currentSeason = currentSeries?.seasons?.find { it.seasonNumber == episode?.seasonNumber }

        // Set titles
        if (content is Content.Movie && !content.logoUrl.isNullOrEmpty()) {
            tvTitle.visibility = View.GONE
            ivLogo.visibility = View.VISIBLE
            // Use Coil to load the image
            coil.ImageLoader(this).enqueue(
                coil.request.ImageRequest.Builder(this)
                    .data(content.logoUrl)
                    .target(ivLogo)
                    .build()
            )
        } else {
            tvTitle.visibility = View.VISIBLE
            ivLogo.visibility = View.GONE
            if (content is Content.Series && episode != null) {
                tvTitle.text = episode.name
            } else {
                tvTitle.text = content.title
            }
        }

        tvSubtitle.text = episode?.let {
            "T${it.seasonNumber}:E${it.episodeNumber} - ${content.title}"
        } ?: (content as? Content.Movie)?.overview ?: ""

        // Configurar botones de navegación
        setupEpisodeNavigation()
        
        // Check if this is an embed URL - FILEMOON DESACTIVADO
        val isEmbedUrl = Extractor.hasExtractor(finalVideoUrl) ||
                         /*finalVideoUrl.contains("filemoon") ||
                         finalVideoUrl.contains("filemoon.sx") ||
                         finalVideoUrl.contains("bysesukior") ||
                         finalVideoUrl.contains("bysejikuar") ||
                         finalVideoUrl.contains("bysezoxexe") ||
                         finalVideoUrl.contains("byseqekaho") ||
                         finalVideoUrl.contains("bysebuho") ||
                         finalVideoUrl.contains("bysekoze") ||
                         finalVideoUrl.contains("bysesayeveum") ||
                         finalVideoUrl.contains("bf0skv") ||
                         finalVideoUrl.contains("moflix-stream") || */
                         finalVideoUrl.contains("ok.ru") ||
                         finalVideoUrl.contains("streamtape") ||
                         finalVideoUrl.contains("vidoza") ||
                         finalVideoUrl.contains("supervideo") ||
                         finalVideoUrl.contains("dood") ||
                         finalVideoUrl.contains("easyvidplay") ||
                         finalVideoUrl.contains("vidhide") ||
                         finalVideoUrl.contains("morencius") ||
                         finalVideoUrl.contains("filelions") ||
                         finalVideoUrl.contains("vidply") ||
                         finalVideoUrl.contains("vidmoly") ||
                         finalVideoUrl.contains("vidmoly.me") ||
                         finalVideoUrl.contains("veev") ||
                         finalVideoUrl.contains("ridoo") ||
                         finalVideoUrl.contains("streamhub") ||
                         finalVideoUrl.contains("vidplay") ||
                         finalVideoUrl.contains("mycloud") ||
                         finalVideoUrl.contains("moviesapi") ||
                         finalVideoUrl.contains("luluvdo") ||
                         finalVideoUrl.contains("dropload") ||
                         finalVideoUrl.contains("yourupload") ||
                         finalVideoUrl.contains("bigwarp") ||
                         finalVideoUrl.contains("savefiles") ||
                         finalVideoUrl.contains("pluspomla") ||
                         finalVideoUrl.contains("streamup") ||
                         finalVideoUrl.contains("vidsonic") ||
                         finalVideoUrl.contains("vidara") ||
                         finalVideoUrl.contains("vidzee") ||
                         finalVideoUrl.contains("mail.ru") ||
                         finalVideoUrl.contains("mixdrop") ||
                         // Missing extractors
                         finalVideoUrl.contains("vidguard") ||
                         finalVideoUrl.contains("vembed.net") ||
                         finalVideoUrl.contains("bembed.cc") ||
                         finalVideoUrl.contains("vgfplay.com") ||
                         finalVideoUrl.contains("listeamed.net") ||
                         finalVideoUrl.contains("vidmoly.net") ||
                         finalVideoUrl.contains("vidmoly.to") ||
                         finalVideoUrl.contains("vidmoly.biz") ||
                         finalVideoUrl.contains("vidara.so") ||
                         finalVideoUrl.contains("primesrc.me") ||
                         finalVideoUrl.contains("upzur.com") ||
                         finalVideoUrl.contains("ups2up.fun") ||
                         finalVideoUrl.contains("up4stream.com") ||
                         finalVideoUrl.contains("up4fun.top") ||
                         finalVideoUrl.contains("uqload.cx") ||
                         finalVideoUrl.contains("uqload.is") ||
                         finalVideoUrl.contains("strmup.to") ||
                         finalVideoUrl.contains("streamix.so") ||
                         finalVideoUrl.contains("stmix.io") ||
                         finalVideoUrl.contains("sharecloudy.com") ||
                         finalVideoUrl.contains("onregardeou.site") ||
                         finalVideoUrl.contains("oneupload.net") ||
                         finalVideoUrl.contains("tipfly.xyz") ||
                         finalVideoUrl.contains("video.sibnet.ru") ||
                         finalVideoUrl.contains("pcloud.link") ||
                         finalVideoUrl.contains("apu.animemovil2.com") ||
                         finalVideoUrl.contains("moflix-stream.xyz") ||
                         finalVideoUrl.contains("moviesapi.club") ||
                         finalVideoUrl.contains("vidrock.net") ||
                         finalVideoUrl.contains("vidora.stream") ||
                         finalVideoUrl.contains("videzz.net") ||
                         finalVideoUrl.contains("kinoger.pw") ||
                         finalVideoUrl.contains("poophq.com") ||
                         finalVideoUrl.contains("doods.to") ||
                         finalVideoUrl.contains("streamta.site") ||
                         finalVideoUrl.contains("stape.fun") ||
                         finalVideoUrl.contains("scloud.online") ||
                         finalVideoUrl.contains("streamtape.net") ||
                         finalVideoUrl.contains("shavetape.cash") ||
                         finalVideoUrl.contains("nupload.me") ||
                         finalVideoUrl.contains("streamhls.to") ||
                         finalVideoUrl.contains("stmruby.com") ||
                         finalVideoUrl.contains("rubystm.com") ||
                         finalVideoUrl.contains("rubyvid.com") ||
                         finalVideoUrl.contains("yucache.net") ||
                         finalVideoUrl.contains("player.vidzee.wtf") ||
                         finalVideoUrl.contains("core.vidzee.wtf") ||
                         (finalVideoUrl.contains("/e/") && !finalVideoUrl.endsWith(".mp4") && !finalVideoUrl.endsWith(".m3u8"))
        
        Log.d(tag, "Initializing player with URL: $finalVideoUrl")
        Log.d(tag, "Is embed URL: $isEmbedUrl")
        Log.d(tag, "URL domain: ${finalVideoUrl.substringBefore("?").substringAfter("//").substringBefore("/")}")
        Log.d(tag, "URL contains vidmoly: ${finalVideoUrl.contains("vidmoly")}")
        Log.d(tag, "URL contains filemoon: ${finalVideoUrl.contains("filemoon")}")
        Log.d(tag, "URL contains luluvdo: ${finalVideoUrl.contains("luluvdo")}")
        Log.d(tag, "URL contains dood: ${finalVideoUrl.contains("dood")}")
        Log.d(tag, "URL contains vidplay: ${finalVideoUrl.contains("vidplay")}")
        
        lifecycleScope.launch {
            try {
                val finalUrl: String
                val headers: Map<String, String>
                
                if (isEmbedUrl) {
                    Log.d(tag, "Extracting video from embed URL: $finalVideoUrl")
                    Log.d(tag, "URL contains vidmoly: ${finalVideoUrl.contains("vidmoly")}")
                    Log.d(tag, "URL contains easyvidplay: ${finalVideoUrl.contains("easyvidplayer")}")
                    Log.d(tag, "Starting extraction process...")
                    Log.d(tag, "URL passed to extractor: $finalVideoUrl")
                    val video = Extractor.extract(finalVideoUrl)
                    var selectedUrl = video.source
                    headers = video.headers ?: emptyMap()
                    Log.d(tag, "EXTRACTOR HEADERS received: ${video.headers}")

                    // Guardar tipo MIME del video para mejor detección de formato
                    currentVideoMimeType = video.type
                    Log.d(tag, "Video MIME type: ${video.type}")

                    // Guardar calidades manuales si están disponibles
                    if (video.qualities.isNotEmpty()) {
                        manualQualities = video.qualities
                        currentHeaders = headers
                        isManualQuality = true
                        Log.d(tag, "Manual qualities received: ${video.qualities.size}")
                        Log.d(tag, "Quality labels: ${video.qualities.map { it.label }}")
                        Log.d(tag, "Quality URLs: ${video.qualities.map { it.url.take(50) + "..." }}")
                        
                        // APLICAR CALIDAD PREFERIDA DEL USUARIO
                        Log.d(tag, "PREFERRED QUALITY CHECK: preferredQuality=$preferredQuality, manualQualities.size=${manualQualities.size}")
                        Log.d(tag, "Available qualities: ${manualQualities.map { it.label }}")
                        
                        if (preferredQuality != "auto" && manualQualities.size > 1) {
                            val targetQuality = preferredQuality
                            Log.d(tag, "Looking for preferred quality: $targetQuality")
                            
                            // Búsqueda en cascada: intentar calidad objetivo y si no existe, la más alta siguiente
                            val candidateTokens = when (targetQuality) {
                                "4k", "2160p" -> listOf(listOf("4k", "2160", "uhd"), listOf("2k", "1440", "qhd"), listOf("1080", "fhd", "full"), listOf("720", "hd"))
                                "2k", "1440p" -> listOf(listOf("2k", "1440", "qhd"), listOf("1080", "fhd", "full"), listOf("720", "hd"))
                                "1080p"       -> listOf(listOf("1080", "fhd", "full"), listOf("720", "hd"))
                                "720p"        -> listOf(listOf("720", "hd"), listOf("480", "sd"))
                                else          -> listOf(listOf(targetQuality))
                            }
                            
                            var matchingQuality: com.anfhly.streaming.data.model.Video.Quality? = null
                            for (groupTokens in candidateTokens) {
                                val match = manualQualities.find { q ->
                                    val l = q.label.lowercase()
                                    groupTokens.any { token -> l.contains(token) }
                                }
                                if (match != null) {
                                    matchingQuality = match
                                    break
                                }
                            }
                            if (matchingQuality == null) {
                                matchingQuality = manualQualities.firstOrNull { it.selected } ?: manualQualities.firstOrNull()
                            }
                            
                            if (matchingQuality != null) {
                                selectedUrl = matchingQuality.url
                                // Marcar como seleccionada
                                manualQualities = manualQualities.map { 
                                    it.copy(selected = it.url == selectedUrl) 
                                }
                                Log.d(tag, "SUCCESS: Applied preferred/best quality URL: ${selectedUrl.take(50)}... (label: ${matchingQuality.label})")
                            } else {
                                Log.d(tag, "FAILED: Preferred quality $targetQuality not found in available qualities, using default")
                            }
                        } else {
                            Log.d(tag, "SKIPPED: preferredQuality=$preferredQuality (auto=${preferredQuality == "auto"}), size=${manualQualities.size}")
                        }
                    }

                    // Guardar subtítulos manuales si están disponibles
                    if (video.subtitles.isNotEmpty()) {
                        manualSubtitles = video.subtitles
                        Log.d(tag, "Manual subtitles available: ${video.subtitles.size} - ${video.subtitles.map { it.label }}")
                        autoSelectDefaultSubtitle()
                    }
                    
                    // Guardar pistas de audio manuales si están disponibles (Vidara)
                    if (video.audios.isNotEmpty()) {
                        manualAudios = video.audios
                        Log.d(tag, "Manual audios available: ${video.audios.size} - ${video.audios.map { it.label }}")
                    }
                    
                    finalUrl = selectedUrl
                    Log.d(tag, "Final selected URL: ${finalUrl.take(100)}...")
                } else {
                    finalUrl = finalVideoUrl
                    headers = emptyMap()
                    isManualQuality = false
                    manualQualities = emptyList()
                    manualSubtitles = emptyList()
                    activeManualSubtitle = null
                }
                
                proceedToPlay(finalUrl, headers)
                
            } catch (e: Exception) {
                Log.e(tag, "Error extracting video: ${e.message}", e)
                // Si falla la extracción y hay filemoonUrl disponible, intentar con ese
                if (!filemoonUrl.isNullOrEmpty() && finalVideoUrl != filemoonUrl) {
                    Log.d(tag, "Trying fallback to filemoonUrl: $filemoonUrl")
                    try {
                        val video = Extractor.extract(filemoonUrl)
                        val fallbackUrl = video.source
                        val fallbackHeaders = video.headers ?: emptyMap()
                        proceedToPlay(fallbackUrl, fallbackHeaders)
                        return@launch
                    } catch (e2: Exception) {
                        Log.e(tag, "Filemoon fallback also failed: ${e2.message}", e2)
                    }
                }
                
                // Si filemoonUrl también falla, intentar con luluvdoUrl
                if (!luluvdoUrl.isNullOrEmpty() && finalVideoUrl != luluvdoUrl) {
                    Log.d(tag, "Trying fallback to luluvdoUrl: $luluvdoUrl")
                    try {
                        val video = Extractor.extract(luluvdoUrl)
                        val fallbackUrl = video.source
                        val fallbackHeaders = video.headers ?: emptyMap()
                        proceedToPlay(fallbackUrl, fallbackHeaders)
                        return@launch
                    } catch (e3: Exception) {
                        Log.e(tag, "LuluVdo fallback also failed: ${e3.message}", e3)
                    }
                }
                Toast.makeText(this@PlayerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                // finish() // ELIMINADO: No cerrar el reproductor en errores de extracción
            }
        }
    }
    
    private fun wrapWithManualSubtitle(videoSource: androidx.media3.exoplayer.source.MediaSource, dataSourceFactory: androidx.media3.datasource.DataSource.Factory): androidx.media3.exoplayer.source.MediaSource {
        if (activeManualSubtitle != null) {
            try {
                val subtitle = activeManualSubtitle!!
                val subtitleUrl = subtitle.file
                val mimeType = when {
                    subtitleUrl.contains(".vtt", ignoreCase = true) || subtitleUrl.contains("ok.ru") || subtitleUrl.contains("okcdn.ru") -> androidx.media3.common.MimeTypes.TEXT_VTT
                    subtitleUrl.contains(".srt", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                    subtitleUrl.contains(".ass", ignoreCase = true) || subtitleUrl.contains(".ssa", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_SSA
                    subtitleUrl.contains(".ttml", ignoreCase = true) || subtitleUrl.contains(".xml", ignoreCase = true) || subtitleUrl.contains(".dfxp", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_TTML
                    else -> androidx.media3.common.MimeTypes.TEXT_VTT
                }
                
                // Extract language code from label or use default
                val languageCode = extractLanguageCode(subtitle.label)
                
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                    .setMimeType(mimeType)
                    .setLanguage(languageCode)
                    .setLabel(subtitle.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setId(subtitle.label)
                    .setRoleFlags(C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)
                    .build()
                
                val subtitleSource: androidx.media3.exoplayer.source.MediaSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(subtitleConfig, C.TIME_UNSET)
                
                Log.d(tag, "Merging video source with manual subtitle: ${subtitle.label}, language: $languageCode, URL: ${subtitleUrl.take(100)}")
                Log.d(tag, "Subtitle MIME type: $mimeType")
                
                // Enable text tracks in the player BEFORE setting media source
                exoPlayer?.let { player ->
                    val currentParams = player.trackSelectionParameters
                    val newParams = currentParams.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(languageCode)
                        .build()
                    player.trackSelectionParameters = newParams
                    Log.d(tag, "Text tracks enabled, preferred language: $languageCode")
                }
                
                return MergingMediaSource(videoSource, subtitleSource)
            } catch (e: Exception) {
                Log.e(tag, "Failed to create merged media source with subtitle: ${e.message}", e)
            }
        }
        return videoSource
    }
    
    private fun extractLanguageCode(label: String): String {
        val lowerLabel = label.lowercase()
        return when {
            lowerLabel.contains("español") || lowerLabel.contains("spanish") || lowerLabel.contains("es") -> "es"
            lowerLabel.contains("english") || lowerLabel.contains("en") -> "en"
            lowerLabel.contains("francés") || lowerLabel.contains("french") || lowerLabel.contains("fr") -> "fr"
            lowerLabel.contains("alemán") || lowerLabel.contains("german") || lowerLabel.contains("de") -> "de"
            lowerLabel.contains("italiano") || lowerLabel.contains("italian") || lowerLabel.contains("it") -> "it"
            lowerLabel.contains("portugués") || lowerLabel.contains("portuguese") || lowerLabel.contains("pt") -> "pt"
            lowerLabel.contains("ruso") || lowerLabel.contains("russian") || lowerLabel.contains("ru") -> "ru"
            else -> "und" // Undetermined
        }
    }

    /**
     * Configuración de AudioAttributes para cine, sonido envolvente (Dolby Digital / Atmos) y audio espacial.
     */
    private fun getDolbyAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    /**
     * Crea un DefaultRenderersFactory con AudioSink optimizado para Dolby Digital (AC-3),
     * Dolby Digital Plus (E-AC-3), Dolby Atmos (E-AC-3 JOC) y bitstream passthrough por HDMI/eARC.
     */
    private fun createDolbyRenderersFactory(codecSelector: MediaCodecSelector? = null): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(this@PlayerActivity) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableAudioTrackPlaybackParams(true)
            setEnableDecoderFallback(true)
            if (codecSelector != null) {
                setMediaCodecSelector(codecSelector)
            }
        }
    }

    private fun initializePlayerInstance() {
        if (exoPlayer != null) return
        
        Log.d(tag, "initializePlayerInstance: Creating ExoPlayer synchronously with Dolby Atmos & Digital support")
        
        // Configurar renderers con soporte Dolby y extensiones de software y fallback
        val codecSelector = createCodecSelectorWithTranslation()
        val renderersFactory = createDolbyRenderersFactory(codecSelector)
            
        // LoadControl adaptativo según RAM libre real del dispositivo en este momento
        val loadControl = deviceCapabilities.createOptimizedLoadControl()
        val playbackTier = deviceCapabilities.getPlaybackTier()
        val adaptiveMaxSize = deviceCapabilities.getAdaptiveMaxVideoSize()
        val useAudioOffload = deviceCapabilities.shouldEnableAudioOffload()
        Log.d(tag, "initializePlayerInstance: tier=$playbackTier, adaptiveMaxSize=$adaptiveMaxSize, audioOffload=$useAudioOffload")
            
        // Configurar TrackSelector inicial con soporte para audio multicanal y espacial
        val parametersBuilder = androidx.media3.exoplayer.trackselection.DefaultTrackSelector.ParametersBuilder(this)
            .setPreferredAudioLanguage(preferredAudioLanguage)
            .setPreferredTextLanguage(preferredSubtitleLanguage)
            .setAudioOffloadPreferences(
                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        if (useAudioOffload)
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        else
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    )
                    .setIsGaplessSupportRequired(false)
                    .build()
            )
            
        if (explicitVideoWidth > 0 && explicitVideoHeight > 0) {
            parametersBuilder.clearVideoSizeConstraints()
            parametersBuilder.setMaxVideoSize(explicitVideoWidth, explicitVideoHeight)
            Log.d(tag, "initializePlayerInstance: explicit size applied = ${explicitVideoWidth}x${explicitVideoHeight}")
        } else {
            // Aplicar límite de resolución del usuario, pero nunca superar el techo adaptativo del dispositivo
            val userMaxSize = when (preferredQuality) {
                "4k", "2160p" -> Pair(3840, 2160)
                "2k", "1440p" -> Pair(2560, 1440)
                "1080p"       -> Pair(1920, 1080)
                "720p"        -> Pair(1280, 720)
                "480p"        -> Pair(854, 480)
                "360p"        -> Pair(640, 360)
                else          -> null // auto: sin límite por usuario
            }
            // El techo efectivo es el mínimo entre lo que pide el usuario y lo que el hardware puede
            val effectiveMax = when {
                userMaxSize == null && adaptiveMaxSize == null -> null
                userMaxSize == null -> adaptiveMaxSize
                adaptiveMaxSize == null -> userMaxSize
                else -> Pair(
                    minOf(userMaxSize.first, adaptiveMaxSize.first),
                    minOf(userMaxSize.second, adaptiveMaxSize.second)
                )
            }
            if (effectiveMax != null) {
                parametersBuilder.setMaxVideoSize(effectiveMax.first, effectiveMax.second)
                Log.d(tag, "initializePlayerInstance: effectiveMaxSize=${effectiveMax.first}x${effectiveMax.second} (user=${userMaxSize}, adaptive=${adaptiveMaxSize})")
            }
        }
        
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this, parametersBuilder.build())
        
        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(getDolbyAudioAttributes(), /* handleAudioFocus = */ true)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        super.onVideoSizeChanged(videoSize)
                        Log.d(tag, "onVideoSizeChanged: width=${videoSize.width}, height=${videoSize.height}, ratio=${videoSize.pixelWidthHeightRatio}")
                        
                        // FIX CRÍTICO PARA ANDROID TV Y VIDHIDE (Bug de Súper Zoom/Crop):
                        // Cuando forzamos una calidad menor (ej. 480p), ExoPlayer llama a setFixedSize(854, 480).
                        // Pero el decodificador de hardware a menudo sigue escupiendo frames a 1080p.
                        // El SurfaceFlinger recorta el frame 1080p a 854x480 (top-left) y luego lo estira,
                        // causando un zoom masivo. Llamando a setSizeFromLayout() ignoramos el buffer fijo
                        // y obligamos al Surface a usar el tamaño real de la pantalla, evitando el recorte.
                        val surfaceView = playerView?.videoSurfaceView as? android.view.SurfaceView
                        surfaceView?.holder?.setSizeFromLayout()

                        // Forzar relayout para Android TV porque a veces AspectRatioFrameLayout no
                        // actualiza el tamaño real del Surface si el decodificador cambia la resolución
                        // abruptamente respecto al manifiesto HLS.
                        playerView?.post {
                            playerView?.requestLayout()
                            playerView?.invalidate()
                        }
                    }
                    
                    override fun onRenderedFirstFrame() {
                        super.onRenderedFirstFrame()
                        Log.d(tag, "onRenderedFirstFrame")
                    }
                    
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        super.onIsPlayingChanged(isPlaying)
                        if (isPlaying) {
                            startPeriodicProgressSaving()
                        } else {
                            stopPeriodicProgressSaving()
                            saveVideoPosition()
                        }
                    }
                    
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.d(tag, "Playback state: $state")
                        updatePlayPauseButton()
                        
                        if (state == Player.STATE_READY) {
                            checkAndUpdateTrackButtons()
                            
                            Log.d(tag, "Playback state is READY, starting playback")
                            play()
                            playerView?.hideController()
                        }
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(tag, "Player error: ${error.errorCodeName} - ${error.message}")
                        handlePlayerError(error)
                    }
                })
            }
            
        playerView?.player = exoPlayer
        bindCustomSubtitleView(exoPlayer)
        
        currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        playerView?.resizeMode = currentResizeMode
        isManualZoomMode = false
    }

    private var explicitVideoWidth: Int = -1
    private var explicitVideoHeight: Int = -1
    private var explicitVideoBitrate: Int = -1

    private fun updatePlayerTrackSelectorParameters() {
        val player = exoPlayer ?: return
        val trackSelector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector ?: return
        
        val parametersBuilder = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(preferredAudioLanguage)
            .setPreferredTextLanguage(preferredSubtitleLanguage)
            
        if (explicitVideoWidth > 0 && explicitVideoHeight > 0) {
            parametersBuilder.clearVideoSizeConstraints()
            parametersBuilder.setMaxVideoSize(explicitVideoWidth, explicitVideoHeight)
            Log.d(tag, "Updated TrackSelector: explicit size = ${explicitVideoWidth}x${explicitVideoHeight}")
        } else if (preferredQuality != "auto") {
            val userMaxSize = when (preferredQuality) {
                "4k", "2160p" -> Pair(3840, 2160)
                "2k", "1440p" -> Pair(2560, 1440)
                "1080p" -> Pair(1920, 1080)
                "720p" -> Pair(1280, 720)
                "480p" -> Pair(854, 480)
                "360p" -> Pair(640, 360)
                else -> Pair(1920, 1080)
            }
            val adaptiveMaxSize = deviceCapabilities.getAdaptiveMaxVideoSize()
            val effectiveMax = if (adaptiveMaxSize != null) {
                Pair(
                    minOf(userMaxSize.first, adaptiveMaxSize.first),
                    minOf(userMaxSize.second, adaptiveMaxSize.second)
                )
            } else userMaxSize
            parametersBuilder.setMaxVideoSize(effectiveMax.first, effectiveMax.second)
            Log.d(tag, "Updated TrackSelector parameters: effectiveMaxSize = ${effectiveMax.first}x${effectiveMax.second} (user=$userMaxSize, adaptive=$adaptiveMaxSize)")
        }
        trackSelector.setParameters(parametersBuilder)
    }

    private fun launchExternalPlayer(videoUrl: String, title: String?) {
        com.anfhly.streaming.util.ExternalPlayerLauncher.launchByKey(this, preferredPlayer, videoUrl, title, null)
        finish()
    }

    private fun setupExoPlayer(videoUrl: String, headers: Map<String, String>, startPosition: Long = -1L) {
        // Si se prefiere un reproductor externo, lanzarlo y salir
        if (preferredPlayer != "exoplayer") {
            val contentTitle = intent.getSerializableExtra(EXTRA_CONTENT)?.let { content ->
                (content as? Content)?.title
            }
            launchExternalPlayer(videoUrl, contentTitle)
            return
        }

        // Guardar URL actual para resume playback y posición inicial para el listener
        currentVideoUrl = videoUrl
        currentHeaders = headers
        currentStartPosition = startPosition
        
        // Verificar si la URL está por expirar y refrescar si es necesario
        checkAndRefreshUrlIfNeeded()
        
        // Resetear contadores de reintento para nuevo video
        hasTriedAudioCodecFallback = false
        hasTriedSoftwareCodecFallback = false
        retryCount = 0

        // Reset aspect ratio and zoom modes
        currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        playerView?.resizeMode = currentResizeMode
        isManualZoomMode = false

        Log.d(tag, "Setting up ExoPlayer media source with URL: ${videoUrl.take(80)}...")

        // Configurar DataSource con headers personalizados
        val userAgent = headers["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        
        val baseHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setDefaultRequestProperties(headers)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, baseHttpDataSourceFactory)

        // Crear MediaItem con tipo MIME explícito si está disponible
        val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
        currentVideoMimeType?.let { mimeType ->
            if (mimeType.isNotBlank()) {
                mediaItemBuilder.setMimeType(mimeType)
            }
        }
        val mediaItem = mediaItemBuilder.build()

        val isHlsStream = videoUrl.contains(".m3u8", ignoreCase = true) ||
                          videoUrl.contains("hls", ignoreCase = true) ||
                          videoUrl.contains("ondemandhls", ignoreCase = true) ||
                          currentVideoMimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8

        val videoSource = if (isHlsStream) {
            val isVidMoly = videoUrl.contains("vidmoly", ignoreCase = true)
            
            if (isVidMoly) {
                val vidmolyDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers)
                    .setConnectTimeoutMs(45000)
                    .setReadTimeoutMs(120000)
                val vidmolyDataSource = androidx.media3.datasource.DefaultDataSource.Factory(this, vidmolyDataSourceFactory)
                HlsMediaSource.Factory(vidmolyDataSource).setAllowChunklessPreparation(true).createMediaSource(mediaItem)
            } else {
                HlsMediaSource.Factory(dataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(mediaItem)
            }
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        val mediaSource = wrapWithManualSubtitle(videoSource, dataSourceFactory)

        // Asegurar que el reproductor está inicializado (debería haberlo sido en onCreate)
        initializePlayerInstance()

        // Actualizar parámetros específicos para VidMoLy/estándar si es necesario
        val isVidMolyStream = videoUrl.contains("vidmoly")
        val trackSelector = exoPlayer?.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector
        if (trackSelector != null) {
            if (isVidMolyStream) {
                Log.d(tag, "Applying VidMoLy TrackSelector overrides dynamically")
                val parametersBuilder = trackSelector.buildUponParameters()
                    .setPreferredAudioLanguage(preferredAudioLanguage)
                if (explicitVideoWidth <= 1920 && preferredQuality != "4k" && preferredQuality != "2k" && preferredQuality != "2160p" && preferredQuality != "1440p") {
                    parametersBuilder.setViewportSize(1920, 1080, true)
                }
                trackSelector.setParameters(parametersBuilder.build())
            } else {
                Log.d(tag, "Applying dynamic TrackSelector parameters")
                val parametersBuilder = trackSelector.buildUponParameters()
                    .setPreferredAudioLanguage(preferredAudioLanguage)
                    .setPreferredTextLanguage(preferredSubtitleLanguage)
                
                if (isManualQuality) {
                    parametersBuilder.clearVideoSizeConstraints()
                    Log.d(tag, "Cleared track selector video size constraints for manual quality stream")
                } else if (explicitVideoWidth > 0 && explicitVideoHeight > 0) {
                    parametersBuilder.clearVideoSizeConstraints()
                    parametersBuilder.setMaxVideoSize(explicitVideoWidth, explicitVideoHeight)
                    Log.d(tag, "Applied explicit size in setupExoPlayer: ${explicitVideoWidth}x${explicitVideoHeight}")
                } else if (preferredQuality != "auto") {
                    val userMaxSize = when (preferredQuality) {
                        "4k", "2160p" -> Pair(3840, 2160)
                        "2k", "1440p" -> Pair(2560, 1440)
                        "1080p" -> Pair(1920, 1080)
                        "720p" -> Pair(1280, 720)
                        "480p" -> Pair(854, 480)
                        "360p" -> Pair(640, 360)
                        else -> Pair(1920, 1080)
                    }
                    val adaptiveMaxSize = deviceCapabilities.getAdaptiveMaxVideoSize()
                    val effectiveMax = if (adaptiveMaxSize != null) {
                        Pair(
                            minOf(userMaxSize.first, adaptiveMaxSize.first),
                            minOf(userMaxSize.second, adaptiveMaxSize.second)
                        )
                    } else userMaxSize
                    parametersBuilder.setMaxVideoSize(effectiveMax.first, effectiveMax.second)
                    Log.d(tag, "Applied maxVideoSize in setupExoPlayer: ${effectiveMax.first}x${effectiveMax.second} (user=$userMaxSize, adaptive=$adaptiveMaxSize)")
                }
                trackSelector.setParameters(parametersBuilder.build())
            }
        }

        // Cargar mediaSource en el reproductor existente y prepararlo
        exoPlayer?.apply {
            setMediaSource(mediaSource)
            // Si startPosition es mayor que 0, hacer seek antes de preparar
            if (startPosition > 0) {
                seekTo(startPosition)
                Log.d(tag, "Seeked to startPosition: $startPosition before prepare")
            }
            prepare()
        }
    }

    /**
     * Crea un MediaCodecSelector con traducción de codecs para mejorar compatibilidad
     * Traduce codecs problemáticos a alternativas más compatibles
     */
    private fun createCodecSelectorWithTranslation(): MediaCodecSelector {
        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunneling ->
            val defaultSelector = MediaCodecSelector.DEFAULT
            val defaultCodecs = defaultSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunneling)
            
            Log.d(tag, "Available codecs for $mimeType: ${defaultCodecs.size}")
            
            val translatedCodecs = defaultCodecs.map { codecInfo ->
                // Traducir codecs problemáticos a alternativas
                val translatedCodec = translateProblematicCodec(codecInfo, mimeType)
                if (translatedCodec != codecInfo) {
                    Log.d(tag, "Translated codec: ${codecInfo.name} -> ${translatedCodec.name}")
                }
                translatedCodec
            }
            
            // Priorizar decodificadores según el tipo de medio (Hardware prioritario para VIDEO)
            translatedCodecs.sortedWith { codec1, codec2 ->
                when {
                    // Para AUDIO: Si es AAC, preferir el decodificador de software de Google sobre chipsets con bugs conocidos de AAC
                    mimeType.startsWith("audio/") -> {
                        when {
                            codec1.name.contains("OMX.google") && isProblematicAudioCodec(codec2) -> -1
                            codec2.name.contains("OMX.google") && isProblematicAudioCodec(codec1) -> 1
                            isProblematicAudioCodec(codec1) && !isProblematicAudioCodec(codec2) -> 1
                            isProblematicAudioCodec(codec2) && !isProblematicAudioCodec(codec1) -> -1
                            else -> 0
                        }
                    }
                    // Para VIDEO: ¡PRIORIZAR HARDWARE SIEMPRE! (Crucial para 1080p, 2K y 4K)
                    // La decodificación por software en CPU no rinde para 2K/4K y congela el reproductor.
                    mimeType.startsWith("video/") -> {
                        val hw1 = isHardwareCodec(codec1)
                        val hw2 = isHardwareCodec(codec2)
                        when {
                            hw1 && !hw2 -> -1 // Hardware primero siempre
                            !hw1 && hw2 -> 1  // Software al final como último recurso
                            else -> 0
                        }
                    }
                    else -> 0
                }
            }
        }
    }

    /**
     * Traduce codecs problemáticos a alternativas más compatibles
     */
    private fun translateProblematicCodec(codecInfo: MediaCodecInfo, mimeType: String): MediaCodecInfo {
        // Para AAC, preferir software codecs si hay problemas conocidos
        if (mimeType == "audio/mp4a-latm" || mimeType == "audio/aac") {
            if (codecInfo.name.contains("OMX.qcom") || codecInfo.name.contains("c2.qcom")) {
                // Buscar alternativa software para Qualcomm AAC
                return findSoftwareAlternative(codecInfo, mimeType) ?: codecInfo
            }
            if (codecInfo.name.contains("OMX.MTK") || codecInfo.name.contains("c2.mtk")) {
                // Buscar alternativa software para MediaTek AAC
                return findSoftwareAlternative(codecInfo, mimeType) ?: codecInfo
            }
        }
        
        return codecInfo
    }

    /**
     * Busca una alternativa software para el codec dado
     */
    private fun findSoftwareAlternative(originalCodec: MediaCodecInfo, mimeType: String): MediaCodecInfo? {
        val allCodecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, false, false)
        
        return allCodecs.find { codec ->
            codec.name.contains("OMX.google") || // Software codecs de Google
            codec.name.contains("c2.android") || // Software codecs de Android
            (!isHardwareCodec(codec) && codec.name != originalCodec.name) // Otro software codec
        }
    }

    /**
     * Verifica si un codec es de hardware basado en flags del sistema y patrones de nombre
     */
    private fun isHardwareCodec(codecInfo: MediaCodecInfo): Boolean {
        if (codecInfo.hardwareAccelerated) return true
        if (codecInfo.softwareOnly) return false
        val hardwarePatterns = listOf(
            "OMX.qcom", // Qualcomm hardware codecs
            "OMX.MTK", // MediaTek hardware codecs  
            "OMX.NVIDIA", // NVIDIA hardware codecs
            "OMX.TI", // Texas Instruments hardware codecs
            "OMX.SEC", // Samsung hardware codecs
            "c2.qcom", // Qualcomm codecs v2
            "c2.mtk", // MediaTek codecs v2
            "c2.nvidia", // NVIDIA codecs v2
            "c2.sec", // Samsung codecs v2
            "c2.amlogic", // Amlogic codecs Android TV
            "OMX.amlogic",
            "c2.realtek", // Realtek codecs Android TV
            "OMX.realtek",
            "c2.allwinner", // Allwinner codecs Android TV
            "OMX.allwinner",
            "c2.hisi", // HiSilicon Kirin
            "OMX.hisi"
        )
        
        return hardwarePatterns.any { pattern ->
            codecInfo.name.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * Verifica si un codec de AUDIO es conocido por tener problemas (exclusivamente para audio AAC)
     */
    private fun isProblematicAudioCodec(codecInfo: MediaCodecInfo): Boolean {
        val problematicPatterns = listOf(
            "OMX.qcom.audio", // Qualcomm codecs con problemas AAC
            "c2.qcom.audio",
            "OMX.MTK.AUDIO", // MediaTek codecs con problemas AAC  
            "c2.mtk.audio"
        )
        
        return problematicPatterns.any { pattern ->
            codecInfo.name.contains(pattern, ignoreCase = true)
        }
    }

    private var hasTriedAudioCodecFallback = false
    private var hasTriedSoftwareCodecFallback = false
    private var hasTriedVideoCodecFallback = false
    private var currentFallbackUrl: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    private fun handlePlayerError(error: PlaybackException) {
        Log.e(tag, "Error de reproducción: code=${error.errorCode}, msg=${error.message}")
        Log.e(tag, "Error cause: ${error.cause}")
        Log.e(tag, "Retry count: $retryCount/$maxRetries")

        // Detectar error específico de codec de audio AAC (MediaCodecAudioRenderer)
        val isAudioCodecError = error.message?.contains("MediaCodecAudioRenderer") == true ||
            error.message?.contains("audio/mp4a-latm") == true ||
            error.message?.contains("mp4a.40.2") == true ||
            error.message?.contains("AAC") == true ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED

        // Detectar error específico de codec de video (MediaCodecVideoRenderer)
        val isVideoCodecError = error.message?.contains("MediaCodecVideoRenderer") == true ||
            error.message?.contains("NO_EXCEEDS_CAPABILITIES") == true ||
            error.message?.contains("avc1.6E0028") == true ||
            error.message?.contains("10bit") == true ||
            error.message?.contains("video/avc") == true ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
        
        // Logging detallado para diagnóstico
        if (isVideoCodecError) {
            Log.e(tag, "=== VIDEO CODEC ERROR DETECTED ===")
            Log.e(tag, "Error message: ${error.message}")
            Log.e(tag, "Error code: ${error.errorCode}")
            Log.e(tag, "Error code name: ${error.errorCodeName}")
            Log.e(tag, "Current video URL: ${currentVideoUrl}")
            Log.e(tag, "Retry count: $retryCount/$maxRetries")
            Log.e(tag, "Has tried video codec fallback: $hasTriedVideoCodecFallback")
            Log.e(tag, "Has tried software codec fallback: $hasTriedSoftwareCodecFallback")
            Log.e(tag, "=====================================")
        }

        // Si es error de codec de video y no hemos intentado conversión de codecs
        if (isVideoCodecError && !hasTriedVideoCodecFallback) {
            Log.d(tag, "Detected video codec error, attempting codec conversion fallback")
            hasTriedVideoCodecFallback = true
            retryCount++
            
            // Intentar con URL convertida usando FFmpegManager
            retryWithConvertedVideo()
            return
        }

        // Si es error de codec de audio y no hemos intentado software codecs
        if (isAudioCodecError && !hasTriedSoftwareCodecFallback) {
            Log.d(tag, "Detected audio codec error, attempting software codec fallback")
            hasTriedSoftwareCodecFallback = true
            retryCount++
            
            // Reintentar con misma URL pero forzando software codecs
            retryWithSoftwareCodecs()
            return
        }

        // Si ya intentamos software codecs y sigue fallando, continuar con reintentos normales
        if (isAudioCodecError && hasTriedSoftwareCodecFallback && !hasTriedAudioCodecFallback) {
            Log.d(tag, "Software codec fallback failed, marking audio codec fallback as tried")
            hasTriedAudioCodecFallback = true
            // No hacer fallback a otras URLs, continuar con el flujo normal de reintentos
        }

        // Si hemos agotado reintentos pero el error es de codec, intentar una última vez con configuración mínima
        if (isAudioCodecError && retryCount < maxRetries) {
            Log.d(tag, "Attempting retry ${retryCount + 1}/$maxRetries with minimal configuration")
            retryCount++
            retryWithMinimalConfiguration()
            return
        }

        val errorMsg = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                if (isAudioCodecError) "Error de audio: Tu dispositivo no soporta el codec AAC de este video"
                else "Error de codec: Tu dispositivo no soporta este formato de video"
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "Error de conexión con el servidor"
            else -> "Error de reproducción: ${error.message}"
        }

        Toast.makeText(this@PlayerActivity, errorMsg, Toast.LENGTH_LONG).show()
    }

    private fun retryWithSoftwareCodecs() {
        Log.d(tag, "Retrying with software codecs for URL: ${currentVideoUrl.take(50)}...")
        
        lifecycleScope.launch {
            runOnUiThread {
                try {
                    // Liberar player actual
                    exoPlayer?.release()
                    
                    // Crear nueva configuración con software codecs forzados y soporte Dolby
                    val renderersFactory = createDolbyRenderersFactory(null)
                    
                    exoPlayer = ExoPlayer.Builder(this@PlayerActivity)
                        .setRenderersFactory(renderersFactory)
                        .setAudioAttributes(getDolbyAudioAttributes(), /* handleAudioFocus = */ true)
                        .build()
                    
                    // Reconfigurar media source con la misma URL
                    setupMediaSourceForRetry(currentVideoUrl, currentHeaders)
                    
                    Toast.makeText(this@PlayerActivity, "Reintentando con codecs de software...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(tag, "Error retrying with software codecs: ${e.message}")
                    val customError = PlaybackException(e.message ?: "Unknown error", e, PlaybackException.ERROR_CODE_UNSPECIFIED)
                    handlePlayerError(customError)
                }
            }
        }
    }

    private fun retryWithMinimalConfiguration() {
        Log.d(tag, "Retrying with minimal configuration for URL: ${currentVideoUrl.take(50)}...")
        
        lifecycleScope.launch {
            runOnUiThread {
                try {
                    // Liberar player actual
                    exoPlayer?.release()
                    
                    // Configuración mínima - codecs con soporte Dolby
                    val renderersFactory = createDolbyRenderersFactory(null)
                    
                    // LoadControl conservador
                    val loadControl = DefaultLoadControl.Builder()
                        .setBufferDurationsMs(2000, 10000, 1000, 2000)
                        .setTargetBufferBytes(16 * 1024 * 1024)
                        .build()
                    
                    exoPlayer = ExoPlayer.Builder(this@PlayerActivity)
                        .setRenderersFactory(renderersFactory)
                        .setLoadControl(loadControl)
                        .setAudioAttributes(getDolbyAudioAttributes(), /* handleAudioFocus = */ true)
                        .build()
                    
                    // Reconfigurar media source
                    setupMediaSourceForRetry(currentVideoUrl, currentHeaders)
                    
                    Toast.makeText(this@PlayerActivity, "Reintentando con configuración mínima...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(tag, "Error retrying with minimal configuration: ${e.message}")
                    val customError = PlaybackException(e.message ?: "Unknown error", e, PlaybackException.ERROR_CODE_UNSPECIFIED)
                    handlePlayerError(customError)
                }
            }
        }
    }

    private fun setupMediaSourceForRetry(videoUrl: String, headers: Map<String, String>) {
        val userAgent = headers["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        
        val dataSourceFactory = object : androidx.media3.datasource.DataSource.Factory {
            override fun createDataSource(): androidx.media3.datasource.DataSource {
                val httpDataSource = DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(30000)
                    .createDataSource()
                
                headers.forEach { entry ->
                    httpDataSource.setRequestProperty(entry.key, entry.value)
                }
                
                return httpDataSource
            }
        }

        val mediaItem = MediaItem.Builder().setUri(videoUrl).build()
        val mediaSource = if (videoUrl.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        exoPlayer?.apply {
            setMediaSource(mediaSource)
            prepare()
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        Log.d(tag, "Retry successful - playback ready")
                        playerView?.player = exoPlayer
                bindCustomSubtitleView(exoPlayer)
                        play()
                    }
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(tag, "Retry failed: ${error.message}")
                    handlePlayerError(error)
                }
            })
        }
    }

        
    private fun toggleManualZoomMode() {
        if (isManualZoomMode) {
            exitManualZoomMode()
        } else {
            enterManualZoomMode()
        }
    }
    
    private fun enterManualZoomMode() {
        isManualZoomMode = true
        exoPlayer?.pause()
        playerView?.hideController()
        Toast.makeText(this, "Manual Zoom Mode: Use D-Pad to zoom, BACK to exit", Toast.LENGTH_LONG).show()
    }
    
    private fun exitManualZoomMode() {
        isManualZoomMode = false
        playerView?.videoSurfaceView?.apply {
            scaleX = 1f
            scaleY = 1f
        }
        exoPlayer?.play()
    }
    
    private fun adjustZoom(deltaX: Float, deltaY: Float) {
        playerView?.videoSurfaceView?.apply {
            scaleX = (scaleX + deltaX).coerceIn(0.5f, 3f)
            scaleY = (scaleY + deltaY).coerceIn(0.5f, 3f)
            Toast.makeText(this@PlayerActivity, "Zoom: ${String.format("%.2f", scaleX)}x", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updatePlayPauseButton() {
        exoPlayer?.let { player ->
            val isPlaying = player.isPlaying
            btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.pause 
                else R.drawable.play
            )
        }
    }
    
    private fun checkAndUpdateTrackButtons() {
        exoPlayer?.let { player ->
            val trackSelector = player.currentTracks
            
            // Check for audio tracks (embedded in stream or manual from extractor)
            val audioGroups = trackSelector.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            // Contar todos los tracks de audio disponibles (incluso en grupos diferentes)
            val totalAudioTracks = audioGroups.sumOf { it.length }
            val hasEmbeddedAudioTracks = totalAudioTracks > 0
            // También incluir audios manuales del extractor (Vidara, etc.)
            val hasManualAudios = manualAudios.isNotEmpty()
            val hasAudioTracks = hasEmbeddedAudioTracks || hasManualAudios
            btnAudio.visibility = if (hasAudioTracks) android.view.View.VISIBLE else android.view.View.GONE
            
            Log.d(tag, "Audio groups: ${audioGroups.size}, Total embedded audio tracks: $totalAudioTracks, Manual audios: ${manualAudios.size}")
            audioGroups.forEachIndexed { index, group ->
                Log.d(tag, "Audio Group $index: length=${group.length}")
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    Log.d(tag, "  Audio Track $i: language=${format.language}, selected=${group.isTrackSelected(i)}")
                }
            }
            
            // Check for video tracks (ExoPlayer tracks for HLS/DASH) or manual qualities
            val videoGroups = trackSelector.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            // Contar todos los tracks de video disponibles (incluso en grupos diferentes)
            val totalVideoTracks = videoGroups.sumOf { it.length }
            val hasExoPlayerTracks = totalVideoTracks > 1
            val hasManualQualities = isManualQuality && manualQualities.size > 1
            
            Log.d(tag, "Video groups: ${videoGroups.size}, Total video tracks: $totalVideoTracks")
            videoGroups.forEachIndexed { index, group ->
                Log.d(tag, "Group $index: type=${group.type}, length=${group.length}")
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    Log.d(tag, "  Track $i: ${format.width}x${format.height}, bitrate=${format.bitrate}")
                }
            }
            
            // Mostrar botón de calidad si hay más de 1 track de video O calidades manuales
            btnQuality.visibility = if (hasExoPlayerTracks || hasManualQualities) android.view.View.VISIBLE else android.view.View.GONE
            Log.d(tag, "Video tracks available: $hasExoPlayerTracks (total=$totalVideoTracks) / $hasManualQualities (Manual)")
            
            // Check for subtitle tracks
            val subtitleGroups = trackSelector.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            // Contar todos los tracks de subtítulos disponibles (incluso en grupos diferentes)
            val totalSubtitleTracks = subtitleGroups.sumOf { it.length }
            // También incluir subtítulos manuales del extractor (Ok.ru, etc.)
            val hasManualSubtitles = manualSubtitles.isNotEmpty()
            val hasSubtitleTracks = totalSubtitleTracks > 0 || hasManualSubtitles
            btnSubtitle.visibility = if (hasSubtitleTracks) android.view.View.VISIBLE else android.view.View.GONE

            Log.d(tag, "Subtitle groups: ${subtitleGroups.size}, Total subtitle tracks: $totalSubtitleTracks, Manual subtitles: ${manualSubtitles.size}")
            subtitleGroups.forEachIndexed { index, group ->
                Log.d(tag, "Subtitle Group $index: length=${group.length}")
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    Log.d(tag, "  Subtitle Track $i: language=${format.language}, selected=${group.isTrackSelected(i)}")
                }
            }

            // Aplicar preferencias de usuario guardadas en DataStore
            Log.d(tag, "APPLYING TRACK PREFERENCES: audio=$preferredAudioLanguage, subtitle=$preferredSubtitleLanguage, quality=$preferredQuality, subtitlesEnabled=$subtitlesEnabled")
            Log.d(tag, "Skip auto audio selection: $skipAutoAudioSelection")
            var trackParamsBuilder = player.trackSelectionParameters.buildUpon()
            
            // 1. Configurar audio preferido (usar audioGroups ya declarado arriba)
            Log.d(tag, "Audio groups count: ${audioGroups.size}")
            if (audioGroups.isNotEmpty() && !skipAutoAudioSelection) {
                // Buscar track de audio que coincida con el idioma preferido
                var audioOverride: TrackSelectionOverride? = null
                for (group in audioGroups) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val trackLang = format.language?.lowercase() ?: ""
                        val prefLang = preferredAudioLanguage.lowercase()
                        
                        // Comparación flexible: "es" con "spanish", "en" con "english", etc.
                        val isMatch = when {
                            trackLang == prefLang -> true
                            prefLang == "es" && (trackLang.contains("spanish") || trackLang.contains("spa")) -> true
                            prefLang == "en" && (trackLang.contains("english") || trackLang.contains("eng")) -> true
                            trackLang == "spanish" && prefLang.contains("es") -> true
                            trackLang == "english" && prefLang.contains("en") -> true
                            else -> false
                        }
                        
                        Log.d(tag, "Audio track comparison: track='$trackLang' vs preferred='$prefLang' -> match=$isMatch")
                        
                        if (isMatch) {
                            audioOverride = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                            break
                        }
                    }
                    if (audioOverride != null) break
                }
                
                // Si encontramos el audio preferido, aplicarlo
                if (audioOverride != null) {
                    trackParamsBuilder = trackParamsBuilder.setOverrideForType(audioOverride)
                    Log.d(tag, "Applied preferred audio: $preferredAudioLanguage")
                } else {
                    // Si no, dejar el audio por defecto (primero disponible)
                    val firstAudioGroup = audioGroups.first()
                    if (firstAudioGroup.length > 0) {
                        val defaultAudioOverride = TrackSelectionOverride(firstAudioGroup.mediaTrackGroup, listOf(0))
                        trackParamsBuilder = trackParamsBuilder.setOverrideForType(defaultAudioOverride)
                        Log.d(tag, "Preferred audio not found, using default: ${firstAudioGroup.getTrackFormat(0).language}")
                    }
                }
            }
            
            // 2. Configurar subtítulos según preferencia o manual
            Log.d(tag, "SUBTITLE CONFIG: subtitlesEnabled=$subtitlesEnabled, preferredSubtitleLanguage=$preferredSubtitleLanguage, activeManualSubtitle=${activeManualSubtitle?.label}")
            if (!subtitlesEnabled && activeManualSubtitle == null) {
                // Subtítulos desactivados
                trackParamsBuilder = trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                Log.d(tag, "SUBTITLE: Disabled by user preference")
            } else {
                // Subtítulos activados - buscar idioma preferido o manual
                trackParamsBuilder = trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                
                val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                Log.d(tag, "SUBTITLE: textGroups count=${textGroups.size}")
                
                if (textGroups.isNotEmpty()) {
                    var subtitleOverride: TrackSelectionOverride? = null
                    
                    // Primero, si hay un subtítulo manual activo, buscar y seleccionar ese
                    if (activeManualSubtitle != null) {
                        val targetLabel = activeManualSubtitle!!.label.lowercase()
                        Log.d(tag, "SUBTITLE: Looking for manual subtitle matching label: $targetLabel")
                        for (group in textGroups) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val trackLang = format.language?.lowercase() ?: ""
                                Log.d(tag, "SUBTITLE: Comparing manual track '$trackLang' vs target '$targetLabel'")
                                if (trackLang == targetLabel || trackLang.contains(targetLabel) || targetLabel.contains(trackLang)) {
                                    subtitleOverride = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                                    Log.d(tag, "SUBTITLE: Matched manual subtitle track!")
                                    break
                                }
                            }
                            if (subtitleOverride != null) break
                        }
                    }
                    
                    // Si no hay manual activo (o no se encontró), buscar por preferencia de idioma (es, en, etc.)
                    if (subtitleOverride == null && activeManualSubtitle == null) {
                        val preferredLang = preferredSubtitleLanguage.lowercase()
                        Log.d(tag, "SUBTITLE: Looking for preferred language: $preferredLang")
                        for (group in textGroups) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val trackLang = format.language?.lowercase() ?: ""
                                
                                // Comparación flexible
                                val isMatch = when {
                                    trackLang == preferredLang -> true
                                    preferredLang == "es" && (trackLang.contains("spanish") || trackLang.contains("spa") || trackLang.contains("es")) -> true
                                    preferredLang == "en" && (trackLang.contains("english") || trackLang.contains("eng") || trackLang.contains("en")) -> true
                                    else -> false
                                }
                                
                                Log.d(tag, "SUBTITLE: Comparing track '$trackLang' vs preferred '$preferredLang' -> match=$isMatch")
                                if (isMatch) {
                                    subtitleOverride = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                                    Log.d(tag, "SUBTITLE: Matched preferred subtitle track!")
                                    break
                                }
                            }
                            if (subtitleOverride != null) break
                        }
                    }
                    
                    if (subtitleOverride != null) {
                        trackParamsBuilder = trackParamsBuilder.setOverrideForType(subtitleOverride)
                        Log.d(tag, "SUBTITLE SUCCESS: Applied subtitle track")
                    } else {
                        // Si no hay manual activo ni coincidencia de idioma, pero el usuario los tiene activados y hay tracks,
                        // no los desactives completamente si hay subtítulos manuales cargados, sino selecciona el primero.
                        if (activeManualSubtitle != null) {
                            val firstGroup = textGroups.first()
                            if (firstGroup.length > 0) {
                                subtitleOverride = TrackSelectionOverride(firstGroup.mediaTrackGroup, listOf(0))
                                trackParamsBuilder = trackParamsBuilder.setOverrideForType(subtitleOverride!!)
                                Log.d(tag, "SUBTITLE: Fallback to first text group track for manual subtitle")
                            }
                        } else {
                            trackParamsBuilder = trackParamsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            Log.d(tag, "SUBTITLE FAILED: No matching subtitle track found, disabling subtitles")
                        }
                    }
                } else {
                    Log.d(tag, "SUBTITLE: No text groups available")
                }
            }
            
            // 3. Configurar calidad preferida (solo para HLS/DASH con múltiple tracks)
            Log.d(tag, "QUALITY CONFIG: preferredQuality=$preferredQuality (isAuto=${preferredQuality == "auto"}), explicit=${explicitVideoWidth}x${explicitVideoHeight}")
            
            if (explicitVideoWidth > 0 && explicitVideoHeight > 0) {
                if (videoGroups.isNotEmpty()) {
                    var videoOverride: TrackSelectionOverride? = null
                    for (group in videoGroups) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val matches = (format.width == explicitVideoWidth && format.height == explicitVideoHeight) ||
                                          (maxOf(format.width, format.height) == maxOf(explicitVideoWidth, explicitVideoHeight))
                            if (matches) {
                                videoOverride = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                                Log.d(tag, "QUALITY EXPLICIT: Found matching track with size=${format.width}x${format.height}")
                                break
                            }
                        }
                        if (videoOverride != null) break
                    }
                    
                    if (videoOverride != null) {
                        trackParamsBuilder = trackParamsBuilder
                            .clearVideoSizeConstraints()
                            .setMaxVideoSize(explicitVideoWidth, explicitVideoHeight)
                            .setOverrideForType(videoOverride)
                        Log.d(tag, "QUALITY EXPLICIT SUCCESS: Applied explicit override")
                    } else {
                        Log.d(tag, "QUALITY EXPLICIT FAILED: Explicit track not found")
                    }
                }
            } else if (preferredQuality != "auto" && !skipAutoQualitySelection) {
                Log.d(tag, "QUALITY: videoGroups count=${videoGroups.size}")
                
                if (videoGroups.isNotEmpty()) {
                    // Buscar track de video con la calidad preferida
                    var videoOverride: TrackSelectionOverride? = null
                    val targetMaxDim = when (preferredQuality) {
                        "4k", "2160p" -> 3840
                        "2k", "1440p" -> 2560
                        "1080p" -> 1920
                        "720p" -> 1280
                        "480p" -> 854
                        "360p" -> 640
                        "240p" -> 426
                        "144p" -> 256
                        else -> 1920
                    }
                    Log.d(tag, "QUALITY: Looking for maxDim ~$targetMaxDim")
                    
                    // Log available video tracks
                    videoGroups.forEachIndexed { gIdx, group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            Log.d(tag, "QUALITY: Group $gIdx Track $i - ${format.width}x${format.height}, maxDim=${maxOf(format.width, format.height)}")
                        }
                    }
                    
                    // Recopilar todos los tracks candidatos válidos
                    val candidates = mutableListOf<Triple<Int, Int, androidx.media3.common.Format>>()
                    videoGroups.forEachIndexed { gIdx, group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.width > 0 || format.height > 0) {
                                candidates.add(Triple(gIdx, i, format))
                            }
                        }
                    }

                    // Techo máximo efectivo según capacidades reales del hardware
                    val hwMax = deviceCapabilities.getHardwareMaxVideoSize()
                    val effectiveCeiling = minOf(targetMaxDim, maxOf(hwMax.first, hwMax.second))

                    // Seleccionar la mejor pista que no supere el techo del hardware (prioriza 4K si existe)
                    val bestMatch = candidates
                        .filter { maxOf(it.third.width, it.third.height) <= (effectiveCeiling + 200) }
                        .maxByOrNull { maxOf(it.third.width, it.third.height) }

                    if (bestMatch != null) {
                        val (bestGroupIdx, bestTrackIdx, bestFormat) = bestMatch
                        videoOverride = TrackSelectionOverride(videoGroups[bestGroupIdx].mediaTrackGroup, listOf(bestTrackIdx))
                        trackParamsBuilder = trackParamsBuilder
                            .clearVideoSizeConstraints()
                            .setMaxVideoSize(bestFormat.width, bestFormat.height)
                            .setOverrideForType(videoOverride)
                        Log.d(tag, "QUALITY SUCCESS: Applied best track ${bestFormat.width}x${bestFormat.height} (target: $preferredQuality, hwMax: ${hwMax.first}x${hwMax.second})")
                    } else {
                        Log.d(tag, "QUALITY FAILED: No compatible track found for $preferredQuality, using auto")
                    }
                } else {
                    Log.d(tag, "QUALITY: No video groups available (probably manual quality)")
                }
            } else {
                Log.d(tag, "QUALITY: Auto/skipped quality selected, skipping track override")
            }
            
            // Aplicar todos los parámetros
            player.trackSelectionParameters = trackParamsBuilder.build()
            Log.d(tag, "Applied user preferences to player tracks")
            
            // Resetear banderas después de un ciclo
            if (skipAutoAudioSelection) {
                skipAutoAudioSelection = false
                Log.d(tag, "Reset skipAutoAudioSelection flag")
            }
            if (skipAutoQualitySelection && explicitVideoWidth <= 0) {
                skipAutoQualitySelection = false
                Log.d(tag, "Reset skipAutoQualitySelection flag")
            }
            
            // Actualizar navegación entre botones visibles tras comprobar pistas
            setupControllerFocusNavigation()
        }
    }
    
    private var periodicProgressJob: kotlinx.coroutines.Job? = null

    private fun startPeriodicProgressSaving() {
        if (periodicProgressJob?.isActive == true) return
        periodicProgressJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                kotlinx.coroutines.delay(15_000L) // Auto-guardar cada 15 segundos
                if (exoPlayer?.isPlaying == true) {
                    saveVideoPosition()
                }
            }
        }
    }

    private fun stopPeriodicProgressSaving() {
        periodicProgressJob?.cancel()
        periodicProgressJob = null
    }

    override fun onPause() {
        super.onPause()
        stopPeriodicProgressSaving()
        saveVideoPosition()
        exoPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        stopPeriodicProgressSaving()
        saveVideoPosition()
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
        if (exoPlayer?.isPlaying == true) {
            startPeriodicProgressSaving()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicProgressSaving()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun getLanguageName(languageCode: String?): String {
        if (languageCode == null) return "Desconocido"

        val code = languageCode.lowercase().trim()

        return when (code) {
            // ISO 639-1 (2 letras) - más común
            "es", "spa" -> "Español"
            "en", "eng" -> "English"
            "fr", "fra", "fre" -> "Français"
            "de", "deu", "ger" -> "Deutsch"
            "it", "ita" -> "Italiano"
            "pt", "por" -> "Português"
            "ja", "jpn" -> "日本語"
            "ko", "kor" -> "한국어"
            "zh", "zho", "chi" -> "中文"
            "ru", "rus" -> "Русский"
            "ar", "ara" -> "العربية"
            "hi", "hin" -> "हिन्दी"
            "tr", "tur" -> "Türkçe"
            "pl", "pol" -> "Polski"
            "nl", "nld", "dut" -> "Nederlands"
            "sv", "swe" -> "Svenska"
            "da", "dan" -> "Dansk"
            "no", "nor" -> "Norsk"
            "fi", "fin" -> "Suomi"
            "cs", "ces", "cze" -> "Čeština"
            "hu", "hun" -> "Magyar"
            "el", "ell", "gre" -> "Ελληνικά"
            "he", "heb" -> "עברית"
            "th", "tha" -> "ไทย"
            "vi", "vie" -> "Tiếng Việt"
            "id", "ind" -> "Bahasa Indonesia"
            "ms", "msa", "may" -> "Bahasa Melayu"
            "uk", "ukr" -> "Українська"
            "ro", "ron", "rum" -> "Română"
            "bg", "bul" -> "Български"
            "hr", "hrv" -> "Hrvatski"
            "sr", "srp" -> "Српски"
            "sk", "slk", "slo" -> "Slovenčina"
            "sl", "slv" -> "Slovenščina"
            "lt", "lit" -> "Lietuvių"
            "lv", "lav" -> "Latviešu"
            "et", "est" -> "Eesti"
            "ca", "cat" -> "Català"
            "eu", "eus", "baq" -> "Euskara"
            "gl", "glg" -> "Galego"
            "ga", "gle" -> "Gaeilge"
            "gd", "gla" -> "Gàidhlig"
            "cy", "cym", "wel" -> "Cymraeg"
            "mt", "mlt" -> "Malti"
            "is", "isl", "ice" -> "Íslenska"
            "af", "afr" -> "Afrikaans"
            "sq", "sqi", "alb" -> "Shqip"
            "hy", "hye", "arm" -> "Հայերեն"
            "az", "aze" -> "Azərbaycanca"
            "ka", "kat", "geo" -> "ქართული"
            "ur", "urd" -> "اردو"
            "fa", "fas", "per" -> "فارسی"
            "ta", "tam" -> "தமிழ்"
            "te", "tel" -> "తెలుగు"
            "kn", "kan" -> "ಕನ್ನಡ"
            "ml", "mal" -> "മലയാളം"
            "mr", "mar" -> "मराठी"
            "bn", "ben" -> "বাংলা"
            "gu", "guj" -> "ગુજરાતી"
            "pa", "pan" -> "ਪੰਜਾਬੀ"
            "ne", "nep" -> "नेपाली"
            "si", "sin" -> "සිංහල"
            "my", "mya", "bur" -> "မြန်မာဘာသာ"
            "km", "khm" -> "ខ្មែរ"
            "lo", "lao" -> "ລາວ"
            "sw", "swa" -> "Kiswahili"
            "zu", "zul" -> "isiZulu"
            "xh", "xho" -> "isiXhosa"
            "am", "amh" -> "አማርኛ"
            "so", "som" -> "Soomaali"
            "mn", "mon" -> "Монгол"
            "ps", "pus" -> "پښتو"
            "ku", "kur" -> "Kurdî"
            "mk", "mkd", "mac" -> "Македонски"
            "bs", "bos" -> "Bosanski"
            // Códigos de idioma con región
            "es-es" -> "Español (España)"
            "es-mx" -> "Español (México)"
            "es-ar" -> "Español (Argentina)"
            "en-us" -> "English (US)"
            "en-gb" -> "English (UK)"
            "en-ca" -> "English (Canada)"
            "fr-fr" -> "Français (France)"
            "fr-ca" -> "Français (Canada)"
            "pt-br" -> "Português (Brasil)"
            "pt-pt" -> "Português (Portugal)"
            "zh-cn" -> "中文 (简体)"
            "zh-tw" -> "中文 (繁體)"
            "zh-hk" -> "中文 (香港)"
            // Si no está en la lista, capitalizar primera letra
            else -> code.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatAudioTrackLabel(languageCode: String?, format: androidx.media3.common.Format): String {
        val baseName = getLanguageName(languageCode)
        val mimeType = format.sampleMimeType ?: ""
        val channels = format.channelCount
        
        val codecInfo = when {
            mimeType.contains("joc", ignoreCase = true) || format.codecs?.contains("joc", ignoreCase = true) == true -> "Dolby Atmos"
            mimeType.equals("audio/eac3", ignoreCase = true) || mimeType.contains("eac3", ignoreCase = true) -> {
                when {
                    channels >= 8 -> "Dolby Digital Plus 7.1"
                    channels >= 6 -> "Dolby Digital Plus 5.1"
                    else -> "Dolby Digital Plus"
                }
            }
            mimeType.equals("audio/ac3", ignoreCase = true) || mimeType.contains("ac3", ignoreCase = true) -> {
                if (channels >= 6) "Dolby Digital 5.1" else "Dolby Digital"
            }
            mimeType.contains("true-hd", ignoreCase = true) -> "Dolby TrueHD"
            mimeType.contains("dts", ignoreCase = true) -> "DTS"
            channels >= 8 -> "7.1 Surround"
            channels >= 6 -> "5.1 Surround"
            else -> null
        }
        
        return if (codecInfo != null) "$baseName ($codecInfo)" else baseName
    }

    private fun showAudioTrackSelection() {
        try {
            Log.d(tag, "showAudioTrackSelection: START")
            
            // Si hay audios manuales (Vidara), mostrar esos
            if (manualAudios.isNotEmpty()) {
                Log.d(tag, "Using manual audio selection for Vidara: ${manualAudios.size} tracks")
                showManualAudioSelection()
                return
            }
            
            exoPlayer?.let { player ->
                Log.d(tag, "showAudioTrackSelection: player is not null")
                val trackSelector = player.currentTracks
                val audioGroups = trackSelector.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                Log.d(tag, "showAudioTrackSelection: audioGroups count = ${audioGroups.size}")

                if (audioGroups.isEmpty()) {
                    Toast.makeText(this, "No hay pistas de audio disponibles", Toast.LENGTH_SHORT).show()
                    return
                }

                val audioTracks = mutableListOf<String>()
                val trackIndices = mutableListOf<Pair<Int, Int>>()
                var selectedIndex = 0

                audioGroups.forEachIndexed { groupIndex, group ->
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val languageCode = format.language
                        val label = formatAudioTrackLabel(languageCode, format)
                        val selected = group.isTrackSelected(trackIndex)
                        if (selected) selectedIndex = audioTracks.size
                        audioTracks.add(label)
                        trackIndices.add(Pair(groupIndex, trackIndex))
                    }
                }
                Log.d(tag, "showAudioTrackSelection: audioTracks count = ${audioTracks.size}")

                var currentSelectedIndex = selectedIndex

                Log.d(tag, "showAudioTrackSelection: Creating AlertDialog.Builder")
                val builder = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                Log.d(tag, "showAudioTrackSelection: Setting title")
                builder.setTitle("Seleccionar Audio")
                Log.d(tag, "showAudioTrackSelection: Setting single choice items")
                val adapter = android.widget.ArrayAdapter(this, com.anfhly.streaming.R.layout.item_sidebar_choice, android.R.id.text1, audioTracks)
                builder.setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
                    val (groupIndex, trackIndex) = trackIndices[which]
                    skipAutoAudioSelection = true
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setPreferredAudioLanguage(null)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                audioGroups[groupIndex].mediaTrackGroup,
                                listOf(trackIndex)
                            )
                        )
                        .build()
                    Toast.makeText(this, "Audio: ${audioTracks[which]}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                Log.d(tag, "showAudioTrackSelection: Showing dialog")
                val dialog = builder.show()
                styleSidebarDialog(dialog)
                Log.d(tag, "showAudioTrackSelection: Dialog shown")
            } ?: run {
                Log.e(tag, "showAudioTrackSelection: player is null")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error showing audio dialog: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showManualAudioSelection() {
        Log.d(tag, "showManualAudioSelection: ${manualAudios.size} manual audio tracks")
        
        val audioTracks = manualAudios.map { it.label }
        
        // Usar currentAudioTrack si está seteado, si no usar el default
        val selectedIndex = if (currentAudioTrack != null) {
            val idx = manualAudios.indexOfFirst { it.file == currentAudioTrack?.file }
            if (idx >= 0) idx else manualAudios.indexOfFirst { it.default }.takeIf { it >= 0 } ?: 0
        } else {
            manualAudios.indexOfFirst { it.default }.takeIf { it >= 0 } ?: 0
        }
        
        Log.d(tag, "Selected index for dialog: $selectedIndex (currentAudioTrack: ${currentAudioTrack?.label})")
        
        var currentSelectedIndex = selectedIndex

        val builder = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
        builder.setTitle("Seleccionar Audio")
        val adapter = android.widget.ArrayAdapter(this, com.anfhly.streaming.R.layout.item_sidebar_choice, android.R.id.text1, audioTracks)
        builder.setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
            val selectedAudio = manualAudios[which]
            Log.d(tag, "Selected audio: ${selectedAudio.label}, file: ${selectedAudio.file}")
            currentAudioTrack = selectedAudio
            skipAutoAudioSelection = true
            loadVideoWithAudio(selectedAudio)
            Toast.makeText(this, "Audio: ${selectedAudio.label}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        val dialog = builder.show()
        styleSidebarDialog(dialog)
    }

    private var currentAudioTrack: com.anfhly.streaming.data.model.Video.AudioTrack? = null
    private var skipAutoAudioSelection = false // Evitar selección automática después de cambio manual
    private var skipAutoQualitySelection = false // Evitar selección automática después de cambio manual

    private fun loadVideoWithAudio(audio: com.anfhly.streaming.data.model.Video.AudioTrack) {
        Log.d(tag, "Loading video with audio track: ${audio.label} (${audio.file})")
        
        // Guardar posición actual antes de recargar
        val currentPosition = exoPlayer?.currentPosition ?: 0
        
        val videoUrl = currentVideoUrl
        val audioUrl = audio.file
        
        if (audioUrl.isBlank()) {
            Log.e(tag, "Error: URL de audio vacía para ${audio.label}")
            Toast.makeText(this, "Error: URL de audio vacía", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Verificar si la URL de audio es relativa
        val fullAudioUrl = if (audioUrl.startsWith("http")) {
            audioUrl
        } else {
            val baseUrl = videoUrl.substringBefore("/api/").substringBefore("/v/")
            val resolved = "$baseUrl$audioUrl"
            Log.d(tag, "Resolved relative audio URL to: ${resolved.take(60)}...")
            resolved
        }
        
        Log.d(tag, "========================================")
        Log.d(tag, "LOAD VIDEO WITH AUDIO - START")
        Log.d(tag, "========================================")
        Log.d(tag, "Video URL: $videoUrl")
        Log.d(tag, "Audio URL: $fullAudioUrl")
        Log.d(tag, "Audio Language: ${audio.language}")
        Log.d(tag, "Audio Label: ${audio.label}")
        Log.d(tag, "Current position: $currentPosition ms")
        Log.d(tag, "Current audio track before: ${currentAudioTrack?.label}")
        
        // Guardar el audio track seleccionado
        currentAudioTrack = audio
        
        // Para HLS con audio tracks embebidos (Vidara), las pistas de audio ya están
        // declaradas en el manifiesto maestro como #EXT-X-MEDIA:TYPE=AUDIO.
        // ExoPlayer ya las reconoce como tracks embebidos, así que intentamos
        // seleccionar el track por idioma/nombre PRIMERO antes de usar MergingMediaSource.
        val isVideoHlsMaster = videoUrl.contains(".m3u8", ignoreCase = true) ||
                               videoUrl.contains("/hls/", ignoreCase = true)
        val isAudioHlsSubManifest = fullAudioUrl.contains(".m3u8", ignoreCase = true)
        
        if (isVideoHlsMaster && isAudioHlsSubManifest) {
            Log.d(tag, "HLS video with HLS audio sub-manifest detected, trying embedded track selection first")
            val selectedEmbedded = trySelectEmbeddedAudioTrack(audio)
            if (selectedEmbedded) {
                Log.d(tag, "Successfully selected embedded HLS audio track: ${audio.label}")
                Toast.makeText(this, "Audio: ${audio.label}", Toast.LENGTH_SHORT).show()
                return
            }
            Log.d(tag, "Embedded track selection failed, falling back to MergingMediaSource")
        }
        
        // Detectar si la URL de audio es un video completo (termina en .mp4, .mkv, etc.)
        // NOTA: .m3u8 NO se incluye aquí porque las pistas de audio HLS son sub-manifiestos
        // que contienen solo audio, no videos completos
        val isAudioUrlAVideo = fullAudioUrl.endsWith(".mp4") || 
                               fullAudioUrl.endsWith(".mkv") ||
                               fullAudioUrl.contains("video") ||
                               fullAudioUrl == videoUrl ||
                               fullAudioUrl.contains(videoUrl.substringAfterLast("/"))
        
        Log.d(tag, "Is audio URL a complete video? $isAudioUrlAVideo")
        
        if (isAudioUrlAVideo) {
            // Si la URL de audio es un video completo, recargar todo con esa URL
            Log.d(tag, "Audio URL is a complete video, reloading with full video URL")
            reloadVideoWithNewSource(fullAudioUrl, currentPosition, audio.label)
            return
        }
        
        // Si la URL del audio es diferente al video actual, intentamos usar solo el audio track
        if (fullAudioUrl == videoUrl || fullAudioUrl.contains(videoUrl) || videoUrl.contains(fullAudioUrl)) {
            Log.d(tag, "Audio URL matches video URL, using track selection instead")
            selectEmbeddedAudioTrack(audio)
            return
        }
        
        // Recargar el player con el nuevo audio track usando MergingMediaSource
        lifecycleScope.launch {
            try {
                runOnUiThread {
                    Log.d(tag, "Stopping and clearing current player")
                    exoPlayer?.stop()
                    exoPlayer?.clearMediaItems()
                    
                    // Configurar DataSource con headers
                    val dataSourceFactory = object : androidx.media3.datasource.DataSource.Factory {
                        override fun createDataSource(): androidx.media3.datasource.DataSource {
                            val httpDataSource = DefaultHttpDataSource.Factory()
                                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .setAllowCrossProtocolRedirects(true)
                                .setConnectTimeoutMs(15000)
                                .setReadTimeoutMs(30000)
                                .createDataSource()
                            currentHeaders.forEach { entry ->
                                httpDataSource.setRequestProperty(entry.key, entry.value)
                            }
                            return httpDataSource
                        }
                    }
                    
                    // Crear MediaItem para video (sin seleccionar tracks de audio)
                    val videoMediaItem = MediaItem.Builder()
                        .setUri(videoUrl)
                        .setMimeType(currentVideoMimeType)
                        .build()
                    
                    // Crear MediaItem para audio
                    val audioMediaItem = MediaItem.Builder()
                        .setUri(fullAudioUrl)
                        .build()
                    
                    Log.d(tag, "Creating media sources")
                    val videoSource = if (videoUrl.contains(".m3u8") || (currentVideoMimeType != null && currentVideoMimeType!!.contains("m3u8"))) {
                        HlsMediaSource.Factory(dataSourceFactory)
                            .setAllowChunklessPreparation(true)
                            .createMediaSource(videoMediaItem)
                    } else {
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(videoMediaItem)
                    }
                    
                    val audioSource = if (fullAudioUrl.contains(".m3u8")) {
                        HlsMediaSource.Factory(dataSourceFactory)
                            .setAllowChunklessPreparation(true)
                            .createMediaSource(audioMediaItem)
                    } else {
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(audioMediaItem)
                    }
                    
                    Log.d(tag, "Creating MergingMediaSource with optional manual subtitle")
                    val mergedSource = wrapWithManualSubtitle(MergingMediaSource(videoSource, audioSource), dataSourceFactory)
                    
                    Log.d(tag, "Setting media source and preparing")
                    exoPlayer?.setMediaSource(mergedSource)
                    exoPlayer?.prepare()
                    exoPlayer?.seekTo(currentPosition)
                    
                    // Esperar a que el player esté listo para aplicar selección de audio
                    exoPlayer?.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                Log.d(tag, "Player ready, applying audio track selection")
                                
                                // Obtener tracks disponibles
                                val tracks = exoPlayer?.currentTracks
                                val audioGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }
                                
                                Log.d(tag, "Available audio groups after prepare: ${audioGroups?.size}")
                                audioGroups?.forEachIndexed { idx, group ->
                                    Log.d(tag, "Audio Group $idx: length=${group.length}, isSelected=${group.isSelected}")
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        Log.d(tag, "  Track $i: ${format.language}, selected=${group.isTrackSelected(i)}")
                                    }
                                }
                                
                                // El audio externo debería estar en el segundo grupo (índice 1)
                                // o en el mismo grupo como track adicional
                                val currentParams = exoPlayer?.trackSelectionParameters
                                if (audioGroups != null && audioGroups.size >= 2 && currentParams != null) {
                                    // Si hay 2 grupos de audio, seleccionar el segundo (audio externo)
                                    val externalAudioGroup = audioGroups[1]
                                    Log.d(tag, "Selecting external audio group 1")
                                    exoPlayer?.trackSelectionParameters = currentParams
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(externalAudioGroup.mediaTrackGroup, 0)
                                        )
                                        .build()
                                } else if (audioGroups != null && audioGroups.isNotEmpty() && currentParams != null) {
                                    // Si solo hay 1 grupo con múltiples tracks, seleccionar el último track
                                    // (asumiendo que el audio externo se añade después del embebido)
                                    val group = audioGroups[0]
                                    if (group.length > 1) {
                                        val lastTrackIndex = group.length - 1
                                        Log.d(tag, "Selecting last audio track: $lastTrackIndex")
                                        exoPlayer?.trackSelectionParameters = currentParams
                                            .buildUpon()
                                            .setOverrideForType(
                                                TrackSelectionOverride(group.mediaTrackGroup, lastTrackIndex)
                                            )
                                            .build()
                                    }
                                }
                                
                                // Verificar si el audio realmente cambió
                                val finalTracks = exoPlayer?.currentTracks
                                val finalAudioGroups = finalTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }
                                Log.d(tag, "FINAL Audio groups after selection: ${finalAudioGroups?.size}")
                                finalAudioGroups?.forEachIndexed { idx, group ->
                                    Log.d(tag, "FINAL Audio Group $idx: selected=${group.isSelected}")
                                    for (i in 0 until group.length) {
                                        val format = group.getTrackFormat(i)
                                        Log.d(tag, "  FINAL Track $i: ${format.language}, selected=${group.isTrackSelected(i)}")
                                    }
                                }
                                
                                // Remover este listener
                                exoPlayer?.removeListener(this)
                            }
                        }
                    })
                    
                    exoPlayer?.play()
                    playerView?.hideController()
                    
                    Log.d(tag, "Audio changed successfully to: ${audio.label}")
                    Toast.makeText(this@PlayerActivity, "Audio cambiado a: ${audio.label}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading video with audio: ${e.message}", e)
                Toast.makeText(this@PlayerActivity, "Error al cambiar audio: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Intenta seleccionar un audio track embebido en el HLS master playlist.
     * Para Vidara, cada audio track del extractor corresponde a un grupo de audio
     * en ExoPlayer (1:1 mapping), así que usamos índice como método principal.
     * @return true si se encontró y seleccionó, false si no.
     */
    private fun trySelectEmbeddedAudioTrack(audio: com.anfhly.streaming.data.model.Video.AudioTrack): Boolean {
        val player = exoPlayer ?: return false
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        
        Log.d(tag, "trySelectEmbeddedAudioTrack: ${audioGroups.size} audio groups, looking for '${audio.label}' / '${audio.language}'")
        
        // Log de todos los tracks disponibles para debug
        audioGroups.forEachIndexed { gIdx, group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                Log.d(tag, "  Group $gIdx Track $i: lang='${format.language}', label='${format.label}', selected=${group.isTrackSelected(i)}")
            }
        }
        
        val targetLang = audio.language.lowercase()
        
        // Estrategia 1: Buscar por coincidencia exacta de idioma
        var matchGroupIdx = -1
        var matchTrackIdx = -1
        for (gIdx in audioGroups.indices) {
            val group = audioGroups[gIdx]
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val trackLang = format.language?.lowercase() ?: ""
                
                // Coincidencia exacta
                if (trackLang.isNotEmpty() && trackLang == targetLang) {
                    matchGroupIdx = gIdx
                    matchTrackIdx = i
                    Log.d(tag, "  -> Exact language match at group $gIdx track $i")
                    break
                }
            }
            if (matchGroupIdx >= 0) break
        }
        
        // Estrategia 2: Si no hay match exacto, usar índice basado en manualAudios
        // Los tracks de Vidara mapean 1:1 con los grupos de audio de ExoPlayer
        if (matchGroupIdx < 0) {
            val audioIndex = manualAudios.indexOf(audio)
            Log.d(tag, "  No exact match, trying index-based: audioIndex=$audioIndex, groups=${audioGroups.size}")
            
            if (audioIndex >= 0) {
                // Caso 1: Cada audio es un grupo separado (un track por grupo)
                if (audioIndex < audioGroups.size) {
                    matchGroupIdx = audioIndex
                    matchTrackIdx = 0
                    Log.d(tag, "  -> Index match: group $matchGroupIdx track $matchTrackIdx")
                }
                // Caso 2: Todos los audios en un solo grupo (múltiples tracks)
                else if (audioGroups.size == 1 && audioIndex < audioGroups[0].length) {
                    matchGroupIdx = 0
                    matchTrackIdx = audioIndex
                    Log.d(tag, "  -> Index match (single group): group 0 track $matchTrackIdx")
                }
            }
        }
        
        if (matchGroupIdx >= 0 && matchTrackIdx >= 0) {
            val group = audioGroups[matchGroupIdx]
            Log.d(tag, "  -> SELECTING: group $matchGroupIdx track $matchTrackIdx")
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, matchTrackIdx)
                )
                .build()
            return true
        }
        
        Log.d(tag, "  -> No match found")
        return false
    }

    private fun selectEmbeddedAudioTrack(audio: com.anfhly.streaming.data.model.Video.AudioTrack) {
        Log.d(tag, "Selecting embedded audio track by language: ${audio.language}")
        
        val found = trySelectEmbeddedAudioTrack(audio)
        if (found) {
            Toast.makeText(this, "Audio: ${audio.label}", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(tag, "No matching embedded audio track found")
            Toast.makeText(this, "No se encontró pista de audio: ${audio.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reloadVideoWithNewSource(newUrl: String, position: Long, audioLabel: String) {
        Log.d(tag, "Reloading video with new source: ${newUrl.take(60)}... at position $position")
        
        lifecycleScope.launch {
            try {
                runOnUiThread {
                    // Limpiar player actual
                    exoPlayer?.stop()
                    exoPlayer?.clearMediaItems()
                    
                    // Actualizar URL actual
                    currentVideoUrl = newUrl
                    
                    // Configurar DataSource
                    val dataSourceFactory = object : androidx.media3.datasource.DataSource.Factory {
                        override fun createDataSource(): androidx.media3.datasource.DataSource {
                            val httpDataSource = DefaultHttpDataSource.Factory()
                                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                                .setAllowCrossProtocolRedirects(true)
                                .setConnectTimeoutMs(15000)
                                .setReadTimeoutMs(30000)
                                .createDataSource()
                            currentHeaders.forEach { entry ->
                                httpDataSource.setRequestProperty(entry.key, entry.value)
                            }
                            return httpDataSource
                        }
                    }
                    
                    // Crear MediaItem
                    val mediaItemBuilder = MediaItem.Builder().setUri(newUrl)
                    currentVideoMimeType?.let { mimeType ->
                        if (mimeType.isNotBlank()) {
                            mediaItemBuilder.setMimeType(mimeType)
                        }
                    }
                    val mediaItem = mediaItemBuilder.build()
                    
                    // Crear MediaSource
                    val mediaSource = if (newUrl.contains(".m3u8")) {
                        HlsMediaSource.Factory(dataSourceFactory)
                            .setAllowChunklessPreparation(true)
                            .createMediaSource(mediaItem)
                    } else {
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                    }
                    
                    // Configurar player con soporte para subtítulos manuales activos
                    val finalSource = wrapWithManualSubtitle(mediaSource, dataSourceFactory)
                    exoPlayer?.setMediaSource(finalSource)
                    exoPlayer?.prepare()
                    exoPlayer?.seekTo(position)
                    exoPlayer?.play()
                    playerView?.hideController()
                    
                    Log.d(tag, "Video reloaded successfully with audio: $audioLabel")
                    Toast.makeText(this@PlayerActivity, "Audio: $audioLabel", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error reloading video: ${e.message}", e)
                Toast.makeText(this@PlayerActivity, "Error al cambiar audio: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun saveVideoPosition() {
        if (isLiveChannel) {
            Log.d(tag, "isLiveChannel is true, skipping saveVideoPosition")
            return
        }
        exoPlayer?.let { player ->
            if (player.duration > 0) {
                val position = player.currentPosition
                val duration = player.duration
                Log.d(tag, "Saving position: ${position}ms / ${duration}ms (contentId=$contentId, type=$contentType)")
                // Guardar a partir de 15 segundos y hasta el 95% del video
                val minThreshold = 15_000L
                if (position >= minThreshold && position < duration * 0.95) {
                    val title = when (contentType) {
                        "series" -> currentSeries?.title ?: currentContent?.title
                        "movie"  -> currentMovie?.title ?: currentContent?.title
                        else     -> currentContent?.title
                    }
                    val posterUrl = currentContent?.posterUrl ?: currentMovie?.posterUrl ?: currentSeries?.posterUrl
                    val backdropUrl = currentContent?.backdropUrl ?: currentMovie?.backdropUrl ?: currentSeries?.backdropUrl
                    val epTitle = currentEpisode?.name

                    // Guardar con WatchProgressRepository usando runBlocking para asegurar que se guarde
                    runBlocking {
                        try {
                            when {
                                contentType == "series" && seasonNumber != null && episodeNumber != null -> {
                                    watchProgressRepository.saveEpisodeProgress(
                                        seriesId = contentId,
                                        seasonNumber = seasonNumber!!,
                                        episodeNumber = episodeNumber!!,
                                        positionMs = position,
                                        durationMs = duration,
                                        title = title,
                                        posterUrl = posterUrl,
                                        backdropUrl = backdropUrl,
                                        episodeTitle = epTitle
                                    )
                                    Log.d(tag, "Episode progress saved successfully: $title S${seasonNumber}E${episodeNumber}")
                                }
                                contentType == "movie" -> {
                                    watchProgressRepository.saveMovieProgress(
                                        movieId = contentId,
                                        positionMs = position,
                                        durationMs = duration,
                                        title = title,
                                        posterUrl = posterUrl,
                                        backdropUrl = backdropUrl
                                    )
                                    Log.d(tag, "Movie progress saved successfully: $title")
                                }
                                else -> {
                                    Log.w(tag, "Unknown content type: $contentType")
                                }
                            }
                            
                        } catch (e: Exception) {
                            Log.e(tag, "Error saving to WatchProgressRepository", e)
                        }
                    }
                    // También guardar en SharedPreferences para compatibilidad (síncrono)
                    if (currentVideoUrl.isNotEmpty()) {
                        val editor = sharedPreferences.edit()
                        editor.putLong(positionPrefix + currentVideoUrl, position)
                        editor.putLong(durationPrefix + currentVideoUrl, duration)
                        editor.apply()
                        Log.d(tag, "Progress saved to SharedPreferences")
                    }
                } else if (position >= duration * 0.95) {
                    // Si llegamos al final, borrar la posición guardada
                    Log.d(tag, "Video finished, clearing progress")
                    clearVideoPosition()
                    runBlocking {
                        try {
                            when {
                                contentType == "series" && seasonNumber != null && episodeNumber != null -> {
                                    watchProgressRepository.clearEpisodeProgress(contentId, seasonNumber!!, episodeNumber!!)
                                }
                                contentType == "movie" -> {
                                    watchProgressRepository.clearMovieProgress(contentId)
                                }
                                else -> {
                                    Log.w(tag, "Unknown content type for clearing: $contentType")
                                }
                            }
                            
                        } catch (e: Exception) {
                            Log.e(tag, "Error clearing progress", e)
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun getSavedPosition(): Long {
        return when {
            contentType == "series" && seasonNumber != null && episodeNumber != null -> {
                watchProgressRepository.getEpisodeProgress(contentId, seasonNumber!!, episodeNumber!!)?.positionMs ?: 0L
            }
            contentType == "movie" -> {
                watchProgressRepository.getMovieProgress(contentId)?.positionMs ?: 0L
            }
            currentVideoUrl.isNotEmpty() -> {
                sharedPreferences.getLong(positionPrefix + currentVideoUrl, 0L)
            }
            else -> 0L
        }
    }
    
    private fun clearVideoPosition() {
        if (currentVideoUrl.isNotEmpty()) {
            val editor = sharedPreferences.edit()
            editor.remove(positionPrefix + currentVideoUrl)
            editor.remove(durationPrefix + currentVideoUrl)
            editor.apply()
            Log.d(tag, "Posición borrada para: $currentVideoUrl")
        }
    }
    
    private fun proceedToPlay(finalUrl: String, headers: Map<String, String>) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isLiveChannel) {
                Log.d(tag, "Live channel detected, playing from beginning directly")
                setupExoPlayer(finalUrl, headers, -1L)
            } else if (resumePositionFromIntent > 0) {
                Log.d(tag, "Auto-reanudando desde posición del intent: ${resumePositionFromIntent}ms")
                setupExoPlayer(finalUrl, headers, resumePositionFromIntent)
            } else {
                val savedPos = getSavedPosition()
                if (savedPos > 10000 && !isFinishing) {
                    Log.d(tag, "Posición guardada encontrada en DB: ${savedPos}ms, mostrando diálogo de confirmación antes de iniciar")
                    showResumeDialogBeforePlay(savedPos, finalUrl, headers)
                } else {
                    Log.d(tag, "No hay posición guardada válida o es menor a 10s, reproduciendo desde inicio")
                    setupExoPlayer(finalUrl, headers, -1L)
                }
            }
        }
    }

    private fun showResumeDialogBeforePlay(savedPosition: Long, finalUrl: String, headers: Map<String, String>) {
        val minutes = savedPosition / 60000
        val seconds = (savedPosition % 60000) / 1000
        val timeText = String.format("%d:%02d", minutes, seconds)
        
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Continuar viendo")
            .setMessage("¿Deseas continuar desde donde te quedaste ($timeText) o reiniciar desde el principio?")
            .setPositiveButton("Continuar") { dialog, _ ->
                dialog.dismiss()
                setupExoPlayer(finalUrl, headers, savedPosition)
            }
            .setNegativeButton("Reiniciar") { dialog, _ ->
                dialog.dismiss()
                clearVideoPosition()
                setupExoPlayer(finalUrl, headers, -1L)
            }
            .setCancelable(false)
            .show()
    }

    private fun autoSelectDefaultSubtitle() {
        if (manualSubtitles.isNotEmpty() && activeManualSubtitle == null) {
            val prefLang = preferredSubtitleLanguage.lowercase()
            val defaultSub = manualSubtitles.find { it.default }
                ?: manualSubtitles.find { it.label.lowercase().contains(prefLang) }
                ?: manualSubtitles.find { it.label.lowercase().contains("es") }
                ?: manualSubtitles.firstOrNull()
            if (defaultSub != null) {
                activeManualSubtitle = defaultSub
                Log.d(tag, "Auto-selected default subtitle: ${defaultSub.label}")
            }
        }
    }

    private fun showSubtitleSelection() {
        try {
            exoPlayer?.let { player ->
                val trackSelector = player.currentTracks
                val textGroups = trackSelector.groups.filter { it.type == C.TRACK_TYPE_TEXT }

                // Verificar si hay subtítulos disponibles (embebidos o manuales)
                val hasEmbeddedSubtitles = textGroups.isNotEmpty()
                val hasManualSubtitles = manualSubtitles.isNotEmpty()

                if (!hasEmbeddedSubtitles && !hasManualSubtitles) {
                    Toast.makeText(this, "No hay subtítulos disponibles", Toast.LENGTH_SHORT).show()
                    return
                }

                val subtitleTracks = mutableListOf<String>()
                val trackIndices = mutableListOf<Triple<Int, Int, Int>>() // type, groupIndex, trackIndex (-1 for manual)
                // type: 0=none, 1=embedded, 2=manual

                // Add "None" option
                subtitleTracks.add("Desactivar subtítulos")
                trackIndices.add(Triple(0, -1, -1))

                // Add embedded subtitle tracks
                if (hasEmbeddedSubtitles) {
                    textGroups.forEachIndexed { groupIndex, group ->
                        for (trackIndex in 0 until group.length) {
                            val format = group.getTrackFormat(trackIndex)
                            
                            // Si este track embebido es en realidad nuestro subtítulo manual ya fusionado, lo ignoramos
                            // para evitar duplicados en la interfaz (se listará en la sección de manuales).
                            if (activeManualSubtitle != null && (format.label == activeManualSubtitle?.label || format.id == activeManualSubtitle?.label)) {
                                continue
                            }
                            
                            val languageCode = format.language
                            val label = getLanguageName(languageCode)
                            subtitleTracks.add(label)
                            trackIndices.add(Triple(1, groupIndex, trackIndex))
                        }
                    }
                }

                // Add manual subtitle tracks
                if (hasManualSubtitles) {
                    manualSubtitles.forEachIndexed { index, subtitle ->
                        val label = getLanguageName(subtitle.label)
                        subtitleTracks.add(label)
                        trackIndices.add(Triple(2, index, -1))
                    }
                }

                // Determinar el índice seleccionado actualmente buscando en la lista final de índices
                var selectedIndex = 0 // Por defecto "Desactivar" (índice 0)
                
                if (activeManualSubtitle != null) {
                    val activeSub = activeManualSubtitle!!
                    val foundIdx = trackIndices.indexOfFirst { triple ->
                        triple.first == 2 && manualSubtitles[triple.second].file == activeSub.file
                    }
                    if (foundIdx >= 0) {
                        selectedIndex = foundIdx
                    }
                } else if (!player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) {
                    // Buscar track embebido seleccionado en la lista final
                    val foundIdx = trackIndices.indexOfFirst { triple ->
                        triple.first == 1 && textGroups[triple.second].isTrackSelected(triple.third)
                    }
                    if (foundIdx >= 0) {
                        selectedIndex = foundIdx
                    }
                }

                val adapter = android.widget.ArrayAdapter(this, com.anfhly.streaming.R.layout.item_sidebar_choice, android.R.id.text1, subtitleTracks)
                AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                    .setTitle("Seleccionar Subtítulos")
                    .setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
                        val (type, groupIndex, trackIndex) = trackIndices[which]
                        val currentPosition = player.currentPosition
                        when (type) {
                            0 -> {
                                val wasManual = activeManualSubtitle != null
                                activeManualSubtitle = null
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                                if (wasManual) {
                                    changeQualityManual(currentVideoUrl, currentPosition)
                                }
                                Toast.makeText(this, "Subtítulos desactivados", Toast.LENGTH_SHORT).show()
                            }
                            1 -> {
                                val wasManual = activeManualSubtitle != null
                                activeManualSubtitle = null
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(
                                            textGroups[groupIndex].mediaTrackGroup,
                                            trackIndex
                                        )
                                    )
                                    .build()
                                if (wasManual) {
                                    changeQualityManual(currentVideoUrl, currentPosition)
                                }
                                Toast.makeText(this, "Subtítulos: ${subtitleTracks[which]}", Toast.LENGTH_SHORT).show()
                            }
                            2 -> {
                                val selectedSubtitle = manualSubtitles[groupIndex]
                                activeManualSubtitle = selectedSubtitle
                                changeQualityManual(currentVideoUrl, currentPosition)
                                Toast.makeText(this, "Subtítulos: ${selectedSubtitle.label}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        dialog.dismiss()
                    }
                    .show().also { styleSidebarDialog(it) }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error showing subtitle dialog: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupEpisodeNavigation() {
        val isMovie = currentEpisode == null

        Log.d(tag, "setupEpisodeNavigation: isMovie=$isMovie, currentEpisode=$currentEpisode, currentSeason=$currentSeason")
        Log.d(tag, "setupEpisodeNavigation: season episodes count=${currentSeason?.episodes?.size ?: 0}")
        Log.d(tag, "setupEpisodeNavigation: episodes list=${currentSeason?.episodes?.map { "E${it.episodeNumber}" }}")

        val exoRew = playerView?.findViewById<android.view.View>(R.id.exo_rew) ?: findViewById(R.id.exo_rew)
        val exoFfwd = playerView?.findViewById<android.view.View>(R.id.exo_ffwd) ?: findViewById(R.id.exo_ffwd)

        if (isMovie) {
            Log.d(tag, "Playing movie - hiding episode navigation, showing rew/ffwd")
            btnPrev.visibility = android.view.View.GONE
            btnNext.visibility = android.view.View.GONE
            btnPrev.isFocusable = false
            btnNext.isFocusable = false
            exoRew?.visibility = android.view.View.VISIBLE
            exoFfwd?.visibility = android.view.View.VISIBLE
            exoRew?.isFocusable = true
            exoFfwd?.isFocusable = true
            setupControllerFocusNavigation()
            return
        }

        Log.d(tag, "Playing series episode - setting up episode navigation")
        btnPrev.visibility = android.view.View.VISIBLE
        btnNext.visibility = android.view.View.VISIBLE
        exoRew?.visibility = android.view.View.GONE
        exoFfwd?.visibility = android.view.View.GONE
        exoRew?.isFocusable = false
        exoFfwd?.isFocusable = false

        val currentEpNumber = currentEpisode?.episodeNumber ?: 0
        Log.d(tag, "Current episode number: $currentEpNumber")

        // Verificar si hay episodio anterior (número menor)
        val prevEpisodes = currentSeason?.episodes?.filter { it.episodeNumber < currentEpNumber }
        val hasPrevEpisode = !prevEpisodes.isNullOrEmpty()
        Log.d(tag, "Previous episodes found: ${prevEpisodes?.size ?: 0}, hasPrev=$hasPrevEpisode")

        btnPrev.isEnabled = hasPrevEpisode
        btnPrev.isFocusable = hasPrevEpisode
        btnPrev.alpha = if (hasPrevEpisode) 1.0f else 0.3f

        // Verificar si hay episodio siguiente (número mayor)
        val nextEpisodes = currentSeason?.episodes?.filter { it.episodeNumber > currentEpNumber }
        val hasNextEpisode = !nextEpisodes.isNullOrEmpty()
        Log.d(tag, "Next episodes found: ${nextEpisodes?.size ?: 0}, hasNext=$hasNextEpisode")

        btnNext.isEnabled = hasNextEpisode
        btnNext.isFocusable = hasNextEpisode
        btnNext.alpha = if (hasNextEpisode) 1.0f else 0.3f

        setupControllerFocusNavigation()

        // Listeners para navegación
        btnPrev.setOnClickListener {
            Log.d(tag, "btnPrev clicked")
            loadPreviousEpisode()
        }

        btnNext.setOnClickListener {
            Log.d(tag, "btnNext clicked")
            loadNextEpisode()
        }
    }

    private fun loadPreviousEpisode() {
        val currentEp = currentEpisode ?: run {
            Log.e(tag, "loadPreviousEpisode: currentEpisode is null")
            return
        }
        val season = currentSeason ?: run {
            Log.e(tag, "loadPreviousEpisode: currentSeason is null")
            return
        }

        Log.d(tag, "loadPreviousEpisode: current=${currentEp.episodeNumber}, available=${season.episodes.map { it.episodeNumber }}")

        // Encontrar episodio anterior (número menor)
        val candidates = season.episodes.filter { it.episodeNumber < currentEp.episodeNumber }
        Log.d(tag, "loadPreviousEpisode: candidates=${candidates.map { it.episodeNumber }}")

        val prevEpisode = candidates.maxByOrNull { it.episodeNumber }

        if (prevEpisode != null) {
            Log.d(tag, "loadPreviousEpisode: loading episode ${prevEpisode.episodeNumber}")
            loadEpisode(prevEpisode)
        } else {
            Log.d(tag, "loadPreviousEpisode: no previous episode found")
            Toast.makeText(this, "No hay episodio anterior", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadNextEpisode() {
        val currentEp = currentEpisode ?: run {
            Log.e(tag, "loadNextEpisode: currentEpisode is null")
            return
        }
        val season = currentSeason ?: run {
            Log.e(tag, "loadNextEpisode: currentSeason is null")
            return
        }

        Log.d(tag, "loadNextEpisode: current=${currentEp.episodeNumber}, available=${season.episodes.map { it.episodeNumber }}")

        // Encontrar episodio siguiente (número mayor)
        val candidates = season.episodes.filter { it.episodeNumber > currentEp.episodeNumber }
        Log.d(tag, "loadNextEpisode: candidates=${candidates.map { it.episodeNumber }}")

        val nextEpisode = candidates.minByOrNull { it.episodeNumber }

        if (nextEpisode != null) {
            Log.d(tag, "loadNextEpisode: loading episode ${nextEpisode.episodeNumber}")
            loadEpisode(nextEpisode)
        } else {
            Log.d(tag, "loadNextEpisode: no next episode found")
            Toast.makeText(this, "No hay episodio siguiente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadEpisode(episode: Episode) {
        val series = currentSeries ?: return

        // Guardar posición del episodio actual antes de cambiar
        saveVideoPosition()

        // Actualizar referencias
        currentEpisode = episode

        // Actualizar UI
        tvSubtitle.text = "T${episode.seasonNumber}:E${episode.episodeNumber} - ${episode.name}"

        // Recargar el player con el nuevo episodio
        val videoUrl = episode.videoUrl ?: episode.filemoonUrl ?: episode.easyvidplayerUrl ?: episode.luluvdoUrl
        if (!videoUrl.isNullOrEmpty()) {
            // Limpiar player actual
            exoPlayer?.release()
            exoPlayer = null

            // Al cambiar de episodio, resetear la posición del intent
            resumePositionFromIntent = 0L

            // Reinicializar con nuevo episodio
            lifecycleScope.launch {
                try {
                    initializePlayer(series, episode)
                    Toast.makeText(this@PlayerActivity, "Reproduciendo: ${episode.name}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(tag, "Error loading episode: ${e.message}", e)
                    Toast.makeText(this@PlayerActivity, "Error al cargar episodio", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "URL de video no disponible", Toast.LENGTH_SHORT).show()
        }

        // Actualizar estado de los botones
        setupEpisodeNavigation()
    }

    private fun showQualitySelection() {
        try {
            // Si hay calidades manuales (MP4 directos de Ok.ru, etc.), usar esas
            if (isManualQuality && manualQualities.size > 1) {
                showManualQualitySelection()
                return
            }
            
            // De lo contrario, usar tracks de ExoPlayer (HLS/DASH)
            exoPlayer?.let { player ->
                val trackSelector = player.currentTracks
                val videoGroups = trackSelector.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                
                if (videoGroups.isEmpty()) {
                    Toast.makeText(this, "No hay opciones de calidad disponibles", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // Collect all video tracks from all groups
                val tempTracks = mutableListOf<Triple<String, Triple<Int, Int, Int>, Int>>() // label, trackInfo, maxDim
                
                // Contar todos los tracks disponibles (incluso en grupos diferentes)
                val totalTracks = videoGroups.sumOf { it.length }
                val hasMultipleQualities = totalTracks > 1
                
                Log.d(tag, "Quality selection - Groups: ${videoGroups.size}, Total tracks: $totalTracks, hasMultiple: $hasMultipleQualities")
                
                // Mostrar mensaje solo si realmente hay una sola calidad
                if (!hasMultipleQualities) {
                    Toast.makeText(this, "Este video solo tiene una calidad disponible", Toast.LENGTH_SHORT).show()
                    return
                }
                
                videoGroups.forEachIndexed { groupIndex, group ->
                    for (trackIndex in 0 until group.length) {
                        val format = group.getTrackFormat(trackIndex)
                        val width = format.width
                        val height = format.height
                        val bitrate = format.bitrate
                        
                        if (height <= 0) continue
                        
                        val maxDim = maxOf(width, height)
                        val finalLabel = when {
                            maxDim >= 3840 -> "4K"
                            maxDim >= 2560 -> "2K"
                            maxDim >= 1920 -> "1080p"
                            maxDim >= 1280 -> "720p"
                            maxDim >= 854 -> "480p"
                            maxDim >= 640 -> "360p"
                            maxDim >= 426 -> "240p"
                            maxDim >= 256 -> "144p"
                            height >= 2160 -> "4K"
                            height >= 1440 -> "2K"
                            height >= 1080 -> "1080p"
                            height >= 720 -> "720p"
                            height >= 480 -> "480p"
                            height >= 360 -> "360p"
                            height >= 240 -> "240p"
                            height >= 144 -> "144p"
                            else -> "144p"
                        }
                        tempTracks.add(Triple(finalLabel, Triple(groupIndex, trackIndex, bitrate), maxDim))
                    }
                }
                
                // Sort tracks: best quality (highest maxDim, then highest bitrate) first
                tempTracks.sortWith(compareByDescending<Triple<String, Triple<Int, Int, Int>, Int>> { it.third }
                    .thenByDescending { it.second.third })
                
                val qualityTracks = tempTracks.map { it.first }
                val trackIndices = tempTracks.map { it.second }
                
                // Añadir opción "Auto (Recomendado)" al inicio de las opciones
                val displayOptions = mutableListOf<String>()
                displayOptions.add("Auto (Recomendado)")
                displayOptions.addAll(qualityTracks)
                
                // Determinar qué opción está actualmente seleccionada
                val currentSelectedIndex = if (explicitVideoWidth <= 0) {
                    0 // Auto
                } else {
                    val foundIdx = trackIndices.indexOfFirst { (groupIdx, trackIdx, _) ->
                        val fmt = videoGroups[groupIdx].getTrackFormat(trackIdx)
                        (fmt.width == explicitVideoWidth && fmt.height == explicitVideoHeight) ||
                        (maxOf(fmt.width, fmt.height) == maxOf(explicitVideoWidth, explicitVideoHeight))
                    }
                    if (foundIdx >= 0) foundIdx + 1 else 0
                }
                
                val adapter = android.widget.ArrayAdapter(this, com.anfhly.streaming.R.layout.item_sidebar_choice, android.R.id.text1, displayOptions)
                AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                    .setTitle("Seleccionar Calidad")
                    .setSingleChoiceItems(adapter, currentSelectedIndex) { dialog, which ->
                        dialog.dismiss()
                        
                        // Restablecer zoom visual
                        playerView?.videoSurfaceView?.apply {
                            scaleX = 1f
                            scaleY = 1f
                        }
                        playerView?.resizeMode = currentResizeMode
                        
                        if (which == 0) {
                            // Opción: "Auto (Recomendado)"
                            explicitVideoWidth = -1
                            explicitVideoHeight = -1
                            explicitVideoBitrate = -1
                            skipAutoQualitySelection = false
                            preferredQuality = "auto"
                            
                            val adaptiveMaxSize = deviceCapabilities.getAdaptiveMaxVideoSize()
                            val paramsBuilder = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .clearVideoSizeConstraints()
                            if (adaptiveMaxSize != null) {
                                paramsBuilder.setMaxVideoSize(adaptiveMaxSize.first, adaptiveMaxSize.second)
                            }
                            player.trackSelectionParameters = paramsBuilder.build()
                            Toast.makeText(this, "Calidad: Auto (Adaptativa)", Toast.LENGTH_SHORT).show()
                            Log.d(tag, "Calidad cambiada a Auto (Adaptativa)")
                            return@setSingleChoiceItems
                        }
                        
                        // Opción de calidad específica
                        val trackPos = which - 1
                        val (groupIndex, trackIndex, _) = trackIndices[trackPos]
                        val format = videoGroups[groupIndex].getTrackFormat(trackIndex)
                        val selectedQuality = qualityTracks[trackPos]

                        // Protección adaptativa de hardware/RAM:
                        // Si el dispositivo no puede decodificar 4K/2K nativo (ej. <= 1.5GB RAM o sin VPU 4K),
                        // seleccionar la mejor pista alternativa (ej. 1080p) para garantizar reproducción fluida sin congelarse.
                        val adaptiveMaxSize = deviceCapabilities.getAdaptiveMaxVideoSize()
                        var effectiveTrackIndex = trackIndex
                        var effectiveFormat = format
                        var effectiveQuality = selectedQuality

                        if (adaptiveMaxSize != null && format.height > adaptiveMaxSize.second) {
                            val altTrack = (0 until videoGroups[groupIndex].length)
                                .map { idx -> idx to videoGroups[groupIndex].getTrackFormat(idx) }
                                .filter { (_, fmt) -> fmt.height in 1..adaptiveMaxSize.second }
                                .maxByOrNull { (_, fmt) -> fmt.height }

                            if (altTrack != null) {
                                effectiveTrackIndex = altTrack.first
                                effectiveFormat = altTrack.second
                                effectiveQuality = "${effectiveFormat.height}p"
                                Toast.makeText(
                                    this,
                                    "Dispositivo optimizado: ajustado a ${effectiveFormat.height}p para evitar lentitud y congelamiento",
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.i(tag, "Calidad $selectedQuality limitada a ${effectiveFormat.height}p por capacidades del dispositivo (techo=${adaptiveMaxSize.second}p)")
                            }
                        }
                        
                        skipAutoQualitySelection = true
                        explicitVideoWidth = effectiveFormat.width
                        explicitVideoHeight = effectiveFormat.height
                        explicitVideoBitrate = effectiveFormat.bitrate
                        
                        try {
                            val trackOverride = TrackSelectionOverride(videoGroups[groupIndex].mediaTrackGroup, listOf(effectiveTrackIndex))
                            val updatedParams = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .clearVideoSizeConstraints()
                                .setMaxVideoSize(effectiveFormat.width, effectiveFormat.height)
                                .setOverrideForType(trackOverride)
                                .build()
                            player.trackSelectionParameters = updatedParams
                            Toast.makeText(this, "Calidad: $effectiveQuality", Toast.LENGTH_SHORT).show()
                            Log.d(tag, "Calidad cambiada dinámicamente: $effectiveQuality (${effectiveFormat.width}x${effectiveFormat.height})")
                        } catch (e: Exception) {
                            Log.e(tag, "Error al aplicar cambio dinámico de calidad: ${e.message}", e)
                            Toast.makeText(this, "Error al cambiar calidad: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show().also { styleSidebarDialog(it) }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error showing quality dialog: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showManualQualitySelection() {
        if (manualQualities.isEmpty()) return

        Log.d(tag, "Showing manual quality selection with ${manualQualities.size} qualities")
        showTVSelectionDialog(
            title = "Seleccionar Calidad",
            items = manualQualities.map { it.label },
            selectedIndex = manualQualities.indexOfFirst { it.url == currentVideoUrl }
                .takeIf { it >= 0 } ?: manualQualities.indexOfFirst { it.selected }
        ) { which ->
            var targetQuality = manualQualities[which]
            val adaptiveMaxSize = deviceCapabilities.getAdaptiveMaxVideoSize()

            // Protección adaptativa de hardware/RAM para calidades manuales (MP4)
            if (adaptiveMaxSize != null) {
                val chosenHeight = when (targetQuality.label.lowercase()) {
                    "4k", "2160p" -> 2160
                    "2k", "1440p" -> 1440
                    "1080p" -> 1080
                    "720p" -> 720
                    "480p" -> 480
                    "360p" -> 360
                    else -> 0
                }
                if (chosenHeight > adaptiveMaxSize.second) {
                    val altQuality = manualQualities.filter { q ->
                        val h = when (q.label.lowercase()) {
                            "1080p" -> 1080
                            "720p" -> 720
                            "480p" -> 480
                            "360p" -> 360
                            else -> 0
                        }
                        h in 1..adaptiveMaxSize.second
                    }.maxByOrNull { q ->
                        when (q.label.lowercase()) {
                            "1080p" -> 1080
                            "720p" -> 720
                            "480p" -> 480
                            "360p" -> 360
                            else -> 0
                        }
                    }
                    if (altQuality != null) {
                        targetQuality = altQuality
                        Toast.makeText(
                            this,
                            "Dispositivo optimizado: reproduciendo a ${targetQuality.label} para evitar lentitud y congelamiento",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            if (targetQuality.url == currentVideoUrl) {
                return@showTVSelectionDialog
            }

            val currentPosition = exoPlayer?.currentPosition ?: 0
            changeQualityManual(targetQuality.url, currentPosition)

            manualQualities = manualQualities.map { q ->
                q.copy(selected = q.url == targetQuality.url)
            }
            
            // Restablecer zoom y forzar el modo de ajuste para evitar redimensionamientos extraños
            playerView?.videoSurfaceView?.apply {
                scaleX = 1f
                scaleY = 1f
            }
            playerView?.resizeMode = currentResizeMode

            Toast.makeText(this, "Calidad: ${targetQuality.label}", Toast.LENGTH_SHORT).show()
            Log.d(tag, "Calidad manual seleccionada: ${targetQuality.label}")
        }
    }
    
    private fun changeQualityManual(newUrl: String, savedPosition: Long) {
        // Liberar player actual
        exoPlayer?.clearVideoSurface()
        exoPlayer?.release()
        exoPlayer = null
        playerView?.player = null
        
        // Reset surface size and layout completely
        playerView?.videoSurfaceView?.apply {
            scaleX = 1f
            scaleY = 1f
            translationX = 0f
            translationY = 0f
        }
        playerView?.requestLayout()

        // Recrear player con nueva URL y posición inicial
        setupExoPlayer(newUrl, currentHeaders, savedPosition)
    }

    private fun showTVSelectionDialog(
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onItemSelected: (Int) -> Unit
    ) {
        val adapter = android.widget.ArrayAdapter(this, com.anfhly.streaming.R.layout.item_sidebar_choice, android.R.id.text1, items)
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(title)
            .setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
                onItemSelected(which)
                dialog.dismiss()
            }
            .show().also { styleSidebarDialog(it) }
    }

    private fun styleSidebarDialog(dialog: androidx.appcompat.app.AlertDialog) {
        dialog.window?.apply {
            setGravity(android.view.Gravity.END)
            setBackgroundDrawableResource(com.anfhly.streaming.R.drawable.bg_sidebar_dialog)
            
            // Ajustar el margen derecho a 0 y altura a MATCH_PARENT para que esté pegado a la derecha
            val layoutParams = attributes
            layoutParams.width = (260 * context.resources.displayMetrics.density).toInt()
            layoutParams.height = android.view.WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.horizontalMargin = 0f
            layoutParams.verticalMargin = 0f
            layoutParams.x = 0
            layoutParams.y = 0
            attributes = layoutParams
        }

        // Empujar los botones hacia abajo
        dialog.setOnShowListener {
            val window = dialog.window
            val parentPanel = window?.findViewById<android.view.View>(androidx.appcompat.R.id.parentPanel) as? android.widget.LinearLayout
            if (parentPanel != null) {
                // Forzar MATCH_PARENT en toda la jerarquía para que ocupe la pantalla completa
                var current: android.view.View? = parentPanel
                while (current != null) {
                    val lp = current.layoutParams
                    if (lp != null && lp.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT) {
                        lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        current.layoutParams = lp
                    }
                    if (current.parent is android.view.View) {
                        current = current.parent as android.view.View
                    } else {
                        break
                    }
                }

                // Agregar un espacio flexible antes del panel de botones para empujarlo al fondo
                val buttonPanel = window.findViewById<android.view.View>(androidx.appcompat.R.id.buttonPanel)
                if (buttonPanel != null && parentPanel.indexOfChild(buttonPanel) >= 0) {
                    val space = android.widget.Space(dialog.context)
                    space.layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                    )
                    parentPanel.addView(space, parentPanel.indexOfChild(buttonPanel))
                }

                // Estilizar los botones
                val btnPositive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                val btnNegative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                arrayOf(btnPositive, btnNegative).forEach { btn ->
                    if (btn != null) {
                        btn.setBackgroundResource(com.anfhly.streaming.R.drawable.tv_button_background)
                        btn.setTextColor(androidx.core.content.ContextCompat.getColorStateList(dialog.context, com.anfhly.streaming.R.color.tv_icon_tint))
                        btn.isAllCaps = false
                        val lp = btn.layoutParams as? android.widget.LinearLayout.LayoutParams
                        if (lp != null) {
                            lp.setMargins(16, 8, 16, 8)
                            btn.layoutParams = lp
                        }
                    }
                }
            }
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                exoPlayer?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                exoPlayer?.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                exoPlayer?.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) + 10000)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) - 10000)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    companion object {
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_EPISODE = "extra_episode"
        const val EXTRA_RESUME_POSITION = "extra_resume_position"
        const val EXTRA_VERSION = "extra_version"


        fun start(activity: Activity, content: Content, episode: Episode? = null, resumePositionMs: Long = 0L, version: String? = null) {
            val intent = Intent(activity, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CONTENT, content)
                putExtra(EXTRA_EPISODE, episode)
                putExtra(EXTRA_RESUME_POSITION, resumePositionMs)
                putExtra(EXTRA_VERSION, version)
            }
            activity.startActivity(intent)
        }
        
        fun startWithEpisode(activity: Activity, series: Content.Series, season: Season, episode: Episode, episodes: List<Episode>? = null, resumePositionMs: Long = 0L) {
            // Crear una copia de la serie con la temporada actualizada (incluyendo sus episodios si se proporcionan)
            val updatedSeason = if (episodes != null) {
                season.copy(episodes = episodes)
            } else {
                season
            }

            val updatedSeasons = series.seasons.map { s ->
                if (s.id == season.id) updatedSeason else s
            }
            val updatedSeries = series.copy(seasons = updatedSeasons)

            val intent = Intent(activity, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CONTENT, updatedSeries)
                putExtra(EXTRA_EPISODE, episode)
                putExtra(EXTRA_RESUME_POSITION, resumePositionMs)
            }
            activity.startActivity(intent)
        }
    }
    
    // FFmpeg utility methods
    private suspend fun extractVideoInfo(videoPath: String) {
        try {
            val info = ffmpegManager.extractVideoInfo(videoPath)
            info?.let {
                Log.d(tag, "Video Info - Duration: ${it.duration}, Codec: ${it.videoCodec}, Resolution: ${it.width}x${it.height}")
                Toast.makeText(this, "Duration: ${it.duration}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting video info", e)
            Toast.makeText(this, "Error extracting video info", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun createVideoThumbnail(inputPath: String, outputPath: String) {
        try {
            val success = ffmpegManager.createThumbnail(inputPath, outputPath)
            if (success) {
                Log.d(tag, "Thumbnail created successfully at $outputPath")
                Toast.makeText(this, "Thumbnail created", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to create thumbnail", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error creating thumbnail", e)
            Toast.makeText(this, "Error creating thumbnail", Toast.LENGTH_SHORT).show()
        }
    }
    
    private suspend fun convertVideoFormat(inputPath: String, outputPath: String) {
        try {
            val success = ffmpegManager.convertToSupportedFormat(inputPath, outputPath) { progress ->
                runOnUiThread {
                    Log.d(tag, "Conversion progress: $progress%")
                }
            }
            if (success) {
                Log.d(tag, "Video converted successfully")
                Toast.makeText(this, "Video converted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to convert video", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error converting video", e)
            Toast.makeText(this, "Error converting video", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkAndRefreshUrlIfNeeded() {
        currentVideoExpiresAt?.let { expiresAt ->
            val currentTime = System.currentTimeMillis()
            val timeUntilExpiry = expiresAt - currentTime
            
            // Si la URL expira en menos de 30 segundos, refrescar
            if (timeUntilExpiry < 30000 && !isRefreshingUrl) {
                Log.w(tag, "URL expiring in ${timeUntilExpiry}ms, refreshing...")
                refreshVideoUrl()
            }
        }
    }
    
    private fun refreshVideoUrl() {
        if (isRefreshingUrl) return
        
        isRefreshingUrl = true
        val savedPosition = exoPlayer?.currentPosition ?: 0L
        
        lifecycleScope.launch {
            try {
                originalVideoUrl?.let { originalUrl ->
                    Log.d(tag, "Refreshing video URL from: $originalUrl")
                    
                    // Extraer nueva URL del extractor
                    val video = Extractor.extract(originalUrl)
                    val newUrl = video.source
                    val newHeaders = video.headers ?: currentHeaders
                    val newExpiresAt = video.expiresAt
                    
                    Log.d(tag, "New URL extracted: ${newUrl.take(50)}...")
                    Log.d(tag, "New headers: ${newHeaders.keys.joinToString()}")
                    
                    // Actualizar variables
                    currentVideoUrl = newUrl
                    currentHeaders = newHeaders
                    currentVideoExpiresAt = newExpiresAt
                    currentVideoMimeType = video.type
                    
                    // Recrear player con nueva URL y posición guardada
                    runOnUiThread {
                        exoPlayer?.release()
                        exoPlayer = null
                        playerView?.player = null
                        setupExoPlayer(newUrl, newHeaders, savedPosition)
                        Toast.makeText(this@PlayerActivity, "URL actualizada", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error refreshing URL: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@PlayerActivity, "Error actualizando URL", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isRefreshingUrl = false
            }
        }
    }
    
    private fun setupVideoWithExpiration(video: com.anfhly.streaming.data.model.Video) {
        // Guardar URL original para refrescar
        originalVideoUrl = currentVideoUrl
        currentVideoExpiresAt = video.expiresAt
        
        Log.d(tag, "Video expires at: ${video.expiresAt}")
        
        // Configurar timer para verificar expiración
        if (video.expiresAt != null) {
            val checkInterval = 30000L // Verificar cada 30 segundos
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val expirationChecker = object : Runnable {
                override fun run() {
                    checkAndRefreshUrlIfNeeded()
                    handler.postDelayed(this, checkInterval)
                }
            }
            handler.postDelayed(expirationChecker, checkInterval)
        }
    }

    private fun retryWithConvertedVideo() {
        Log.d(tag, "Retrying with converted video for URL: ${currentVideoUrl.take(50)}...")
        
        lifecycleScope.launch {
            try {
                Log.d(tag, "Attempting video codec conversion using FFmpegManager")
                
                // Usar FFmpegManager para convertir la URL
                val codecFixResult = ffmpegManager.detectAndFixCodecIssues(currentVideoUrl)
                
                if (codecFixResult.needsFix) {
                    Log.d(tag, "Video codec conversion needed: ${codecFixResult.issues.joinToString()}")
                    currentFallbackUrl = codecFixResult.fixedUrl
                    
                    runOnUiThread {
                        try {
                            // Liberar player actual
                            exoPlayer?.release()
                            
                            // Crear nueva configuración optimizada con soporte Dolby
                            val renderersFactory = createDolbyRenderersFactory(createCodecSelectorWithTranslation())
                            
                            exoPlayer = ExoPlayer.Builder(this@PlayerActivity)
                                .setRenderersFactory(renderersFactory)
                                .setAudioAttributes(getDolbyAudioAttributes(), /* handleAudioFocus = */ true)
                                .build()
                            
                            // Configurar listener
                            exoPlayer?.addListener(object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    Log.e(tag, "Player error after video conversion: ${error.errorCodeName} - ${error.message}")
                                    handlePlayerError(error)
                                }
                            })
                            
                            // Reconfigurar media source con URL convertida
                            setupMediaSourceForRetry(codecFixResult.fixedUrl, currentHeaders)
                            
                            Toast.makeText(this@PlayerActivity, "Aplicando conversión de codecs de video...", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(tag, "Error retrying with converted video: ${e.message}")
                            val customError = PlaybackException(e.message ?: "Unknown error", e, PlaybackException.ERROR_CODE_UNSPECIFIED)
                            handlePlayerError(customError)
                        }
                    }
                } else {
                    Log.d(tag, "No video codec conversion needed, trying software codec fallback")
                    hasTriedVideoCodecFallback = true // Marcar como intentado
                    retryWithSoftwareCodecs()
                }
                
            } catch (e: Exception) {
                Log.e(tag, "Error in video codec conversion: ${e.message}")
                // Si la conversión falla, intentar con software codecs
                hasTriedVideoCodecFallback = true
                retryWithSoftwareCodecs()
            }
        }
    }

    // Picture-in-Picture support
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val params = PictureInPictureParams.Builder().build()
                val result = enterPictureInPictureMode(params)
                if (result) {
                    Log.d(tag, "Entered Picture-in-Picture mode")
                } else {
                    Log.w(tag, "Failed to enter Picture-in-Picture mode")
                    Toast.makeText(this, "No se pudo activar Picture-in-Picture", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error entering PiP mode: ${e.message}", e)
                Toast.makeText(this, "Error activando PiP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        this.isInPictureInPictureMode = isInPictureInPictureMode
        
        if (isInPictureInPictureMode) {
            Log.d(tag, "Entered PiP mode - hiding controller")
            playerView?.hideController()
            playerView?.useController = false
        } else {
            Log.d(tag, "Exited PiP mode - showing controller")
            playerView?.useController = true
            playerView?.showController()
        }
    }
}
