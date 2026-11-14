package com.toigo.miptvga

import org.junit.Assert.*
import org.junit.Test

class GroupBrowserStateTest {
    private val groups = listOf(ChannelGroup("z", "Z", 120_000), ChannelGroup("a", "A", 30_000))
    private fun browser() = GroupBrowserState().apply { bindSource("source-A", groups) }

    @Test fun openingGroupsDoesNotSelectAnyFilter() {
        val state = browser()
        state.openGroups()
        assertTrue(state.active)
        assertTrue(state.showingGroups)
        assertNull(state.selectedGroupId)
    }

    @Test fun selectionReturnsExistingFilterIdInOriginalOrderAndBackRestoresGroup() {
        val state = browser()
        state.openGroups()
        assertEquals("a", state.chooseGroup(1))
        assertFalse(state.showingGroups)
        assertEquals("a", state.selectedGroupId)
        assertTrue(state.back())
        assertTrue(state.showingGroups)
        assertEquals(1, state.focusedGroupIndex)
        assertFalse(state.back()) // Let the contextual Android handler take over.
    }

    @Test fun televisionAndFavoritesCanLeaveWithoutForgettingLastGroup() {
        val state = browser()
        state.chooseGroup(1)
        val selection = state.selectionEpoch
        state.leaveGroups()
        assertFalse(state.active)
        assertFalse(state.back())
        state.openGroups()
        assertEquals("a", state.selectedGroupId)
        assertEquals(1, state.focusedGroupIndex)
        assertEquals(selection, state.selectionEpoch)
    }

    @Test fun sameGroupKeepsChannelPositionEpochAndDifferentGroupResetsIt() {
        val state = browser()
        state.chooseGroup(0)
        val epoch = state.selectionEpoch
        state.back()
        state.chooseGroup(0)
        assertEquals(epoch, state.selectionEpoch)
        state.chooseGroup(1)
        assertEquals(epoch + 1, state.selectionEpoch)
    }

    @Test fun changedSourceWithMissingGroupReturnsSafelyToGroupList() {
        val state = browser()
        state.chooseGroup(1)
        val epoch = state.sourceEpoch
        state.bindSource("source-B", listOf(ChannelGroup("other", "Other", 1)))
        assertTrue(state.active)
        assertTrue(state.showingGroups)
        assertNull(state.selectedGroupId)
        assertEquals(0, state.focusedGroupIndex)
        assertEquals(epoch + 1, state.sourceEpoch)
    }

    @Test fun groupCollectionRefreshRestoresFocusByIdAfterReordering() {
        val state = browser()
        state.chooseGroup(1)
        state.back()
        state.bindSource("source-A", groups.reversed())
        assertEquals("a", state.selectedGroupId)
        assertEquals(0, state.focusedGroupIndex)
    }

    @Test fun newSourceWithSameGroupRemembersItButRequiresExplicitEntry() {
        val state = browser()
        state.chooseGroup(1)
        state.bindSource("source-B", groups)
        assertEquals("a", state.selectedGroupId)
        assertTrue(state.showingGroups)
        assertEquals(1, state.focusedGroupIndex)
        assertEquals("a", state.chooseGroup(1))
        assertFalse(state.showingGroups)
    }

    @Test fun emptySourceAndInvalidSelectionCannotOpenStaleChannels() {
        val state = browser()
        state.chooseGroup(0)
        state.bindSource("source-B", emptyList())
        assertNull(state.chooseGroup(0))
        assertNull(state.chooseGroup(-1))
        assertNull(state.selectedGroupId)
        assertTrue(state.showingGroups)
    }

    @Test fun repeatedCompositionAndFocusDoNotTraverseOrCopyCatalogue() {
        var reads = 0
        val available = object : AbstractList<ChannelGroup>() {
            override val size = 2
            override fun get(index: Int): ChannelGroup { reads++; return groups[index] }
        }
        val state = GroupBrowserState()
        state.bindSource("source-A", available)
        reads = 0
        repeat(100) { state.bindSource("source-A", available); state.focusGroup(it % 2) }
        assertEquals(100, reads) // One indexed read per focus, no scan of the collection.
        assertNull(state.selectedGroupId) // Focus never chooses a filter.
    }
}
