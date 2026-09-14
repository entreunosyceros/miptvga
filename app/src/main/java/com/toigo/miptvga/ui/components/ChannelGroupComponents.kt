package com.toigo.miptvga.ui.components

import android.view.KeyEvent as AndroidViewKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.toigo.miptvga.ChannelGroup
import com.toigo.miptvga.ChannelListEntry
import com.toigo.miptvga.CurrentProgram
import com.toigo.miptvga.FavoriteChannelsGroupId
import com.toigo.miptvga.currentProgramForChannel
import com.toigo.miptvga.isChannelEntryFavorite

@Composable
internal fun GroupList(
    modifier: Modifier = Modifier,
    groups: List<ChannelGroup>,
    selectedGroupId: String,
    isLoading: Boolean,
    focusRequestToken: Int = 0,
    onSelectGroup: (String) -> Unit
) {
    if (groups.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(ItemShape)
                .background(ChannelRowColor)
                .border(1.dp, PanelBorderColor, ItemShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLoading) "Cargando grupos…" else "Sin grupos",
                color = SecondaryTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = groups,
            key = { it.id },
            contentType = { "group" }
        ) { group ->
            GroupRow(
                group = group,
                selected = group.id == selectedGroupId,
                requestInitialFocus = focusRequestToken > 0 && group.id == selectedGroupId,
                onSelectGroup = onSelectGroup
            )
        }
    }
}

