package com.toigo.miptvga.ui

import android.view.KeyEvent as AndroidViewKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toigo.miptvga.ChannelListEntry
import com.toigo.miptvga.CurrentProgram
import com.toigo.miptvga.MainViewModel
import com.toigo.miptvga.UiState
import com.toigo.miptvga.currentProgramForChannel
import com.toigo.miptvga.isChannelEntryFavorite
import com.toigo.miptvga.nextProgramForChannel
import com.toigo.miptvga.ui.components.AppActionButton
import com.toigo.miptvga.ui.components.AppBackgroundColor
import com.toigo.miptvga.ui.components.AppPanel
import com.toigo.miptvga.ui.components.ChannelLogo
import com.toigo.miptvga.ui.components.ChannelRowColor
import com.toigo.miptvga.ui.components.ChannelRowSelectedColor
import com.toigo.miptvga.ui.components.ChipShape
import com.toigo.miptvga.ui.components.GroupList
import com.toigo.miptvga.ui.components.ItemShape
import com.toigo.miptvga.ui.components.MutedTextColor
import com.toigo.miptvga.ui.components.PanelBorderColor
import com.toigo.miptvga.ui.components.PanelColorElevated
import com.toigo.miptvga.ui.components.PrimaryButtonColor
import com.toigo.miptvga.ui.components.SecondaryTextColor
import com.toigo.miptvga.ui.components.SectionTitle
import com.toigo.miptvga.ui.components.StatusChip
import com.toigo.miptvga.ui.components.StatusChipColor
import com.toigo.miptvga.ui.components.formatClock
import com.toigo.miptvga.ui.components.rememberDeviceTimeFormatter

