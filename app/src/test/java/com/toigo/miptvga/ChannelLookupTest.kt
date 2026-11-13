package com.toigo.miptvga

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ChannelLookupTest {
    private val channel = Channel("Synthetic", "https://example.invalid/stream")
    private fun entries(vararg indices: Int) = indices.map { ChannelListEntry(it, channel) }

    @Test fun matchesLinearLookupForGroupsSearchAndFavorites() {
        val random = Random(23)
        val all = List(3000) { ChannelListEntry(it, channel.copy(group = "Group ${it % 11}")) }
        val groups = buildChannelsByGroupId(all)
        val variants = groups.values + listOf(all, all.filter { it.originalIndex % 7 == 0 }, emptyList())
        for (list in variants) {
            repeat(200) {
                val index = random.nextInt(-1, 3100)
                assertEquals(list.indexOfFirst { it.originalIndex == index }, findVisibleChannelIndex(list, index))
            }
        }
    }

    @Test fun absentSelectionDoesNotResolveToAnotherChannel() {
        val list = entries(5, 9, 21)
        assertEquals(-1, findVisibleChannelIndex(list, 0))
        assertEquals(-1, findVisibleChannelIndex(list, Int.MAX_VALUE))
        // Top-of-list focus fallback is deliberately separate from channel identity.
        assertEquals(0, visibleIndexForSelection(0, list))
        assertEquals(-1, visibleIndexForSelection(0, emptyList()))
        assertEquals(1, findVisibleChannelIndex(list, 9))
    }

    @Test fun groupAndFilteredEntriesKeepIncreasingOriginalIndices() {
        val all = List(1000) { ChannelListEntry(it, channel.copy(group = "Group ${it % 13}")) }
        for (list in buildChannelsByGroupId(all).values) {
            assertTrue(list.zipWithNext().all { (a, b) -> a.originalIndex < b.originalIndex })
            val filtered = list.filter { it.originalIndex % 5 == 0 }
            filtered.forEachIndexed { visible, entry -> assertEquals(visible, findVisibleChannelIndex(filtered, entry.originalIndex)) }
        }
    }

    @Test fun millionEntryAllLookupDoesNotScanCatalogue() {
        var reads = 0
        val list = object : AbstractList<ChannelListEntry>(), java.util.RandomAccess {
            override val size = 1_000_000
            override fun get(index: Int): ChannelListEntry { reads++; return ChannelListEntry(index, channel) }
        }
        assertEquals(999_999, findVisibleChannelIndex(list, 999_999))
        assertEquals(1, reads)
    }

    @Test fun millionEntrySubsetLookupUsesBoundedReads() {
        var reads = 0
        val list = object : AbstractList<ChannelListEntry>(), java.util.RandomAccess {
            override val size = 1_000_000
            override fun get(index: Int): ChannelListEntry { reads++; return ChannelListEntry(index * 2 + 5, channel) }
        }
        assertEquals(999_999, findVisibleChannelIndex(list, 2_000_003))
        assertTrue("Binary lookup must not scan a million entries", reads <= 21)
    }
}
