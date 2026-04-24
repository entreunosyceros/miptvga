package com.toigo.miptvga

internal class EpgRepository(
    private val playlistLoader: PlaylistLoader,
    private val xmltvParser: XmltvParser
) {
    suspend fun loadProgramTimelines(
        epgUrl: String,
        channels: List<Channel>,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, List<CurrentProgram>> {
        val interests = EpgChannelInterests(
            ids = channels.mapNotNull { it.tvgId?.normalizeEpgLookupKey()?.takeIf(String::isNotBlank) }.toSet(),
            names = channels.map { it.name.normalizeEpgLookupKey() }.filter { it.isNotBlank() }.toSet()
        )
        if (interests.ids.isEmpty() && interests.names.isEmpty()) return emptyMap()

        return playlistLoader.withUrlReader(epgUrl.trim()) { reader ->
            xmltvParser.parseProgramTimelines(
                reader = reader,
                interests = interests,
                nowMillis = nowMillis
            )
        }
    }
}

