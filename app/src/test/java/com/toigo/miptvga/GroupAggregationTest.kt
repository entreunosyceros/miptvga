package com.toigo.miptvga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupAggregationTest {
    @Test
    fun groupsWithSameNormalizedIdAppearOnlyOnce() {
        val entries = listOf(
            ChannelListEntry(
                originalIndex = 0,
                channel = Channel(
                    name = "Canal 1",
                    streamUrl = "https://example.com/1.m3u8",
                    group = "Noticias"
                )
            ),
            ChannelListEntry(
                originalIndex = 1,
                channel = Channel(
                    name = "Canal 2",
                    streamUrl = "https://example.com/2.m3u8",
                    group = " noticias "
                )
            ),
            ChannelListEntry(
                originalIndex = 2,
                channel = Channel(
                    name = "Canal 3",
                    streamUrl = "https://example.com/3.m3u8",
                    group = "NOTICIAS"
                )
            )
        )

        val groups = buildChannelGroups(
            entries = entries,
            favoriteIds = emptySet(),
            favoriteGroupIds = emptySet()
        )

        val matchingGroups = groups.filter { it.id == buildGroupId("Noticias") }
        assertEquals(1, matchingGroups.size)
        assertEquals(3, matchingGroups.single().count)
        assertEquals("Noticias", matchingGroups.single().title)
    }

    @Test
    fun favoriteVirtualGroupIsStillAddedWhenNeeded() {
        val favoriteChannel = Channel(
            name = "Canal Favorito",
            streamUrl = "https://example.com/live/fav.m3u8",
            group = "Deportes"
        )
        val entries = listOf(
            ChannelListEntry(
                originalIndex = 0,
                channel = favoriteChannel
            )
        )

        val groups = buildChannelGroups(
            entries = entries,
            favoriteIds = setOf(favoriteIdForChannel(favoriteChannel)),
            favoriteGroupIds = emptySet()
        )

        assertTrue(groups.any { it.id == FavoriteChannelsGroupId && it.count == 1 })
        assertTrue(groups.any { it.id == AllChannelsGroupId && it.count == 1 })
    }
}
