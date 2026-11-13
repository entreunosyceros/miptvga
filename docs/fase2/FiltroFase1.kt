package com.toigo.miptvga

// Exact phase-1 method and result, visibility adjusted for isolated measurement.
internal class FiltroFase1 {
    data class FilterResult(
        val filteredChannels: List<ChannelListEntry>,
        val indexByOriginalIndex: Map<Int, Int>,
        val selectedIndex: Int,
        val selectedVisibleIndex: Int,
        val groups: List<ChannelGroup>
    )

    fun computeFilterResult(
        entries: List<ChannelListEntry>,
        channelsByGroupId: Map<String, List<ChannelListEntry>>,
        selectedGroupId: String,
        searchQuery: String,
        favoriteIds: Set<String>,
        favoriteGroupIds: Set<String>,
        selectedIndex: Int,
        groups: List<ChannelGroup>,
        preservePlaybackSelection: Boolean,
        checkCancelled: () -> Unit = {}
    ): FilterResult {
        checkCancelled()
        var checked = 0
        val groupFiltered = when (selectedGroupId) {
            AllChannelsGroupId -> entries
            FavoriteChannelsGroupId -> entries.filter {
                if (checked++ and 255 == 0) checkCancelled()
                isChannelEntryFavorite(it, favoriteIds, favoriteGroupIds)
            }
            else -> channelsByGroupId[selectedGroupId].orEmpty()
        }

        val filteredChannels = if (searchQuery.isBlank()) {
            groupFiltered
        } else {
            groupFiltered.filter { entry ->
                if (checked++ and 255 == 0) checkCancelled()
                entry.channel.name.contains(searchQuery, ignoreCase = true) ||
                    entry.groupTitle.contains(searchQuery, ignoreCase = true)
            }
        }

        val indexMap = HashMap<Int, Int>(filteredChannels.size.coerceAtLeast(16))
        filteredChannels.forEachIndexed { visibleIndex, entry ->
            if (visibleIndex and 255 == 0) checkCancelled()
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
}
