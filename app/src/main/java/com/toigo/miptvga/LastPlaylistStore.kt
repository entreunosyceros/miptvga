package com.toigo.miptvga

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val PreferencesName = "miptvga_prefs"
private const val KeySourceType = "source_type"
private const val KeySourceValue = "source_value"
private const val KeySourceLabel = "source_label"
private const val KeySelectedGroupId = "selected_group_id"
private const val KeyFavoriteIds = "favorite_ids"
private const val KeyFavoriteGroupIds = "favorite_group_ids"
private const val KeyVideoCompatibilityMode = "video_compatibility_mode"
private const val KeyPlaybackBackend = "playback_backend"
private const val KeyShowChannelLogos = "show_channel_logos"
private const val KeyEpgEnabled = "epg_enabled"
private const val KeyEpgUrl = "epg_url"
private const val KeyXtreamKeepAliveEnabled = "xtream_keepalive_enabled"
private const val KeyXtreamKeepAliveIntervalSeconds = "xtream_keepalive_interval_seconds"

internal class LastPlaylistStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun saveSource(source: PlaylistSource) {
        preferences.edit {
            putString(KeySourceType, source.type.name)
            putString(KeySourceValue, source.value)
            putString(KeySourceLabel, source.label)
        }
    }

    fun readSource(): PlaylistSource? {
        val typeName = preferences.getString(KeySourceType, null) ?: return null
        val value = preferences.getString(KeySourceValue, null) ?: return null
        val label = preferences.getString(KeySourceLabel, value) ?: value
        val type = runCatching { PlaylistSourceType.valueOf(typeName) }.getOrNull() ?: return null
        return PlaylistSource(type = type, value = value, label = label)
    }

    fun saveSelectedGroupId(groupId: String) {
        preferences.edit { putString(KeySelectedGroupId, groupId) }
    }

    fun readSelectedGroupId(): String {
        return preferences.getString(KeySelectedGroupId, AllChannelsGroupId) ?: AllChannelsGroupId
    }

    fun saveFavoriteIds(favoriteIds: Set<String>) {
        preferences.edit { putStringSet(KeyFavoriteIds, favoriteIds) }
    }

    fun readFavoriteIds(): Set<String> {
        return preferences.getStringSet(KeyFavoriteIds, emptySet())?.toSet().orEmpty()
    }

    fun saveFavoriteGroupIds(favoriteGroupIds: Set<String>) {
        preferences.edit { putStringSet(KeyFavoriteGroupIds, favoriteGroupIds) }
    }

    fun readFavoriteGroupIds(): Set<String> {
        return preferences.getStringSet(KeyFavoriteGroupIds, emptySet())?.toSet().orEmpty()
    }

    fun saveVideoCompatibilityMode(mode: VideoCompatibilityMode) {
        preferences.edit { putString(KeyVideoCompatibilityMode, mode.name) }
    }

    fun savePlaybackBackend(backend: PlaybackBackend) {
        preferences.edit { putString(KeyPlaybackBackend, backend.name) }
    }

    fun readVideoCompatibilityMode(): VideoCompatibilityMode {
        val stored = preferences.getString(KeyVideoCompatibilityMode, VideoCompatibilityMode.SURFACE_VIEW.name)
        return runCatching { VideoCompatibilityMode.valueOf(stored.orEmpty()) }
            .getOrDefault(VideoCompatibilityMode.SURFACE_VIEW)
    }

    fun readPlaybackBackend(): PlaybackBackend {
        val stored = preferences.getString(KeyPlaybackBackend, PlaybackBackend.VLC.name)
        return runCatching { PlaybackBackend.valueOf(stored.orEmpty()) }
            .getOrDefault(PlaybackBackend.VLC)
    }

    fun saveShowChannelLogos(show: Boolean) {
        preferences.edit { putBoolean(KeyShowChannelLogos, show) }
    }

    fun readShowChannelLogos(): Boolean {
        return preferences.getBoolean(KeyShowChannelLogos, false)
    }

    fun saveEpgSettings(settings: EpgSettings) {
        preferences.edit {
            putBoolean(KeyEpgEnabled, settings.enabled)
            putString(KeyEpgUrl, settings.url)
        }
    }

    fun readEpgSettings(): EpgSettings {
        return EpgSettings(
            enabled = preferences.getBoolean(KeyEpgEnabled, false),
            url = preferences.getString(KeyEpgUrl, "").orEmpty()
        )
    }

    fun saveXtreamKeepAliveSettings(settings: XtreamKeepAliveSettings) {
        val safeSettings = settings.sanitized()
        preferences.edit {
            putBoolean(KeyXtreamKeepAliveEnabled, safeSettings.enabled)
            putInt(KeyXtreamKeepAliveIntervalSeconds, safeSettings.intervalSeconds)
        }
    }

    fun readXtreamKeepAliveSettings(): XtreamKeepAliveSettings {
        return XtreamKeepAliveSettings(
            enabled = preferences.getBoolean(KeyXtreamKeepAliveEnabled, true),
            intervalSeconds = preferences.getInt(KeyXtreamKeepAliveIntervalSeconds, 45)
        ).sanitized()
    }
}

