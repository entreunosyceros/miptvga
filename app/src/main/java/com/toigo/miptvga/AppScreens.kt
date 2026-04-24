package com.toigo.miptvga

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.format.DateFormat
import android.provider.Settings
import android.view.KeyEvent as AndroidViewKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.util.Date

private const val AboutRepositoryUrl = "https://github.com/sapoclay/m3u8play"
private const val AppDisplayName = "miptvga"
private const val AppFullName = "miptvga (Make IPTV Great Again)"
private const val AppLogoDescription = "Logo de miptvga"
private const val AppByline = "app creada por entreunosycero.net"
private const val SidePanelCompactWidthDp = 320
private const val SidePanelExpandedWidthDp = 368
private val TvSafeAreaPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

private val AppBackgroundColor = Color(0xFF080B10)
private val PanelColor = Color(0xFF11161D)
private val PanelColorElevated = Color(0xFF1A2230)
private val PanelBorderColor = Color(0xFF314052)
private val ChannelRowColor = Color(0xFF121923)
private val ChannelRowSelectedColor = Color(0xFF1E5C91)
private val ButtonSecondaryColor = Color(0xFF233345)
private val PrimaryButtonColor = Color(0xFF2F89D5)
private val SecondaryTextColor = Color(0xFF9AAABD)
private val MutedTextColor = Color(0xFFDCE5EF)
private val InputBackgroundColor = Color(0xFF0D1118)
private val PlayerOverlayColor = Color(0xE60C1016)
private val StatusChipColor = Color(0xFF18212C)
private val StatusChipAccentColor = Color(0xFF214F79)
private val SearchStripColor = Color(0xFF10161E)
private val GroupSectionColor = Color(0xFF0D131B)
private val PanelShape = RoundedCornerShape(2.dp)
private val ItemShape = RoundedCornerShape(2.dp)
private val ChipShape = RoundedCornerShape(2.dp)

private fun PlaylistSource?.sourceTypeLabel(): String {
    return when (this?.type) {
        PlaylistSourceType.URL -> "URL"
        PlaylistSourceType.FILE -> "Archivo"
        PlaylistSourceType.URI -> "URI"
        null -> "Sin origen"
    }
}

private fun PlaylistSource?.sourceDisplayLabel(): String {
    return this?.label?.ifBlank { value } ?: "Todavía no hay lista guardada"
}

