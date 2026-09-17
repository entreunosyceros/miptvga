package com.toigo.miptvga

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale

private const val SearchDebounceMillis = 220L
private const val EpgRefreshIntervalMillis = 60_000L
private const val XtreamKeepAliveInitialDelayMillis = 2_000L
private const val XtreamKeepAliveRetryDelayMillis = 8_000L
private const val AutoEpgChannelLimit = 40_000

@UnstableApi
internal class MainViewModel(
    private val appContext: Context,
    private val repository: PlaylistRepository,
    private val epgRepository: EpgRepository,
    private val fileBrowserRepository: FileBrowserRepository,
    private val lastPlaylistStore: LastPlaylistStore,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val initialEpgSettings = lastPlaylistStore.readEpgSettings()
    private val initialXtreamKeepAliveSettings = lastPlaylistStore.readXtreamKeepAliveSettings()

    private val _uiState = MutableStateFlow(
        UiState(
            favoriteIds = lastPlaylistStore.readFavoriteIds(),
            favoriteGroupIds = lastPlaylistStore.readFavoriteGroupIds(),
            watchedIds = lastPlaylistStore.readWatchedIds(),
            epgSettings = initialEpgSettings,
            epgStatus = initialEpgSettings.statusLabel(),
            xtreamKeepAliveSettings = initialXtreamKeepAliveSettings,
            showChannelLogos = lastPlaylistStore.readShowChannelLogos(),
            playbackBackend = lastPlaylistStore.readPlaybackBackend(),
            videoCompatibilityMode = lastPlaylistStore.readVideoCompatibilityMode()
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var indexedChannels: List<ChannelListEntry> = emptyList()
    private var channelsByGroupId: Map<String, List<ChannelListEntry>> = emptyMap()
    private var continuousPlayOriginalIndices: List<Int> = emptyList()
    private var cachedGroups: List<ChannelGroup> = emptyList()
    private var filteredIndexByOriginalIndex: Map<Int, Int> = emptyMap()
    private var browserRootPaths: Set<String> = emptySet()
    private var filterJob: Job? = null
    private var epgRefreshJob: Job? = null
    private var xtreamKeepAliveJob: Job? = null
    private var epgTimelinesByKey: Map<String, List<CurrentProgram>> = emptyMap()
    private var xtreamKeepAliveSignature: String? = null
    private var consecutivePlaybackFailures = 0
    private val backendFallbackThreshold = 3

    val isNetworkAvailable: StateFlow<Boolean> = networkMonitor.isConnected

    private val _storageInfo = MutableStateFlow(StorageInfo.Empty)
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    init {
        networkMonitor.start()
        viewModelScope.launch {
            var wasConnected = networkMonitor.isConnected.value
            networkMonitor.isConnected.collect { connected ->
                if (!wasConnected && connected) {
                    val state = _uiState.value
                    val channel = state.channels.getOrNull(state.selectedIndex)
                    if (channel != null && isXtreamStreamUrl(channel.playbackUrl)) {
                        restartXtreamKeepAliveIfNeeded(forceRestart = true)
                        _uiState.value = state.copy(
                            playbackMessage = "Red recuperada · reanudando sesión Xtream",
                            playbackMessageIsError = false,
                            controlsVisible = true
                        )
                    }
                }
                wasConnected = connected
            }
        }
        restoreLastPlaylist()
        refreshStorageInfo()
    }

    fun loadFromUrl(url: String) {
        val sanitizedUrl = url.trim()
        if (sanitizedUrl.isBlank()) return
        val source = PlaylistSource(
            type = PlaylistSourceType.URL,
            value = sanitizedUrl,
            label = sanitizedUrl
        )
        loadFromUrlInternal(
            url = sanitizedUrl,
            source = source,
            restoring = false,
            preferredGroupId = AllChannelsGroupId
        )
    }

    fun loadFromUri(context: Context, uri: Uri) {
        val source = PlaylistSource(
            type = PlaylistSourceType.URI,
            value = uri.toString(),
            label = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Archivo local" } ?: "Archivo local"
        )
        loadFromUriInternal(
            uri = uri,
            source = source,
            restoring = false,
            preferredGroupId = AllChannelsGroupId
        )
    }

    fun loadFromFile(path: String) {
        if (!fileBrowserRepository.isPlaylistPath(path)) {
            updateFileBrowserStatus("Selecciona un archivo .m3u o .m3u8 válido")
            return
        }

        val file = File(path)
        val source = PlaylistSource(
            type = PlaylistSourceType.FILE,
            value = path,
            label = file.name.ifBlank { path }
        )
        loadFromFileInternal(
            file = file,
            source = source,
            restoring = false,
            preferredGroupId = AllChannelsGroupId
        )
    }

    fun reloadLastPlaylist() {
        restoreLastPlaylist(forceReload = true)
    }

    fun openFileBrowser() {
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.FILE_BROWSER,
            isFullscreen = false,
            fileBrowserStatus = "Selecciona una carpeta o una lista M3U"
        )
    }

    fun closeFileBrowser() {
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.MAIN,
            isFileBrowserLoading = false
        )
    }

    fun updateFileBrowserStatus(message: String) {
        _uiState.value = _uiState.value.copy(
            fileBrowserStatus = message,
            isFileBrowserLoading = false
        )
    }

    fun loadFileBrowserRoots(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentScreen = AppScreen.FILE_BROWSER,
                isFileBrowserLoading = true,
                fileBrowserStatus = "Cargando ubicaciones..."
            )

            runCatching { fileBrowserRepository.listRoots(context) }
                .onSuccess { entries ->
                    browserRootPaths = entries.map { it.path }.toSet()
                    _uiState.value = _uiState.value.copy(
                        fileBrowserEntries = entries,
                        fileBrowserCurrentPath = null,
                        fileBrowserStatus = if (entries.isEmpty()) {
                            "No se encontraron ubicaciones accesibles"
                        } else {
                            "Selecciona una ubicación"
                        },
                        isFileBrowserLoading = false,
                        currentScreen = AppScreen.FILE_BROWSER
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        fileBrowserEntries = emptyList(),
                        fileBrowserCurrentPath = null,
                        fileBrowserStatus = "No se pudieron cargar las ubicaciones: ${error.message}",
                        isFileBrowserLoading = false,
                        currentScreen = AppScreen.FILE_BROWSER
                    )
                }
        }
    }

    fun openFileBrowserDirectory(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentScreen = AppScreen.FILE_BROWSER,
                isFileBrowserLoading = true,
                fileBrowserStatus = "Abriendo carpeta..."
            )

            runCatching { fileBrowserRepository.listDirectory(path) }
                .onSuccess { entries ->
                    _uiState.value = _uiState.value.copy(
                        fileBrowserEntries = entries,
                        fileBrowserCurrentPath = path,
                        fileBrowserStatus = if (entries.isEmpty()) {
                            "Esta carpeta no contiene listas M3U ni subcarpetas accesibles"
                        } else {
                            "Selecciona un archivo .m3u o .m3u8"
                        },
                        isFileBrowserLoading = false,
                        currentScreen = AppScreen.FILE_BROWSER
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        fileBrowserStatus = "No se pudo abrir la carpeta: ${error.message}",
                        isFileBrowserLoading = false,
                        currentScreen = AppScreen.FILE_BROWSER
                    )
                }
        }
    }

    fun navigateFileBrowserUp(context: Context) {
        val currentPath = _uiState.value.fileBrowserCurrentPath
        if (currentPath.isNullOrBlank()) {
            loadFileBrowserRoots(context)
            return
        }

        val currentFile = runCatching { File(currentPath).canonicalFile }.getOrNull()
        val canonicalCurrentPath = currentFile?.absolutePath

        if (canonicalCurrentPath == null || canonicalCurrentPath in browserRootPaths) {
            loadFileBrowserRoots(context)
            return
        }

        val parent = currentFile.parentFile?.absolutePath
        if (parent.isNullOrBlank()) {
            loadFileBrowserRoots(context)
        } else {
            openFileBrowserDirectory(parent)
        }
    }

    fun updateSearchQuery(query: String) {
        if (_uiState.value.searchQuery == query) return
        _uiState.value = _uiState.value.copy(searchQuery = query)
        scheduleFiltering(immediate = false)
    }

    fun selectGroup(groupId: String) {
        if (_uiState.value.selectedGroupId == groupId) return
        _uiState.value = _uiState.value.copy(selectedGroupId = groupId)
        viewModelScope.launch(Dispatchers.IO) {
            lastPlaylistStore.saveSelectedGroupId(groupId)
        }
        // Group switches use a prebuilt index; apply on the main thread for instant UI.
        scheduleFiltering(immediate = true, preferSync = true)
    }

    fun toggleFavorite(index: Int) {
        val channel = _uiState.value.channels.getOrNull(index) ?: return
        toggleFavoriteById(favoriteIdForChannel(channel))
    }

    fun toggleFavoriteForSelected() {
        val selectedIndex = _uiState.value.selectedIndex
        if (selectedIndex >= 0) {
            toggleFavorite(selectedIndex)
        }
    }

    fun toggleFavoriteGroup(groupId: String = _uiState.value.selectedGroupId) {
        val group = _uiState.value.groups.firstOrNull { it.id == groupId } ?: return
        if (!isUserSelectableChannelGroup(group.id)) return
        toggleFavoriteGroupById(group.id, group.title)
    }

    fun toggleFavoriteGroupForSelectedChannel() {
        val selectedEntry = indexedChannels.firstOrNull { it.originalIndex == _uiState.value.selectedIndex } ?: return
        if (!isUserSelectableChannelGroup(selectedEntry.groupId)) return
        toggleFavoriteGroupById(selectedEntry.groupId, selectedEntry.groupTitle)
    }

    fun selectChannel(index: Int, keepContinuousPlay: Boolean = false) {
        if (index !in _uiState.value.channels.indices) return
        if (
            _uiState.value.selectedIndex == index &&
            _uiState.value.controlsVisible &&
            !keepContinuousPlay
        ) {
            return
        }

        // Drop the previous Xtream keep-alive immediately so the panel frees the slot
        // before the player opens the next stream.
        stopXtreamKeepAlive()

        if (!keepContinuousPlay) {
            clearContinuousPlay(updateState = false)
        }

        val channel = _uiState.value.channels[index]
        val continuousActive = keepContinuousPlay && continuousPlayOriginalIndices.isNotEmpty()
        val remaining = if (continuousActive) continuousPlayRemainingFrom(index) else 0
        _uiState.value = _uiState.value.copy(
            selectedIndex = index,
            selectedVisibleIndex = filteredIndexByOriginalIndex[index] ?: -1,
            controlsVisible = true,
            status = "Abriendo: ${channel.name}",
            playbackMessage = if (continuousActive) {
                "Lista continua · quedan $remaining"
            } else {
                "Abriendo canal…"
            },
            playbackMessageIsError = false,
            continuousPlayActive = continuousActive,
            continuousPlayRemaining = remaining
        )
    }

    fun playContinuousFrom(originalIndex: Int) {
        if (originalIndex !in _uiState.value.channels.indices) return
        val filtered = _uiState.value.filteredChannels
        val startVisible = filtered.indexOfFirst { it.originalIndex == originalIndex }
        continuousPlayOriginalIndices = if (startVisible >= 0) {
            filtered.drop(startVisible).map { it.originalIndex }
        } else {
            listOf(originalIndex)
        }
        selectChannel(originalIndex, keepContinuousPlay = true)
        val remaining = continuousPlayOriginalIndices.size
        _uiState.value = _uiState.value.copy(
            continuousPlayActive = true,
            continuousPlayRemaining = remaining,
            playbackMessage = "Reproduciendo lista desde aquí · $remaining vídeos",
            playbackMessageIsError = false,
            controlsVisible = true
        )
    }

    fun stopContinuousPlay() {
        clearContinuousPlay(updateState = true)
        _uiState.value = _uiState.value.copy(
            playbackMessage = "Lista continua detenida",
            playbackMessageIsError = false,
            controlsVisible = true
        )
    }

    fun toggleWatched(index: Int) {
        val channel = _uiState.value.channels.getOrNull(index) ?: return
        val watchedId = favoriteIdForChannel(channel)
        val current = _uiState.value.watchedIds
        val updated = if (watchedId in current) current - watchedId else current + watchedId
        lastPlaylistStore.saveWatchedIds(updated)
        _uiState.value = _uiState.value.copy(
            watchedIds = updated,
            playbackMessage = if (watchedId in current) "Marcado como no visto" else "Marcado como visto",
            playbackMessageIsError = false,
            controlsVisible = true
        )
    }

    fun markWatched(index: Int) {
        val channel = _uiState.value.channels.getOrNull(index) ?: return
        val watchedId = favoriteIdForChannel(channel)
        if (watchedId in _uiState.value.watchedIds) return
        val updated = _uiState.value.watchedIds + watchedId
        lastPlaylistStore.saveWatchedIds(updated)
        _uiState.value = _uiState.value.copy(watchedIds = updated)
    }

    fun onPlaybackEnded(channelName: String) {
        val finishedIndex = _uiState.value.selectedIndex
        if (finishedIndex >= 0) {
            markWatched(finishedIndex)
        }

        val queue = continuousPlayOriginalIndices
        val positionInQueue = queue.indexOf(finishedIndex)
        val nextIndex = if (positionInQueue >= 0) queue.getOrNull(positionInQueue + 1) else null

        if (nextIndex != null) {
            selectChannel(nextIndex, keepContinuousPlay = true)
            return
        }

        clearContinuousPlay(updateState = false)
        _uiState.value = _uiState.value.copy(
            status = "Finalizado: $channelName",
            playbackMessage = if (queue.isNotEmpty()) {
                "Lista continua finalizada"
            } else {
                "Vídeo finalizado"
            },
            playbackMessageIsError = false,
            controlsVisible = true,
            continuousPlayActive = false,
            continuousPlayRemaining = 0
        )
    }

    private fun clearContinuousPlay(updateState: Boolean) {
        continuousPlayOriginalIndices = emptyList()
        if (updateState) {
            _uiState.value = _uiState.value.copy(
                continuousPlayActive = false,
                continuousPlayRemaining = 0
            )
        }
    }

    private fun continuousPlayRemainingFrom(currentOriginalIndex: Int): Int {
        val position = continuousPlayOriginalIndices.indexOf(currentOriginalIndex)
        return if (position >= 0) {
            continuousPlayOriginalIndices.size - position
        } else {
            continuousPlayOriginalIndices.size
        }
    }

    fun showControls(show: Boolean) {
        val currentState = _uiState.value
        if (!show) {
            if (!currentState.controlsVisible) return
            _uiState.value = currentState.copy(controlsVisible = false)
            return
        }

        _uiState.value = currentState.copy(
            controlsVisible = true,
            controlsVisibilityToken = currentState.controlsVisibilityToken + 1
        )
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = !_uiState.value.isFullscreen,
            controlsVisible = true
        )
    }

    fun toggleVideoCompatibilityMode() {
        val nextMode = _uiState.value.videoCompatibilityMode.toggle()
        lastPlaylistStore.saveVideoCompatibilityMode(nextMode)
        _uiState.value = _uiState.value.copy(
            videoCompatibilityMode = nextMode,
            controlsVisible = true,
            playbackMessage = nextMode.displayName(),
            playbackMessageIsError = false
        )
    }

    fun setPlaybackBackend(backend: PlaybackBackend) {
        if (_uiState.value.playbackBackend == backend) return
        lastPlaylistStore.savePlaybackBackend(backend)
        _uiState.value = _uiState.value.copy(
            playbackBackend = backend,
            controlsVisible = true,
            playbackMessage = "Backend: ${backend.displayName()}",
            playbackMessageIsError = false
        )
    }

    fun toggleChannelLogos() {
        val nextValue = !_uiState.value.showChannelLogos
        lastPlaylistStore.saveShowChannelLogos(nextValue)
        _uiState.value = _uiState.value.copy(
            showChannelLogos = nextValue,
            controlsVisible = true,
            playbackMessage = if (nextValue) "Logos activados" else "Logos desactivados",
            playbackMessageIsError = false
        )
    }

    fun updateEpgSettings(enabled: Boolean, url: String) {
        val settings = EpgSettings(enabled = enabled, url = url.trim())
        lastPlaylistStore.saveEpgSettings(settings)
        _uiState.value = _uiState.value.copy(
            epgSettings = settings,
            epgStatus = settings.statusLabel(),
            isEpgLoading = false
        )

        if (!settings.enabled || settings.url.isBlank()) {
            clearEpgState(settings.statusLabel())
            return
        }

        if (_uiState.value.channels.isNotEmpty()) {
            refreshEpg()
        }
    }

    fun updateXtreamKeepAliveSettings(enabled: Boolean, intervalSeconds: Int) {
        val settings = XtreamKeepAliveSettings(enabled = enabled, intervalSeconds = intervalSeconds).sanitized()
        lastPlaylistStore.saveXtreamKeepAliveSettings(settings)
        _uiState.value = _uiState.value.copy(
            xtreamKeepAliveSettings = settings,
            controlsVisible = true,
            playbackMessage = settings.statusLabel(),
            playbackMessageIsError = false
        )
        restartXtreamKeepAliveIfNeeded(forceRestart = true)
    }

    fun refreshEpg() {
        val state = _uiState.value
        val settings = state.epgSettings

        if (!settings.enabled || settings.url.isBlank()) {
            clearEpgState(settings.statusLabel())
            return
        }

        if (state.channels.isEmpty()) {
            _uiState.value = state.copy(
                currentPrograms = emptyMap(),
                nextPrograms = emptyMap(),
                epgStatus = "Carga una lista antes de usar la EPG",
                isEpgLoading = false
            )
            return
        }

        epgRefreshJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isEpgLoading = true,
                epgStatus = "Cargando EPG…"
            )

            runCatching {
                epgRepository.loadProgramTimelines(
                    epgUrl = settings.url,
                    channels = _uiState.value.channels
                )
            }.onSuccess { timelines ->
                epgTimelinesByKey = timelines
                val nowMillis = System.currentTimeMillis()
                val currentPrograms = resolveCurrentPrograms(nowMillis)
                val nextPrograms = resolveNextPrograms(nowMillis)
                _uiState.value = _uiState.value.copy(
                    currentPrograms = currentPrograms,
                    nextPrograms = nextPrograms,
                    epgStatus = when {
                        currentPrograms.isNotEmpty() -> "EPG activa"
                        timelines.isNotEmpty() -> "EPG cargada · sin emisión actual"
                        else -> "EPG sin coincidencias"
                    },
                    isEpgLoading = false
                )
                startEpgTicker()
            }.onFailure { error ->
                clearEpgState("Error EPG: ${error.message}")
            }
        }
    }

    fun exitFullscreen() {
        if (!_uiState.value.isFullscreen) return
        _uiState.value = _uiState.value.copy(
            isFullscreen = false,
            controlsVisible = true
        )
    }

    fun showStatus(message: String) {
        _uiState.value = _uiState.value.copy(
            status = message,
            isLoading = false
        )
    }

    fun onPlaybackStarted(channelName: String) {
        consecutivePlaybackFailures = 0
        _uiState.value = _uiState.value.copy(
            status = "Reproduciendo: $channelName",
            playbackMessage = "En reproducción",
            playbackMessageIsError = false,
            isLoading = false
        )
        restartXtreamKeepAliveIfNeeded(forceRestart = true)
    }

    fun onPlaybackReconnectScheduled(channelName: String) {
        _uiState.value = _uiState.value.copy(
            status = "Reconectando: $channelName",
            playbackMessage = "Reconectando la emisión... un poco de paciencia...",
            playbackMessageIsError = false,
            controlsVisible = true,
            isLoading = false
        )
    }

    fun onPlaybackError(channelName: String, detail: String?) {
        val selected = _uiState.value.channels.getOrNull(_uiState.value.selectedIndex)
        val isXtream = selected?.let { isXtreamStreamUrl(it.playbackUrl) } == true
        // Keep Xtream session alive during reconnect attempts; only stop for non-Xtream.
        if (!isXtream) {
            stopXtreamKeepAlive()
        }
        consecutivePlaybackFailures++

        val currentBackend = _uiState.value.playbackBackend
        val alternativeBackend = when (currentBackend) {
            PlaybackBackend.VLC -> PlaybackBackend.EXOPLAYER
            PlaybackBackend.EXOPLAYER -> PlaybackBackend.VLC
        }

        val errorMessage = detail?.takeIf { it.isNotBlank() } ?: "No se pudo reproducir este canal"

        if (consecutivePlaybackFailures >= backendFallbackThreshold) {
            consecutivePlaybackFailures = 0
            val fallbackMessage = "$errorMessage · Cambiando a ${alternativeBackend.displayName()} automáticamente"
            lastPlaylistStore.savePlaybackBackend(alternativeBackend)
            _uiState.value = _uiState.value.copy(
                status = "Fallback: $channelName → ${alternativeBackend.displayName()}",
                playbackBackend = alternativeBackend,
                playbackMessage = fallbackMessage,
                playbackMessageIsError = true,
                controlsVisible = true,
                isLoading = false
            )
        } else {
            _uiState.value = _uiState.value.copy(
                status = "Error de reproducción: $channelName",
                playbackMessage = errorMessage,
                playbackMessageIsError = true,
                controlsVisible = true,
                isLoading = false
            )
        }
    }

    fun prepareForBackgroundExit() {
        stopXtreamKeepAlive()
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            currentScreen = AppScreen.MAIN,
            selectedIndex = -1,
            selectedVisibleIndex = -1,
            isFullscreen = false,
            controlsVisible = true,
            playbackMessage = null,
            playbackMessageIsError = false,
            isLoading = false,
            isFileBrowserLoading = false,
            fileBrowserStatus = "Selecciona una carpeta o una lista M3U"
        )
    }

    fun openAbout() {
        openAuxiliaryScreen(AppScreen.ABOUT)
    }

    fun openSearch() {
        openAuxiliaryScreen(AppScreen.SEARCH)
    }

    fun openGuide() {
        openAuxiliaryScreen(AppScreen.GUIDE)
    }

    fun openChannelInfo() {
        if (_uiState.value.selectedIndex < 0) return
        openAuxiliaryScreen(AppScreen.CHANNEL_INFO)
    }

    fun openSettings() {
        openAuxiliaryScreen(AppScreen.SETTINGS)
    }

    fun openMain() {
        if (_uiState.value.currentScreen == AppScreen.MAIN) return
        _uiState.value = _uiState.value.copy(currentScreen = AppScreen.MAIN)
    }

    fun handleMouseBackAction(): Boolean {
        val state = _uiState.value
        return when {
            state.isFullscreen -> {
                exitFullscreen()
                true
            }

            state.currentScreen == AppScreen.FILE_BROWSER -> {
                closeFileBrowser()
                true
            }

            state.currentScreen != AppScreen.MAIN -> {
                openMain()
                true
            }

            state.channels.isNotEmpty() -> {
                showControls(true)
                true
            }

            else -> true
        }
    }

    private fun toggleFavoriteById(favoriteId: String) {
        val currentFavorites = _uiState.value.favoriteIds
        val updatedFavorites = if (favoriteId in currentFavorites) {
            currentFavorites - favoriteId
        } else {
            currentFavorites + favoriteId
        }

        lastPlaylistStore.saveFavoriteIds(updatedFavorites)

        val currentGroupId = _uiState.value.selectedGroupId
        val updatedGroups = buildChannelGroups(indexedChannels, updatedFavorites, _uiState.value.favoriteGroupIds)
        cachedGroups = updatedGroups
        val nextGroupId = if (updatedFavorites.isEmpty() && _uiState.value.favoriteGroupIds.isEmpty() && currentGroupId == FavoriteChannelsGroupId) {
            AllChannelsGroupId
        } else {
            resolveGroupId(currentGroupId, updatedGroups)
        }

        _uiState.value = _uiState.value.copy(
            favoriteIds = updatedFavorites,
            groups = updatedGroups,
            selectedGroupId = nextGroupId,
            controlsVisible = true,
            playbackMessage = if (favoriteId in currentFavorites) "Eliminado de favoritos" else "Añadido a favoritos",
            playbackMessageIsError = false
        )
        lastPlaylistStore.saveSelectedGroupId(nextGroupId)
        scheduleFiltering(immediate = true)
    }

    private fun toggleFavoriteGroupById(groupId: String, groupTitle: String) {
        if (!isUserSelectableChannelGroup(groupId)) return

        val currentFavoriteGroupIds = _uiState.value.favoriteGroupIds
        val updatedFavoriteGroupIds = if (groupId in currentFavoriteGroupIds) {
            currentFavoriteGroupIds - groupId
        } else {
            currentFavoriteGroupIds + groupId
        }

        lastPlaylistStore.saveFavoriteGroupIds(updatedFavoriteGroupIds)

        val updatedGroups = buildChannelGroups(indexedChannels, _uiState.value.favoriteIds, updatedFavoriteGroupIds)
        cachedGroups = updatedGroups
        val currentGroupId = _uiState.value.selectedGroupId
        val nextGroupId = if (
            _uiState.value.favoriteIds.isEmpty() && updatedFavoriteGroupIds.isEmpty() && currentGroupId == FavoriteChannelsGroupId
        ) {
            AllChannelsGroupId
        } else {
            resolveGroupId(currentGroupId, updatedGroups)
        }

        _uiState.value = _uiState.value.copy(
            favoriteGroupIds = updatedFavoriteGroupIds,
            groups = updatedGroups,
            selectedGroupId = nextGroupId,
            controlsVisible = true,
            playbackMessage = if (groupId in currentFavoriteGroupIds) {
                "Grupo eliminado de favoritos: $groupTitle"
            } else {
                "Grupo añadido a favoritos: $groupTitle"
            },
            playbackMessageIsError = false
        )
        lastPlaylistStore.saveSelectedGroupId(nextGroupId)
        scheduleFiltering(immediate = true)
    }

    private fun restoreLastPlaylist(forceReload: Boolean = false) {
        val source = lastPlaylistStore.readSource() ?: return
        if (!forceReload && _uiState.value.channels.isNotEmpty()) return

        val preferredGroupId = lastPlaylistStore.readSelectedGroupId()
        when (source.type) {
            PlaylistSourceType.URL -> loadFromUrlInternal(source.value, source, restoring = true, preferredGroupId = preferredGroupId)
            PlaylistSourceType.FILE -> {
                val file = File(source.value)
                if (file.exists() && file.canRead()) {
                    loadFromFileInternal(file, source, restoring = true, preferredGroupId = preferredGroupId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        playlistSource = source,
                        status = "La última lista local ya no está disponible",
                        playbackMessage = null,
                        playbackMessageIsError = false
                    )
                }
            }
            PlaylistSourceType.URI -> {
                loadFromUriInternal(Uri.parse(source.value), source, restoring = true, preferredGroupId = preferredGroupId)
            }
        }
    }

    private fun loadFromUrlInternal(
        url: String,
        source: PlaylistSource,
        restoring: Boolean,
        preferredGroupId: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                playlistSource = source,
                currentScreen = AppScreen.MAIN,
                status = if (restoring) "Recuperando última lista..." else "Descargando lista...",
                isLoading = true,
                playbackMessage = if (restoring) "Restaurando lista guardada…" else null,
                playbackMessageIsError = false
            )

            runCatching { repository.loadFromUrl(url) }
                .onSuccess { channels ->
                    applyLoadedChannels(
                        channels = channels,
                        messagePrefix = if (restoring) "Lista recuperada" else "Lista cargada",
                        source = source,
                        preferredGroupId = preferredGroupId
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        playlistSource = source,
                        status = if (restoring) {
                            "No se pudo recuperar la última lista: ${error.message}"
                        } else {
                            "Error URL: ${error.message}"
                        },
                        playbackMessage = null,
                        isLoading = false
                    )
                }
        }
    }

    private fun loadFromUriInternal(
        uri: Uri,
        source: PlaylistSource,
        restoring: Boolean,
        preferredGroupId: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                playlistSource = source,
                currentScreen = AppScreen.MAIN,
                status = if (restoring) "Recuperando archivo guardado..." else "Leyendo archivo local...",
                isLoading = true,
                playbackMessage = if (restoring) "Restaurando lista guardada…" else null,
                playbackMessageIsError = false
            )

            runCatching { repository.loadFromUri(appContext, uri) }
                .onSuccess { channels ->
                    applyLoadedChannels(
                        channels = channels,
                        messagePrefix = if (restoring) "Archivo recuperado" else "Archivo cargado",
                        source = source,
                        preferredGroupId = preferredGroupId
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        playlistSource = source,
                        status = if (restoring) {
                            "No se pudo recuperar el último archivo: ${error.message}"
                        } else {
                            "Error archivo: ${error.message}"
                        },
                        playbackMessage = null,
                        isLoading = false
                    )
                }
        }
    }

    private fun loadFromFileInternal(
        file: File,
        source: PlaylistSource,
        restoring: Boolean,
        preferredGroupId: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                playlistSource = source,
                currentScreen = if (restoring) AppScreen.MAIN else _uiState.value.currentScreen,
                status = if (restoring) "Recuperando última lista local..." else "Leyendo archivo local...",
                isLoading = true,
                fileBrowserStatus = "Abriendo ${file.name}...",
                playbackMessage = if (restoring) "Restaurando lista guardada…" else null,
                playbackMessageIsError = false
            )

            runCatching { repository.loadFromFile(file) }
                .onSuccess { channels ->
                    applyLoadedChannels(
                        channels = channels,
                        messagePrefix = if (restoring) "Archivo recuperado" else "Archivo cargado",
                        source = source,
                        preferredGroupId = preferredGroupId
                    )
                    _uiState.value = _uiState.value.copy(
                        currentScreen = AppScreen.MAIN,
                        fileBrowserStatus = "Archivo cargado: ${file.name}"
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        playlistSource = source,
                        status = if (restoring) {
                            "No se pudo recuperar el último archivo: ${error.message}"
                        } else {
                            "Error archivo: ${error.message}"
                        },
                        fileBrowserStatus = "No se pudo abrir ${file.name}",
                        isLoading = false,
                        currentScreen = if (restoring) AppScreen.MAIN else AppScreen.FILE_BROWSER,
                        playbackMessage = null
                    )
                }
        }
    }

    private suspend fun applyLoadedChannels(
        channels: List<Channel>,
        messagePrefix: String,
        source: PlaylistSource,
        preferredGroupId: String
    ) {
        stopXtreamKeepAlive()
        val currentState = _uiState.value
        val favoriteIds = currentState.favoriteIds
        val favoriteGroupIds = currentState.favoriteGroupIds
        val prepared = withContext(Dispatchers.Default) {
            prepareLoadedChannels(
                channels = channels,
                favoriteIds = favoriteIds,
                favoriteGroupIds = favoriteGroupIds,
                preferredGroupId = preferredGroupId,
                searchQuery = currentState.searchQuery
            )
        }

        indexedChannels = prepared.indexedChannels
        channelsByGroupId = prepared.channelsByGroupId
        cachedGroups = prepared.groups
        filteredIndexByOriginalIndex = emptyMap()
        filteredIndexByOriginalIndex = prepared.filterResult.indexByOriginalIndex
        continuousPlayOriginalIndices = emptyList()
        lastPlaylistStore.saveSource(source)
        lastPlaylistStore.saveSelectedGroupId(prepared.resolvedGroupId)

        _uiState.value = _uiState.value.copy(
            channels = channels,
            filteredChannels = prepared.filterResult.filteredChannels,
            groups = prepared.groups,
            selectedGroupId = prepared.resolvedGroupId,
            selectedIndex = prepared.filterResult.selectedIndex,
            selectedVisibleIndex = prepared.filterResult.selectedVisibleIndex,
            playlistSource = source,
            status = "$messagePrefix: ${channels.size} canales",
            playbackMessage = if (channels.isNotEmpty()) "Lista lista · selecciona un canal" else null,
            playbackMessageIsError = false,
            continuousPlayActive = false,
            continuousPlayRemaining = 0,
            currentPrograms = if (channels.isEmpty()) emptyMap() else _uiState.value.currentPrograms,
            nextPrograms = if (channels.isEmpty()) emptyMap() else _uiState.value.nextPrograms,
            isLoading = false,
            currentScreen = AppScreen.MAIN
        )

        if (channels.isEmpty()) {
            clearEpgState(_uiState.value.epgSettings.statusLabel())
        } else {
            refreshEpgIfConfigured(channelCount = channels.size)
        }
    }

    private fun scheduleFiltering(immediate: Boolean, preferSync: Boolean = false) {
        filterJob?.cancel()

        if (indexedChannels.isEmpty()) {
            cachedGroups = emptyList()
            channelsByGroupId = emptyMap()
            filteredIndexByOriginalIndex = emptyMap()
            _uiState.value = _uiState.value.copy(
                filteredChannels = emptyList(),
                groups = emptyList(),
                selectedIndex = -1,
                selectedVisibleIndex = -1,
                isLoading = false
            )
            return
        }

        val querySnapshot = _uiState.value.searchQuery.trim()
        val selectedGroupIdSnapshot = _uiState.value.selectedGroupId
        val favoriteIdsSnapshot = _uiState.value.favoriteIds
        val favoriteGroupIdsSnapshot = _uiState.value.favoriteGroupIds
        val channelsSnapshot = _uiState.value.channels
        val selectedIndexSnapshot = _uiState.value.selectedIndex
        val canApplySync =
            preferSync &&
                querySnapshot.isBlank() &&
                selectedGroupIdSnapshot != FavoriteChannelsGroupId

        fun applyFilterResult(filterResult: FilterResult, latestState: UiState = _uiState.value) {
            filteredIndexByOriginalIndex = filterResult.indexByOriginalIndex
            _uiState.value = latestState.copy(
                filteredChannels = filterResult.filteredChannels,
                groups = filterResult.groups,
                selectedIndex = filterResult.selectedIndex,
                selectedVisibleIndex = filterResult.selectedVisibleIndex
            )
        }

        if (canApplySync) {
            applyFilterResult(
                computeFilterResult(
                    entries = indexedChannels,
                    channelsByGroupId = channelsByGroupId,
                    selectedGroupId = selectedGroupIdSnapshot,
                    searchQuery = querySnapshot,
                    favoriteIds = favoriteIdsSnapshot,
                    favoriteGroupIds = favoriteGroupIdsSnapshot,
                    selectedIndex = selectedIndexSnapshot,
                    groups = cachedGroups,
                    preservePlaybackSelection = true
                )
            )
            return
        }

        filterJob = viewModelScope.launch {
            if (!immediate) delay(SearchDebounceMillis)

            val filterResult = withContext(Dispatchers.Default) {
                computeFilterResult(
                    entries = indexedChannels,
                    channelsByGroupId = channelsByGroupId,
                    selectedGroupId = selectedGroupIdSnapshot,
                    searchQuery = querySnapshot,
                    favoriteIds = favoriteIdsSnapshot,
                    favoriteGroupIds = favoriteGroupIdsSnapshot,
                    selectedIndex = selectedIndexSnapshot,
                    groups = cachedGroups,
                    preservePlaybackSelection = true
                )
            }

            val latestState = _uiState.value
            val latestQuery = latestState.searchQuery.trim()
            if (
                latestQuery != querySnapshot ||
                latestState.selectedGroupId != selectedGroupIdSnapshot ||
                latestState.favoriteIds != favoriteIdsSnapshot ||
                latestState.favoriteGroupIds != favoriteGroupIdsSnapshot ||
                latestState.channels !== channelsSnapshot
            ) return@launch

            applyFilterResult(filterResult, latestState)
        }
    }

    private fun resolveGroupId(preferredGroupId: String, groups: List<ChannelGroup>): String {
        return groups.firstOrNull { it.id == preferredGroupId }?.id ?: AllChannelsGroupId
    }


    private fun refreshEpgIfConfigured() {
        refreshEpgIfConfigured(_uiState.value.channels.size)
    }

    private fun refreshEpgIfConfigured(channelCount: Int) {
        val settings = _uiState.value.epgSettings
        if (!settings.enabled || settings.url.isBlank()) {
            clearEpgState(settings.statusLabel())
            return
        }
        if (channelCount > AutoEpgChannelLimit) {
            clearEpgState("EPG pospuesta automáticamente: lista muy grande")
            return
        }
        refreshEpg()
    }

    private fun clearEpgState(status: String) {
        epgRefreshJob?.cancel()
        epgTimelinesByKey = emptyMap()
        _uiState.value = _uiState.value.copy(
            currentPrograms = emptyMap(),
            nextPrograms = emptyMap(),
            epgStatus = status,
            isEpgLoading = false
        )
    }

    private fun startEpgTicker() {
        epgRefreshJob?.cancel()
        if (epgTimelinesByKey.isEmpty()) return

        epgRefreshJob = viewModelScope.launch {
            while (true) {
                delay(EpgRefreshIntervalMillis)
                val nowMillis = System.currentTimeMillis()
                val currentPrograms = resolveCurrentPrograms(nowMillis)
                val nextPrograms = resolveNextPrograms(nowMillis)
                _uiState.value = _uiState.value.copy(
                    currentPrograms = currentPrograms,
                    nextPrograms = nextPrograms
                )
            }
        }
    }

    private fun resolveCurrentPrograms(nowMillis: Long): Map<String, CurrentProgram> {
        if (epgTimelinesByKey.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, CurrentProgram>(epgTimelinesByKey.size)
        epgTimelinesByKey.forEach { (key, programs) ->
            programs.firstOrNull { nowMillis in it.startTimeMillis until it.endTimeMillis }?.let { current ->
                result[key] = current
            }
        }
        return result
    }

    private fun resolveNextPrograms(nowMillis: Long): Map<String, CurrentProgram> {
        if (epgTimelinesByKey.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, CurrentProgram>(epgTimelinesByKey.size)
        epgTimelinesByKey.forEach { (key, programs) ->
            val currentIndex = programs.indexOfFirst { nowMillis in it.startTimeMillis until it.endTimeMillis }
            val nextProgram = when {
                currentIndex >= 0 && currentIndex + 1 < programs.size -> programs[currentIndex + 1]
                else -> programs.firstOrNull { it.startTimeMillis >= nowMillis }
            }
            if (nextProgram != null) {
                result[key] = nextProgram
            }
        }
        return result
    }

    private fun openAuxiliaryScreen(screen: AppScreen) {
        if (_uiState.value.currentScreen == screen) return
        _uiState.value = _uiState.value.copy(
            currentScreen = screen,
            isFullscreen = false,
            controlsVisible = true
        )
    }

    private fun restartXtreamKeepAliveIfNeeded(forceRestart: Boolean) {
        val settings = _uiState.value.xtreamKeepAliveSettings.sanitized()
        if (!settings.enabled) {
            stopXtreamKeepAlive()
            return
        }

        val channel = _uiState.value.channels.getOrNull(_uiState.value.selectedIndex)
        if (channel == null || !isXtreamStreamUrl(channel.playbackUrl)) {
            stopXtreamKeepAlive()
            return
        }

        val request = buildXtreamKeepAliveRequest(
            streamUrl = channel.playbackUrl,
            requestHeaders = channel.requestHeaders
        ) ?: run {
            stopXtreamKeepAlive()
            return
        }

        val nextSignature = "${request.signature}|${settings.intervalSeconds}"
        if (!forceRestart && xtreamKeepAliveSignature == nextSignature && xtreamKeepAliveJob?.isActive == true) {
            return
        }

        stopXtreamKeepAlive()
        xtreamKeepAliveSignature = nextSignature
        xtreamKeepAliveJob = viewModelScope.launch {
            delay(XtreamKeepAliveInitialDelayMillis)
            while (true) {
                val ok = repository.sendXtreamKeepAlive(request)
                if (ok) {
                    delay(settings.intervalSeconds * 1_000L)
                } else {
                    // Panel may be busy or flaky — retry sooner instead of waiting full interval.
                    delay(XtreamKeepAliveRetryDelayMillis)
                }
            }
        }
    }

    private fun stopXtreamKeepAlive() {
        xtreamKeepAliveJob?.cancel()
        xtreamKeepAliveJob = null
        xtreamKeepAliveSignature = null
    }

    @UnstableApi
    fun clearPlaybackCache() {
        viewModelScope.launch(Dispatchers.IO) {
            PlaybackCache.releaseAndClear(appContext)
            refreshStorageInfo()
        }
        showMaintenanceMessage("Caché de reproducción limpiada")
    }

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            ImageLoaderConfig.clearAllCaches(appContext)
            refreshStorageInfo()
        }
        showMaintenanceMessage("Caché de imágenes limpiada")
    }

    @UnstableApi
    fun clearAllCache() {
        viewModelScope.launch(Dispatchers.IO) {
            PlaybackCache.releaseAndClear(appContext)
            ImageLoaderConfig.clearAllCaches(appContext)
            clearGeneralAppCache()
            refreshStorageInfo()
        }
        showMaintenanceMessage("Todo el caché ha sido limpiado")
    }

    fun resetPreferences() {
        lastPlaylistStore.clearAll()
        val clearedState = UiState()
        _uiState.value = clearedState
        indexedChannels = emptyList()
        channelsByGroupId = emptyMap()
        cachedGroups = emptyList()
        filteredIndexByOriginalIndex = emptyMap()
        continuousPlayOriginalIndices = emptyList()
        epgTimelinesByKey = emptyMap()
        stopXtreamKeepAlive()
        epgRefreshJob?.cancel()
        showMaintenanceMessage("Preferencias restablecidas")
    }

    @UnstableApi
    fun resetApp() {
        viewModelScope.launch(Dispatchers.IO) {
            PlaybackCache.releaseAndClear(appContext)
            ImageLoaderConfig.clearAllCaches(appContext)
            clearGeneralAppCache()
        }
        resetPreferences()
        refreshStorageInfo()
        showMaintenanceMessage("Aplicación restablecida por completo")
    }

    @UnstableApi
    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val playbackCacheSize = PlaybackCache.cacheSizeBytes(appContext)
            val imageCacheSize = ImageLoaderConfig.diskCacheSizeBytes(appContext)
            val appCacheSize = calculateAppCacheSize()
            _storageInfo.value = StorageInfo(
                playbackCacheBytes = playbackCacheSize,
                imageCacheBytes = imageCacheSize,
                appCacheBytes = appCacheSize,
                totalBytes = playbackCacheSize + imageCacheSize + appCacheSize
            )
        }
    }

    private fun calculateAppCacheSize(): Long {
        val cacheDir = appContext.cacheDir ?: return 0L
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .filter { !it.absolutePath.contains("media_stream_cache") }
            .filter { !it.absolutePath.contains("coil_image_cache") }
            .sumOf { it.length() }
    }

    private fun clearGeneralAppCache() {
        val cacheDir = appContext.cacheDir ?: return
        cacheDir.listFiles()?.forEach { file ->
            if (file.name != "media_stream_cache" && file.name != "coil_image_cache") {
                file.deleteRecursively()
            }
        }
    }

    private fun showMaintenanceMessage(message: String) {
        _uiState.value = _uiState.value.copy(
            playbackMessage = message,
            playbackMessageIsError = false,
            controlsVisible = true
        )
    }

    override fun onCleared() {
        stopXtreamKeepAlive()
        networkMonitor.stop()
        epgRefreshJob?.cancel()
        filterJob?.cancel()
        super.onCleared()
    }

    private data class FilterResult(
        val filteredChannels: List<ChannelListEntry>,
        val indexByOriginalIndex: Map<Int, Int>,
        val selectedIndex: Int,
        val selectedVisibleIndex: Int,
        val groups: List<ChannelGroup>
    )

    private data class PreparedChannels(
        val indexedChannels: List<ChannelListEntry>,
        val channelsByGroupId: Map<String, List<ChannelListEntry>>,
        val groups: List<ChannelGroup>,
        val resolvedGroupId: String,
        val filterResult: FilterResult
    )

    private fun prepareLoadedChannels(
        channels: List<Channel>,
        favoriteIds: Set<String>,
        favoriteGroupIds: Set<String>,
        preferredGroupId: String,
        searchQuery: String
    ): PreparedChannels {
        val indexedEntries = channels.mapIndexed { index, channel ->
            ChannelListEntry(
                originalIndex = index,
                channel = channel
            )
        }
        val byGroupId = buildChannelsByGroupId(indexedEntries)
        val groups = buildChannelGroups(indexedEntries, favoriteIds, favoriteGroupIds)
        val resolvedGroupId = resolveGroupId(preferredGroupId, groups)
        return PreparedChannels(
            indexedChannels = indexedEntries,
            channelsByGroupId = byGroupId,
            groups = groups,
            resolvedGroupId = resolvedGroupId,
            filterResult = computeFilterResult(
                entries = indexedEntries,
                channelsByGroupId = byGroupId,
                selectedGroupId = resolvedGroupId,
                searchQuery = searchQuery.trim(),
                favoriteIds = favoriteIds,
                favoriteGroupIds = favoriteGroupIds,
                selectedIndex = -1,
                groups = groups,
                preservePlaybackSelection = false
            )
        )
    }

    private fun computeFilterResult(
        entries: List<ChannelListEntry>,
        channelsByGroupId: Map<String, List<ChannelListEntry>>,
        selectedGroupId: String,
        searchQuery: String,
        favoriteIds: Set<String>,
        favoriteGroupIds: Set<String>,
        selectedIndex: Int,
        groups: List<ChannelGroup>,
        preservePlaybackSelection: Boolean
    ): FilterResult {
        val groupFiltered = when (selectedGroupId) {
            AllChannelsGroupId -> entries
            FavoriteChannelsGroupId -> entries.filter {
                isChannelEntryFavorite(it, favoriteIds, favoriteGroupIds)
            }
            else -> channelsByGroupId[selectedGroupId].orEmpty()
        }

        val filteredChannels = if (searchQuery.isBlank()) {
            groupFiltered
        } else {
            groupFiltered.filter { entry ->
                entry.channel.name.contains(searchQuery, ignoreCase = true) ||
                    entry.groupTitle.contains(searchQuery, ignoreCase = true)
            }
        }

        val indexMap = HashMap<Int, Int>(filteredChannels.size.coerceAtLeast(16))
        filteredChannels.forEachIndexed { visibleIndex, entry ->
            indexMap[entry.originalIndex] = visibleIndex
        }

        val resolvedSelectedIndex = when {
            selectedIndex < 0 -> if (!preservePlaybackSelection && filteredChannels.isNotEmpty()) {
                filteredChannels.first().originalIndex
            } else {
                selectedIndex
            }
            indexMap.containsKey(selectedIndex) -> selectedIndex
            // Keep current playback when browsing another group; only auto-pick on initial load.
            preservePlaybackSelection -> selectedIndex
            filteredChannels.isNotEmpty() -> filteredChannels.first().originalIndex
            else -> -1
        }

        val selectedVisibleIndex = indexMap[resolvedSelectedIndex]
            ?: if (filteredChannels.isNotEmpty() && !indexMap.containsKey(selectedIndex)) {
                // Focus the top of the new group list without changing playback.
                0
            } else {
                -1
            }

        return FilterResult(
            filteredChannels = filteredChannels,
            indexByOriginalIndex = indexMap,
            selectedIndex = resolvedSelectedIndex,
            selectedVisibleIndex = selectedVisibleIndex,
            groups = groups
        )
    }

    companion object {
        @UnstableApi
        internal fun factory(applicationContext: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = applicationContext.applicationContext
                val playlistLoader = PlaylistLoader()
                val repo = PlaylistRepository(M3uParser(), playlistLoader)
                return MainViewModel(
                    appContext = appContext,
                    repository = repo,
                    epgRepository = EpgRepository(playlistLoader, XmltvParser()),
                    fileBrowserRepository = FileBrowserRepository(),
                    lastPlaylistStore = LastPlaylistStore(appContext),
                    networkMonitor = NetworkMonitor(appContext)
                ) as T
            }
        }
    }
}

private fun EpgSettings.statusLabel(): String {
    return when {
        !enabled -> "EPG desactivada"
        url.isBlank() -> "Configura la URL EPG"
        else -> "EPG lista para cargar"
    }
}

