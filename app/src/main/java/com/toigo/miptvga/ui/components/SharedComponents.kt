package com.toigo.miptvga.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.toigo.miptvga.ControlsAutoHideDelayMillis
import com.toigo.miptvga.EpgSettings
import com.toigo.miptvga.PlaylistSource
import com.toigo.miptvga.PlaylistSourceType
import kotlinx.coroutines.delay
import java.util.Date

// ── String constants ────────────────────────────────────────────────────────────

internal const val AboutRepositoryUrl = "https://github.com/sapoclay/m3u8play"
internal const val AppDisplayName = "miptvga"
internal const val AppFullName = "miptvga (Make IPTV Great Again)"
internal const val AppLogoDescription = "Logo de miptvga"
internal const val AppByline = "app creada por entreunosycero.net"
internal const val SidePanelCompactWidthDp = 320
internal const val SidePanelExpandedWidthDp = 368

// ── Layout constants ────────────────────────────────────────────────────────────

internal val TvSafeAreaPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

// ── Color constants ─────────────────────────────────────────────────────────────

internal val AppBackgroundColor = Color(0xFF080B10)
internal val PanelColor = Color(0xFF11161D)
internal val PanelColorElevated = Color(0xFF1A2230)
internal val PanelBorderColor = Color(0xFF314052)
internal val ChannelRowColor = Color(0xFF121923)
internal val ChannelRowSelectedColor = Color(0xFF1E5C91)
internal val ButtonSecondaryColor = Color(0xFF233345)
internal val PrimaryButtonColor = Color(0xFF2F89D5)
internal val SecondaryTextColor = Color(0xFF9AAABD)
internal val MutedTextColor = Color(0xFFDCE5EF)
internal val InputBackgroundColor = Color(0xFF0D1118)
internal val PlayerOverlayColor = Color(0xE60C1016)
internal val StatusChipColor = Color(0xFF18212C)
internal val StatusChipAccentColor = Color(0xFF214F79)
internal val SearchStripColor = Color(0xFF10161E)
internal val GroupSectionColor = Color(0xFF0D131B)
internal val FocusHighlightColor = Color(0xFF4DA6FF)
internal val FocusedPrimaryColor = Color(0xFF3A9BE8)
internal val FocusedSecondaryColor = Color(0xFF2A4460)

// ── Shape constants ─────────────────────────────────────────────────────────────

internal val PanelShape = RoundedCornerShape(4.dp)
internal val ItemShape = RoundedCornerShape(4.dp)
internal val ChipShape = RoundedCornerShape(4.dp)

// ── Extension helpers ───────────────────────────────────────────────────────────

internal fun PlaylistSource?.sourceTypeLabel(): String {
    return when (this?.type) {
        PlaylistSourceType.URL -> "URL"
        PlaylistSourceType.FILE -> "Archivo"
        PlaylistSourceType.URI -> "URI"
        null -> "Sin origen"
    }
}

internal fun PlaylistSource?.sourceDisplayLabel(): String {
    return this?.label?.ifBlank { value } ?: "Todavía no hay lista guardada"
}

// ── Composable UI components ────────────────────────────────────────────────────

