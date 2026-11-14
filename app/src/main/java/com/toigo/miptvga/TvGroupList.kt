package com.toigo.miptvga

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.yield

@Composable
internal fun GroupBrowserBackHandler(enabled: Boolean, browser: GroupBrowserState, onReturned: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled && browser.active && !browser.showingGroups) {
        if (browser.back()) onReturned()
    }
}

@Composable
internal fun TvGroupList(
    groups: List<ChannelGroup>, selectedGroupId: String?, focusedGroupIndex: Int,
    listState: LazyListState, focusRequestToken: Int,
    onFocused: (Int) -> Unit, onSelect: (Int) -> Unit,
    onMoveLeft: () -> Unit, onMoveRight: () -> Unit, modifier: Modifier = Modifier
) {
    val request = remember(focusRequestToken, listState) { BrowserFocusRequest() }
    // Snapshot the target once per explicit request, not once per recomposition/focused row.
    val target = remember(focusRequestToken, listState) { focusedGroupIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(focusRequestToken, listState) {
        if (focusRequestToken > 0 && groups.isNotEmpty() &&
            listState.layoutInfo.visibleItemsInfo.none { it.index == target }) listState.scrollToItem(target)
    }
    if (groups.isEmpty()) Text("No hay grupos disponibles", color = TvMuted, modifier = modifier.padding(12.dp))
    else LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(groups, key = { _, group -> group.id }, contentType = { _, _ -> "group" }) { index, group ->
            val requester = remember { FocusRequester() }
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val selected = group.id == selectedGroupId
            LaunchedEffect(focusRequestToken, request) {
                if (focusRequestToken > 0 && index == target && !request.consumed) {
                    yield()
                    runCatching { requester.requestFocus(); request.consumed = true }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .background(if (selected) Color(0xFF20443E) else TvSurface, RoundedCornerShape(4.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFE1FFF6) else if (selected) TvAccent else Color(0xFF314052), RoundedCornerShape(4.dp))
                .focusRequester(requester)
                .onFocusChanged { if (it.isFocused) onFocused(index) }
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false
                    else when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { onMoveLeft(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onMoveRight(); true }
                        else -> false
                    }
                }
                .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text((if (group.isFavorite || group.id == FavoriteChannelsGroupId) "★ " else "") + group.title,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("${group.count} canales", color = TvMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
