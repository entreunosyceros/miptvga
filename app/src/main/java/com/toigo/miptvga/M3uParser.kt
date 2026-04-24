package com.toigo.miptvga

import java.io.BufferedReader

private const val InitialChannelCapacity = 8_192
private const val ExtInfMarker = "#EXTINF"
private const val GroupTitleAttribute = "group-title=\""
private const val TvgLogoAttribute = "tvg-logo=\""
private const val TvgIdAttribute = "tvg-id=\""
private const val ExtVlcOptMarker = "#EXTVLCOPT:"
private const val HttpHeaderSeparator = '|'
private val KnownHeaderAliases = mapOf(
    "http-user-agent" to "User-Agent",
    "user-agent" to "User-Agent",
    "http-referrer" to "Referer",
    "referrer" to "Referer",
    "referer" to "Referer",
    "http-referer" to "Referer",
    "http-origin" to "Origin",
    "origin" to "Origin",
    "cookie" to "Cookie",
    "authorization" to "Authorization"
)
private const val DefaultChannelName = "Canal"
private const val DefaultUnnamedChannel = "Canal sin nombre"

internal class M3uParser {
    fun parse(content: String): List<Channel> {
        return content.reader().buffered().use(::parse)
    }

    fun parse(reader: BufferedReader): List<Channel> {
        val result = ArrayList<Channel>(InitialChannelCapacity)
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogoUrl: String? = null
        var pendingTvgId: String? = null
        var pendingHeaders: Map<String, String> = emptyMap()

        while (true) {
            val rawLine = reader.readLine() ?: break
            val line = rawLine.trim().trimStart('\uFEFF')
            if (line.isEmpty()) continue

            if (line.startsWith(ExtInfMarker, ignoreCase = true)) {
                pendingGroup = extractGroupTitle(line)
                pendingLogoUrl = extractLogoUrl(line)
                pendingTvgId = extractTvgId(line)
                pendingName = line.substringAfterLast(',').trim().ifBlank { DefaultUnnamedChannel }
                pendingHeaders = emptyMap()
            } else if (line.startsWith(ExtVlcOptMarker, ignoreCase = true)) {
                extractHeaderFromVlcOpt(line)?.let { (name, value) ->
                    val mutableHeaders = when (pendingHeaders) {
                        is LinkedHashMap<String, String> -> pendingHeaders
                        else -> LinkedHashMap<String, String>(4).also { it.putAll(pendingHeaders) }
                    }
                    mutableHeaders[name] = value
                    pendingHeaders = mutableHeaders
                }
            } else if (!line.startsWith("#")) {
                val name = pendingName ?: DefaultChannelName
                val parsedSource = parsePlaybackSource(line, pendingHeaders)
                result.add(
                    Channel(
                        name = name,
                        streamUrl = line,
                        playbackUrl = parsedSource.url,
                        requestHeaders = parsedSource.headers,
                        group = pendingGroup,
                        logoUrl = pendingLogoUrl,
                        tvgId = pendingTvgId
                    )
                )
                pendingName = null
                pendingGroup = null
                pendingLogoUrl = null
                pendingTvgId = null
                pendingHeaders = emptyMap()
            }
        }

        return result
    }

    private fun extractGroupTitle(line: String): String? {
        return extractQuotedAttribute(line, GroupTitleAttribute)
    }

    private fun extractLogoUrl(line: String): String? {
        return extractQuotedAttribute(line, TvgLogoAttribute)
    }

    private fun extractTvgId(line: String): String? {
        return extractQuotedAttribute(line, TvgIdAttribute)
    }

    private fun extractQuotedAttribute(line: String, attribute: String): String? {
        val startIndex = line.indexOf(attribute, ignoreCase = true)
        if (startIndex < 0) return null

        val valueStart = startIndex + attribute.length
        val valueEnd = line.indexOf('"', startIndex = valueStart)
        if (valueEnd <= valueStart) return null

        return line.substring(valueStart, valueEnd)
    }

    private fun extractHeaderFromVlcOpt(line: String): Pair<String, String>? {
        val option = line.substringAfter(':', missingDelimiterValue = "").trim()
        if (option.isBlank()) return null

        val key = option.substringBefore('=', missingDelimiterValue = "").trim()
        val value = option.substringAfter('=', missingDelimiterValue = "").trim()
        val headerName = normalizeHeaderName(key) ?: return null
        if (value.isBlank()) return null
        return headerName to value
    }

    private fun parsePlaybackSource(
        rawSource: String,
        baseHeaders: Map<String, String>
    ): PlaybackSource {
        val trimmed = rawSource.trim()
        if (trimmed.isEmpty()) return PlaybackSource(url = rawSource, headers = baseHeaders)

        val firstSeparatorIndex = trimmed.indexOf(HttpHeaderSeparator)
        if (firstSeparatorIndex < 0) {
            return PlaybackSource(url = trimmed, headers = baseHeaders)
        }

        val resolvedHeaders = LinkedHashMap<String, String>(baseHeaders.size + 4).apply {
            putAll(baseHeaders)
        }
        var segmentStart = firstSeparatorIndex + 1

        while (segmentStart <= trimmed.lastIndex) {
            val nextSeparatorIndex = trimmed.indexOf(HttpHeaderSeparator, startIndex = segmentStart)
            val segmentEnd = if (nextSeparatorIndex >= 0) nextSeparatorIndex else trimmed.length
            val headerSegment = trimmed.substring(segmentStart, segmentEnd)
            val equalsIndex = headerSegment.indexOf('=')
            if (equalsIndex <= 0 || equalsIndex == headerSegment.lastIndex) {
                return PlaybackSource(url = trimmed, headers = baseHeaders)
            }

            val headerKey = headerSegment.substring(0, equalsIndex).trim()
            val headerValue = headerSegment.substring(equalsIndex + 1).trim()
            val headerName = normalizeHeaderName(headerKey)
            if (headerName == null || headerValue.isBlank()) {
                return PlaybackSource(url = trimmed, headers = baseHeaders)
            }

            resolvedHeaders[headerName] = headerValue
            if (nextSeparatorIndex < 0) break
            segmentStart = nextSeparatorIndex + 1
        }

        return PlaybackSource(
            url = trimmed.substring(0, firstSeparatorIndex).trim(),
            headers = resolvedHeaders
        )
    }

    private fun normalizeHeaderName(rawKey: String): String? {
        if (rawKey.isBlank()) return null
        val normalizedKey = rawKey.trim().lowercase()
        return KnownHeaderAliases[normalizedKey]
    }

    private data class PlaybackSource(
        val url: String,
        val headers: Map<String, String>
    )
}

