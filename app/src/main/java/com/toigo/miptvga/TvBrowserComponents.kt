package com.toigo.miptvga

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.yield

internal val TvAccent = Color(0xFF41D3B0)
internal val TvSurface = Color(0xFF151D29)
internal val TvMuted = Color(0xFFB6C2D1)

internal enum class TvDestination(val label: String, val icon: Int) {
    TELEVISION("Televisión", R.drawable.ic_nav_tv), FAVORITES("Favoritos", R.drawable.ic_nav_favorites),
    GROUPS("Grupos", R.drawable.ic_nav_groups),
    SEARCH("Buscar", R.drawable.ic_nav_search), GUIDE("Guía", R.drawable.ic_nav_guide),
    FILES("Archivos", R.drawable.ic_nav_files), SETTINGS("Ajustes", R.drawable.ic_nav_settings),
    ABOUT("Acerca de", R.drawable.ic_nav_about), EXIT("Salir", R.drawable.ic_nav_exit)
}

@Composable
internal fun TvNavigationRail(
    compact: Boolean,
    active: TvDestination,
    onSelect: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
    exitRequester: FocusRequester = remember { FocusRequester() },
    requesters: List<FocusRequester> = remember { List(TvDestination.entries.size) { FocusRequester() } },
    memory: BrowserFocusMemory = remember { BrowserFocusMemory() },
    onMoveRight: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        if (!memory.hasFocused) { yield(); runCatching { requesters[TvDestination.TELEVISION.ordinal].requestFocus() } }
    }
    fun focusModifier(item: TvDestination): Modifier = Modifier
        .focusRequester(if (item == TvDestination.EXIT) exitRequester else requesters[item.ordinal])
        .onFocusChanged { if (it.isFocused) { memory.zone = BrowserZone.RAIL; memory.rail = item; memory.hasFocused = true } }
        .onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && onMoveRight != null) {
                onMoveRight(); true
            } else false
        }
    Column(modifier.background(TvSurface, RoundedCornerShape(6.dp)).padding(8.dp)) {
        Text(if (compact) "TV" else "miptvga", color = TvAccent,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TvDestination.entries.filter { it != TvDestination.EXIT }.forEach { item ->
                RailButton(item, compact, item == active, { onSelect(item) }, focusModifier(item))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFF314052))
        RailButton(TvDestination.EXIT, compact, false, { onSelect(TvDestination.EXIT) }, focusModifier(TvDestination.EXIT))
    }
}

@Composable
private fun RailButton(item: TvDestination, compact: Boolean, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semantics { contentDescription = item.label }
        .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFE1FFF6) else if (active) TvAccent else Color.Transparent, RoundedCornerShape(4.dp)),
        interactionSource = source, shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (active) Color(0xFF20443E) else if (focused) Color(0xFF2A3C4A) else TvSurface,
            contentColor = if (active) TvAccent else Color(0xFFE9EDF3))) {
        if (compact && item == TvDestination.EXIT) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(item.icon), null, Modifier.size(22.dp))
                Text("Salir", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Icon(painterResource(item.icon), null, Modifier.size(22.dp))
        }
        if (!compact) {
            Spacer(Modifier.width(8.dp))
            Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ExitConfirmationDialog(onCancel: () -> Unit, onExit: () -> Unit) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { yield(); cancel.requestFocus() }
    AlertDialog(onDismissRequest = onCancel,
        title = { Text("¿Quieres salir de miptvga?") },
        text = { Text("Se detendrá la reproducción. Tus listas, favoritos y ajustes se conservarán.") },
        confirmButton = { TextButton(onClick = onExit) { Text("Salir") } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.focusRequester(cancel)) { Text("Cancelar") } },
        containerColor = TvSurface, tonalElevation = 0.dp)
}

@Composable
internal fun BrowserPlaybackHeader(channel: Channel?, backend: PlaybackBackend, message: String?, error: Boolean, onFullscreen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(channel?.name ?: "Reproductor", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFE9EDF3), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(channel?.group?.takeIf { it.isNotBlank() } ?: "Selecciona un canal y pulsa OK", maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = TvMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onFullscreen, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_nav_fullscreen), "Pantalla completa", Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp)); Text("Ampliar")
            }
        }
        Text(listOfNotNull(message, backend.displayName()).joinToString(" · "), maxLines = 1,
            overflow = TextOverflow.Ellipsis, color = if (error) Color(0xFFEC9292) else TvAccent, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun BrowserEpgCard(current: CurrentProgram?, next: CurrentProgram?, modifier: Modifier = Modifier) {
    // One shared minute tick while this visible card exists; no per-channel timers.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(current) { now = System.currentTimeMillis(); while (current != null) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val formatter = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    Column(modifier.fillMaxWidth().background(TvSurface, RoundedCornerShape(4.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(current?.let { "Ahora · ${it.title}" } ?: "Sin información EPG disponible", color = Color(0xFFE9EDF3),
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        if (current != null) {
            Text("${formatter.format(java.util.Date(current.startTimeMillis))} — ${formatter.format(java.util.Date(current.endTimeMillis))}", color = TvMuted, style = MaterialTheme.typography.labelMedium)
            val progress = ((now - current.startTimeMillis).toFloat() / (current.endTimeMillis - current.startTimeMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = TvAccent, trackColor = Color(0xFF314052))
        }
        Text(next?.let { "Después · ${it.title}" } ?: "Siguiente programa no disponible", color = TvMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}
