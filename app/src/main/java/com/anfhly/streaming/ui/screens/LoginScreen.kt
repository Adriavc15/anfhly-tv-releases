package com.anfhly.streaming.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
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
import com.anfhly.streaming.data.local.SettingsDataStore
import com.anfhly.streaming.ui.components.AppBackground
import com.anfhly.streaming.data.repository.AuthRepository
import com.anfhly.streaming.ui.theme.AnfhlyColors
import com.anfhly.streaming.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.ArrowUpward

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToProfiles: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onGoogleSignInClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember {
        AuthViewModel(AuthRepository(), SettingsDataStore(context))
    }
    
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var activeField by remember { mutableStateOf(LoginFocusElement.EMAIL) }
    
    val keyboardFocusRequester = remember { FocusRequester() }
    val fieldsFocusRequester = remember { FocusRequester() }
    
    val onEmailChangeLambda = remember { { value: String -> email = value } }
    val onPasswordChangeLambda = remember { { value: String -> password = value } }
    val onActiveFieldChangeLambda = remember { { value: LoginFocusElement -> activeField = value } }
    
    val onForgotClickLambda = remember(viewModel) {
        {
            if (email.isNotBlank()) {
                viewModel.resetPassword(email)
            }
        }
    }
    
    val onLoginClickLambda = remember(viewModel) {
        {
            if (email.isNotBlank() && password.isNotBlank()) {
                viewModel.login(email, password)
            }
        }
    }
    
    // Always remember email on successful login by default
    LaunchedEffect(Unit) {
        viewModel.setRememberEmail(true)
        val dataStore = SettingsDataStore(context)
        dataStore.rememberedEmail.collect { savedEmail ->
            if (savedEmail != null) {
                email = savedEmail
            }
        }
    }

    // Handle login state changes
    LaunchedEffect(loginState) {
        when (loginState) {
            is AuthViewModel.LoginUiState.Success -> {
                onNavigateToProfiles()
                viewModel.resetLoginState()
            }
            else -> {}
        }
    }

    // Auto-focus keyboard on launch
    LaunchedEffect(Unit) {
        delay(200)
        try {
            keyboardFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back -> {
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AppBackground()
        
        LoginForm(
            email = email,
            onEmailChange = onEmailChangeLambda,
            password = password,
            onPasswordChange = onPasswordChangeLambda,
            activeField = activeField,
            onActiveFieldChange = onActiveFieldChangeLambda,
            errorMessage = (loginState as? AuthViewModel.LoginUiState.Error)?.message,
            isLoading = loginState is AuthViewModel.LoginUiState.Loading,
            onRegisterClick = onNavigateToRegister,
            onLoginClick = onLoginClickLambda,
            onGoogleSignInClick = onGoogleSignInClick,
            keyboardFocusRequester = keyboardFocusRequester,
            fieldsFocusRequester = fieldsFocusRequester
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoginForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    activeField: LoginFocusElement,
    onActiveFieldChange: (LoginFocusElement) -> Unit,
    errorMessage: String?,
    isLoading: Boolean,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    keyboardFocusRequester: FocusRequester,
    fieldsFocusRequester: FocusRequester
) {
    var localActiveField by remember(activeField) { mutableStateOf(activeField) }
    
    val currentEmail = rememberUpdatedState(email)
    val currentPassword = rememberUpdatedState(password)
    val currentActiveField = rememberUpdatedState(localActiveField)
    val currentOnEmailChange = rememberUpdatedState<(String) -> Unit>(onEmailChange)
    val currentOnPasswordChange = rememberUpdatedState<(String) -> Unit>(onPasswordChange)
    val currentOnLoginClick = rememberUpdatedState<() -> Unit>(onLoginClick)
    val currentOnActiveFieldChange = rememberUpdatedState<(LoginFocusElement) -> Unit>(onActiveFieldChange)

    val onEmailFocus = remember {
        {
            localActiveField = LoginFocusElement.EMAIL
            currentOnActiveFieldChange.value(LoginFocusElement.EMAIL)
            try { keyboardFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    val onPasswordFocus = remember {
        {
            localActiveField = LoginFocusElement.PASSWORD
            currentOnActiveFieldChange.value(LoginFocusElement.PASSWORD)
            try { keyboardFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val handleKeyPressed = remember {
        { key: String ->
            if (currentActiveField.value == LoginFocusElement.EMAIL) {
                currentOnEmailChange.value(currentEmail.value + key)
            } else {
                currentOnPasswordChange.value(currentPassword.value + key)
            }
        }
    }

    val handleDeletePressed = remember {
        {
            if (currentActiveField.value == LoginFocusElement.EMAIL) {
                val emailVal = currentEmail.value
                if (emailVal.isNotEmpty()) currentOnEmailChange.value(emailVal.dropLast(1))
            } else {
                val passVal = currentPassword.value
                if (passVal.isNotEmpty()) currentOnPasswordChange.value(passVal.dropLast(1))
            }
        }
    }


    val handleClearPressed = remember {
        {
            if (currentActiveField.value == LoginFocusElement.EMAIL) {
                currentOnEmailChange.value("")
            } else {
                currentOnPasswordChange.value("")
            }
        }
    }


    val handleLoginButtonClick = remember {
        {
            if (currentEmail.value.isNotBlank() && currentPassword.value.isNotBlank()) {
                currentOnLoginClick.value()
            }
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // Columna Izquierda: Teclado Virtual + Botones (42% del ancho)
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cabecera arriba del teclado
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "ANFHLYTV+",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Disfruta de tus películas y series favoritas.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teclado virtual adaptado para login
            VirtualKeyboard(
                onKeyPressed = handleKeyPressed,
                onDeletePressed = handleDeletePressed,
                onClearPressed = handleClearPressed,
                onNavigateToHeader = {
                    // No-op en login
                },
                onNavigateToCards = {
                    try { fieldsFocusRequester.requestFocus() } catch (_: Exception) {}
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(keyboardFocusRequester)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Botones de acción abajo del teclado virtual
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón Iniciar Sesión
                    var isLoginFocused by remember { mutableStateOf(false) }
                    val loginFocusRequester = remember { FocusRequester() }
                    // Quest 3 hover-to-focus support
                    val loginInteraction = remember { MutableInteractionSource() }
                    val isLoginHovered by loginInteraction.collectIsHoveredAsState()
                    LaunchedEffect(isLoginHovered) {
                        if (isLoginHovered) {
                            try { loginFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    Surface(
                        onClick = handleLoginButtonClick,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(loginFocusRequester)
                            .hoverable(loginInteraction)
                            .onFocusChanged { state ->
                                isLoginFocused = state.isFocused
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White))
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Iniciar Sesión",
                            color = if (isLoginFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Botón Registrar
                    var isRegisterFocused by remember { mutableStateOf(false) }
                    val registerFocusRequester = remember { FocusRequester() }
                    // Quest 3 hover-to-focus support
                    val registerInteraction = remember { MutableInteractionSource() }
                    val isRegisterHovered by registerInteraction.collectIsHoveredAsState()
                    LaunchedEffect(isRegisterHovered) {
                        if (isRegisterHovered) {
                            try { registerFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                    Surface(
                        onClick = onRegisterClick,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(registerFocusRequester)
                            .hoverable(registerInteraction)
                            .onFocusChanged { state ->
                                isRegisterFocused = state.isFocused
                            },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)))
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                    ) {
                        Text(
                            text = "Registrar",
                            color = if (isRegisterFocused) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Google Sign-In Button
                GoogleSignInButton(
                    onClick = onGoogleSignInClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Columna Derecha: Campos de Texto (58% del ancho)
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Campos de entrada interactivos
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            try { keyboardFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        } else false
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Selecciona un campo para editar:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                // Campo Correo
                val isEmailActive = localActiveField == LoginFocusElement.EMAIL
                LoginTextField(
                    value = email,
                    placeholder = "Correo electrónico",
                    isFocused = isEmailActive,
                    onFocus = onEmailFocus,
                    isActiveLabel = "Escribiendo Correo...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldsFocusRequester)
                )

                // Campo Contraseña
                val isPasswordActive = localActiveField == LoginFocusElement.PASSWORD
                LoginTextField(
                    value = password,
                    placeholder = "Contraseña",
                    isPassword = true,
                    isFocused = isPasswordActive,
                    onFocus = onPasswordFocus,
                    isActiveLabel = "Escribiendo Contraseña...",
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoginTextField(
    value: String,
    placeholder: String,
    isPassword: Boolean = false,
    isFocused: Boolean,
    onFocus: () -> Unit,
    isActiveLabel: String,
    modifier: Modifier = Modifier
) {
    val fieldFocusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val fieldInteraction = remember { MutableInteractionSource() }
    val isFieldHovered by fieldInteraction.collectIsHoveredAsState()
    LaunchedEffect(isFieldHovered) {
        if (isFieldHovered) {
            try { fieldFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    Column(modifier = modifier) {
        Surface(
            onClick = onFocus,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .focusRequester(fieldFocusRequester)
                .hoverable(fieldInteraction),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                focusedContainerColor = Color.White.copy(alpha = 0.2f)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))),
                focusedBorder = Border(BorderStroke(2.dp, Color.White))
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val displayValue = if (isPassword) {
                    "•".repeat(value.length)
                } else {
                    value
                }
                
                if (displayValue.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = displayValue,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        if (isFocused) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = isActiveLabel,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val googleFocusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val googleInteraction = remember { MutableInteractionSource() }
    val isGoogleHovered by googleInteraction.collectIsHoveredAsState()
    LaunchedEffect(isGoogleHovered) {
        if (isGoogleHovered) {
            try { googleFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(googleFocusRequester)
            .hoverable(googleInteraction)
            .onFocusChanged { state ->
                isFocused = state.isFocused
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            focusedContainerColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.7f)))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "G",
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp)
            )
            
            Text(
                text = "Continuar con Google",
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VirtualKeyboard(
    onKeyPressed: (String) -> Unit,
    onDeletePressed: () -> Unit,
    onClearPressed: () -> Unit,
    onNavigateToHeader: () -> Unit = {},
    onNavigateToCards: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isUppercase by remember { mutableStateOf(false) }
    var focusedKey by remember { mutableStateOf("") }
    var currentRow by remember { mutableStateOf(0) }

    val row1Keys = remember { listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0") }
    val row2Keys = remember { listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p") }
    val row3Keys = remember { listOf("a", "s", "d", "f", "g", "h", "j", "k", "l") }
    val row4Keys = remember { listOf("z", "x", "c", "v", "b", "n", "ñ", "m") }
    val row5Keys = remember { listOf("@", ".", "_", "-", "$", "#", "&", "!", "?", "/") }

    val onRow1KeyFocused = remember { { key: String -> focusedKey = key; currentRow = 1 } }
    val onRow2KeyFocused = remember { { key: String -> focusedKey = key; currentRow = 2 } }
    val onRow3KeyFocused = remember { { key: String -> focusedKey = key; currentRow = 3 } }
    val onRow4KeyFocused = remember { { key: String -> focusedKey = key; currentRow = 4 } }
    val onRow5KeyFocused = remember { { key: String -> focusedKey = key; currentRow = 5 } }

    val handleCasePressed = remember(isUppercase, onKeyPressed) {
        { key: String -> onKeyPressed(if (isUppercase) key.uppercase() else key.lowercase()) }
    }

    val onShiftFocused = remember { { key: String -> focusedKey = key; currentRow = 6 } }
    val onClearFocused = remember { { key: String -> focusedKey = key; currentRow = 6 } }
    val onDeleteFocused = remember { { key: String -> focusedKey = key; currentRow = 6 } }

    val toggleCase = remember { { isUppercase = !isUppercase } }

    Column(
        modifier = modifier
            .padding(8.dp)
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (currentRow == 1) {
                                onNavigateToHeader()
                                  true
                            } else false
                        }
                        Key.DirectionRight -> {
                            val lastKeys = listOf("0", "p", "l", "m", "/", "delete")
                            if (focusedKey in lastKeys) {
                                onNavigateToCards()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KeyboardRow(
            keys = row1Keys,
            isUppercase = true,
            onKeyPressed = onKeyPressed,
            onKeyFocused = onRow1KeyFocused
        )

        KeyboardRow(
            keys = row2Keys,
            isUppercase = isUppercase,
            onKeyPressed = handleCasePressed,
            onKeyFocused = onRow2KeyFocused
        )

        KeyboardRow(
            keys = row3Keys,
            isUppercase = isUppercase,
            onKeyPressed = handleCasePressed,
            onKeyFocused = onRow3KeyFocused
        )

        KeyboardRow(
            keys = row4Keys,
            isUppercase = isUppercase,
            onKeyPressed = handleCasePressed,
            onKeyFocused = onRow4KeyFocused
        )

        KeyboardRow(
            keys = row5Keys,
            isUppercase = true, // Symbols are case-insensitive
            onKeyPressed = onKeyPressed,
            onKeyFocused = onRow5KeyFocused
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VirtualKeyButton(
                text = "SHIFT",
                keyValue = "shift",
                onClick = toggleCase,
                onFocus = onShiftFocused,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ArrowUpward
            )

            VirtualKeyButton(
                text = "CLEAR",
                keyValue = "clear",
                onClick = onClearPressed,
                onFocus = onClearFocused,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Delete
            )

            VirtualKeyButton(
                text = "DEL",
                keyValue = "delete",
                onClick = onDeletePressed,
                onFocus = onDeleteFocused,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Backspace
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyboardRow(
    keys: List<String>,
    isUppercase: Boolean,
    onKeyPressed: (String) -> Unit,
    onKeyFocused: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        keys.forEach { key ->
            val label = remember(isUppercase, key) { if (isUppercase) key.uppercase() else key.lowercase() }
            val onKeyClick = remember(key, onKeyPressed) { { onKeyPressed(key) } }
            VirtualKeyButton(
                text = label,
                keyValue = key,
                onClick = onKeyClick,
                onFocus = onKeyFocused,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VirtualKeyButton(
    text: String,
    keyValue: String,
    onClick: () -> Unit,
    onFocus: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyFocusRequester = remember { FocusRequester() }
    val keyInteraction = remember { MutableInteractionSource() }
    val isKeyHovered by keyInteraction.collectIsHoveredAsState()
    
    LaunchedEffect(isKeyHovered) {
        if (isKeyHovered) {
            try { keyFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val lastClickTime = remember { mutableStateOf(0L) }
    val safeOnClick = remember(onClick) {
        {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime.value > 200L) {
                lastClickTime.value = currentTime
                onClick()
            }
        }
    }

    Surface(
        onClick = safeOnClick,
        modifier = modifier
            .height(30.dp)
            .focusRequester(keyFocusRequester)
            .hoverable(keyInteraction)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocus(keyValue)
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isHighlight) AnfhlyColors.primary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
            focusedContainerColor = if (isHighlight) AnfhlyColors.primary else Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = if (isFocused) (if (isHighlight) Color.White else Color.Black) else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = text,
                    color = if (isFocused) (if (isHighlight) Color.White else Color.Black) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private enum class LoginFocusElement {
    EMAIL, PASSWORD, REMEMBER, FORGOT, REGISTER, LOGIN, GOOGLE_SIGN_IN
}
