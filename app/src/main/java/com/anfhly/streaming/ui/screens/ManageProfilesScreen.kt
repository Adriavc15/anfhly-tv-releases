package com.anfhly.streaming.ui.screens

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.anfhly.streaming.R
import com.anfhly.streaming.data.local.SettingsDataStore
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.data.repository.ProfileRepository
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.ui.theme.AnfhlyColors
import com.anfhly.streaming.ui.viewmodel.ProfileViewModel
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageProfilesScreen(
    userId: String,
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onProfileSelected: (UserProfile) -> Unit,
    onEditProfile: (UserProfile) -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusedIndex by viewModel.focusedProfileIndex.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateAvatarSelection by remember { mutableStateOf(false) }
    var creatingProfileName by remember { mutableStateOf("") }
    var creatingProfileAvatar by remember { mutableStateOf("https://preview.redd.it/the-boys-soldier-boy-build-v0-184k9gzbleb91.jpg?width=640&crop=smart&auto=webp&s=2620fe50c1b516db0aa2dae38b6b6bf6186cf797") }
    
    LaunchedEffect(Unit) {
        viewModel.loadProfiles(userId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (focusedIndex > 0) {
                                viewModel.setFocusedProfileIndex(focusedIndex - 1)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            val maxIndex = profiles.size + 1 // +1 for "Add" button
                            if (focusedIndex < maxIndex) {
                                viewModel.setFocusedProfileIndex(focusedIndex + 1)
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (focusedIndex >= 0 && focusedIndex < profiles.size) {
                                // Navigate to edit profile screen
                                onEditProfile(profiles[focusedIndex])
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            // No hay botón volver, mantener foco en perfiles
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            when {
                                focusedIndex >= 0 && focusedIndex < profiles.size -> {
                                    onProfileSelected(profiles[focusedIndex])
                                }
                                focusedIndex == profiles.size -> {
                                    showCreateDialog = true
                                }
                            }
                            true
                        }
                        Key.Escape, Key.Back -> {
                            onNavigateBack()
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
        
        // Header - solo título, sin botón Volver (se usa tecla retroceder del control)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "GESTIONAR PERFILES", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "Selecciona, edita o elimina perfiles",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Text(text = "ANFHLY", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 3.sp)
        }
        
        // Content
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(80.dp))
                
                Text(
                    text = "Selecciona un perfil",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 48.dp)
                )
                
                when (uiState) {
                    is ProfileViewModel.ProfileUiState.Loading -> {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                    }
                    is ProfileViewModel.ProfileUiState.Empty -> {
                        Text(
                            text = "No hay perfiles",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp
                        )
                    }
                    is ProfileViewModel.ProfileUiState.Error -> {
                        val errorMessage = (uiState as ProfileViewModel.ProfileUiState.Error).message
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error: $errorMessage",
                                color = Color(0xFFFF4444),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                            Surface(
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
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                            ) {
                                Text(
                                    text = "Reintentar",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        // Profiles grid with add button
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Existing profiles
                            profiles.forEachIndexed { index, profile ->
                                ManageProfileCard(
                                    profile = profile,
                                    isFocused = focusedIndex == index,
                                    onFocus = { viewModel.setFocusedProfileIndex(index) },
                                    onClick = { onEditProfile(profile) }
                                )
                            }
                            
                            // Add profile button
                            if (profiles.size < 5) {
                                AddProfileCard(
                                    isFocused = focusedIndex == profiles.size,
                                    onFocus = { viewModel.setFocusedProfileIndex(profiles.size) },
                                    onClick = { showCreateDialog = true }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
    
    // Create Profile Dialog
    if (showCreateDialog) {
        CreateProfileDialog(
            onCreate = { name, initialAvatar ->
                // Guardar el nombre y mostrar AvatarSelectionScreen
                creatingProfileName = name
                creatingProfileAvatar = initialAvatar
                showCreateDialog = false
                showCreateAvatarSelection = true
            },
            onDismiss = { showCreateDialog = false }
        )
    }
    
    // Avatar Selection for Create Profile
    if (showCreateAvatarSelection) {
        AvatarSelectionScreen(
            currentAvatarUrl = creatingProfileAvatar,
            onAvatarSelected = { avatarUrl ->
                creatingProfileAvatar = avatarUrl
            },
            onNavigateBack = {
                // Guardar el perfil cuando se presiona Back/Escape
                println("DEBUG: onNavigateBack llamado - guardando perfil...")
                if (creatingProfileName.isNotBlank()) {
                    viewModel.createProfile(userId, creatingProfileName, false, "custom", creatingProfileAvatar)
                    println("DEBUG: Perfil guardado: $creatingProfileName")
                }
                showCreateAvatarSelection = false
                creatingProfileName = ""
                // Forzar recarga de perfiles para que aparezca inmediatamente
                viewModel.loadProfiles(userId)
                // Volver a ProfilesScreen después de guardar
                println("DEBUG: Llamando onNavigateBack para volver a ProfilesScreen")
                onNavigateBack()
            },
            onSaveProfile = {
                // Guardar el perfil cuando se presiona Enter/Confirmar
                println("DEBUG: onSaveProfile llamado - guardando perfil...")
                if (creatingProfileName.isNotBlank()) {
                    viewModel.createProfile(userId, creatingProfileName, false, "custom", creatingProfileAvatar)
                    println("DEBUG: Perfil guardado: $creatingProfileName")
                }
                showCreateAvatarSelection = false
                creatingProfileName = ""
                // Forzar recarga de perfiles para que aparezca inmediatamente
                viewModel.loadProfiles(userId)
                // Volver a ProfilesScreen después de guardar
                println("DEBUG: Llamando onNavigateBack para volver a ProfilesScreen")
                onNavigateBack()
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ManageProfileCard(
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
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(120.dp)
                .focusRequester(focusRequester)
                .hoverable(profileInteraction)
                .onFocusChanged { state ->
                    if (state.isFocused) onFocus()
                }
                .pointerInput(onClick) {
                    detectTapGestures {
                        onClick()
                    }
                },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White))
            ),
            shape = ClickableSurfaceDefaults.shape(CircleShape)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Avatar
                val imageUrl = profile.customImageUrl ?: profile.avatar
                AsyncImage(
                    model = imageUrl,
                    contentDescription = profile.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                
                // Lock icon
                if (profile.isLocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset((-8).dp, (-8).dp)
                            .size(32.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔒", fontSize = 16.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = profile.name,
            fontSize = 18.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
    }
    
    LaunchedEffect(isFocused) {
        if (isFocused) focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AddProfileCard(
    isFocused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val addInteraction = remember { MutableInteractionSource() }
    val isAddHovered by addInteraction.collectIsHoveredAsState()
    LaunchedEffect(isAddHovered) {
        if (isAddHovered) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .hoverable(addInteraction)
                    .onFocusChanged { state ->
                        if (state.isFocused) onFocus()
                    }
                    .pointerInput(onClick) {
                        detectTapGestures {
                            onClick()
                        }
                    },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White.copy(alpha = 0.2f)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.5f)))
                ),
                shape = ClickableSurfaceDefaults.shape(CircleShape)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Light,
                        color = if (isFocused) Color.White else Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Agregar perfil",
            fontSize = 18.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
    
    LaunchedEffect(isFocused) {
        if (isFocused) focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CreateProfileDialog(
    onCreate: (String, String) -> Unit, // Solo nombre y avatar
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var focusedField by remember { mutableStateOf(0) } // 0=name, 1=create, 2=cancel
    
    // Focus requesters para los botones
    val createFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    
    // Manejar el focus cuando cambia focusedField
    LaunchedEffect(focusedField) {
        when (focusedField) {
            1 -> createFocusRequester.requestFocus()
            2 -> cancelFocusRequester.requestFocus()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            when (focusedField) {
                                0 -> focusedField = 1 // name -> create
                                1 -> focusedField = 2 // create -> cancel
                                2 -> {} // cancel, no ir más abajo
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            when (focusedField) {
                                0 -> {} // name, no ir más arriba
                                1 -> focusedField = 0 // create -> name
                                2 -> focusedField = 1 // cancel -> create
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            when (focusedField) {
                                1 -> focusedField = 2 // create -> cancel
                                2 -> focusedField = 1 // cancel -> create
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            when (focusedField) {
                                1 -> focusedField = 2 // create -> cancel
                                2 -> focusedField = 1 // cancel -> create
                            }
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            color = AnfhlyColors.backgroundCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(400.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Crear Nuevo Perfil",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Name field
                TVProfileTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nombre del perfil",
                    isFocused = focusedField == 0,
                    onFocus = { focusedField = 0 },
                    onImeDone = { focusedField = 1 },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cancel button
                    val cancelInteraction = remember { MutableInteractionSource() }
                    val isCancelHovered by cancelInteraction.collectIsHoveredAsState()
                    LaunchedEffect(isCancelHovered) {
                        if (isCancelHovered) {
                            try { cancelFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cancelFocusRequester)
                            .hoverable(cancelInteraction)
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedField = 2
                            }
                            .pointerInput(onDismiss) {
                                detectTapGestures {
                                    onDismiss()
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.Black))
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Cancelar",
                            color = if (focusedField == 2) Color.Black else Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // Create button
                    val createInteraction = remember { MutableInteractionSource() }
                    val isCreateHovered by createInteraction.collectIsHoveredAsState()
                    LaunchedEffect(isCreateHovered) {
                        if (isCreateHovered) {
                            try { createFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    Surface(
                        onClick = {
                            if (name.isNotBlank()) {
                                // Abrir AvatarSelectionScreen con el nombre
                                onCreate(name, "https://preview.redd.it/the-boys-soldier-boy-build-v0-184k9gzbleb91.jpg?width=640&crop=smart&auto=webp&s=2620fe50c1b516db0aa2dae38b6b6bf6186cf797")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(createFocusRequester)
                            .hoverable(createInteraction)
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedField = 1
                            }
                            .pointerInput(name) {
                                detectTapGestures {
                                    if (name.isNotBlank()) {
                                        onCreate(name, "https://preview.redd.it/the-boys-soldier-boy-build-v0-184k9gzbleb91.jpg?width=640&crop=smart&auto=webp&s=2620fe50c1b516db0aa2dae38b6b6bf6186cf797")
                                    }
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.Black))
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Crear",
                            color = if (focusedField == 1) Color.Black else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onUpdate: (String, Boolean, String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var isLocked by remember { mutableStateOf(profile.isLocked) }
    var selectedAvatar by remember { mutableStateOf(profile.customImageUrl ?: profile.avatar) }
    val avatars = listOf(
        "https://preview.redd.it/the-boys-soldier-boy-build-v0-184k9gzbleb91.jpg?width=640&crop=smart&auto=webp&s=2620fe50c1b516db0aa2dae38b6b6bf6186cf797",
        "https://wallpapers.com/images/high/the-boys-bloody-homelander-480j6btuoxiobjlj.jpg",
        "https://images.thedirect.com/media/article_full/atrain.jpg",
        "https://i.pinimg.com/736x/fe/1b/34/fe1b345a176b99c660a8e4006e436d17.jpg",
        "https://i.pinimg.com/736x/77/09/3c/77093c6cb24aaadb1931f20f5c3272a7.jpg",
        "https://i.pinimg.com/736x/27/12/c9/2712c99fe31db1609f1cd95c81644f64.jpg",
        "https://i.pinimg.com/736x/54/d4/a5/54d4a53922cfea4a361a706f75190ebb.jpg",
        "https://i.pinimg.com/474x/5a/4e/11/5a4e115ebdc5e4bd2f50e9e20a12a9cd.jpg",
        "https://www.biografiasyvidas.com/biografia/j/fotos/jackson_michael_1.jpg",
        "https://i.pinimg.com/736x/ea/28/86/ea2886bdf313cd82f12ba4b143735e30.jpg",
        "https://i.pinimg.com/736x/2e/ac/6d/2eac6d3e0b29e208535113ea3f2ca3ab.jpg",
        "https://i.pinimg.com/736x/31/37/32/313732aa86a872499a246d97b04dbf9b.jpg",
        "https://i.pinimg.com/736x/b8/90/d9/b890d901634961895f1b59aad4afb539.jpg",
        "https://i.pinimg.com/736x/ee/52/82/ee5282420939b0f73430116cf372f55a.jpg",
        "https://i.pinimg.com/236x/ec/18/0e/ec180e230e72e39d61bfdc7f11ace8b3.jpg",
        "https://i.pinimg.com/736x/44/a1/ea/44a1eab47e47909195c86e25765498df.jpg",
        "https://i.pinimg.com/736x/f2/0f/1a/f20f1a4af19a0b520074d8acbdbcdc82.jpg",
        "https://www.nit.pt/wp-content/uploads/2024/12/24db7a2d06147cd586e8afe2626a52ff.jpg",
        "https://i.blogs.es/5b679f/taquilla-blade-runner-2049-ryan-gosling/1200_900.jpg",
        "https://i.pinimg.com/736x/11/17/1f/11171fd3098fd8695455d4e0a947e668.jpg",
        "https://i.ibb.co/q3jWDPTK/3-AWO6-VGCRZCXTHKE5-Z24-BZWN6-Q.jpg",
        "https://i.pinimg.com/1200x/ca/45/b9/ca45b967fd970deccd323b6756a43ea1.jpg",
        "https://i.ibb.co/7xc0WqP9/LXVO3-NGPWJDNVFFKLYAGLC3-L5-Q.jpg",
        "https://s2-techtudo.glbimg.com/E-RYtfyv1rsR-4STrNGcgDqcT74=/0x0:1596x888/984x0/smart/filters:strip_icc()/i.s3.glbimg.com/v1/AUTH_08fbf48bc0524877943fe86e43087e7a/internal_photos/bs/2024/S/B/cFhlA1S3G8B7crStsmgw/the-boys-prime-video-techtudo-06.jpg",
        "https://www.looper.com/img/gallery/stormfronts-strongest-power-on-the-boys-isnt-what-you-think/l-intro-1600366075.jpg",
        "https://www.giantfreakinrobot.com/wp-content/uploads/2021/07/the-boys-homelander-antony-starr.jpg",
        "https://i.pinimg.com/736x/9c/84/88/9c8488eb100746a5fb7821e22f36ac7a.jpg",
        "https://indiehoy.com/wp-content/uploads/2020/10/Annie-January2.jpg",
        "https://i.ibb.co/vvPDcXtY/KD2-DQAV7-L5-OHZOGTEEFTUIQAEY.png",
        "https://i.ibb.co/Hpbfdrdc/the-series-supernatural-jensen-ackles-season-1-wallpaper-preview.png",
        "https://i.pinimg.com/736x/95/71/01/957101717be534403d1269eed4d0578f.jpg"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            color = AnfhlyColors.backgroundCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(500.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Editar Perfil",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Name field
                TVProfileTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nombre del perfil",
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Lock toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { isLocked = !isLocked },
                        modifier = Modifier
                            .size(24.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    isLocked = !isLocked
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isLocked) Color.White else Color.Gray.copy(alpha = 0.5f),
                            focusedContainerColor = if (isLocked) Color.White else Color.Gray.copy(alpha = 0.5f)
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)))
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                    ) {
                        if (isLocked) {
                            Text(text = "✓", color = Color.Black, fontSize = 16.sp,
                                modifier = Modifier.wrapContentSize(Alignment.Center))
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(text = "Bloquear con PIN", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Avatar selection - scrollable horizontal con navegación optimizada para TV
                Text(
                    text = "Cambiar avatar",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Estado para manejar el focus de avatares
                var focusedAvatarIndex by remember { mutableIntStateOf(avatars.indexOf(selectedAvatar)) }
                val scrollState = rememberScrollState()
                
                LaunchedEffect(focusedAvatarIndex) {
                    // Auto-scroll al avatar enfocado sin animación
                    val itemWidth = 72.dp // 60dp avatar + 12dp spacing
                    val targetOffset = (focusedAvatarIndex * itemWidth.value).toInt()
                    scrollState.scrollTo(targetOffset)
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        if (focusedAvatarIndex > 0) {
                                            focusedAvatarIndex--
                                            selectedAvatar = avatars[focusedAvatarIndex]
                                        }
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        if (focusedAvatarIndex < avatars.size - 1) {
                                            focusedAvatarIndex++
                                            selectedAvatar = avatars[focusedAvatarIndex]
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    avatars.forEachIndexed { index, avatarUrl ->
                        val isSelected = selectedAvatar == avatarUrl
                        val isFocused = focusedAvatarIndex == index
                        
                        Surface(
                            onClick = { 
                                selectedAvatar = avatarUrl
                                focusedAvatarIndex = index
                            },
                            modifier = Modifier
                                .size(60.dp)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusedAvatarIndex = index
                                        selectedAvatar = avatarUrl
                                    }
                                }
                                .pointerInput(avatarUrl) {
                                    detectTapGestures {
                                        selectedAvatar = avatarUrl
                                        focusedAvatarIndex = index
                                    }
                                },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                                border = Border(BorderStroke(2.dp, if (isSelected) AnfhlyColors.primary else Color.Transparent))
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f), // Sin zoom
                            shape = ClickableSurfaceDefaults.shape(CircleShape)
                        ) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(onDelete) {
                                detectTapGestures {
                                    onDelete()
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFFCC0000),
                            focusedContainerColor = Color(0xFFFF4444)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Eliminar",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(onDismiss) {
                                detectTapGestures {
                                    onDismiss()
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Cancelar",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    Surface(
                        onClick = {
                            if (name.isNotBlank()) {
                                onUpdate(name, isLocked, selectedAvatar)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(name, isLocked, selectedAvatar) {
                                detectTapGestures {
                                    if (name.isNotBlank()) {
                                        onUpdate(name, isLocked, selectedAvatar)
                                    }
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = AnfhlyColors.primary,
                            focusedContainerColor = AnfhlyColors.primary
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White))
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Guardar",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun DeleteConfirmDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var focusedButton by remember { mutableIntStateOf(0) } // 0 = Cancelar, 1 = Eliminar
    val cancelFocusRequester = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape, Key.Back -> {
                            onDismiss()
                            true
                        }
                        Key.DirectionLeft -> {
                            if (focusedButton == 1) {
                                focusedButton = 0
                                cancelFocusRequester.requestFocus()
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (focusedButton == 0) {
                                focusedButton = 1
                                deleteFocusRequester.requestFocus()
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            if (focusedButton == 0) {
                                onDismiss()
                            } else {
                                onConfirm()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            color = AnfhlyColors.backgroundCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(400.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "¿Eliminar perfil?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "¿Estás seguro de que deseas eliminar el perfil \"$profileName\"? Esta acción no se puede deshacer.",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Hint text
                Text(
                    text = "← → para cambiar | ENTER para seleccionar",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val cancelBorder = if (focusedButton == 0) Color.White else Color.Transparent
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cancelFocusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedButton = 0
                            }
                            .border(2.dp, cancelBorder, RoundedCornerShape(50.dp))
                            .pointerInput(onDismiss) {
                                detectTapGestures {
                                    onDismiss()
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White.copy(alpha = 0.3f)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Cancelar",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    val deleteBorder = if (focusedButton == 1) Color.White else Color.Transparent
                    Surface(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(deleteFocusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedButton = 1
                            }
                            .border(2.dp, deleteBorder, RoundedCornerShape(50.dp))
                            .pointerInput(onConfirm) {
                                detectTapGestures {
                                    onConfirm()
                                }
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFFCC0000),
                            focusedContainerColor = Color(0xFFFF4444)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(3.dp, Color.White))
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Eliminar",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    
    // Request initial focus on Cancel button
    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }
}

@Composable
fun TVProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    isFocused: Boolean = false,
    onFocus: () -> Unit = {},
    onImeDone: () -> Unit = {},
    nextFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    
    // Cache the background drawable to avoid recreation
    val defaultBackground = remember {
        android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(51, 128, 128, 128))
            cornerRadius = 8.dp.value
            setStroke(2, android.graphics.Color.argb(128, 128, 128, 128))
        }
    }
    
    val focusedBackground = remember {
        android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.argb(51, 128, 128, 128))
            cornerRadius = 8.dp.value
            setStroke(3, android.graphics.Color.WHITE)
        }
    }
    
    AndroidView(
        factory = { ctx ->
            android.widget.EditText(ctx).apply {
                setText(value)
                setHint(placeholder)
                setTextColor(android.graphics.Color.WHITE)
                setHintTextColor(android.graphics.Color.argb(128, 255, 255, 255))
                textSize = 16f
                setPadding(16.dp.value.toInt(), 16.dp.value.toInt(), 16.dp.value.toInt(), 16.dp.value.toInt())
                background = defaultBackground
                
                if (isPassword) {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                } else {
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                }
                
                isFocusable = true
                isFocusableInTouchMode = true
                
                // Cache InputMethodManager reference
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        onFocus()
                        // Reduce delay and avoid multiple postDelayed calls
                        post {
                            try {
                                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                selectAll()
                            } catch (e: Exception) {
                                // Handle potential exceptions on low memory devices
                            }
                        }
                    } else {
                        setSelection(0)
                        // Hide keyboard when losing focus to free memory
                        try {
                            imm.hideSoftInputFromWindow(windowToken, 0)
                        } catch (e: Exception) {
                            // Ignore exceptions on cleanup
                        }
                    }
                }
                
                setOnEditorActionListener { _, actionId, event ->
                    val isEnterKey = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                     event?.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                     event?.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                    
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                        isEnterKey) {
                        
                        try {
                            imm.hideSoftInputFromWindow(this.windowToken, 0)
                        } catch (e: Exception) {
                            // Ignore exceptions
                        }
                        clearFocus()
                        onImeDone()
                        
                        nextFocusRequester?.let { req ->
                            post { req.requestFocus() }
                        }
                        true
                    } else false
                }
                
                setOnKeyListener { _, keyCode, event ->
                    when (event?.action) {
                        android.view.KeyEvent.ACTION_DOWN -> {
                            when (keyCode) {
                                android.view.KeyEvent.KEYCODE_BACK -> {
                                    try {
                                        if (imm.isActive(this)) {
                                            imm.hideSoftInputFromWindow(this.windowToken, 0)
                                            clearFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    try {
                                        imm.hideSoftInputFromWindow(this.windowToken, 0)
                                        clearFocus()
                                        false // Let the event propagate
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                }
                
                // Create and store TextWatcher reference for cleanup
                val textWatcher = object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        onValueChange(s?.toString() ?: "")
                    }
                }
                addTextChangedListener(textWatcher)
                
                // Store reference for cleanup
                tag = textWatcher
            }
        },
        update = { editText ->
            // Avoid unnecessary text updates
            if (editText.text.toString() != value) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
            
            // Handle focus changes more efficiently
            if (isFocused && !editText.hasFocus()) {
                editText.requestFocus()
                try {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                } catch (e: Exception) {
                    // Ignore exceptions
                }
            }
            
            if (!isFocused && editText.hasFocus()) {
                try {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(editText.windowToken, 0)
                } catch (e: Exception) {
                    // Ignore exceptions
                }
                editText.clearFocus()
            }
            
            // Update background more efficiently
            editText.background = if (isFocused) focusedBackground else defaultBackground
        },
        onRelease = { editText ->
            // Cleanup TextWatcher to prevent memory leaks
            val textWatcher = editText.tag as? android.text.TextWatcher
            if (textWatcher != null) {
                editText.removeTextChangedListener(textWatcher)
            }
            // Clear focus and hide keyboard
            editText.clearFocus()
            try {
                val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
            } catch (e: Exception) {
                // Ignore exceptions during cleanup
            }
        },
        modifier = modifier
            .height(56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) onFocus()
            }
    )
    
    LaunchedEffect(isFocused) {
        if (isFocused) focusRequester.requestFocus()
    }
}
