package com.toigo.miptvga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class EpgRepository(
    private val playlistLoader: PlaylistLoader,
    private val xmltvParser: XmltvParser
) {
    suspend fun loadProgramTimelines(
        epgUrl: String,
        channels: List<Channel>,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, List<CurrentProgram>> = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        val interests = EpgChannelInterests(
            ids = channels.mapNotNull { context.ensureActive(); it.tvgId?.normalizeEpgLookupKey()?.takeIf(String::isNotBlank) }.toSet(),
            names = channels.map { context.ensureActive(); it.name.normalizeEpgLookupKey() }.filter { it.isNotBlank() }.toSet()
        )
        if (interests.ids.isEmpty() && interests.names.isEmpty()) return@withContext emptyMap()

        playlistLoader.withUrlReader(epgUrl.trim()) { reader ->
            xmltvParser.parseProgramTimelines(
                reader = reader,
                interests = interests,
                nowMillis = nowMillis,
                checkCancelled = { context.ensureActive() }
            )
        }
    }
}

