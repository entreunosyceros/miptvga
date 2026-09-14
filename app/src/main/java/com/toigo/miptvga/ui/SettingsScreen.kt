package com.toigo.miptvga.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toigo.miptvga.MainViewModel
import com.toigo.miptvga.PlaybackBackend
import com.toigo.miptvga.PlaylistSourceType
import com.toigo.miptvga.UiState
import com.toigo.miptvga.formatStorageSize
import com.toigo.miptvga.statusLabel
import com.toigo.miptvga.ui.components.AppActionButton
import com.toigo.miptvga.ui.components.AppBackgroundColor
import com.toigo.miptvga.ui.components.AppPanel
import com.toigo.miptvga.ui.components.ItemShape
import com.toigo.miptvga.ui.components.PanelBorderColor
import com.toigo.miptvga.ui.components.PanelColor
import com.toigo.miptvga.ui.components.PanelColorElevated
import com.toigo.miptvga.ui.components.SecondaryTextColor
import com.toigo.miptvga.ui.components.SectionTitle
import com.toigo.miptvga.ui.components.StatusChip
import com.toigo.miptvga.ui.components.appTextFieldColors
import com.toigo.miptvga.ui.components.sourceDisplayLabel

@Composable
internal fun SettingsScreen(
    ui: UiState,
    vm: MainViewModel,
    onOpenAbout: () -> Unit,
    onExitApp: () -> Unit
) {
    var playlistUrl by rememberSaveable(ui.playlistSource?.value, ui.playlistSource?.type) {
        mutableStateOf(
            if (ui.playlistSource?.type == PlaylistSourceType.URL) ui.playlistSource.value else ""
        )
    }
    var epgEnabled by rememberSaveable(ui.epgSettings.enabled, ui.epgSettings.url) {
        mutableStateOf(ui.epgSettings.enabled)
    }
    var epgUrl by rememberSaveable(ui.epgSettings.enabled, ui.epgSettings.url) {
        mutableStateOf(ui.epgSettings.url)
    }
    var xtreamKeepAliveEnabled by rememberSaveable(
        ui.xtreamKeepAliveSettings.enabled,
        ui.xtreamKeepAliveSettings.intervalSeconds
    ) {
        mutableStateOf(ui.xtreamKeepAliveSettings.enabled)
    }
    var xtreamKeepAliveIntervalSeconds by rememberSaveable(
        ui.xtreamKeepAliveSettings.enabled,
        ui.xtreamKeepAliveSettings.intervalSeconds
    ) {
        mutableIntStateOf(ui.xtreamKeepAliveSettings.intervalSeconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(20.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle(
                            title = "Ajustes",
                            subtitle = "Configura lista, reproducción y guía EPG"
                        )
                        StatusChip(text = ui.status, accent = !ui.isLoading)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppActionButton(text = "Volver", onClick = vm::openMain, modifier = Modifier.weight(1f))
                        AppActionButton(text = "Buscar", onClick = vm::openSearch, modifier = Modifier.weight(1f))
                        AppActionButton(text = "Guía", onClick = vm::openGuide, modifier = Modifier.weight(1f))
                        AppActionButton(text = "About", onClick = onOpenAbout, modifier = Modifier.weight(1f))
                        AppActionButton(text = "Salir", onClick = onExitApp, modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionTitle(title = "Lista M3U", subtitle = ui.playlistSource.sourceDisplayLabel())
                    OutlinedTextField(
                        value = playlistUrl,
                        onValueChange = { playlistUrl = it },
                        label = { Text("URL M3U") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        shape = ItemShape,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppActionButton(
                            text = "Cargar URL",
                            onClick = { vm.loadFromUrl(playlistUrl) },
                            modifier = Modifier.weight(1f),
                            primary = true,
                            enabled = playlistUrl.isNotBlank() && !ui.isLoading
                        )
                        AppActionButton(
                            text = "Archivo",
                            onClick = vm::openFileBrowser,
                            modifier = Modifier.weight(1f),
                            enabled = !ui.isLoading
                        )
                        AppActionButton(
                            text = "Recargar",
                            onClick = vm::reloadLastPlaylist,
                            modifier = Modifier.weight(1f),
                            enabled = ui.playlistSource != null && !ui.isLoading
                        )
                    }
                }
            }

            item {
                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionTitle(title = "Reproducción")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Backend de reproducción", color = Color.White)
                            Text(ui.playbackBackend.displayName(), color = SecondaryTextColor, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppActionButton(
                                text = "VLC",
                                onClick = { vm.setPlaybackBackend(PlaybackBackend.VLC) },
                                modifier = Modifier.width(110.dp),
                                primary = ui.playbackBackend == PlaybackBackend.VLC
                            )
                            AppActionButton(
                                text = "ExoPlayer",
                                onClick = { vm.setPlaybackBackend(PlaybackBackend.EXOPLAYER) },
                                modifier = Modifier.width(140.dp),
                                primary = ui.playbackBackend == PlaybackBackend.EXOPLAYER
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mostrar logos de canales", color = Color.White)
                        Switch(checked = ui.showChannelLogos, onCheckedChange = { vm.toggleChannelLogos() })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Compatibilidad de vídeo", color = Color.White)
                            Text(ui.videoCompatibilityMode.displayName(), color = SecondaryTextColor, style = MaterialTheme.typography.bodySmall)
                        }
                        AppActionButton(text = "Cambiar", onClick = vm::toggleVideoCompatibilityMode, modifier = Modifier.width(150.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("Keepalive Xtream", color = Color.White)
                            Text(
                                ui.xtreamKeepAliveSettings.statusLabel(),
                                color = SecondaryTextColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = xtreamKeepAliveEnabled,
                            onCheckedChange = { xtreamKeepAliveEnabled = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Intervalo",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(72.dp)
                        )
                        listOf(30, 45, 60).forEach { interval ->
                            AppActionButton(
                                text = "${interval}s",
                                onClick = { xtreamKeepAliveIntervalSeconds = interval },
                                modifier = Modifier.width(92.dp),
                                enabled = xtreamKeepAliveEnabled,
                                primary = xtreamKeepAliveIntervalSeconds == interval
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        AppActionButton(
                            text = "Guardar",
                            onClick = {
                                vm.updateXtreamKeepAliveSettings(
                                    enabled = xtreamKeepAliveEnabled,
                                    intervalSeconds = xtreamKeepAliveIntervalSeconds
                                )
                            },
                            modifier = Modifier.width(130.dp),
                            primary = true
                        )
                    }
                }
            }

            item {
                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionTitle(title = "Guía EPG/XMLTV", subtitle = ui.epgStatus)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activar guía EPG", color = Color.White)
                        Switch(checked = epgEnabled, onCheckedChange = { epgEnabled = it })
                    }
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        enabled = epgEnabled,
                        label = { Text("URL XMLTV") },
                        singleLine = true,
                        shape = ItemShape,
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppActionButton(
                            text = "Guardar",
                            onClick = { vm.updateEpgSettings(epgEnabled, epgUrl) },
                            modifier = Modifier.weight(1f),
                            primary = true
                        )
                        AppActionButton(
                            text = "Recargar EPG",
                            onClick = vm::refreshEpg,
                            modifier = Modifier.weight(1f),
                            enabled = epgEnabled && epgUrl.isNotBlank() && !ui.isEpgLoading
                        )
                    }
                }
            }

            item {
                MaintenanceSection(vm = vm)
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
internal fun MaintenanceSection(vm: MainViewModel) {
    val storageInfo by vm.storageInfo.collectAsStateWithLifecycle()
    var showResetPreferencesDialog by rememberSaveable { mutableStateOf(false) }
    var showResetAppDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshStorageInfo() }

    if (showResetPreferencesDialog) {
        AlertDialog(
            onDismissRequest = { showResetPreferencesDialog = false },
            title = { Text("Restablecer preferencias", color = Color.White) },
            text = {
                Text(
                    "Se borrarán todos los favoritos, ajustes EPG, keepalive y la lista guardada. Los archivos de caché no se borrarán.",
                    color = SecondaryTextColor
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetPreferences()
                    showResetPreferencesDialog = false
                }) { Text("Restablecer", color = Color(0xFFE57373)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetPreferencesDialog = false }) {
                    Text("Cancelar", color = SecondaryTextColor)
                }
            },
            containerColor = PanelColor,
            tonalElevation = 0.dp
        )
    }

    if (showResetAppDialog) {
        AlertDialog(
            onDismissRequest = { showResetAppDialog = false },
            title = { Text("Restablecer aplicación completa", color = Color.White) },
            text = {
                Text(
                    "Se borrarán TODOS los datos: caché de reproducción, imágenes, preferencias, favoritos y la lista guardada. La aplicación volverá a su estado inicial.",
                    color = Color(0xFFE57373)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetApp()
                    showResetAppDialog = false
                }) { Text("Borrar todo", color = Color(0xFFE57373)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetAppDialog = false }) {
                    Text("Cancelar", color = SecondaryTextColor)
                }
            },
            containerColor = PanelColor,
            tonalElevation = 0.dp
        )
    }

    AppPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle(
            title = "Mantenimiento",
            subtitle = "Almacenamiento total: ${storageInfo.totalBytes.formatStorageSize()}"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StorageInfoCard(
                label = "Caché reproducción",
                size = storageInfo.playbackCacheBytes.formatStorageSize(),
                modifier = Modifier.weight(1f)
            )
            StorageInfoCard(
                label = "Caché imágenes",
                size = storageInfo.imageCacheBytes.formatStorageSize(),
                modifier = Modifier.weight(1f)
            )
            StorageInfoCard(
                label = "Otros datos",
                size = storageInfo.appCacheBytes.formatStorageSize(),
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Limpieza de caché",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AppActionButton(
                text = "Limpiar caché reproducción",
                onClick = vm::clearPlaybackCache,
                modifier = Modifier.weight(1f)
            )
            AppActionButton(
                text = "Limpiar caché imágenes",
                onClick = vm::clearImageCache,
                modifier = Modifier.weight(1f)
            )
            AppActionButton(
                text = "Limpiar todo el caché",
                onClick = vm::clearAllCache,
                modifier = Modifier.weight(1f),
                primary = true
            )
        }

        Text(
            text = "Restablecer datos",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AppActionButton(
                text = "Restablecer preferencias",
                onClick = { showResetPreferencesDialog = true },
                modifier = Modifier.weight(1f)
            )
            AppActionButton(
                text = "Restablecer aplicación",
                onClick = { showResetAppDialog = true },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "El restablecimiento de preferencias borra favoritos, EPG y lista guardada. " +
                "El restablecimiento completo borra además todo el caché.",
            color = SecondaryTextColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun StorageInfoCard(
    label: String,
    size: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ItemShape)
            .background(PanelColorElevated)
            .border(1.dp, PanelBorderColor, ItemShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                color = SecondaryTextColor,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = size,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