@Composable
private fun AppPanel(
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
private fun StatusChip(
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
private fun SectionTitle(
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
private fun AppActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        shape = ItemShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) PrimaryButtonColor else ButtonSecondaryColor,
            contentColor = Color.White,
            disabledContainerColor = PanelColorElevated,
            disabledContentColor = SecondaryTextColor
        )
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun MiniOsdButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onFocus: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

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
                    isFocused -> PanelColorElevated
                    else -> StatusChipColor
                }
            )
            .border(1.dp, if (active || isFocused) PrimaryButtonColor else PanelBorderColor, ItemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
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

@Composable
private fun rememberDeviceTimeFormatter(): java.text.DateFormat {
    val context = LocalContext.current
    return remember(context) { DateFormat.getTimeFormat(context) }
}

private fun java.text.DateFormat.formatClock(millis: Long): String = format(Date(millis))

@Composable
private fun ChannelSearchDialog(
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
private fun EpgSettingsDialog(
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

@Composable
private fun PlayerControlsAutoHideEffect(
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

private fun hasInternalFileAccess(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        else -> true
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun createManageAllFilesIntent(context: Context): Intent {
    return Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun createFallbackManageAllFilesIntent(): Intent {
    return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@UnstableApi
@Composable
internal fun RootScreen(vm: MainViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val fullscreenActive = ui.currentScreen == AppScreen.MAIN && ui.isFullscreen

    BackHandler(enabled = ui.currentScreen != AppScreen.MAIN) {
        if (ui.currentScreen == AppScreen.FILE_BROWSER) {
            vm.closeFileBrowser()
        } else {
            vm.openMain()
        }
    }

    DisposableEffect(activity, fullscreenActive) {
        val targetActivity = activity
        if (targetActivity == null) {
            onDispose { }
        } else {
            val window = targetActivity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)

            WindowCompat.setDecorFitsSystemWindows(window, !fullscreenActive)
            if (fullscreenActive) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }

            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(if (fullscreenActive) PaddingValues(0.dp) else TvSafeAreaPadding)
    ) {
        when (ui.currentScreen) {
            AppScreen.ABOUT -> AboutScreen(onBack = vm::openMain)
            AppScreen.MAIN -> MainScreen(
                ui = ui,
                vm = vm,
                onOpenAbout = vm::openAbout,
                onOpenSettings = vm::openSettings
            )
            AppScreen.FILE_BROWSER -> FileBrowserScreen(ui = ui, vm = vm)
            AppScreen.SEARCH -> SearchScreen(ui = ui, vm = vm)
            AppScreen.GUIDE -> GuideScreen(ui = ui, vm = vm)
            AppScreen.CHANNEL_INFO -> ChannelInfoScreen(ui = ui, vm = vm)
            AppScreen.SETTINGS -> SettingsScreen(
                ui = ui,
                vm = vm,
                onOpenAbout = vm::openAbout,
                onExitApp = { (activity as? MainActivity)?.requestAppExit() }
            )
        }
    }
}

@Composable
private fun WelcomePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            StatusChip(text = "Listo para reproducir", accent = true)
            Image(
                painter = painterResource(id = R.drawable.miptvga),
                contentDescription = AppLogoDescription,
                modifier = Modifier.size(152.dp)
            )
            Text(
                text = AppFullName,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Carga una lista M3U por URL o archivo local y empieza a reproducir al instante.",
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = AppByline,
                color = MutedTextColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AppPanel(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusChip(text = "Acerca de", accent = true)
            Image(
                painter = painterResource(id = R.drawable.miptvga),
                contentDescription = AppLogoDescription,
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = AppFullName,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Aplicación ligera para Android TV y cajas Android que carga listas M3U por URL o archivo local, organiza miles de canales en un panel lateral y reproduce el canal seleccionado en el área central.",
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = AppByline,
                color = SecondaryTextColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppActionButton(
                    text = "Volver",
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Abrir repositorio",
                    onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, AboutRepositoryUrl.toUri()))
                    },
                    modifier = Modifier.weight(1f),
                    primary = true
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(
    ui: UiState,
    vm: MainViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(20.dp)
    ) {
        AppPanel(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle(
                    title = "Buscar canales",
                    subtitle = "Filtra por nombre o grupo y abre el canal directamente"
                )
                StatusChip(text = "${ui.filteredChannels.size} resultados", accent = true)
            }

            OutlinedTextField(
                value = ui.searchQuery,
                onValueChange = vm::updateSearchQuery,
                label = { Text("Canal o grupo") },
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
                    text = "Volver",
                    onClick = vm::openMain,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Limpiar",
                    onClick = { vm.updateSearchQuery("") },
                    modifier = Modifier.weight(1f),
                    enabled = ui.searchQuery.isNotBlank()
                )
                AppActionButton(
                    text = "Guía",
                    onClick = vm::openGuide,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Ajustes",
                    onClick = vm::openSettings,
                    modifier = Modifier.weight(1f)
                )
            }

            ChannelList(
                modifier = Modifier.fillMaxSize(),
                filteredChannels = ui.filteredChannels,
                favoriteIds = ui.favoriteIds,
                favoriteGroupIds = ui.favoriteGroupIds,
                currentPrograms = ui.currentPrograms,
                showChannelLogos = ui.showChannelLogos,
                selectedIndex = ui.selectedIndex,
                selectedVisibleIndex = ui.selectedVisibleIndex,
                onToggleFavorite = vm::toggleFavorite,
                onSelectChannel = {
                    vm.selectChannel(it)
                    vm.openMain()
                }
            )
        }
    }
}

@Composable
private fun GuideScreen(
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
private fun TvGuideHeader(
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
private fun TvGuideProgramPanel(
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
private fun GuideRow(
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
private fun ChannelInfoScreen(
    ui: UiState,
    vm: MainViewModel
) {
    val channel = ui.channels.getOrNull(ui.selectedIndex)
    val currentProgram = channel?.let { currentProgramForChannel(it, ui.currentPrograms) }
    val nextProgram = channel?.let { nextProgramForChannel(it, ui.nextPrograms) }
    val timeFormatter = rememberDeviceTimeFormatter()
    val channelFavoriteId = channel?.let(::favoriteIdForChannel)
    val channelGroupId = channel?.let(::groupIdForChannel)
    val channelIsIndividuallyFavorite = channelFavoriteId?.let { it in ui.favoriteIds } == true
    val channelGroupIsFavorite = channelGroupId?.let { it in ui.favoriteGroupIds } == true
    val channelIsFavorite = channel?.let { isChannelFavorite(it, ui.favoriteIds, ui.favoriteGroupIds) } == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
            .padding(20.dp)
    ) {
        AppPanel(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (channel == null) {
                SectionTitle(
                    title = "Información del canal",
                    subtitle = "Selecciona un canal desde la pantalla principal"
                )
                AppActionButton(text = "Volver", onClick = vm::openMain, modifier = Modifier.width(180.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChannelLogo(
                        showChannelLogos = true,
                        logoUrl = channel.logoUrl,
                        channelName = channel.name,
                        modifier = Modifier
                            .width(120.dp)
                            .height(72.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = channel.name,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = channel.group?.ifBlank { "Sin grupo" } ?: "Sin grupo",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(text = if (channelIsFavorite) "Favorito" else "Normal")
                            if (channelIsIndividuallyFavorite) {
                                StatusChip(text = "Canal favorito")
                            }
                            if (channelGroupIsFavorite) {
                                StatusChip(text = "Grupo favorito")
                            }
                            StatusChip(text = ui.epgStatus, accent = ui.epgSettings.enabled)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppActionButton(
                        text = "Volver",
                        onClick = vm::openMain,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = "Guía",
                        onClick = vm::openGuide,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = if (channelIsIndividuallyFavorite) "Canal ★" else "Canal ☆",
                        onClick = { vm.toggleFavorite(ui.selectedIndex) },
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = if (channelGroupIsFavorite) "Grupo ★" else "Grupo ☆",
                        onClick = vm::toggleFavoriteGroupForSelectedChannel,
                        modifier = Modifier.weight(1f)
                    )
                    AppActionButton(
                        text = "Reproducir",
                        onClick = vm::openMain,
                        modifier = Modifier.weight(1f),
                        primary = true
                    )
                }

                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionTitle(title = "Emisión actual")
                    Text(
                        text = currentProgram?.title ?: "No hay información EPG disponible",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    currentProgram?.let { program ->
                        Text(
                            text = "${timeFormatter.formatClock(program.startTimeMillis)} - ${timeFormatter.formatClock(program.endTimeMillis)}",
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = nextProgram?.let { "Siguiente: ${it.title} · ${timeFormatter.formatClock(it.startTimeMillis)}" }
                            ?: "No hay siguiente programa disponible",
                        color = MutedTextColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                AppPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionTitle(title = "Datos del canal")
                    Text(text = "Grupo: ${channel.group?.ifBlank { "Sin grupo" } ?: "Sin grupo"}", color = Color.White)
                    Text(text = "TVG-ID: ${channel.tvgId?.ifBlank { "No disponible" } ?: "No disponible"}", color = Color.White)
                    Text(text = "Stream: ${channel.streamUrl}", color = SecondaryTextColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
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
        }
    }
}

private fun groupsTitleForGuide(ui: UiState): String {
    val selectedGroupTitle = ui.groups.firstOrNull { it.id == ui.selectedGroupId }?.title ?: "Todos los canales"
    return "${ui.filteredChannels.size} canales · $selectedGroupTitle"
}

@Composable
private fun GuideRow(
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

@Composable
private fun LandingScreen(
    ui: UiState,
    url: String,
    onUrlChange: (String) -> Unit,
    onLoadUrl: () -> Unit,
    onLoadFile: () -> Unit,
    onReloadLast: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AppPanel(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusChip(text = "Android TV · Caja Android", accent = true)
            Image(
                painter = painterResource(id = R.drawable.miptvga),
                contentDescription = AppLogoDescription,
                modifier = Modifier
                    .size(176.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = AppFullName,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Interfaz simple, rápida y pensada para reproducir tus listas M3U con el mínimo consumo posible.",
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = AppByline,
                color = MutedTextColor,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("URL M3U") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !ui.isLoading,
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
                    onClick = onLoadUrl,
                    enabled = !ui.isLoading,
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Cargar archivo",
                    onClick = onLoadFile,
                    enabled = !ui.isLoading,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "Última lista",
                    onClick = onReloadLast,
                    enabled = !ui.isLoading && ui.playlistSource != null,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppActionButton(
                    text = "Ajustes",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
                AppActionButton(
                    text = "About",
                    onClick = onOpenAbout,
                    modifier = Modifier.weight(1f)
                )
            }
            AppPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionTitle(
                    title = "Estado",
                    subtitle = if (ui.isLoading) "Procesando lista..." else ui.status
                )
                StatusChip(text = "Origen: ${ui.playlistSource.sourceTypeLabel()}")
                Text(
                    text = ui.playlistSource.sourceDisplayLabel(),
                    color = SecondaryTextColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
                StatusChip(
                    text = if (ui.channels.isEmpty()) "Aún no hay canales cargados" else "${ui.channels.size} canales disponibles"
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun MainScreen(
    ui: UiState,
    vm: MainViewModel,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    val selectedChannel = ui.channels.getOrNull(ui.selectedIndex)
    val selectedChannelIsFavorite = selectedChannel?.let {
        isChannelFavorite(it, ui.favoriteIds, ui.favoriteGroupIds)
    } == true
    val selectedChannelIsIndividuallyFavorite = selectedChannel?.let {
        favoriteIdForChannel(it) in ui.favoriteIds
    } == true
    val selectedChannelGroupId = selectedChannel?.let(::groupIdForChannel)
    val selectedChannelGroup = selectedChannelGroupId?.let { groupId ->
        ui.groups.firstOrNull { it.id == groupId }
    }
    val selectedChannelGroupIsFavorite = selectedChannelGroup?.isFavorite == true
    val sidePanelWidth = if (ui.channels.size > 10_000) SidePanelExpandedWidthDp.dp else SidePanelCompactWidthDp.dp

    LaunchedEffect(ui.playlistSource?.value, ui.playlistSource?.type) {
        if (ui.playlistSource?.type == PlaylistSourceType.URL && ui.playlistSource.value != url) {
            url = ui.playlistSource.value
        }
    }

    BackHandler(enabled = ui.isFullscreen) {
        vm.exitFullscreen()
    }

    if (ui.channels.isEmpty()) {
        LandingScreen(
            ui = ui,
            url = url,
            onUrlChange = { url = it },
            onLoadUrl = { vm.loadFromUrl(url) },
            onLoadFile = vm::openFileBrowser,
            onReloadLast = vm::reloadLastPlaylist,
            onOpenAbout = onOpenAbout,
            onOpenSettings = onOpenSettings
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundColor)
    ) {
        val useVerticalLayout = maxWidth < 720.dp
        PlayerPanel(
            modifier = when {
                ui.isFullscreen -> Modifier.fillMaxSize()
                useVerticalLayout -> Modifier
                    .fillMaxSize()
                    .padding(top = 360.dp)
                else -> Modifier
                    .fillMaxSize()
                    .padding(start = sidePanelWidth)
            },
            selectedChannel = selectedChannel,
            selectedChannelIsFavorite = selectedChannelIsFavorite,
            selectedChannelIsIndividuallyFavorite = selectedChannelIsIndividuallyFavorite,
            selectedChannelGroup = selectedChannelGroup,
            selectedChannelGroupIsFavorite = selectedChannelGroupIsFavorite,
            showChannelLogos = ui.showChannelLogos,
            playlistSource = ui.playlistSource,
            playbackBackend = ui.playbackBackend,
            videoCompatibilityMode = ui.videoCompatibilityMode,
            playbackMessage = ui.playbackMessage,
            playbackMessageIsError = ui.playbackMessageIsError,
            controlsVisible = ui.controlsVisible,
            controlsVisibilityToken = ui.controlsVisibilityToken,
            isFullscreen = ui.isFullscreen,
            onToggleFullscreen = vm::toggleFullscreen,
            onToggleChannelLogos = vm::toggleChannelLogos,
            onToggleFavorite = vm::toggleFavoriteForSelected,
            onToggleFavoriteGroup = vm::toggleFavoriteGroupForSelectedChannel,
            onToggleVideoCompatibilityMode = vm::toggleVideoCompatibilityMode,
            onOpenSettings = onOpenSettings,
            onInputActivity = { vm.showControls(true) },
            onAutoHide = { vm.showControls(false) },
            onReconnectScheduled = {
                selectedChannel?.let { channel -> vm.onPlaybackReconnectScheduled(channel.name) }
            },
            onPlaybackStarted = {
                selectedChannel?.let { channel -> vm.onPlaybackStarted(channel.name) }
            },
            onPlaybackError = { message ->
                val channelName = selectedChannel?.name ?: "Canal"
                vm.onPlaybackError(channelName, message)
            }
        )

        if (!ui.isFullscreen) {
            SidePanel(
                modifier = if (useVerticalLayout) {
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(360.dp)
                } else {
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                },
                widthOverride = if (useVerticalLayout) null else sidePanelWidth,
                url = url,
                onUrlChange = { url = it },
                playlistSource = ui.playlistSource,
                epgSettings = ui.epgSettings,
                currentPrograms = ui.currentPrograms,
                epgStatus = ui.epgStatus,
                isEpgLoading = ui.isEpgLoading,
                searchQuery = ui.searchQuery,
                groups = ui.groups,
                selectedGroupId = ui.selectedGroupId,
                status = ui.status,
                isLoading = ui.isLoading,
                totalChannelCount = ui.channels.size,
                selectedIndex = ui.selectedIndex,
                selectedVisibleIndex = ui.selectedVisibleIndex,
                filteredChannels = ui.filteredChannels,
                favoriteIds = ui.favoriteIds,
                favoriteGroupIds = ui.favoriteGroupIds,
                showChannelLogos = ui.showChannelLogos,
                onLoadUrl = { vm.loadFromUrl(url) },
                onLoadFile = vm::openFileBrowser,
                onReloadLastPlaylist = vm::reloadLastPlaylist,
                onOpenAbout = onOpenAbout,
                onOpenSearch = vm::openSearch,
                onOpenGuide = vm::openGuide,
                onOpenChannelInfo = vm::openChannelInfo,
                onOpenSettings = vm::openSettings,
                onQueryInputChange = vm::updateSearchQuery,
                onSelectGroup = vm::selectGroup,
                onToggleFavoriteGroup = vm::toggleFavoriteGroup,
                onToggleChannelLogos = vm::toggleChannelLogos,
                onToggleFavorite = vm::toggleFavorite,
                onSelectChannel = vm::selectChannel
            )
        }
    }
}

@Composable
private fun SidePanel(
    modifier: Modifier,
    widthOverride: androidx.compose.ui.unit.Dp?,
    url: String,
    onUrlChange: (String) -> Unit,
    playlistSource: PlaylistSource?,
    epgSettings: EpgSettings,
    currentPrograms: Map<String, CurrentProgram>,
    epgStatus: String,
    isEpgLoading: Boolean,
    searchQuery: String,
    groups: List<ChannelGroup>,
    selectedGroupId: String,
    status: String,
    isLoading: Boolean,
    totalChannelCount: Int,
    selectedIndex: Int,
    selectedVisibleIndex: Int,
    onQueryInputChange: (String) -> Unit,
    filteredChannels: List<ChannelListEntry>,
    favoriteIds: Set<String>,
    favoriteGroupIds: Set<String>,
    showChannelLogos: Boolean,
    onLoadUrl: () -> Unit,
    onLoadFile: () -> Unit,
    onReloadLastPlaylist: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenChannelInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onToggleFavoriteGroup: (String) -> Unit,
    onToggleChannelLogos: () -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onSelectChannel: (Int) -> Unit
) {
    val panelModifier = if (widthOverride != null) {
        modifier.width(widthOverride)
    } else {
        modifier
    }
    val selectedEntry = filteredChannels.firstOrNull { it.originalIndex == selectedIndex }
    val selectedGroupTitle = groups.firstOrNull { it.id == selectedGroupId }?.title ?: "Todos los canales"
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
    val canToggleSelectedGroupFavorite = selectedGroup?.let { isUserSelectableChannelGroup(it.id) } == true
    val selectedGroupDisplayTitle = if (selectedGroup?.isFavorite == true) "★ $selectedGroupTitle" else selectedGroupTitle
    val loadGroupFocusRequester = remember { FocusRequester() }
    var showSourceControls by rememberSaveable { mutableStateOf(false) }
    var showSelectedGroupChannels by rememberSaveable { mutableStateOf(false) }
    var focusLoadGroupButton by rememberSaveable { mutableStateOf(false) }
    var channelListFocusToken by rememberSaveable { mutableIntStateOf(0) }
    var groupListFocusToken by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(focusLoadGroupButton, selectedGroupId) {
        if (focusLoadGroupButton && !showSelectedGroupChannels) {
            loadGroupFocusRequester.requestFocus()
            focusLoadGroupButton = false
        }
    }

    BackHandler(enabled = showSelectedGroupChannels) {
        showSelectedGroupChannels = false
        groupListFocusToken += 1
    }

    Column(
        modifier = panelModifier
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ItemShape)
                    .background(StatusChipAccentColor.copy(alpha = 0.42f))
                    .border(1.dp, PrimaryButtonColor.copy(alpha = 0.7f), ItemShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.miptvga),
                            contentDescription = AppLogoDescription,
                            modifier = Modifier.size(30.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "TV CHANNELS",
                                color = MutedTextColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = groups.firstOrNull { it.id == selectedGroupId }?.title ?: AppDisplayName,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                            Text(
                                text = selectedEntry?.channel?.name ?: playlistSource.sourceDisplayLabel(),
                                color = MutedTextColor,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniOsdButton(
                            text = if (showSourceControls) "Fuente" else "Menú",
                            onClick = { showSourceControls = !showSourceControls },
                            modifier = Modifier.weight(1f),
                            active = showSourceControls
                        )
                        MiniOsdButton(
                            text = "Buscar",
                            onClick = onOpenSearch,
                            modifier = Modifier.weight(1f),
                            active = searchQuery.isNotBlank()
                        )
                        MiniOsdButton(
                            text = "Guía",
                            onClick = onOpenGuide,
                            modifier = Modifier.weight(1f),
                            active = epgSettings.enabled
                        )
                        MiniOsdButton(
                            text = "Ajustes",
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                StatusChip(
                    text = "${filteredChannels.size} / $totalChannelCount"
                )
                StatusChip(
                    text = selectedGroupTitle,
                    accent = true
                )
                StatusChip(text = "${groups.size} grupos")
                if (isLoading) {
                    StatusChip(text = "Cargando")
                }
                if (favoriteIds.isNotEmpty()) {
                    StatusChip(text = "★C ${favoriteIds.size}")
                }
                if (favoriteGroupIds.isNotEmpty()) {
                    StatusChip(text = "★G ${favoriteGroupIds.size}")
                }
                if (epgSettings.enabled || isEpgLoading) {
                    StatusChip(
                        text = if (isEpgLoading) "EPG…" else "EPG",
                        accent = epgSettings.enabled
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ItemShape)
                    .background(SearchStripColor)
                    .border(1.dp, PanelBorderColor, ItemShape)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NAVEGACIÓN",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "Búsqueda activa en $selectedGroupTitle"
                                showSelectedGroupChannels -> "Canales del grupo seleccionado"
                                selectedGroupId == FavoriteChannelsGroupId -> "Canales favoritos por canal o por grupo"
                                else -> "Selecciona un grupo para abrir sus canales"
                            },
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        text = epgStatus,
                        color = SecondaryTextColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppActionButton(
                            text = "Grupos",
                            onClick = {
                                showSelectedGroupChannels = false
                                groupListFocusToken += 1
                            },
                            modifier = Modifier.weight(0.95f),
                            enabled = showSelectedGroupChannels,
                            primary = showSelectedGroupChannels
                        )
                        AppActionButton(
                            text = if (selectedGroupId == AllChannelsGroupId && searchQuery.isBlank()) "Todos" else "Reset",
                            onClick = {
                                onQueryInputChange("")
                                onSelectGroup(AllChannelsGroupId)
                                showSelectedGroupChannels = true
                                channelListFocusToken += 1
                            },
                            modifier = Modifier.weight(1.05f),
                            enabled = !isLoading && (selectedGroupId != AllChannelsGroupId || searchQuery.isNotBlank()),
                            primary = selectedGroupId != AllChannelsGroupId || searchQuery.isNotBlank()
                        )
                    }
                }
            }

            if (showSourceControls) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ItemShape)
                        .background(InputBackgroundColor)
                        .border(1.dp, PanelBorderColor, ItemShape)
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = onUrlChange,
                            label = { Text("URL M3U") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            enabled = !isLoading,
                            singleLine = true,
                            shape = ItemShape,
                            colors = appTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            AppActionButton(
                                text = "URL",
                                onClick = onLoadUrl,
                                enabled = !isLoading,
                                primary = true,
                                modifier = Modifier.weight(1f)
                            )
                            AppActionButton(
                                text = "Archivo",
                                onClick = onLoadFile,
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f)
                            )
                            AppActionButton(
                                text = "Recargar",
                                onClick = onReloadLastPlaylist,
                                enabled = !isLoading && playlistSource != null,
                                modifier = Modifier.weight(1f)
                            )
                            AppActionButton(
                                text = "About",
                                onClick = onOpenAbout,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = if (isLoading) "Procesando lista..." else status,
                            color = SecondaryTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = if (showSelectedGroupChannels) "CANALES" else "GRUPOS TV",
                        color = SecondaryTextColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (showSelectedGroupChannels) selectedGroupDisplayTitle else selectedGroupDisplayTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedGroup?.isFavorite == true) {
                        StatusChip(text = "Grupo favorito")
                    }
                    StatusChip(
                        text = if (showSelectedGroupChannels) filteredChannels.size.toString() else groups.size.toString(),
                        accent = true
                    )
                }
            }
            if (!showSelectedGroupChannels) {
                if (canToggleSelectedGroupFavorite) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppActionButton(
                            text = "Cargar grupo",
                            onClick = {
                                showSelectedGroupChannels = true
                                channelListFocusToken += 1
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(loadGroupFocusRequester),
                            enabled = groups.isNotEmpty() && !isLoading,
                            primary = true
                        )
                        AppActionButton(
                            text = if (selectedGroup?.isFavorite == true) "★ Quitar favorito" else "☆ Marcar grupo",
                            onClick = { onToggleFavoriteGroup(selectedGroupId) },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading,
                            primary = selectedGroup?.isFavorite != true
                        )
                    }
                } else {
                    AppActionButton(
                        text = "Cargar grupo",
                        onClick = {
                            showSelectedGroupChannels = true
                            channelListFocusToken += 1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(loadGroupFocusRequester),
                        enabled = groups.isNotEmpty() && !isLoading,
                        primary = true
                    )
                }
            }
            if (showSelectedGroupChannels) {
                ChannelList(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    filteredChannels = filteredChannels,
                    favoriteIds = favoriteIds,
                    favoriteGroupIds = favoriteGroupIds,
                    currentPrograms = currentPrograms,
                    showChannelLogos = showChannelLogos,
                    selectedIndex = selectedIndex,
                    selectedVisibleIndex = selectedVisibleIndex,
                    focusRequestToken = channelListFocusToken,
                    onBackToGroups = {
                        showSelectedGroupChannels = false
                        groupListFocusToken += 1
                    },
                    onToggleFavorite = onToggleFavorite,
                    onSelectChannel = onSelectChannel
                )
            } else {
                GroupList(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    isLoading = isLoading,
                    focusRequestToken = groupListFocusToken,
                    onSelectGroup = { groupId ->
                        showSelectedGroupChannels = false
                        focusLoadGroupButton = true
                        onSelectGroup(groupId)
                    }
                )
            }
        }
    }
}

@Composable
private fun FileBrowserScreen(
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
private fun FileBrowserList(
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
private fun FileBrowserRow(
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

@Composable
private fun GroupList(
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
private fun GroupRow(
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
private fun ChannelList(
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
private fun ChannelRow(
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
private fun ChannelLogo(
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

private fun playbackProgressFraction(state: PlaybackControllerState): Float {
    if (!state.canSeek || state.durationMs <= 0L) return 0f
    return (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun formatPlaybackClock(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun livePlaybackDelayMs(state: PlaybackControllerState): Long {
    if (!state.isLive || !state.canSeek || state.durationMs <= 0L) return 0L
    return (state.durationMs - state.positionMs).coerceAtLeast(0L)
}

private fun isNearLiveEdge(state: PlaybackControllerState): Boolean {
    return livePlaybackDelayMs(state) <= 3_000L
}

private fun playbackModeLabel(state: PlaybackControllerState, backend: PlaybackBackend): String {
    return when {
        state.isLive && state.canSeek -> "DIRECTO · DVR"
        state.isLive -> "DIRECTO"
        else -> backend.displayName()
    }
}

private fun playbackTimelineLabel(state: PlaybackControllerState): String {
    return when {
        state.isLive && state.canSeek -> {
            val delayMs = livePlaybackDelayMs(state)
            when {
                !state.isPlaying -> "Pausa en directo"
                delayMs <= 3_000L -> "En directo"
                else -> "Retraso · -${formatPlaybackClock(delayMs)}"
            }
        }
        state.isLive -> if (state.isPlaying) "En directo" else "Pausa en directo"
        state.canSeek && state.durationMs > 0L -> {
            "${formatPlaybackClock(state.positionMs)} / ${formatPlaybackClock(state.durationMs)}"
        }
        state.isPlaying -> "Reproduciendo"
        else -> "Pausado"
    }
}

@UnstableApi
@Composable
private fun PlayerPanel(
    modifier: Modifier,
    selectedChannel: Channel?,
    selectedChannelIsFavorite: Boolean,
    selectedChannelIsIndividuallyFavorite: Boolean,
    selectedChannelGroup: ChannelGroup?,
    selectedChannelGroupIsFavorite: Boolean,
    showChannelLogos: Boolean,
    playlistSource: PlaylistSource?,
    playbackBackend: PlaybackBackend,
    videoCompatibilityMode: VideoCompatibilityMode,
    playbackMessage: String?,
    playbackMessageIsError: Boolean,
    controlsVisible: Boolean,
    controlsVisibilityToken: Int,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleChannelLogos: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFavoriteGroup: () -> Unit,
    onToggleVideoCompatibilityMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String?) -> Unit
) {
    val fullscreenTransitionToken = remember(selectedChannel?.playbackUrl) { mutableIntStateOf(0) }
    var playbackControllerState by remember(selectedChannel?.playbackUrl, playbackBackend) {
        mutableStateOf(PlaybackControllerState())
    }
    var playbackControllerActions by remember(selectedChannel?.playbackUrl, playbackBackend) {
        mutableStateOf(PlaybackControllerActions())
    }
    val handleFullscreenToggle = {
        fullscreenTransitionToken.intValue += 1
        onToggleFullscreen()
    }

    PlayerControlsAutoHideEffect(
        controlsVisible = controlsVisible,
        activityToken = controlsVisibilityToken,
        streamUrl = selectedChannel?.streamUrl,
        onAutoHide = onAutoHide
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isFullscreen) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier
                        .padding(12.dp)
                        .clip(PanelShape)
                        .background(PanelColor)
                        .border(1.dp, PanelBorderColor, PanelShape)
                        .padding(10.dp)
                }
            ),
        verticalArrangement = Arrangement.spacedBy(if (isFullscreen) 0.dp else 10.dp)
    ) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selectedChannel?.name ?: "Vista previa",
                        color = Color.White,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = selectedChannel?.group?.takeIf { it.isNotBlank() }
                            ?: "Carga una lista y selecciona un canal para empezar",
                        color = SecondaryTextColor,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    playbackMessage?.let { message ->
                        StatusChip(
                            text = message,
                            accent = !playbackMessageIsError
                        )
                    }
                    AppActionButton(
                        text = "Fullscreen",
                        onClick = onToggleFullscreen,
                        modifier = Modifier.width(122.dp)
                    )
                    AppActionButton(
                        text = "Ajustes",
                        onClick = onOpenSettings,
                        modifier = Modifier.width(112.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (isFullscreen) {
                        Modifier.background(Color.Black)
                    } else {
                        Modifier
                            .clip(PanelShape)
                            .background(Color.Black)
                            .border(1.dp, PanelBorderColor, PanelShape)
                    }
                )
        ) {
            if (selectedChannel == null || selectedChannel.playbackUrl.isBlank()) {
                WelcomePlaceholder()
            } else {
                PlayerSurface(
                    streamUrl = selectedChannel.playbackUrl,
                    requestHeaders = selectedChannel.requestHeaders,
                    playbackBackend = playbackBackend,
                    videoCompatibilityMode = videoCompatibilityMode,
                    fullscreenTransitionToken = fullscreenTransitionToken.intValue,
                    controlsVisible = controlsVisible,
                    onInputActivity = onInputActivity,
                    onAutoHide = onAutoHide,
                    onToggleFullscreen = handleFullscreenToggle,
                    onReconnectScheduled = onReconnectScheduled,
                    onPlaybackStarted = onPlaybackStarted,
                    onPlaybackError = onPlaybackError,
                    onPlaybackControllerStateChanged = { playbackControllerState = it },
                    onPlaybackControllerActionsChanged = { playbackControllerActions = it }
                )
            }

            if (controlsVisible && isFullscreen && selectedChannel != null && selectedChannel.playbackUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (isFullscreen) 12.dp else 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ItemShape)
                            .background(PlayerOverlayColor)
                            .border(1.dp, PanelBorderColor, ItemShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = selectedChannel.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                text = selectedChannel.group?.takeIf { it.isNotBlank() }
                                    ?: playlistSource.sourceDisplayLabel(),
                                color = MutedTextColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(
                                text = when {
                                    selectedChannelGroupIsFavorite && selectedChannelIsIndividuallyFavorite -> "Canal + grupo"
                                    selectedChannelGroupIsFavorite -> "Grupo favorito"
                                    selectedChannelIsIndividuallyFavorite -> "Canal favorito"
                                    selectedChannelIsFavorite -> "Favorito"
                                    else -> "Directo"
                                },
                                accent = true
                            )
                            selectedChannelGroup?.let { group ->
                                StatusChip(text = "${group.count} canales · ${group.title}")
                            }
                            playbackMessage?.let { message ->
                                StatusChip(text = message, accent = !playbackMessageIsError)
                            }
                            if (isFullscreen) {
                                MiniOsdButton(
                                    text = "Salir",
                                    onClick = handleFullscreenToggle,
                                    active = true
                                )
                            }
                        }
                    }
                }

                PlayerBottomControlsOverlay(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    bottomPadding = 88.dp,
                    playbackControllerState = playbackControllerState,
                    playbackControllerActions = playbackControllerActions,
                    selectedChannelIsFavorite = selectedChannelIsIndividuallyFavorite,
                    selectedChannelGroup = selectedChannelGroup,
                    selectedChannelGroupIsFavorite = selectedChannelGroupIsFavorite,
                    playbackBackend = playbackBackend,
                    fullscreenButtonLabel = if (isFullscreen) "Salir" else "Fullscreen",
                    onToggleFavorite = onToggleFavorite,
                    onToggleFavoriteGroup = onToggleFavoriteGroup,
                    onToggleFullscreen = handleFullscreenToggle,
                    onOpenSettings = onOpenSettings,
                    onInputActivity = onInputActivity
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomControlsOverlay(
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp,
    playbackControllerState: PlaybackControllerState,
    playbackControllerActions: PlaybackControllerActions,
    selectedChannelIsFavorite: Boolean,
    selectedChannelGroup: ChannelGroup?,
    selectedChannelGroupIsFavorite: Boolean,
    playbackBackend: PlaybackBackend,
    fullscreenButtonLabel: String,
    onToggleFavorite: () -> Unit,
    onToggleFavoriteGroup: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onInputActivity: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .clip(ItemShape)
                .background(PlayerOverlayColor)
                .border(1.dp, PanelBorderColor, ItemShape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val liveSeekLabelBack = if (playbackControllerState.isLive) "⏪ Atrás 10 s" else "⏪ 10 s"
                val liveSeekLabelForward = if (playbackControllerState.isLive) "10 s ⏩" else "10 s ⏩"

                MiniOsdButton(
                    text = if (playbackControllerState.isPlaying) "⏸ Pausa" else "▶ Play",
                    onClick = {
                        onInputActivity()
                        playbackControllerActions.togglePlayPause()
                    },
                    active = playbackControllerState.isPlaying,
                    onFocus = onInputActivity
                )

                if (playbackControllerState.canSeek) {
                    MiniOsdButton(
                        text = liveSeekLabelBack,
                        onClick = {
                            onInputActivity()
                            playbackControllerActions.seekBack()
                        },
                        onFocus = onInputActivity
                    )
                    MiniOsdButton(
                        text = liveSeekLabelForward,
                        onClick = {
                            onInputActivity()
                            playbackControllerActions.seekForward()
                        },
                        onFocus = onInputActivity
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                StatusChip(
                    text = playbackModeLabel(playbackControllerState, playbackBackend),
                    accent = playbackControllerState.isLive || playbackControllerState.isPlaying
                )
                Text(
                    text = playbackTimelineLabel(playbackControllerState),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (playbackControllerState.isLive && playbackControllerState.canSeek) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(
                        text = if (isNearLiveEdge(playbackControllerState)) "Punto en directo" else "Timeshift activo",
                        accent = isNearLiveEdge(playbackControllerState)
                    )
                    StatusChip(
                        text = if (isNearLiveEdge(playbackControllerState)) {
                            "Sin retraso"
                        } else {
                            "Retraso ${formatPlaybackClock(livePlaybackDelayMs(playbackControllerState))}"
                        }
                    )
                }
            } else if (playbackControllerState.canSeek && playbackControllerState.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(ChipShape)
                        .background(StatusChipColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(playbackProgressFraction(playbackControllerState))
                            .fillMaxHeight()
                            .background(PrimaryButtonColor)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniOsdButton(
                    text = if (selectedChannelIsFavorite) "★ Canal" else "☆ Canal",
                    onClick = {
                        onInputActivity()
                        onToggleFavorite()
                    },
                    active = selectedChannelIsFavorite,
                    onFocus = onInputActivity
                )
                selectedChannelGroup?.let { group ->
                    MiniOsdButton(
                        text = if (selectedChannelGroupIsFavorite) "★ Grupo · ${group.count}" else "☆ Grupo · ${group.count}",
                        onClick = {
                            onInputActivity()
                            onToggleFavoriteGroup()
                        },
                        active = selectedChannelGroupIsFavorite,
                        onFocus = onInputActivity
                    )
                }
                MiniOsdButton(
                    text = fullscreenButtonLabel,
                    onClick = {
                        onInputActivity()
                        onToggleFullscreen()
                    },
                    onFocus = onInputActivity
                )
                MiniOsdButton(
                    text = "Ajustes",
                    onClick = {
                        onInputActivity()
                        onOpenSettings()
                    },
                    onFocus = onInputActivity
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun FullscreenPlayerPanel(
    modifier: Modifier,
    selectedChannel: Channel?,
    selectedChannelIsFavorite: Boolean,
    selectedChannelIsIndividuallyFavorite: Boolean,
    selectedChannelGroup: ChannelGroup?,
    selectedChannelGroupIsFavorite: Boolean,
    showChannelLogos: Boolean,
    playlistSource: PlaylistSource?,
    playbackBackend: PlaybackBackend,
    videoCompatibilityMode: VideoCompatibilityMode,
    playbackMessage: String?,
    playbackMessageIsError: Boolean,
    controlsVisible: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleChannelLogos: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFavoriteGroup: () -> Unit,
    onToggleVideoCompatibilityMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onInputActivity: () -> Unit,
    onAutoHide: () -> Unit,
    onReconnectScheduled: () -> Unit,
    onPlaybackStarted: () -> Unit,
    onPlaybackError: (String?) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (selectedChannel == null || selectedChannel.playbackUrl.isBlank()) {
            WelcomePlaceholder()
        } else {
            PlayerSurface(
                streamUrl = selectedChannel.playbackUrl,
                requestHeaders = selectedChannel.requestHeaders,
                playbackBackend = playbackBackend,
                videoCompatibilityMode = videoCompatibilityMode,
                fullscreenTransitionToken = 0,
                controlsVisible = controlsVisible,
                onInputActivity = onInputActivity,
                onAutoHide = onAutoHide,
                onToggleFullscreen = onToggleFullscreen,
                onReconnectScheduled = onReconnectScheduled,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackError = onPlaybackError,
                onPlaybackControllerStateChanged = { },
                onPlaybackControllerActionsChanged = { }
            )
        }

        if (controlsVisible && selectedChannel != null && selectedChannel.playbackUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ItemShape)
                        .background(PlayerOverlayColor)
                        .border(1.dp, PanelBorderColor, ItemShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = selectedChannel.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1
                        )
                        Text(
                            text = selectedChannel.group?.takeIf { it.isNotBlank() }
                                ?: playlistSource.sourceDisplayLabel(),
                            color = MutedTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusChip(
                            text = when {
                                selectedChannelGroupIsFavorite && selectedChannelIsIndividuallyFavorite -> "Canal + grupo"
                                selectedChannelGroupIsFavorite -> "Grupo favorito"
                                selectedChannelIsIndividuallyFavorite -> "Canal favorito"
                                selectedChannelIsFavorite -> "Favorito"
                                else -> "Directo"
                            },
                            accent = true
                        )
                        selectedChannelGroup?.let { group ->
                            StatusChip(text = "${group.count} canales · ${group.title}")
                        }
                        playbackMessage?.let { message ->
                            StatusChip(text = message, accent = !playbackMessageIsError)
                        }
                        MiniOsdButton(
                            text = "Salir",
                            onClick = onToggleFullscreen,
                            active = true
                        )
                    }
                }
            }

            PlayerBottomControlsOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                bottomPadding = 32.dp,
                playbackControllerState = PlaybackControllerState(
                    isPlaying = true,
                    isLive = true
                ),
                playbackControllerActions = PlaybackControllerActions(),
                selectedChannelIsFavorite = selectedChannelIsIndividuallyFavorite,
                selectedChannelGroup = selectedChannelGroup,
                selectedChannelGroupIsFavorite = selectedChannelGroupIsFavorite,
                playbackBackend = playbackBackend,
                fullscreenButtonLabel = "Salir",
                onToggleFavorite = onToggleFavorite,
                onToggleFavoriteGroup = onToggleFavoriteGroup,
                onToggleFullscreen = onToggleFullscreen,
                onOpenSettings = onOpenSettings,
                onInputActivity = onInputActivity
            )
        }
    }
}