@Composable
internal fun GroupRow(
    group: ChannelGroup,
    selected: Boolean,
    requestInitialFocus: Boolean,
    onSelectGroup: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = selected || isFocused

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(
                when {
                    selected -> ChannelRowSelectedColor.copy(alpha = 0.78f)
                    isFocused -> PanelColorElevated
                    else -> GroupSectionColor
                }
            )
            .border(1.dp, if (isActive) PrimaryButtonColor else PanelBorderColor.copy(alpha = 0.65f), ItemShape)
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onSelectGroup(group.id) }
            .hoverable(interactionSource = interactionSource)
            .focusable(interactionSource = interactionSource)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(if (isActive) PrimaryButtonColor else Color.Transparent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (group.id == FavoriteChannelsGroupId || group.isFavorite) "★ ${group.title}" else group.title,
                    color = Color.White,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = if (selected || isFocused || isHovered) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    }
                )
                Text(
                    text = buildString {
                        append(if (group.count == 1) "1 canal" else "${group.count} canales")
                        if (group.isFavorite) append(" · grupo favorito")
                    },
                    color = if (isActive) MutedTextColor else SecondaryTextColor,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Box(
                modifier = Modifier
                    .clip(ChipShape)
                    .background(if (selected) PrimaryButtonColor.copy(alpha = 0.26f) else StatusChipColor)
                    .border(1.dp, if (selected) PrimaryButtonColor else PanelBorderColor, ChipShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = group.count.toString(),
                    color = if (selected) Color.White else SecondaryTextColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}


@Composable
internal fun ChannelList(
    modifier: Modifier = Modifier,
    filteredChannels: List<ChannelListEntry>,
    favoriteIds: Set<String>,
    favoriteGroupIds: Set<String>,
    currentPrograms: Map<String, CurrentProgram>,
    showChannelLogos: Boolean,
    selectedIndex: Int,
    selectedVisibleIndex: Int,
    focusRequestToken: Int = 0,
    onBackToGroups: (() -> Unit)? = null,
    onToggleFavorite: (Int) -> Unit,
    onSelectChannel: (Int) -> Unit
) {
    if (filteredChannels.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(ItemShape)
                .background(ChannelRowColor)
                .border(1.dp, PanelBorderColor, ItemShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No hay canales para este filtro",
                color = SecondaryTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedVisibleIndex, filteredChannels.size) {
        if (selectedVisibleIndex < 0 || filteredChannels.isEmpty()) return@LaunchedEffect

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val isAlreadyVisible = visibleItems.any { it.index == selectedVisibleIndex }
        if (!isAlreadyVisible) {
            listState.scrollToItem(selectedVisibleIndex)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = filteredChannels,
            key = { item -> item.originalIndex },
            contentType = { "channel" }
        ) { entry ->
            ChannelRow(
                entry = entry,
                isFavorite = isChannelEntryFavorite(entry, favoriteIds, favoriteGroupIds),
                currentProgram = currentProgramForChannel(entry.channel, currentPrograms),
                showChannelLogos = showChannelLogos,
                selected = entry.originalIndex == selectedIndex,
                requestInitialFocus = selectedVisibleIndex >= 0 && filteredChannels.getOrNull(selectedVisibleIndex)?.originalIndex == entry.originalIndex,
                focusRequestToken = focusRequestToken,
                onBackToGroups = onBackToGroups,
                onToggleFavorite = onToggleFavorite,
                onSelectChannel = onSelectChannel
            )
        }
    }
}

@Composable
internal fun ChannelRow(
    modifier: Modifier = Modifier,
    entry: ChannelListEntry,
    isFavorite: Boolean,
    currentProgram: CurrentProgram?,
    showChannelLogos: Boolean,
    selected: Boolean,
    requestInitialFocus: Boolean,
    focusRequestToken: Int,
    onBackToGroups: (() -> Unit)?,
    onToggleFavorite: (Int) -> Unit,
    onSelectChannel: (Int) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = selected || isFocused
    val backgroundColor = when {
        selected -> ChannelRowSelectedColor.copy(alpha = 0.86f)
        isFocused -> PanelColorElevated
        else -> ChannelRowColor
    }
    val accentColor = if (selected || isFocused) PrimaryButtonColor else PanelBorderColor.copy(alpha = 0.55f)

    LaunchedEffect(focusRequestToken, requestInitialFocus) {
        if (focusRequestToken > 0 && requestInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = accentColor,
                shape = ItemShape
            )
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onSelectChannel(entry.originalIndex) }
            .hoverable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == AndroidViewKeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == AndroidViewKeyEvent.KEYCODE_DPAD_LEFT
                ) {
                    onBackToGroups?.invoke()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 0.dp, vertical = 0.dp)
            .focusable(interactionSource = interactionSource)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .background(if (isActive) PrimaryButtonColor else Color.Transparent)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (entry.originalIndex + 1).toString().padStart(3, '0'),
                    color = if (isActive) Color.White else SecondaryTextColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(30.dp)
                )
                ChannelLogo(
                    showChannelLogos = showChannelLogos,
                    logoUrl = entry.channel.logoUrl,
                    channelName = entry.channel.name,
                    modifier = Modifier
                        .width(56.dp)
                        .height(32.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isFavorite) "★ ${entry.channel.name}" else entry.channel.name,
                        color = Color.White,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = if (selected || isFocused || isHovered) {
                            Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        } else {
                            Modifier
                        }
                    )
                    Text(
                        text = entry.groupTitle,
                        color = if (selected) Color.White else SecondaryTextColor,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall
                    )
                    currentProgram?.let { program ->
                        Text(
                            text = program.title,
                            color = if (selected) Color.White else MutedTextColor,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                MiniOsdButton(
                    text = if (isFavorite) "★" else "☆",
                    onClick = { onToggleFavorite(entry.originalIndex) },
                    active = isFavorite
                )
                if (selected) {
                    Text(
                        text = "▶",
                        color = PrimaryButtonColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChannelLogo(
    showChannelLogos: Boolean,
    logoUrl: String?,
    channelName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sanitizedLogoUrl = logoUrl?.trim().orEmpty()
    val logoModifier = modifier
        .clip(ItemShape)
        .background(if (sanitizedLogoUrl.isBlank()) PanelColorElevated else Color.White)
        .border(1.dp, PanelBorderColor, ItemShape)
        .padding(horizontal = 4.dp, vertical = 3.dp)

    if (!showChannelLogos || sanitizedLogoUrl.isBlank()) {
        Box(
            modifier = logoModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelName.take(2).uppercase().ifBlank { "TV" },
                color = MutedTextColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(sanitizedLogoUrl)
            .crossfade(false)
            .build(),
        contentDescription = "Logo de $channelName",
        contentScale = ContentScale.Fit,
        modifier = logoModifier
    )
}
