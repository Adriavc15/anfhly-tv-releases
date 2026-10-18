package com.anfhly.streaming

import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.os.Build
import android.os.Process
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import com.anfhly.streaming.utils.QuestInputHelper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import com.anfhly.streaming.data.local.SettingsDataStore
import com.anfhly.streaming.data.model.AuthState
import com.anfhly.streaming.data.model.Content
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.data.repository.AuthRepository
import com.anfhly.streaming.data.repository.FavoritesRepository
import com.anfhly.streaming.data.repository.ProfileRepository
import com.anfhly.streaming.ui.screens.ActorFilmographyScreen
import com.anfhly.streaming.ui.screens.AvatarSelectionScreen
import com.anfhly.streaming.ui.screens.CastScreen
import com.anfhly.streaming.ui.screens.ConfigScreen
import com.anfhly.streaming.ui.screens.ContentDetailScreen
import com.anfhly.streaming.ui.screens.EditProfileScreen
import com.anfhly.streaming.ui.screens.HomeScreen
import com.anfhly.streaming.ui.screens.LoginScreen
import com.anfhly.streaming.ui.screens.ManageProfilesScreen
import com.anfhly.streaming.ui.screens.PlatformMoviesScreen
import com.anfhly.streaming.ui.screens.ProfilesScreen
import com.anfhly.streaming.ui.screens.RegisterScreen
import com.anfhly.streaming.ui.screens.SearchScreen
import com.anfhly.streaming.ui.screens.SeasonsScreen
// import com.anfhly.streaming.ui.screens.SettingsScreen // TODO: Create SettingsScreen
import com.anfhly.streaming.ui.theme.AnfhlyColors
import com.anfhly.streaming.ui.viewmodel.AuthViewModel
import com.anfhly.streaming.ui.viewmodel.HomeViewModel
import com.anfhly.streaming.ui.viewmodel.ProfileViewModel
import com.anfhly.streaming.data.repository.ContentRepository
import com.anfhly.streaming.updater.UpdateManager
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.utils.ExceptionHandler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    
    private val auth = Firebase.auth
    private val RC_SIGN_IN = 9001
    private val searchIntentQuery = mutableStateOf<String?>(null)
    private val globalSearchContentToOpen = mutableStateOf<Pair<String, String>?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Guardar referencia al Activity para usar en callbacks
        val activity = this
        
        // Install global exception handler to prevent crashes
        ExceptionHandler.install()
        
        handleIncomingIntent(intent)
        
        // Update Android TV Home Screen recommendation channel
        lifecycleScope.launch {
            try {
                com.anfhly.streaming.recommendations.TvRecommendationsManager.updateRecommendations(this@MainActivity)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating recommendations", e)
            }
        }

        // Consultar actualizaciones en GitHub en segundo plano
        lifecycleScope.launch {
            try {
                val updateManager = UpdateManager(this@MainActivity)
                val currentVersionCode = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0).versionCode
                    }
                } catch (e: Exception) { 1 }

                val currentVersionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
                } catch (e: Exception) { "1.0.0" }

                val updateInfo = updateManager.checkForUpdate(currentVersionCode, currentVersionName)
                if (updateInfo != null) {
                    updateManager.showUpdateDialog(
                        updateInfo = updateInfo,
                        onUpdate = {
                            updateManager.downloadAndInstall(updateInfo, lifecycleScope)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error comprobando actualizaciones en GitHub: ${e.message}")
            }
        }
        
        setContent {
            AnfhlyApp(
                onGoogleSignIn = { handleGoogleSignIn() },
                searchIntentQuery = searchIntentQuery,
                globalSearchContentToOpen = globalSearchContentToOpen
            )
        }
    }
    
    /**
     * Intercepta eventos genéricos de motion (joystick/gamepad) para soporte de Meta Quest 3.
     * Traduce los ejes del thumbstick del mando Quest a eventos de D-pad.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val translatedEvent = QuestInputHelper.processGenericMotionEvent(event)
        if (translatedEvent != null) {
            Log.d("MainActivity", "Quest joystick -> D-pad: keyCode=${translatedEvent.keyCode}")
            // Despachar el evento de D-pad traducido
            dispatchKeyEvent(translatedEvent)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
    
    /**
     * Intercepta eventos de tecla/botón para soporte de Meta Quest 3.
     * Traduce los botones del mando Quest (A, B, X, Y) a sus equivalentes de Android TV.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        try {
            if (QuestInputHelper.isGamepadButton(event)) {
                val translatedEvent = QuestInputHelper.translateButtonEvent(event)
                if (translatedEvent != null) {
                    Log.d("MainActivity", "Quest button ${event.keyCode} -> ${translatedEvent.keyCode}")
                    return super.dispatchKeyEvent(translatedEvent)
                }
            }
            return super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            // Compose focus engine can crash with "visitAncestors called on an unattached node"
            // when navigating via D-pad on recycled lazy-list items. Swallow and drop the event.
            Log.w("MainActivity", "Focus navigation error caught in dispatchKeyEvent, ignoring", e)
            return false
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }
    
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        Log.d("MainActivity", "handleIncomingIntent: action=${intent.action}, data=${intent.data}")
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(android.app.SearchManager.QUERY)
            if (!query.isNullOrEmpty()) {
                searchIntentQuery.value = query
            }
        } else if (Intent.ACTION_VIEW == intent.action) {
            val uri = intent.data
            if (uri != null && uri.scheme == "anfhly") {
                val pathSegments = uri.pathSegments
                if (pathSegments.size >= 2) {
                    val contentType = pathSegments[0]
                    val contentId = pathSegments[1]
                    globalSearchContentToOpen.value = Pair(contentType, contentId)
                }
            }
        }
    }
    
    private fun handleGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.anfhly.streaming.R.string.default_web_client_id))
            .requestEmail()
            .build()
            
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("MainActivity", "Google sign in successful: ${account.email}")
                
                // Autenticar con Firebase
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            Log.d("MainActivity", "Firebase auth successful")
                            // La navegación se manejará automáticamente a través de AuthState
                        } else {
                            Log.w("MainActivity", "Firebase auth failed", authTask.exception)
                        }
                    }
            } catch (e: ApiException) {
                Log.w("MainActivity", "Google sign in failed", e)
            }
        }
    }
}

@Composable
fun AnfhlyApp(
    onGoogleSignIn: () -> Unit = {},
    searchIntentQuery: MutableState<String?> = remember { mutableStateOf(null) },
    globalSearchContentToOpen: MutableState<Pair<String, String>?> = remember { mutableStateOf(null) }
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val authRepository = remember { AuthRepository() }
    val authViewModel = remember { AuthViewModel(authRepository, settingsDataStore) }
    
    val authState by authViewModel.authState.collectAsState()
    
    // Create ProfileViewModel to share between ManageProfiles and EditProfile
    val profileViewModel = remember {
        ProfileViewModel(ProfileRepository(), settingsDataStore)
    }
    
    // Create HomeViewModel for content management
    val homeViewModel = remember {
        HomeViewModel(ContentRepository())
    }
    
        
    // Navigation state
    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
    var selectedContent by remember { mutableStateOf<Content?>(null) }
    var selectedSeries by remember { mutableStateOf<Content.Series?>(null) }
    var castScreenContent by remember { mutableStateOf<Content?>(null) }
    var actorFilmographyActorName by remember { mutableStateOf<String?>(null) }
    var selectedPlatformId by remember { mutableStateOf<String?>(null) }
    var profileToEdit by remember { mutableStateOf<UserProfile?>(null) }
    var tempSelectedAvatar by remember { mutableStateOf<String?>(null) }
    
    val voiceQuery by searchIntentQuery
    LaunchedEffect(voiceQuery) {
        if (!voiceQuery.isNullOrBlank()) {
            homeViewModel.search(voiceQuery!!)
            currentScreen = Screen.SEARCH
            searchIntentQuery.value = null
        }
    }
    
    val contentToOpen by globalSearchContentToOpen
    LaunchedEffect(contentToOpen) {
        contentToOpen?.let { (type, id) ->
            val repository = ContentRepository()
            val result = if (type == "movie") {
                repository.getMovieById(id)
            } else {
                repository.getSeriesById(id)
            }
            
            if (result is com.anfhly.streaming.data.model.Result.Success) {
                selectedContent = result.data
                currentScreen = Screen.CONTENT_DETAIL
            }
            globalSearchContentToOpen.value = null
        }
    }
    
    // Estado para manejar doble retroceso
    var backPressedOnce by remember { mutableStateOf(false) }
    var backPressTime by remember { mutableStateOf(0L) }
    
    // Manejar retroceso a nivel de Activity
    val activityContext = LocalContext.current
    val activity = activityContext as ComponentActivity
    DisposableEffect(Unit) {
        val callback = activity.onBackPressedDispatcher.addCallback {
            android.util.Log.d("MainActivity", "Back pressed at Activity level, currentScreen: $currentScreen")
            when (currentScreen) {
                Screen.PROFILES -> {
                    // Solo cerrar si no viene de ManageProfiles - Forced stop
                    activity.finishAndRemoveTask()
                    System.exit(0)
                    Process.killProcess(Process.myPid())
                }
                Screen.HOME, Screen.SEARCH, Screen.CONFIG -> {
                    // Implementar doble retroceso para cerrar app
                    val currentTime = System.currentTimeMillis()
                    if (backPressedOnce && currentTime - backPressTime < 2000) {
                        // Segundo retroceso dentro de 2 segundos - cerrar app - Forced stop
                        activity.finishAndRemoveTask()
                        System.exit(0)
                        Process.killProcess(Process.myPid())
                    } else {
                        // Primer retroceso - mostrar mensaje y preparar para segundo
                        backPressedOnce = true
                        backPressTime = currentTime
                        Toast.makeText(activityContext, "Presiona nuevamente para salir", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    // Para otras pantallas, dejar que el manejo normal funcione
                    if (isEnabled) {
                        isEnabled = false
                        // No hacer nada, dejar que Compose maneje
                    }
                }
            }
        }
        onDispose {
            callback.remove()
        }
    }
    
    // Check auth state and navigate accordingly
    LaunchedEffect(authState) {
        currentScreen = when (authState) {
            is AuthState.Authenticated -> {
                // Only auto-redirect on initial auth, not when manually navigating
                when (currentScreen) {
                    Screen.SPLASH, Screen.LOGIN, Screen.REGISTER -> {
                        val profile = settingsDataStore.selectedProfile.first()
                        if (profile != null) Screen.HOME else Screen.PROFILES
                    }
                    else -> currentScreen // Keep current screen (e.g., PROFILES when navigating manually)
                }
            }
            is AuthState.Unauthenticated -> {
                when (currentScreen) {
                    Screen.SPLASH -> Screen.LOGIN
                    else -> Screen.LOGIN
                }
            }
            else -> currentScreen
        }
    }
    
    // Initial navigation check
    LaunchedEffect(Unit) {
        // Wait for AnimatedLogo animation to finish completely before loading data
        kotlinx.coroutines.delay(2800)

        // Start loading data now that the animation is done
        homeViewModel.loadContent()

        // Check current auth state
        val user = authRepository.currentUser
        if (user != null) {
            // User is logged in, check for selected profile
            val profile = settingsDataStore.selectedProfile.first()
            currentScreen = if (profile != null) Screen.HOME else Screen.PROFILES
        } else {
            currentScreen = Screen.LOGIN
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.SPLASH -> SplashScreen()
            Screen.LOGIN -> LoginScreen(
                onNavigateToProfiles = { currentScreen = Screen.PROFILES },
                onNavigateToRegister = { currentScreen = Screen.REGISTER },
                onGoogleSignInClick = onGoogleSignIn
            )
            Screen.PROFILES -> {
                val user = (authState as? AuthState.Authenticated)?.user ?: authRepository.currentUser
                if (user != null) {
                    ProfilesScreen(
                        userId = user.uid,
                        authViewModel = authViewModel,
                        onProfileSelected = {
                            // El perfil ya se guardó en SettingsDataStore
                            // Ahora navegamos al HOME
                            currentScreen = Screen.HOME
                        },
                        onManageProfiles = { currentScreen = Screen.MANAGE_PROFILES },
                        onNavigateBack = {
                            // Cerrar la app
                            android.util.Log.d("MainActivity", "ProfilesScreen onNavigateBack called - closing app")
                            exitProcess(0)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            Screen.HOME -> {
                val profile by settingsDataStore.selectedProfile.collectAsState(initial = null)
                
                // Limpiar estado cuando volvemos al HOME (después del reproductor)
                LaunchedEffect(Unit) {
                    Log.d("MainActivity", "HOME screen - clearing navigation state")
                    selectedContent = null
                    selectedSeries = null
                    castScreenContent = null
                    actorFilmographyActorName = null
                }
                
                // Show HomeScreen regardless - it will display profile when loaded
                HomeScreen(
                    viewModel = homeViewModel,
                    selectedProfile = profile,
                    onContentSelected = { content ->
                        Log.d("MainActivity", "Content selected: ${content.title} (${content::class.simpleName})")
                        Log.d("MainActivity", "Previous selectedContent: ${selectedContent?.title}")
                        
                        // Limpiar estado anterior para evitar conflicto
                        selectedSeries = null
                        
                        selectedContent = content
                        currentScreen = Screen.CONTENT_DETAIL
                        
                        Log.d("MainActivity", "New selectedContent: ${selectedContent?.title}")
                    },
                    onNavigateToProfiles = { currentScreen = Screen.PROFILES },
                    onNavigateToSearch = {
                        homeViewModel.clearSearch()
                        currentScreen = Screen.SEARCH
                    },
                    onNavigateToConfig = { currentScreen = Screen.CONFIG },
                    onPlayContent = { contentToPlay, version ->
                        if (contentToPlay is Content.Movie) {
                            PlayerActivity.start(context as MainActivity, contentToPlay, null, 0L, version)
                        } else {
                            // Para series, ir a detalle para cargar episodios
                            selectedSeries = null
                            selectedContent = contentToPlay
                            currentScreen = Screen.CONTENT_DETAIL
                        }
                    },
                    onNavigateToCast = { contentForCast ->
                        castScreenContent = contentForCast
                        currentScreen = Screen.CAST
                    },
                    onNavigateToPlatform = { platformId ->
                        selectedPlatformId = platformId
                        currentScreen = Screen.PLATFORM_MOVIES
                    }
                )
            }
            Screen.CONTENT_DETAIL -> {
                selectedContent?.let { content ->
                    // Key para forzar recreación cuando el contenido cambia
                    key(content.id + content::class.simpleName) {
                        ContentDetailScreen(
                            content = content,
                            similarContent = emptyList(),
                            onNavigateBack = { currentScreen = Screen.HOME },
                            onPlayContent = { contentToPlay, resumePositionMs, version ->
                                if (contentToPlay is Content.Movie) {
                                    PlayerActivity.start(context as MainActivity, contentToPlay, null, resumePositionMs, version)
                                }
                            },
                            onNavigateToSeasons = { series ->
                                selectedSeries = series
                                currentScreen = Screen.SEASONS
                            },
                            onContinueSeries = { series, seasonNumber, episodeNumber, resumePositionMs ->
                                val season = series.seasons.find { it.seasonNumber == seasonNumber }
                                val episode = season?.episodes?.find { it.episodeNumber == episodeNumber }
                                if (season != null && episode != null) {
                                    PlayerActivity.startWithEpisode(
                                        activity = context as MainActivity,
                                        series = series,
                                        season = season,
                                        episode = episode,
                                        episodes = season.episodes,
                                        resumePositionMs = resumePositionMs
                                    )
                                }
                            },
                            onSimilarContentSelected = { similar ->
                                selectedContent = similar
                            },
                            onRandomContentSelected = { randomContent ->
                                selectedContent = randomContent
                            },
                            onNavigateToCast = { contentForCast ->
                                castScreenContent = contentForCast
                                currentScreen = Screen.CAST
                            },
                            onViewTrailer = { trailerUrl ->
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(trailerUrl))
                                context.startActivity(intent)
                            },
                            onPlayEpisode = { series, season, episode, episodes, resumePositionMs ->
                                PlayerActivity.startWithEpisode(
                                    activity = context as MainActivity,
                                    series = series,
                                    season = season,
                                    episode = episode,
                                    episodes = episodes,
                                    resumePositionMs = resumePositionMs
                                )
                            }
                        )
                    } // Cierre del key
                } ?: run {
                    currentScreen = Screen.HOME
                }
            }
            Screen.CAST -> {
                castScreenContent?.let { c ->
                    val members = when (c) {
                        is Content.Movie -> c.castMembers
                        is Content.Series -> c.castMembers
                    }
                    CastScreen(
                        contentTitle = c.title,
                        castMembers = members,
                        onNavigateBack = {
                            castScreenContent = null
                            currentScreen = Screen.CONTENT_DETAIL
                        },
                        onActorSelected = { member ->
                            val n = member.name.trim()
                            if (n.isNotEmpty()) {
                                actorFilmographyActorName = n
                                currentScreen = Screen.ACTOR_FILMOGRAPHY
                            }
                        }
                    )
                } ?: run {
                    currentScreen = Screen.HOME
                }
            }
            Screen.ACTOR_FILMOGRAPHY -> {
                actorFilmographyActorName?.let { actorName ->
                    val bgUrl = castScreenContent?.backdropUrl
                    ActorFilmographyScreen(
                        actorName = actorName,
                        backgroundUrl = bgUrl,
                        onNavigateBack = {
                            actorFilmographyActorName = null
                            currentScreen = Screen.CAST
                        },
                        onContentSelected = { content ->
                            selectedSeries = null
                            selectedContent = content
                            actorFilmographyActorName = null
                            castScreenContent = null
                            currentScreen = Screen.CONTENT_DETAIL
                        }
                    )
                } ?: run {
                    currentScreen = Screen.HOME
                }
            }
            Screen.SEASONS -> {
                selectedSeries?.let { series ->
                    SeasonsScreen(
                        series = series,
                        onNavigateBack = { currentScreen = Screen.CONTENT_DETAIL },
                        onPlayEpisode = { series, season, episode, episodes ->
                            PlayerActivity.startWithEpisode(context as MainActivity, series, season, episode, episodes)
                        }
                    )
                } ?: run {
                    currentScreen = Screen.HOME
                }
            }
            Screen.REGISTER -> RegisterScreen(
                onNavigateToLogin = { currentScreen = Screen.LOGIN },
                onRegisterSuccess = { currentScreen = Screen.PROFILES },
                viewModel = authViewModel,
                onGoogleSignInClick = onGoogleSignIn
            )
            Screen.MANAGE_PROFILES -> {
                val user = (authState as? AuthState.Authenticated)?.user
                if (user != null) {
                    ManageProfilesScreen(
                        userId = user.uid,
                        viewModel = profileViewModel,
                        onNavigateBack = { currentScreen = Screen.PROFILES },
                        onProfileSelected = { profile ->
                            // Save selected profile and go to home
                            kotlinx.coroutines.runBlocking {
                                settingsDataStore.saveSelectedProfile(profile)
                            }
                            currentScreen = Screen.HOME
                        },
                        onEditProfile = { profile ->
                            profileToEdit = profile
                            currentScreen = Screen.EDIT_PROFILE
                        }
                    )
                } else {
                    currentScreen = Screen.LOGIN
                }
            }
            Screen.EDIT_PROFILE -> {
                profileToEdit?.let { profile ->
                    EditProfileScreen(
                        profile = profile,
                        viewModel = profileViewModel,
                        onNavigateBack = {
                            profileToEdit = null
                            currentScreen = Screen.MANAGE_PROFILES
                        },
                        onProfileUpdated = {
                            profileToEdit = null
                            currentScreen = Screen.MANAGE_PROFILES
                        },
                        onSelectAvatar = {
                            tempSelectedAvatar = profile.customImageUrl ?: profile.avatar
                            currentScreen = Screen.AVATAR_SELECTION
                        }
                    )
                } ?: run {
                    currentScreen = Screen.MANAGE_PROFILES
                }
            }
            Screen.AVATAR_SELECTION -> {
                val currentAvatar = tempSelectedAvatar ?: profileToEdit?.customImageUrl ?: profileToEdit?.avatar ?: ""
                AvatarSelectionScreen(
                    currentAvatarUrl = currentAvatar,
                    onAvatarSelected = { avatarUrl ->
                        // Update the profile with new avatar
                        profileToEdit?.let { profile ->
                            profileToEdit = profile.copy(
                                customImageUrl = avatarUrl,
                                avatar = "custom"
                            )
                        }
                        tempSelectedAvatar = null
                        currentScreen = Screen.EDIT_PROFILE
                    },
                    onNavigateBack = {
                        tempSelectedAvatar = null
                        currentScreen = Screen.MANAGE_PROFILES
                    }
                )
            }
            Screen.SEARCH -> {
                val searchQuery by homeViewModel.searchQuery.collectAsState()
                val searchResults by homeViewModel.searchResults.collectAsState()
                
                SearchScreen(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    onSearchQueryChange = { query ->
                        homeViewModel.search(query)
                    },
                    onContentSelected = { content ->
                        selectedContent = content
                        currentScreen = Screen.CONTENT_DETAIL
                    },
                    onNavigateBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.CONFIG -> {
                ConfigScreen(
                    onBack = { currentScreen = Screen.HOME }
                )
            }
            Screen.PLATFORM_MOVIES -> {
                val movies by homeViewModel.movies.collectAsState()
                val series by homeViewModel.series.collectAsState()
                PlatformMoviesScreen(
                    platformId = selectedPlatformId ?: "netflix",
                    allMovies = movies,
                    allSeries = series,
                    onNavigateBack = { currentScreen = Screen.HOME },
                    onContentSelected = { content ->
                        if (content is Content.Series) {
                            selectedSeries = content
                            selectedContent = content
                        } else {
                            selectedSeries = null
                            selectedContent = content
                        }
                        currentScreen = Screen.CONTENT_DETAIL
                    }
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppBackground()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            com.anfhly.streaming.ui.components.AnimatedLogo(
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Efecto CRT superpuesto
        com.anfhly.streaming.ui.components.CrtEffectOverlay()
    }
}

private enum class Screen {
    SPLASH,
    LOGIN,
    REGISTER,
    PROFILES,
    MANAGE_PROFILES,
    EDIT_PROFILE,
    AVATAR_SELECTION,
    HOME,
    SEARCH,
    CONFIG,
    CONTENT_DETAIL,
    CAST,
    ACTOR_FILMOGRAPHY,
    SEASONS,
    PLATFORM_MOVIES
}
