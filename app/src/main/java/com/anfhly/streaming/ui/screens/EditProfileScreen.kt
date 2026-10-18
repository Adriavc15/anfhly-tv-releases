package com.anfhly.streaming.ui.screens

import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anfhly.streaming.data.model.UserProfile
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.ui.theme.AnfhlyColors
import androidx.activity.compose.BackHandler
import com.anfhly.streaming.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EditProfileScreen(
    profile: UserProfile,
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onProfileUpdated: () -> Unit,
    onSelectAvatar: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    var name by remember { mutableStateOf(profile.name) }
    var isLocked by remember { mutableStateOf(profile.isLocked) }
    var selectedAvatar by remember { mutableStateOf(profile.customImageUrl ?: profile.avatar) }
    
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Focus indices: 0=avatar preview, 1=name, 2=delete, 3=cancel, 4=save
    var focusedIndex by remember { mutableIntStateOf(0) }
    
    // Focus requesters for proper navigation
    val avatarFocusRequester = remember { FocusRequester() }
    val deleteFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }
    val nameFieldFocusRequester = remember { FocusRequester() }
    
    // Hide keyboard helper
    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(null, 0)
        focusManager.clearFocus()
    }
    
    // Manejar el focus cuando cambia focusedIndex
    LaunchedEffect(focusedIndex) {
        when (focusedIndex) {
            0 -> avatarFocusRequester.requestFocus()
            1 -> nameFieldFocusRequester.requestFocus()
            2 -> deleteFocusRequester.requestFocus()
            3 -> cancelFocusRequester.requestFocus()
            4 -> saveFocusRequester.requestFocus()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape, Key.Back -> {
                            hideKeyboard()
                            onNavigateBack()
                            true
                        }
                        Key.DirectionUp -> {
                            hideKeyboard()
                            when (focusedIndex) {
                                1 -> { focusedIndex = 0 }
                                2, 3, 4 -> { 
                                    focusedIndex = 1
                                    nameFieldFocusRequester.requestFocus()
                                }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            hideKeyboard()
                            when (focusedIndex) {
                                0 -> { 
                                    focusedIndex = 1
                                    nameFieldFocusRequester.requestFocus()
                                }
                                1 -> { 
                                    focusedIndex = 3
                                    cancelFocusRequester.requestFocus()
                                }
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            hideKeyboard()
                            when (focusedIndex) {
                                3 -> { 
                                    focusedIndex = 2
                                    deleteFocusRequester.requestFocus()
                                }
                                4 -> { 
                                    focusedIndex = 3
                                    cancelFocusRequester.requestFocus()
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            hideKeyboard()
                            when (focusedIndex) {
                                2 -> { 
                                    focusedIndex = 3
                                    cancelFocusRequester.requestFocus()
                                }
                                3 -> { 
                                    focusedIndex = 4
                                    saveFocusRequester.requestFocus()
                                }
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            when (focusedIndex) {
                                0 -> {
                                    // Avatar preview - navigate to avatar selection
                                    onSelectAvatar()
                                }
                                1 -> {
                                    // Let the text field handle it
                                    return@onPreviewKeyEvent false
                                }
                                2 -> {
                    // Eliminar el perfil directamente
                    viewModel.deleteProfile(profile.id, profile.userId)
                    // Forzar recarga para asegurar que la UI se actualice inmediatamente
                    viewModel.loadProfiles(profile.userId)
                    onNavigateBack()
                }
                                3 -> {
                                    hideKeyboard()
                                    onNavigateBack()
                                }
                                4 -> {
                                    if (name.isNotBlank()) {
                                        hideKeyboard()
                                        viewModel.updateProfile(profile.copy(
                                            name = name,
                                            isLocked = false,
                                            avatar = "custom",
                                            customImageUrl = selectedAvatar
                                        ))
                                        onProfileUpdated()
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        // App background image
        AppBackground()
        
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .verticalScroll(rememberScrollState())
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Editar Perfil",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Modifica los datos del perfil",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Avatar preview (clickable)
            val avatarBorderColor = if (focusedIndex == 0) Color.White else Color.Transparent
            // Quest 3 hover-to-focus support
            val avatarInteraction = remember { MutableInteractionSource() }
            val isAvatarHovered by avatarInteraction.collectIsHoveredAsState()
            LaunchedEffect(isAvatarHovered) {
                if (isAvatarHovered) {
                    try { avatarFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            }
            Surface(
                onClick = onSelectAvatar,
                modifier = Modifier
                    .size(120.dp)
                    .focusRequester(avatarFocusRequester)
                    .border(3.dp, avatarBorderColor, CircleShape)
                    .hoverable(avatarInteraction)
                    .onFocusChanged { state ->
                        if (state.isFocused) focusedIndex = 0
                    }
                    .pointerInput(onSelectAvatar) {
                        detectTapGestures {
                            onSelectAvatar()
                        }
                    },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape(CircleShape)
            ) {
                AsyncImage(
                    model = selectedAvatar,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            
            if (focusedIndex == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ENTER para cambiar avatar",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Name field
            val nameBorderColor = when {
                focusedIndex == 1 -> Color.White
                else -> Color.Gray.copy(alpha = 0.3f)
            }
            // Quest 3 hover-to-focus support
            val nameInteraction = remember { MutableInteractionSource() }
            val isNameHovered by nameInteraction.collectIsHoveredAsState()
            LaunchedEffect(isNameHovered) {
                if (isNameHovered) {
                    try { nameFieldFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .border(2.dp, nameBorderColor, RoundedCornerShape(8.dp))
                    .focusRequester(nameFieldFocusRequester)
                    .hoverable(nameInteraction)
                    .onFocusChanged { state ->
                        if (state.isFocused) focusedIndex = 1
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusedIndex = 1
                            nameFieldFocusRequester.requestFocus()
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                TVProfileTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nombre del perfil",
                    isFocused = focusedIndex == 1,
                    onFocus = { focusedIndex = 1 },
                    onImeDone = { 
                        focusedIndex = 3
                        cancelFocusRequester.requestFocus()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (focusedIndex == 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ENTER para editar, flechas para navegar",
                    fontSize = 12.sp,
                    color = AnfhlyColors.primary
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Delete button
                val deleteInteraction = remember { MutableInteractionSource() }
                val isDeleteHovered by deleteInteraction.collectIsHoveredAsState()
                LaunchedEffect(isDeleteHovered) {
                    if (isDeleteHovered) {
                        try { deleteFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
                Surface(
                    onClick = {
                        // Eliminar el perfil directamente y volver a ManageProfilesScreen
                        viewModel.deleteProfile(profile.id, profile.userId)
                        // Forzar recarga para asegurar que la UI se actualice inmediatamente
                        viewModel.loadProfiles(profile.userId)
                        onNavigateBack()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(deleteFocusRequester)
                        .hoverable(deleteInteraction)
                        .onFocusChanged { state ->
                            if (state.isFocused) focusedIndex = 2
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                viewModel.deleteProfile(profile.id, profile.userId)
                                viewModel.loadProfiles(profile.userId)
                                onNavigateBack()
                            }
                        },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.25f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, Color.White))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                ) {
                    Text(
                        text = "Eliminar",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        textAlign = TextAlign.Center
                    )
                }
                
                // Cancel button
                val cancelInteraction = remember { MutableInteractionSource() }
                val isCancelHovered by cancelInteraction.collectIsHoveredAsState()
                LaunchedEffect(isCancelHovered) {
                    if (isCancelHovered) {
                        try { cancelFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
                Surface(
                    onClick = { 
                        hideKeyboard()
                        onNavigateBack() 
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(cancelFocusRequester)
                        .hoverable(cancelInteraction)
                        .onFocusChanged { state ->
                            if (state.isFocused) focusedIndex = 3
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                hideKeyboard()
                                onNavigateBack()
                            }
                        },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color.White.copy(alpha = 0.3f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, Color.White))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                ) {
                    Text(
                        text = "Cancelar",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        textAlign = TextAlign.Center
                    )
                }
                
                // Save button
                val saveInteraction = remember { MutableInteractionSource() }
                val isSaveHovered by saveInteraction.collectIsHoveredAsState()
                LaunchedEffect(isSaveHovered) {
                    if (isSaveHovered) {
                        try { saveFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
                Surface(
                    onClick = {
                        if (name.isNotBlank()) {
                            hideKeyboard()
                            viewModel.updateProfile(profile.copy(
                                name = name,
                                isLocked = false,
                                avatar = "custom",
                                customImageUrl = selectedAvatar
                            ))
                            onProfileUpdated()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(saveFocusRequester)
                        .hoverable(saveInteraction)
                        .onFocusChanged { state ->
                            if (state.isFocused) focusedIndex = 4
                        }
                        .pointerInput(name, selectedAvatar) {
                            detectTapGestures {
                                if (name.isNotBlank()) {
                                    hideKeyboard()
                                    viewModel.updateProfile(profile.copy(
                                        name = name,
                                        isLocked = false,
                                        avatar = "custom",
                                        customImageUrl = selectedAvatar
                                    ))
                                    onProfileUpdated()
                                }
                            }
                        },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White.copy(alpha = 0.4f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, Color.White))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                ) {
                    Text(
                        text = "Guardar",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Request initial focus
    LaunchedEffect(Unit) {
        focusedIndex = 0
    }
}
