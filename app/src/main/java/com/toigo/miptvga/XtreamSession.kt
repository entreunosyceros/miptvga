package com.toigo.miptvga

import android.net.Uri
import androidx.core.net.toUri

/** User-Agent compatible with most Xtream Codes / IPTV panels (many reject custom agents). */
internal const val IptvDefaultUserAgent = "VLC/3.0.21 LibVLC/3.0.21"

internal data class XtreamKeepAliveRequest(
    val pingUrl: String,
    val requestHeaders: Map<String, String>,
    val signature: String
)

internal fun buildXtreamKeepAliveRequest(
    streamUrl: String,
    requestHeaders: Map<String, String>
): XtreamKeepAliveRequest? {
    val session = parseXtreamSession(streamUrl) ?: return null
    return XtreamKeepAliveRequest(
        pingUrl = session.pingUrl,
        requestHeaders = ensureIptvHeaders(requestHeaders),
        signature = session.signature
    )
}

internal fun isXtreamStreamUrl(streamUrl: String): Boolean {
    return parseXtreamSession(streamUrl) != null || looksLikeXtreamPath(streamUrl)
}

internal fun isXtreamLiveStreamUrl(streamUrl: String): Boolean {
    if (looksLikeXtreamVodPath(streamUrl)) return false
    val session = parseXtreamSession(streamUrl)
    if (session != null) return session.isLive
    return looksLikeXtreamLivePath(streamUrl)
}

internal fun isXtreamVodStreamUrl(streamUrl: String): Boolean {
    val trimmed = streamUrl.trim()
    if (trimmed.isBlank()) return false

    if (looksLikeXtreamVodPath(trimmed)) return true

    val session = parseXtreamSession(streamUrl) ?: return false
    return !session.isLive
}

/**
 * Ensures IPTV-friendly headers. Many Xtream panels block unknown User-Agents.
 * Playlist-provided headers always win over defaults.
 */
internal fun ensureIptvHeaders(headers: Map<String, String>): Map<String, String> {
    if (headers.isEmpty()) {
        return mapOf("User-Agent" to IptvDefaultUserAgent)
    }
    val hasUserAgent = headers.keys.any { it.equals("User-Agent", ignoreCase = true) }
    if (hasUserAgent) return headers
    return LinkedHashMap<String, String>(headers.size + 1).apply {
        put("User-Agent", IptvDefaultUserAgent)
        putAll(headers)
    }
}

private fun looksLikeXtreamPath(streamUrl: String): Boolean {
    return looksLikeXtreamLivePath(streamUrl) || looksLikeXtreamVodPath(streamUrl)
}

private fun looksLikeXtreamLivePath(streamUrl: String): Boolean {
    val normalized = streamUrl.trim().lowercase()
    if (normalized.isBlank()) return false
    return when {
        "/live/" in normalized -> true
        "type=m3u_plus" in normalized -> true
        "type=live" in normalized -> true
        "get.php" in normalized && ("username=" in normalized || "password=" in normalized) -> true
        "player_api.php" in normalized -> true
        "streaming/clients_live" in normalized -> true
        "action=get_live" in normalized -> true
        else -> isClassicXtreamLivePath(streamUrl)
    }
}

private fun looksLikeXtreamVodPath(streamUrl: String): Boolean {
    val normalized = streamUrl.trim().lowercase()
    return normalized.contains("/movie/") ||
        normalized.contains("/series/") ||
        normalized.contains("/vod/") ||
        normalized.contains("type=movie") ||
        normalized.contains("type=vod") ||
        normalized.contains("type=series") ||
        normalized.contains("action=get_vod_stream") ||
        normalized.contains("action=get_series_stream") ||
        normalized.contains("action=get_series_info")
}

/**
 * Classic Xtream format: http://host:port/username/password/streamId[.ts|.m3u8]
 * Path has 3 segments; last segment is mostly numeric.
 */
private fun isClassicXtreamLivePath(streamUrl: String): Boolean {
    val uri = runCatching { streamUrl.trim().toUri() }.getOrNull() ?: return false
    if (uri.scheme !in setOf("http", "https")) return false
    val segments = uri.pathSegments.orEmpty()
    if (segments.size != 3) return false
    val streamId = segments[2].substringBefore('.').trim()
    return streamId.isNotBlank() && streamId.all { it.isDigit() } &&
        segments[0].isNotBlank() && segments[1].isNotBlank()
}

