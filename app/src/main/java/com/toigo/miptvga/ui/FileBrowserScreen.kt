package com.toigo.miptvga.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toigo.miptvga.AppScreen
import com.toigo.miptvga.FileBrowserEntry
import com.toigo.miptvga.MainViewModel
import com.toigo.miptvga.UiState
import com.toigo.miptvga.ui.components.AppActionButton
import com.toigo.miptvga.ui.components.AppBackgroundColor
import com.toigo.miptvga.ui.components.AppPanel
import com.toigo.miptvga.ui.components.ChannelRowColor
import com.toigo.miptvga.ui.components.ItemShape
import com.toigo.miptvga.ui.components.PanelBorderColor
import com.toigo.miptvga.ui.components.PanelColorElevated
import com.toigo.miptvga.ui.components.PrimaryButtonColor
import com.toigo.miptvga.ui.components.SecondaryTextColor
import com.toigo.miptvga.ui.components.SectionTitle
import com.toigo.miptvga.ui.components.StatusChip
import com.toigo.miptvga.ui.components.createFallbackManageAllFilesIntent
import com.toigo.miptvga.ui.components.createManageAllFilesIntent
import com.toigo.miptvga.ui.components.hasInternalFileAccess

@Composable
internal fun FileBrowserScreen(
    ui: UiState,
    vm: MainViewModel
) {
    val context = LocalContext.current
    var accessStateVersion by remember { mutableIntStateOf(0) }
    val hasStorageAccess = remember(accessStateVersion, context) { hasInternalFileAccess(context) }

    val readPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        accessStateVersion++
        if (granted || hasInternalFileAccess(context)) {
            vm.loadFileBrowserRoots(context)
        } else {
            vm.updateFileBrowserStatus("Se necesita permiso para explorar archivos locales")
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        accessStateVersion++
        if (hasInternalFileAccess(context)) {
            vm.loadFileBrowserRoots(context)
        } else {
            vm.updateFileBrowserStatus("Concede acceso a todos los archivos para usar el explorador interno")
        }
    }

    LaunchedEffect(ui.currentScreen, hasStorageAccess) {
        if (ui.currentScreen != AppScreen.FILE_BROWSER) return@LaunchedEffect

        if (!hasStorageAccess) {
            vm.updateFileBrowserStatus("Concede acceso al almacenamiento para usar el explorador interno")
        } else if (ui.fileBrowserEntries.isEmpty() && !ui.isFileBrowserLoading) {
            vm.loadFileBrowserRoots(context)
        }
    }

    val requestStorageAccess = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = try {
                createManageAllFilesIntent(context)
            } catch (_: Throwable) {
                createFallbackManageAllFilesIntent()
            }

            try {
                manageStorageLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                try {
                    manageStorageLauncher.launch(createFallbackManageAllFilesIntent())
                } catch (_: ActivityNotFoundException) {
                    vm.updateFileBrowserStatus("No se pudo abrir la pantalla de permisos del sistema")
                }
            }
        } else {
            readPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(20.dp)
    ) {
        AppPanel(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    title = "Explorador interno",
                    subtitle = "Navega por carpetas y selecciona archivos .m3u o .m3u8"
                )
                StatusChip(
                    text = if (hasStorageAccess) "Acceso concedido" else "Permiso requerido",
                    accent = hasStorageAccess
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppActionButton(
                    text = "Volver",
                    onClick = vm::closeFileBrowser,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = if (ui.fileBrowserCurrentPath == null) "Ubicaciones" else "Subir",
                    onClick = { vm.navigateFileBrowserUp(context) },
                    enabled = hasStorageAccess && !ui.isFileBrowserLoading,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = if (hasStorageAccess) "Actualizar" else "Conceder acceso",
                    onClick = {
                        if (hasStorageAccess) {
                            if (ui.fileBrowserCurrentPath.isNullOrBlank()) {
                                vm.loadFileBrowserRoots(context)
                            } else {
                                vm.openFileBrowserDirectory(ui.fileBrowserCurrentPath)
                            }
                        } else {
                            requestStorageAccess()
                        }
                    },
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            }

            StatusChip(
                text = ui.fileBrowserCurrentPath ?: "Ubicaciones disponibles",
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (ui.isFileBrowserLoading) "Cargando contenido..." else ui.fileBrowserStatus,
                color = SecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium
            )

            AppPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasStorageAccess) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Para explorar archivos locales desde la propia app necesitas conceder acceso al almacenamiento del dispositivo.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "En muchas cajas Android este permiso aparece como acceso a todos los archivos.",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (ui.fileBrowserEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay carpetas o listas M3U accesibles en esta ubicación.",
                            color = SecondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    FileBrowserList(
                        entries = ui.fileBrowserEntries,
                        isLoading = ui.isFileBrowserLoading,
                        onOpenEntry = { entry ->
                            if (entry.isDirectory) {
                                vm.openFileBrowserDirectory(entry.path)
                            } else {
                                vm.loadFromFile(entry.path)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FileBrowserList(
    entries: List<FileBrowserEntry>,
    isLoading: Boolean,
    onOpenEntry: (FileBrowserEntry) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = entries,
            key = { it.path },
            contentType = { if (it.isDirectory) "directory" else "playlist" }
        ) { entry ->
            FileBrowserRow(
                entry = entry,
                enabled = !isLoading,
                onOpen = onOpenEntry
            )
        }
    }
}

@Composable
internal fun FileBrowserRow(
    entry: FileBrowserEntry,
    enabled: Boolean,
    onOpen: (FileBrowserEntry) -> Unit
) {
    val titlePrefix = if (entry.isDirectory) "[DIR]" else "[M3U]"
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundColor = if (isFocused) PanelColorElevated else ChannelRowColor
    val borderColor = if (isFocused) PrimaryButtonColor else PanelBorderColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, ItemShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onOpen(entry) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .focusable(enabled = enabled, interactionSource = interactionSource)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "$titlePrefix ${entry.name}",
                color = Color.White,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = entry.subtitle,
                color = SecondaryTextColor,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
