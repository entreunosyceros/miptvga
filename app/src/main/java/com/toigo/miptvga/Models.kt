package com.toigo.miptvga

import androidx.compose.runtime.Immutable

internal const val AllChannelsGroupId = "__all__"
internal const val FavoriteChannelsGroupId = "__favorites__"
internal const val ControlsAutoHideDelayMillis = 4_000L
internal const val DefaultGroupTitle = "Sin grupo"

@Immutable
data class Channel(
    val name: String,
    val streamUrl: String,
    val playbackUrl: String = streamUrl,
    val requestHeaders: Map<String, String> = emptyMap(),
    val group: String? = null,
    val logoUrl: String? = null,
    val tvgId: String? = null
)

internal fun favoriteIdForChannel(channel: Channel): String {
    return listOf(channel.name.trim(), channel.group.orEmpty().trim(), channel.streamUrl.trim())
        .joinToString(separator = "|")
}

enum class PlaylistSourceType {
    URL,
    FILE,
    URI
}

@Immutable
internal data class EpgSettings(
    val enabled: Boolean = false,
    val url: String = ""
)

@Immutable
internal data class XtreamKeepAliveSettings(
    val enabled: Boolean = true,
    val intervalSeconds: Int = 45
)

@Immutable
internal data class CurrentProgram(
    val title: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

@Immutable
internal data class PlaylistSource(
    val type: PlaylistSourceType,
    val value: String,
    val label: String
)

@Immutable
internal data class ChannelGroup(
    val id: String,
    val title: String,
    val count: Int,
    val isFavorite: Boolean = false
)

enum class AppScreen {
    MAIN,
    ABOUT,
    FILE_BROWSER,
    SEARCH,
    GUIDE,
    CHANNEL_INFO,
    SETTINGS
}

enum class VideoCompatibilityMode {
    TEXTURE_VIEW,
    SURFACE_VIEW;

    fun displayName(): String = when (this) {
        TEXTURE_VIEW -> "Vídeo: Texture"
        SURFACE_VIEW -> "Vídeo: Surface"
    }

    fun toggle(): VideoCompatibilityMode = when (this) {
        TEXTURE_VIEW -> SURFACE_VIEW
        SURFACE_VIEW -> TEXTURE_VIEW
    }
}

enum class PlaybackBackend {
    VLC,
    EXOPLAYER;

    fun displayName(): String = when (this) {
        VLC -> "VLC"
        EXOPLAYER -> "ExoPlayer"
    }
}

@Immutable
internal data class PlaybackControllerState(
    val isPlaying: Boolean = false,
    val canPause: Boolean = false,
    val canSeek: Boolean = false,
    val isLive: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

internal data class PlaybackControllerActions(
    val togglePlayPause: () -> Unit = {},
    val play: () -> Unit = {},
    val pause: () -> Unit = {},
    val seekBack: () -> Unit = {},
    val seekForward: () -> Unit = {}
)

@Immutable
internal data class UiState(
    val channels: List<Channel> = emptyList(),
    val filteredChannels: List<ChannelListEntry> = emptyList(),
    val groups: List<ChannelGroup> = emptyList(),
    val searchQuery: String = "",
    val selectedGroupId: String = AllChannelsGroupId,
    val selectedIndex: Int = -1,
    val selectedVisibleIndex: Int = -1,
    val status: String = "Carga una lista M3U por URL o archivo",
    val playlistSource: PlaylistSource? = null,
    val favoriteIds: Set<String> = emptySet(),
    val favoriteGroupIds: Set<String> = emptySet(),
    val epgSettings: EpgSettings = EpgSettings(),
    val currentPrograms: Map<String, CurrentProgram> = emptyMap(),
    val nextPrograms: Map<String, CurrentProgram> = emptyMap(),
    val epgStatus: String = "EPG desactivada",
    val isEpgLoading: Boolean = false,
    val xtreamKeepAliveSettings: XtreamKeepAliveSettings = XtreamKeepAliveSettings(),
    val showChannelLogos: Boolean = false,
    val playbackBackend: PlaybackBackend = PlaybackBackend.VLC,
    val videoCompatibilityMode: VideoCompatibilityMode = VideoCompatibilityMode.SURFACE_VIEW,
    val playbackMessage: String? = null,
    val playbackMessageIsError: Boolean = false,
    val isFullscreen: Boolean = false,
    val controlsVisible: Boolean = true,
    val controlsVisibilityToken: Int = 0,
    val isLoading: Boolean = false,
    val fileBrowserEntries: List<FileBrowserEntry> = emptyList(),
    val fileBrowserCurrentPath: String? = null,
    val fileBrowserStatus: String = "Selecciona una carpeta o una lista M3U",
    val isFileBrowserLoading: Boolean = false,
    val currentScreen: AppScreen = AppScreen.MAIN
)

@Immutable
internal data class ChannelListEntry(
    val originalIndex: Int,
    val channel: Channel
) {
    val groupTitle: String
        get() = normalizedGroupTitle(channel.group)

    val groupId: String
        get() = groupIdForChannel(channel)

    val favoriteId: String
        get() = favoriteIdForChannel(channel)
}

@Immutable
internal data class FileBrowserEntry(
    val path: String,
    val name: String,
    val subtitle: String,
    val isDirectory: Boolean,
    val isRoot: Boolean = false
)

internal fun normalizedGroupTitle(rawTitle: String?): String {
    return rawTitle?.trim().takeUnless { it.isNullOrBlank() } ?: DefaultGroupTitle
}

internal fun buildGroupId(title: String): String {
    return title.trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .ifBlank { DefaultGroupTitle.lowercase() }
}

internal fun groupIdForChannel(channel: Channel): String {
    return buildGroupId(normalizedGroupTitle(channel.group))
}

internal fun isUserSelectableChannelGroup(groupId: String): Boolean {
    return groupId != AllChannelsGroupId && groupId != FavoriteChannelsGroupId
}

internal fun isChannelEntryFavorite(
    entry: ChannelListEntry,
    favoriteIds: Set<String>,
    favoriteGroupIds: Set<String>
): Boolean {
    return entry.favoriteId in favoriteIds || entry.groupId in favoriteGroupIds
}

internal fun isChannelFavorite(
    channel: Channel,
    favoriteIds: Set<String>,
    favoriteGroupIds: Set<String>
): Boolean {
    return favoriteIdForChannel(channel) in favoriteIds || groupIdForChannel(channel) in favoriteGroupIds
}

internal fun buildChannelGroups(
    entries: List<ChannelListEntry>,
    favoriteIds: Set<String>,
    favoriteGroupIds: Set<String>
): List<ChannelGroup> {
    if (entries.isEmpty()) return emptyList()

    data class GroupAccumulator(
        val title: String,
        var count: Int
    )

    val grouped = LinkedHashMap<String, GroupAccumulator>()
    entries.forEach { entry ->
        val accumulator = grouped[entry.groupId]
        if (accumulator == null) {
            grouped[entry.groupId] = GroupAccumulator(
                title = entry.groupTitle,
                count = 1
            )
        } else {
            accumulator.count += 1
        }
    }

    val channelGroups = grouped.entries
        .sortedBy { it.value.title.lowercase() }
        .map { (groupId, group) ->
            ChannelGroup(
                id = groupId,
                title = group.title,
                count = group.count,
                isFavorite = groupId in favoriteGroupIds
            )
        }

    val favoriteCount = entries.count { isChannelEntryFavorite(it, favoriteIds, favoriteGroupIds) }

    return buildList(channelGroups.size + 2) {
        add(
            ChannelGroup(
                id = AllChannelsGroupId,
                title = "Todos los canales",
                count = entries.size
            )
        )
        addAll(channelGroups)
        if (favoriteCount > 0) {
            add(
                ChannelGroup(
                    id = FavoriteChannelsGroupId,
                    title = "Favoritos",
                    count = favoriteCount
                )
            )
        }
    }
}

internal fun epgLookupKeysForChannel(channel: Channel): List<String> {
    return buildList(2) {
        channel.tvgId?.normalizeEpgLookupKey()?.takeIf { it.isNotBlank() }?.let(::add)
        channel.name.normalizeEpgLookupKey().takeIf { it.isNotBlank() }?.let { normalizedName ->
            if (normalizedName !in this) add(normalizedName)
        }
    }
}

internal fun currentProgramForChannel(
    channel: Channel,
    currentPrograms: Map<String, CurrentProgram>
): CurrentProgram? {
    return epgLookupKeysForChannel(channel).firstNotNullOfOrNull { currentPrograms[it] }
}

internal fun nextProgramForChannel(
    channel: Channel,
    nextPrograms: Map<String, CurrentProgram>
): CurrentProgram? {
    return epgLookupKeysForChannel(channel).firstNotNullOfOrNull { nextPrograms[it] }
}

internal fun String.normalizeEpgLookupKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}

internal fun XtreamKeepAliveSettings.sanitized(): XtreamKeepAliveSettings {
    val normalizedInterval = intervalSeconds.coerceIn(30, 60)
    val supportedInterval = when {
        normalizedInterval <= 30 -> 30
        normalizedInterval <= 45 -> 45
        else -> 60
    }
    return copy(intervalSeconds = supportedInterval)
}

internal fun XtreamKeepAliveSettings.statusLabel(): String {
    val safeSettings = sanitized()
    return if (safeSettings.enabled) {
        "Keepalive Xtream activo · ${safeSettings.intervalSeconds}s"
    } else {
        "Keepalive Xtream desactivado"
    }
}