private fun parseXtreamSession(streamUrl: String): XtreamSessionInfo? {
    val trimmed = streamUrl.trim()
    if (trimmed.isBlank()) return null

    val uri = runCatching { trimmed.toUri() }.getOrNull() ?: return null
    val scheme = uri.scheme?.takeIf(String::isNotBlank) ?: return null
    val authority = uri.encodedAuthority?.takeIf(String::isNotBlank) ?: return null
    val pathSegments = uri.pathSegments.orEmpty()
    val lowerSegments = pathSegments.map { it.lowercase() }

    val usernameFromQuery = uri.getQueryParameter("username")?.trim().orEmpty()
    val passwordFromQuery = uri.getQueryParameter("password")?.trim().orEmpty()
    if (usernameFromQuery.isNotBlank() && passwordFromQuery.isNotBlank()) {
        val prefixSegments = when {
            lowerSegments.lastOrNull() in setOf("get.php", "player_api.php", "panel_api.php") ->
                pathSegments.dropLast(1)
            else -> pathSegments.dropLast(1)
        }
        val pingUrl = buildPlayerApiUrl(
            scheme = scheme,
            authority = authority,
            prefixSegments = prefixSegments,
            username = usernameFromQuery,
            password = passwordFromQuery
        )
        val isLive = isXtreamLiveByQuery(uri) && !looksLikeXtreamVodPath(trimmed)
        return XtreamSessionInfo(
            pingUrl = pingUrl,
            signature = "$pingUrl|$usernameFromQuery",
            isLive = isLive
        )
    }

    val liveLikeIndex = lowerSegments.indexOfFirst {
        it == "live" || it == "movie" || it == "series"
    }
    if (liveLikeIndex >= 0 && pathSegments.size > liveLikeIndex + 2) {
        val username = pathSegments[liveLikeIndex + 1].trim()
        val password = pathSegments[liveLikeIndex + 2].trim()
        if (username.isBlank() || password.isBlank()) return null

        val pingUrl = buildPlayerApiUrl(
            scheme = scheme,
            authority = authority,
            prefixSegments = pathSegments.take(liveLikeIndex),
            username = username,
            password = password
        )
        return XtreamSessionInfo(
            pingUrl = pingUrl,
            signature = "$pingUrl|$username",
            isLive = lowerSegments[liveLikeIndex] == "live"
        )
    }

    // Classic: /username/password/streamId
    if (pathSegments.size == 3) {
        val username = pathSegments[0].trim()
        val password = pathSegments[1].trim()
        val streamId = pathSegments[2].substringBefore('.').trim()
        if (username.isNotBlank() && password.isNotBlank() && streamId.all { it.isDigit() }) {
            val pingUrl = buildPlayerApiUrl(
                scheme = scheme,
                authority = authority,
                prefixSegments = emptyList(),
                username = username,
                password = password
            )
            return XtreamSessionInfo(
                pingUrl = pingUrl,
                signature = "$pingUrl|$username",
                isLive = !looksLikeXtreamVodPath(trimmed)
            )
        }
    }

    return null
}

private fun isXtreamLiveByQuery(uri: Uri): Boolean {
    val path = uri.path.orEmpty().lowercase()
    val type = uri.getQueryParameter("type")?.lowercase().orEmpty()
    val action = uri.getQueryParameter("action")?.lowercase().orEmpty()
    val output = uri.getQueryParameter("output")?.lowercase().orEmpty()

    return when {
        "/movie/" in path || "/series/" in path || "/vod/" in path -> false
        "/live/" in path -> true
        type == "m3u_plus" -> true
        type == "live" -> true
        action.contains("live") -> true
        output == "m3u8" -> true
        output == "ts" -> true
        else -> true
    }
}

private fun buildPlayerApiUrl(
    scheme: String,
    authority: String,
    prefixSegments: List<String>,
    username: String,
    password: String
): String {
    val normalizedPrefix = prefixSegments
        .map { it.trim('/') }
        .filter { it.isNotBlank() }

    return Uri.Builder()
        .scheme(scheme)
        .encodedAuthority(authority)
        .apply {
            normalizedPrefix.forEach(::appendPath)
            appendPath("player_api.php")
            appendQueryParameter("username", username)
            appendQueryParameter("password", password)
        }
        .build()
        .toString()
}

private data class XtreamSessionInfo(
    val pingUrl: String,
    val signature: String,
    val isLive: Boolean
)
