package com.toigo.miptvga

import android.net.Uri
import androidx.core.net.toUri

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
        requestHeaders = requestHeaders,
        signature = session.signature
    )
}

internal fun isXtreamLiveStreamUrl(streamUrl: String): Boolean {
    val session = parseXtreamSession(streamUrl) ?: return false
    return session.isLive
}

internal fun isXtreamVodStreamUrl(streamUrl: String): Boolean {
    val trimmed = streamUrl.trim()
    if (trimmed.isBlank()) return false

    val normalized = trimmed.lowercase()
    if (
        normalized.contains("/movie/") ||
        normalized.contains("/series/") ||
        normalized.contains("type=movie") ||
        normalized.contains("type=vod") ||
        normalized.contains("type=series") ||
        normalized.contains("action=get_vod_stream") ||
        normalized.contains("action=get_series_stream") ||
        normalized.contains("action=get_series_info")
    ) {
        return true
    }

    val session = parseXtreamSession(streamUrl) ?: return false
    return !session.isLive
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
        val prefixSegments = pathSegments.dropLast(1)
        val pingUrl = buildPlayerApiUrl(
            scheme = scheme,
            authority = authority,
            prefixSegments = prefixSegments,
            username = usernameFromQuery,
            password = passwordFromQuery
        )
        val isLive = isXtreamLiveByQuery(uri)
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

    return null
}

private fun isXtreamLiveByQuery(uri: Uri): Boolean {
    val path = uri.path.orEmpty().lowercase()
    val type = uri.getQueryParameter("type")?.lowercase().orEmpty()
    val action = uri.getQueryParameter("action")?.lowercase().orEmpty()
    val output = uri.getQueryParameter("output")?.lowercase().orEmpty()

    return when {
        "/live/" in path -> true
        type == "m3u_plus" -> true
        type == "live" -> true
        action.contains("live") -> true
        output == "m3u8" -> true
        output == "ts" -> true
        else -> false
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


