package com.anfhly.streaming.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.ExperimentalTvFoundationApi
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.anfhly.streaming.data.model.CastMember
import com.anfhly.streaming.ui.components.AppBackground
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalTvFoundationApi::class)
@Composable
fun CastScreen(
    contentTitle: String,
    castMembers: List<CastMember>,
    onNavigateBack: () -> Unit,
    onActorSelected: (CastMember) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val firstItemFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }

    BackHandler { onNavigateBack() }

    LaunchedEffect(castMembers.size) {
        delay(120)
        try {
            if (castMembers.isNotEmpty()) {
                firstItemFocus.requestFocus()
            } else {
                backFocus.requestFocus()
            }
        } catch (_: Exception) {
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Quest 3 hover-to-focus support
                val backInteraction = remember { MutableInteractionSource() }
                val isBackHovered by backInteraction.collectIsHoveredAsState()
                LaunchedEffect(isBackHovered) {
                    if (isBackHovered) {
                        try { backFocus.requestFocus() } catch (_: Exception) {}
                    }
                }
                Surface(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .focusRequester(backFocus)
                        .hoverable(backInteraction)
                        .onFocusChanged { backFocused = it.isFocused }
                        .pointerInput(onNavigateBack) {
                            detectTapGestures {
                                onNavigateBack()
                            }
                        },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF3A3A4A),
                        focusedContainerColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, Color.White))
                    ),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50.dp))
                ) {
                    Text(
                        text = "Volver",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (backFocused) Color.Black else Color.White
                    )
                }

                Column {
                    Text(
                        text = "Reparto",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = contentTitle,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (castMembers.isEmpty()) {
                Text(
                    text = "No hay reparto disponible en Firestore para este título.",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                // Ocupa todo el alto bajo la cabecera para no recortar foto + textos + escala al foco
                TvLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(end = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    pivotOffsets = PivotOffsets(0.12f, 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer { clip = false }
                ) {
                    itemsIndexed(
                        castMembers,
                        key = { index, member -> "${member.name}_$index" }
                    ) { index, member ->
                        CastMemberCard(
                            member = member,
                            onClick = { onActorSelected(member) },
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstItemFocus)
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CastMemberCard(
    member: CastMember,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardFocusRequester = remember { FocusRequester() }
    // Quest 3 hover-to-focus support
    val cardInteraction = remember { MutableInteractionSource() }
    val isCardHovered by cardInteraction.collectIsHoveredAsState()
    LaunchedEffect(isCardHovered) {
        if (isCardHovered) {
            try { cardFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(200.dp)
            .height(300.dp)
            .focusRequester(cardFocusRequester)
            .hoverable(cardInteraction)
            .pointerInput(onClick) {
                detectTapGestures {
                    onClick()
                }
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF252530),
            focusedContainerColor = Color(0xFF353545)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White))
        ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!member.profileUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = member.profileUrl,
                        contentDescription = member.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = member.name.take(1).uppercase(),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = member.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            member.character?.let { role ->
                Text(
                    text = role,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