@Composable
internal fun AppPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(PanelColor)
            .border(1.dp, PanelBorderColor, PanelShape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
internal fun StatusChip(
    text: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ChipShape)
            .background(if (accent) StatusChipAccentColor else StatusChipColor)
            .border(1.dp, if (accent) PrimaryButtonColor.copy(alpha = 0.45f) else PanelBorderColor, ChipShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (accent) Color.White else SecondaryTextColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
internal fun SectionTitle(
    title: String,
    subtitle: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = SecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun AppActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val highlighted = isFocused || isHovered

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(
                if (highlighted && enabled) Modifier.border(
                    2.dp,
                    FocusHighlightColor,
                    ItemShape
                ) else Modifier
            ),
        shape = ItemShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                highlighted && enabled -> if (primary) FocusedPrimaryColor else FocusedSecondaryColor
                primary -> PrimaryButtonColor
                else -> ButtonSecondaryColor
            },
            contentColor = Color.White,
            disabledContainerColor = PanelColorElevated,
            disabledContentColor = SecondaryTextColor
        )
    ) {
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun MiniOsdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onFocus: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val highlighted = isFocused || isHovered

    LaunchedEffect(isFocused, onFocus) {
        if (isFocused) {
            onFocus?.invoke()
        }
    }

    Box(
        modifier = modifier
            .clip(ItemShape)
            .background(
                when {
                    active -> PrimaryButtonColor
                    highlighted -> FocusedSecondaryColor
                    else -> StatusChipColor
                }
            )
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = when {
                    highlighted -> FocusHighlightColor
                    active -> PrimaryButtonColor
                    else -> PanelBorderColor
                },
                shape = ItemShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource = interactionSource)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
internal fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = MutedTextColor,
    focusedContainerColor = InputBackgroundColor,
    unfocusedContainerColor = InputBackgroundColor,
    disabledContainerColor = InputBackgroundColor.copy(alpha = 0.7f),
    focusedBorderColor = PrimaryButtonColor,
    unfocusedBorderColor = PanelBorderColor,
    disabledBorderColor = PanelBorderColor,
    focusedLabelColor = MutedTextColor,
    unfocusedLabelColor = SecondaryTextColor,
    disabledLabelColor = SecondaryTextColor,
    cursorColor = PrimaryButtonColor
)

// ── Utility functions ───────────────────────────────────────────────────────────

@Composable
internal fun rememberDeviceTimeFormatter(): java.text.DateFormat {
    val context = LocalContext.current
    return remember(context) { DateFormat.getTimeFormat(context) }
}

internal fun java.text.DateFormat.formatClock(millis: Long): String = format(Date(millis))

internal fun hasInternalFileAccess(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        else -> true
    }
}

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

internal fun createManageAllFilesIntent(context: Context): Intent {
    return Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun createFallbackManageAllFilesIntent(): Intent {
    return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

// ── Dialog composables ──────────────────────────────────────────────────────────

@Composable
internal fun ChannelSearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onQueryPreview: (String) -> Unit,
    onConfirm: () -> Unit,
    onClear: () -> Unit
) {
    var draftQuery by rememberSaveable { mutableStateOf(initialQuery) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Buscar canal",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Escribe parte del nombre del canal o del grupo. La lista lateral se actualizará al momento.",
                    color = SecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = draftQuery,
                    onValueChange = {
                        draftQuery = it
                        onQueryPreview(it)
                    },
                    label = { Text("Canal o grupo") },
                    singleLine = true,
                    shape = ItemShape,
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Aplicar", color = Color.White)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onClear) {
                    Text("Limpiar", color = SecondaryTextColor)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = SecondaryTextColor)
                }
            }
        },
        containerColor = PanelColor,
        tonalElevation = 0.dp
    )
}

@Composable
internal fun EpgSettingsDialog(
    settings: EpgSettings,
    isLoading: Boolean,
    status: String,
    onDismiss: () -> Unit,
    onSave: (Boolean, String) -> Unit,
    onRefresh: () -> Unit
) {
    var enabled by rememberSaveable { mutableStateOf(settings.enabled) }
    var url by rememberSaveable { mutableStateOf(settings.url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configurar EPG",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Guía EPG/XMLTV",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = status,
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL XMLTV") },
                    enabled = enabled,
                    singleLine = true,
                    shape = ItemShape,
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Usa la URL de tu guía XMLTV. El programa actual aparecerá debajo del grupo en cada canal.",
                    color = SecondaryTextColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(enabled, url) }) {
                Text("Guardar", color = Color.White)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRefresh, enabled = enabled && url.isNotBlank() && !isLoading) {
                    Text("Recargar", color = if (enabled && url.isNotBlank() && !isLoading) Color.White else SecondaryTextColor)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = SecondaryTextColor)
                }
            }
        },
        containerColor = PanelColor,
        tonalElevation = 0.dp
    )
}

// ── Player auto-hide effect ─────────────────────────────────────────────────────

@Composable
internal fun PlayerControlsAutoHideEffect(
    controlsVisible: Boolean,
    activityToken: Int,
    streamUrl: String?,
    onAutoHide: () -> Unit
) {
    LaunchedEffect(controlsVisible, activityToken, streamUrl) {
        if (!controlsVisible || streamUrl.isNullOrBlank()) return@LaunchedEffect
        delay(ControlsAutoHideDelayMillis)
        onAutoHide()
    }
}