@Composable
internal fun GuideScreen(
    ui: UiState,
    vm: MainViewModel
) {
    val timeFormatter = rememberDeviceTimeFormatter()
    val listState = rememberLazyListState()
    val focusTargetIndex = remember(ui.selectedVisibleIndex, ui.filteredChannels.size) {
        when {
            ui.filteredChannels.isEmpty() -> -1
            ui.selectedVisibleIndex in ui.filteredChannels.indices -> ui.selectedVisibleIndex
            else -> 0
        }
    }

    LaunchedEffect(focusTargetIndex, ui.filteredChannels.size) {
        if (focusTargetIndex >= 0) {
            listState.scrollToItem(focusTargetIndex)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(20.dp)
    ) {
        val useVerticalLayout = maxWidth < 980.dp

        AppPanel(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (useVerticalLayout) {
                TvGuideHeader(
                    ui = ui,
                    onOpenMain = vm::openMain,
                    onOpenSearch = vm::openSearch,
                    onRefreshEpg = vm::refreshEpg,
                    onOpenSettings = vm::openSettings
                )
                GroupList(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(204.dp),
                    groups = ui.groups,
                    selectedGroupId = ui.selectedGroupId,
                    isLoading = ui.isLoading,
                    onSelectGroup = vm::selectGroup
                )
                TvGuideProgramPanel(
                    modifier = Modifier.weight(1f),
                    ui = ui,
                    listState = listState,
                    focusTargetIndex = focusTargetIndex,
                    timeFormatter = timeFormatter,
                    onSelectChannel = { index ->
                        vm.selectChannel(index)
                        vm.openMain()
                    },
                    onOpenChannelInfo = { index ->
                        vm.selectChannel(index)
                        vm.openChannelInfo()
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppPanel(
                        modifier = Modifier
                            .width(294.dp)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TvGuideHeader(
                            ui = ui,
                            compact = true,
                            onOpenMain = vm::openMain,
                            onOpenSearch = vm::openSearch,
                            onRefreshEpg = vm::refreshEpg,
                            onOpenSettings = vm::openSettings
                        )
                        Text(
                            text = "GRUPOS",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        GroupList(
                            modifier = Modifier.fillMaxSize(),
                            groups = ui.groups,
                            selectedGroupId = ui.selectedGroupId,
                            isLoading = ui.isLoading,
                            onSelectGroup = vm::selectGroup
                        )
                    }

                    TvGuideProgramPanel(
                        modifier = Modifier.weight(1f),
                        ui = ui,
                        listState = listState,
                        focusTargetIndex = focusTargetIndex,
                        timeFormatter = timeFormatter,
                        onSelectChannel = { index ->
                            vm.selectChannel(index)
                            vm.openMain()
                        },
                        onOpenChannelInfo = { index ->
                            vm.selectChannel(index)
                            vm.openChannelInfo()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun TvGuideHeader(
    ui: UiState,
    compact: Boolean = false,
    onOpenMain: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefreshEpg: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(
                title = if (compact) "Guía TV" else "Guía EPG · TV",
                subtitle = groupsTitleForGuide(ui)
            )
            StatusChip(
                text = if (ui.isEpgLoading) "EPG…" else ui.epgStatus,
                accent = ui.epgSettings.enabled
            )
        }

        AppPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "OK: reproducir · INFO/Menú: ficha del canal · ↑↓: recorrer guía",
                color = MutedTextColor,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AppActionButton(text = "Volver", onClick = onOpenMain, modifier = Modifier.weight(1f))
                    AppActionButton(text = "Buscar", onClick = onOpenSearch, modifier = Modifier.weight(1f))
                    AppActionButton(
                        text = "Recargar EPG",
                        onClick = onRefreshEpg,
                        modifier = Modifier.weight(1f),
                        enabled = ui.epgSettings.enabled && ui.epgSettings.url.isNotBlank() && !ui.isEpgLoading
                    )
                    AppActionButton(text = "Ajustes", onClick = onOpenSettings, modifier = Modifier.weight(1f), primary = true)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppActionButton(text = "Volver", onClick = onOpenMain, modifier = Modifier.fillMaxWidth())
                    AppActionButton(text = "Buscar", onClick = onOpenSearch, modifier = Modifier.fillMaxWidth())
                    AppActionButton(
                        text = "Recargar EPG",
                        onClick = onRefreshEpg,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ui.epgSettings.enabled && ui.epgSettings.url.isNotBlank() && !ui.isEpgLoading
                    )
                    AppActionButton(text = "Ajustes", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), primary = true)
                }
            }
        }
    }
}

@Composable
internal fun TvGuideProgramPanel(
    modifier: Modifier = Modifier,
    ui: UiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusTargetIndex: Int,
    timeFormatter: java.text.DateFormat,
    onSelectChannel: (Int) -> Unit,
    onOpenChannelInfo: (Int) -> Unit
) {
    val selectedGroupTitle = ui.groups.firstOrNull { it.id == ui.selectedGroupId }?.title ?: "Todos los canales"

    AppPanel(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "EN EMISIÓN",
                    color = SecondaryTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedGroupTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            StatusChip(text = "${ui.filteredChannels.size} canales", accent = true)
        }

        if (ui.filteredChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No hay canales en este grupo o filtro.",
                    color = SecondaryTextColor,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = ui.filteredChannels,
                    key = { it.originalIndex },
                    contentType = { "guide_row" }
                ) { entry ->
                    GuideRow(
                        entry = entry,
                        currentProgram = currentProgramForChannel(entry.channel, ui.currentPrograms),
                        nextProgram = nextProgramForChannel(entry.channel, ui.nextPrograms),
                        isFavorite = isChannelEntryFavorite(entry, ui.favoriteIds, ui.favoriteGroupIds),
                        showChannelLogos = ui.showChannelLogos,
                        selected = entry.originalIndex == ui.selectedIndex,
                        requestInitialFocus = ui.filteredChannels.getOrNull(focusTargetIndex)?.originalIndex == entry.originalIndex,
                        timeFormatter = timeFormatter,
                        onSelectChannel = { onSelectChannel(entry.originalIndex) },
                        onOpenInfo = { onOpenChannelInfo(entry.originalIndex) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun GuideRow(
    entry: ChannelListEntry,
    currentProgram: CurrentProgram?,
    nextProgram: CurrentProgram?,
    isFavorite: Boolean,
    showChannelLogos: Boolean,
    selected: Boolean,
    requestInitialFocus: Boolean,
    timeFormatter: java.text.DateFormat,
    onSelectChannel: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = selected || isFocused
    val currentProgress = remember(currentProgram) {
        currentProgram?.let { program ->
            val duration = (program.endTimeMillis - program.startTimeMillis).coerceAtLeast(1L)
            ((System.currentTimeMillis() - program.startTimeMillis).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(if (selected) ChannelRowSelectedColor.copy(alpha = 0.32f) else if (isFocused) PanelColorElevated else ChannelRowColor)
            .border(1.dp, if (isActive) PrimaryButtonColor else PanelBorderColor, ItemShape)
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelectChannel)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != AndroidViewKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidViewKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidViewKeyEvent.KEYCODE_MENU,
                    AndroidViewKeyEvent.KEYCODE_INFO -> {
                        onOpenInfo()
                        true
                    }

                    else -> false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(92.dp)
                    .background(if (isActive) PrimaryButtonColor else Color.Transparent)
            )
            ChannelLogo(
                showChannelLogos = showChannelLogos,
                logoUrl = entry.channel.logoUrl,
                channelName = entry.channel.name,
                modifier = Modifier
                    .width(72.dp)
                    .height(44.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 0.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFavorite) "★ ${entry.channel.name}" else entry.channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        text = if (selected) "ACTIVO" else "OK",
                        accent = selected || isFocused
                    )
                }

                Text(
                    text = entry.groupTitle,
                    color = if (isActive) Color.White else SecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )

                Text(
                    text = currentProgram?.let {
                        "AHORA  ${timeFormatter.formatClock(it.startTimeMillis)} - ${timeFormatter.formatClock(it.endTimeMillis)}   ${it.title}"
                    } ?: "AHORA  Sin información EPG",
                    color = if (currentProgram != null) MutedTextColor else SecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium
                )

                currentProgress?.let { progress ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(ChipShape)
                            .background(StatusChipColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(PrimaryButtonColor)
                        )
                    }
                }

                Text(
                    text = nextProgram?.let {
                        "SIGUIENTE  ${timeFormatter.formatClock(it.startTimeMillis)}   ${it.title}"
                    } ?: "SIGUIENTE  No disponible",
                    color = SecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clip(ChipShape)
                    .background(StatusChipColor)
                    .border(1.dp, if (isFocused) PrimaryButtonColor else PanelBorderColor, ChipShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "INFO",
                    color = if (isFocused) Color.White else SecondaryTextColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun GuideRow(
    entry: ChannelListEntry,
    currentProgram: CurrentProgram?,
    nextProgram: CurrentProgram?,
    isFavorite: Boolean,
    showChannelLogos: Boolean,
    selected: Boolean,
    timeFormatter: java.text.DateFormat,
    onSelectChannel: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isActive = selected || isFocused

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(if (isActive) PanelColorElevated else ChannelRowColor)
            .border(1.dp, if (isActive) PrimaryButtonColor else PanelBorderColor, ItemShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelectChannel)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(
                showChannelLogos = showChannelLogos,
                logoUrl = entry.channel.logoUrl,
                channelName = entry.channel.name,
                modifier = Modifier
                    .width(56.dp)
                    .height(34.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (isFavorite) "★ ${entry.channel.name}" else entry.channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    text = currentProgram?.let {
                        "Ahora · ${timeFormatter.formatClock(it.startTimeMillis)}-${timeFormatter.formatClock(it.endTimeMillis)} · ${it.title}"
                    } ?: "Ahora · Sin información EPG",
                    color = if (currentProgram != null) MutedTextColor else SecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    text = nextProgram?.let {
                        "Siguiente · ${timeFormatter.formatClock(it.startTimeMillis)} · ${it.title}"
                    } ?: "Siguiente · No disponible",
                    color = SecondaryTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            AppActionButton(
                text = "Info",
                onClick = onOpenInfo,
                modifier = Modifier.width(92.dp)
            )
        }
    }
}

internal fun groupsTitleForGuide(ui: UiState): String {
    val selectedGroupTitle = ui.groups.firstOrNull { it.id == ui.selectedGroupId }?.title ?: "Todos los canales"
    return "${ui.filteredChannels.size} canales · $selectedGroupTitle"
}
