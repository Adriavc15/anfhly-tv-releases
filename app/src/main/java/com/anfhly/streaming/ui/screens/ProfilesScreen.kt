package com.anfhly.streaming.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.anfhly.streaming.R
import coil.compose.AsyncImage
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.data.model.AuthState
import com.anfhly.streaming.data.local.SettingsDataStore
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.data.repository.ProfileRepository
import com.anfhly.streaming.ui.theme.AnfhlyColors
import com.anfhly.streaming.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.delay

import com.anfhly.streaming.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProfilesScreen(
    userId: String,
    authViewModel: AuthViewModel,
    onProfileSelected: () -> Unit,
    onManageProfiles: () -> Unit,
    onNavigateBack: () -> Unit = { } // Default: cerrar app o logout
) {
    val context = LocalContext.current
    val viewModel = remember {
        ProfileViewModel(ProfileRepository(), SettingsDataStore(context))
    }
    
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusedIndex by viewModel.focusedProfileIndex.collectAsStateWithLifecycle()
    val selectedProfile by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    
    var showPinDialog by remember { mutableStateOf(false) }
    var profileRequiringPin by remember { mutableStateOf<UserProfile?>(null) }
    var hasManuallySelectedProfile by remember { mutableStateOf(false) }
    var navigationCount by remember { mutableStateOf(0) }
    
    androidx.activity.compose.BackHandler {
        navigationCount++
        android.util.Log.d("ProfilesScreen", "BackHandler triggered, navigationCount: $navigationCount")
        if (navigationCount > 1) {
            android.util.Log.d("ProfilesScreen", "Closing app via BackHandler")
            onNavigateBack()
        } else {
            android.util.Log.d("ProfilesScreen", "BackHandler ignored first back press")
        }
    }
    
    // Load profiles when screen is first shown
    LaunchedEffect(Unit) {
        android.util.Log.d("ProfilesScreen", "Screen shown, loading profiles for: $userId")
        viewModel.loadProfiles(userId)
    }
    
    // Load profiles when auth state changes
    LaunchedEffect(userId, authState) {
        if (authState is AuthState.Authenticated) {
            android.util.Log.d("ProfilesScreen", "User authenticated, loading profiles for: $userId")
            viewModel.loadProfiles(userId)
        }
    }
    
    // Handle profile selection - only navigate when manually selected
    LaunchedEffect(selectedProfile) {
        if (selectedProfile != null && hasManuallySelectedProfile) {
            onProfileSelected()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                android.util.Log.d("ProfilesScreen", "Key event: ${event.key}, type: ${event.type}")
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            viewModel.navigateToPreviousProfile()
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.navigateToNextProfile()
                            true
                        }
                        Key.DirectionDown -> {
                            // Focus on manage button
                            viewModel.setFocusedProfileIndex(profiles.size)
                            true
                        }
                        Key.DirectionUp -> {
                            if (focusedIndex >= profiles.size) {
                                viewModel.setFocusedProfileIndex(profiles.size - 1)
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            if (focusedIndex >= 0 && focusedIndex < profiles.size) {
                                val profile = profiles[focusedIndex]
                                if (profile.isLocked) {
                                    profileRequiringPin = profile
                                    showPinDialog = true
                                } else {
                                    if (selectedProfile?.id == profile.id) {
                                        onProfileSelected()
                                    } else {
                                        hasManuallySelectedProfile = true
                                        viewModel.selectProfile(profile)
                                    }
                                }
                            } else if (focusedIndex == profiles.size) {
                                onManageProfiles()
                            }
                            true
                        }
                        Key.Escape, Key.Back -> {
                            // Go back (logout o cerrar app) solo si no es la primera navegación
                            navigationCount++
                            android.util.Log.d("ProfilesScreen", "Back pressed, navigationCount: $navigationCount")
                            if (navigationCount > 1) {
                                android.util.Log.d("ProfilesScreen", "Closing app")
                                onNavigateBack()
                            } else {
                                android.util.Log.d("ProfilesScreen", "Ignoring first back press")
                            }
                            true
                        }
                        else -> false
                    }
                }
                false
            }
    ) {
        // App background image
        AppBackground()
        
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // Logo eliminado
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Title
            Text(
                text = "¿Quién está viendo?",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            when (uiState) {
                is ProfileViewModel.ProfileUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Cargando perfiles...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            // Reload button while loading
                            androidx.tv.material3.Surface(
                                onClick = { viewModel.loadProfiles(userId) },
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures {
                                        viewModel.loadProfiles(userId)
                                    }
                                },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.3f)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "↻ Recargar",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
                is ProfileViewModel.ProfileUiState.Empty -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyProfilesState(
                            onCreateProfile = {
                                viewModel.createProfile(userId, "Usuario", false, "adult1")
                            },
                            onReload = {
                                viewModel.loadProfiles(userId)
                            },
                            isFocused = focusedIndex == 0
                        )
                    }
                }
                is ProfileViewModel.ProfileUiState.Error -> {
                    val errorMessage = (uiState as ProfileViewModel.ProfileUiState.Error).message
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = errorMessage,
                                color = AnfhlyColors.error,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.tv.material3.Surface(
                                onClick = { viewModel.loadProfiles(userId) },
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures {
                                        viewModel.loadProfiles(userId)
                                    }
                                },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = AnfhlyColors.primary,
                                    focusedContainerColor = AnfhlyColors.primary
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "Reintentar",
                                    color = Color.White,
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
                is ProfileViewModel.ProfileUiState.Success -> {
                    // Profiles grid
                    Row(
                        modifier = Modifier.padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        profiles.forEachIndexed { index, profile ->
                            ProfileCard(
                                profile = profile,
                                isFocused = focusedIndex == index,
                                onFocus = { viewModel.setFocusedProfileIndex(index) },
                                onClick = {
                                    if (profile.isLocked) {
                                        profileRequiringPin = profile
                                        showPinDialog = true
                                    } else {
                                        if (selectedProfile?.id == profile.id) {
                                            onProfileSelected()
                                        } else {
                                            hasManuallySelectedProfile = true
                                            viewModel.selectProfile(profile)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    // Manage profiles button
                    ManageProfilesButton(
                        isFocused = focusedIndex >= profiles.size,
                        onFocus = { viewModel.setFocusedProfileIndex(profiles.size) },
                        onClick = onManageProfiles
                    )
                    
                    // Profile limit warning
                    if (profiles.size >= 5) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Has alcanzado el límite máximo de 5 perfiles",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
    
    // PIN Dialog
    if (showPinDialog && profileRequiringPin != null) {
        PinDialog(
            profileName = profileRequiringPin!!.name,
            onPinEntered = { pin ->
                val profile = profileRequiringPin
                if (profile != null && viewModel.verifyPin(profile, pin)) {
                    showPinDialog = false
                    profileRequiringPin = null
                    if (selectedProfile?.id == profile.id) {
                        onProfileSelected()
                    } else {
                        hasManuallySelectedProfile = true
                        viewModel.selectProfile(profile)
                    }
                } else {
                    // Wrong PIN - shake animation or error
                }
            },
            onDismiss = {
                showPinDialog = false
                profileRequiringPin = null
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val profileInteraction = remember { MutableInteractionSource() }
    val isProfileHovered by profileInteraction.collectIsHoveredAsState()
    LaunchedEffect(isProfileHovered) {
        if (isProfileHovered) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.tv.material3.Surface(
            onClick = onClick,
            modifier = Modifier
                .size(140.dp)
                .focusRequester(focusRequester)
                .hoverable(profileInteraction)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        onFocus()
                    }
                }
                .pointerInput(onClick) {
                    detectTapGestures {
                        onClick()
                    }
                },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AnfhlyColors.backgroundCard,
                focusedContainerColor = AnfhlyColors.backgroundCard
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(BorderStroke(4.dp, Color.White))
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Profile image or initial
                if (profile.customImageUrl != null) {
                    AsyncImage(
                        model = profile.customImageUrl,
                        contentDescription = profile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    val bgColor = try {
                        Color(android.graphics.Color.parseColor(profile.profileColor))
                    } catch (e: Exception) {
                        Color(0xFF3b82f6) // Default blue color
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(bgColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = profile.name.firstOrNull()?.toString() ?: "?"
                        Text(
                            text = initial,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // Lock icon
                if (profile.isLocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset((-8).dp, (-8).dp)
                            .size(40.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔒",
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = profile.name,
            fontSize = 18.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f)
        )
    }
    
    // Removed LaunchedEffect - let D-pad navigation handle focus naturally
}

@Composable
private fun ManageProfilesButton(
    isFocused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isSurfaceFocused by remember { mutableStateOf(false) }
    // Quest 3 hover-to-focus support
    val manageInteraction = remember { MutableInteractionSource() }
    val isManageHovered by manageInteraction.collectIsHoveredAsState()
    LaunchedEffect(isManageHovered) {
        if (isManageHovered) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .hoverable(manageInteraction)
            .onFocusChanged { state ->
                isSurfaceFocused = state.isFocused
                if (state.isFocused) {
                    onFocus()
                }
            }
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(BorderStroke(0.dp, Color.Transparent))
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚙",
                fontSize = 20.sp,
                color = if (isSurfaceFocused) Color.Black else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Gestionar perfiles",
                color = if (isSurfaceFocused) Color.Black else Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = if (isSurfaceFocused) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
    
    // Removed LaunchedEffect - let D-pad navigation handle focus naturally
}

@Composable
private fun EmptyProfilesState(
    onCreateProfile: () -> Unit,
    onReload: () -> Unit,
    isFocused: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = R.drawable.avatar,
                contentDescription = "Avatar",
                modifier = Modifier.size(80.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No tienes perfiles",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Crea tu primer perfil para comenzar a disfrutar del contenido",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Create profile button
        androidx.tv.material3.Surface(
            onClick = onCreateProfile,
            modifier = Modifier.pointerInput(onCreateProfile) {
                detectTapGestures {
                    onCreateProfile()
                }
            },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AnfhlyColors.primary,
                focusedContainerColor = AnfhlyColors.primary
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)))
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Crear primer perfil",
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Reload button
        androidx.tv.material3.Surface(
            onClick = onReload,
            modifier = Modifier.pointerInput(onReload) {
                detectTapGestures {
                    onReload()
                }
            },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White.copy(alpha = 0.2f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)))
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
        ) {
            Text(
                text = "↻ Recargar",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun PinDialog(
    profileName: String,
    onPinEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            color = AnfhlyColors.backgroundCard,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .width(400.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔒",
                    fontSize = 48.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Perfil bloqueado",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Ingresa el PIN para acceder al perfil de $profileName",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // PIN display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    if (index < pin.length) AnfhlyColors.primary 
                                    else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < pin.length) {
                                Text(
                                    text = "•",
                                    fontSize = 24.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(3) { col ->
                                val number = row * 3 + col + 1
                                PinKey(
                                    number = number.toString(),
                                    onClick = {
                                        if (pin.length < 4) {
                                            pin += number
                                            if (pin.length == 4) {
                                                onPinEntered(pin)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PinKey("", onClick = {}) // Empty
                        PinKey("0", onClick = {
                            if (pin.length < 4) {
                                pin += "0"
                                if (pin.length == 4) {
                                    onPinEntered(pin)
                                }
                            }
                        })
                        PinKey("⌫", onClick = {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                        })
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                androidx.compose.material3.Surface(
                    onClick = onDismiss,
                    color = Color.Transparent
                ) {
                    Text(
                        text = "Cancelar",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    number: String,
    onClick: () -> Unit
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .size(60.dp)
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = AnfhlyColors.primary
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 24.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
