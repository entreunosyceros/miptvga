package com.toigo.miptvga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesBehaviorTest {
    @Test
    fun groupFavoriteMarksItsChannelsAsFavorite() {
        val channel = Channel(
            name = "Canal Noticias",
            streamUrl = "https://example.com/live/news.m3u8",
            group = "Noticias"
        )
        val entry = ChannelListEntry(
            originalIndex = 0,
            channel = channel
        )

        assertTrue(
            isChannelEntryFavorite(
                entry = entry,
                favoriteIds = emptySet(),
                favoriteGroupIds = setOf(buildGroupId("Noticias"))
            )
        )
        assertTrue(
            isChannelFavorite(
                channel = channel,
                favoriteIds = emptySet(),
                favoriteGroupIds = setOf(buildGroupId("Noticias"))
            )
        )
    }

    @Test
    fun channelOutsideFavoriteGroupRemainsNonFavorite() {
        val channel = Channel(
            name = "Canal Cine",
            streamUrl = "https://example.com/vod/movie.mp4",
            group = "Películas"
        )

        assertFalse(
            isChannelFavorite(
                channel = channel,
                favoriteIds = emptySet(),
                favoriteGroupIds = setOf(buildGroupId("Noticias"))
            )
        )
    }

    @Test
    fun blankChannelGroupFallsBackToDefaultGroupId() {
        val channel = Channel(
            name = "Canal Suelto",
            streamUrl = "https://example.com/live/free.m3u8",
            group = "   "
        )

        assertEquals(buildGroupId(DefaultGroupTitle), groupIdForChannel(channel))
        assertEquals(DefaultGroupTitle, normalizedGroupTitle(channel.group))
    }
}
