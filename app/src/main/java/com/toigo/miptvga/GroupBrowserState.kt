package com.toigo.miptvga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Session-only UI navigation. Owns no channels, media request or persisted preferences. */
internal class GroupBrowserState {
    var active by mutableStateOf(false)
        private set
    var showingGroups by mutableStateOf(true)
        private set
    var selectedGroupId by mutableStateOf<String?>(null)
        private set
    var sourceEpoch by mutableIntStateOf(0)
        private set
    var selectionEpoch by mutableIntStateOf(0)
        private set
    // Focus changes do not invalidate the screen or publish playback state.
    var focusedGroupIndex = 0
        private set
    private var focusedGroupId: String? = null
    private var selectedGroupIndex = -1
    private var source: Any? = null
    private var groups: List<ChannelGroup>? = null

    fun bindSource(sourceKey: Any?, available: List<ChannelGroup>) {
        val changedSource = source != sourceKey
        if (!changedSource && groups === available) return
        source = sourceKey
        groups = available
        if (changedSource) {
            sourceEpoch++
            if (active) showingGroups = true
        }
        // Only run when the source/group collection changes, never for each focus event.
        selectedGroupIndex = -1
        var focused = -1
        available.forEachIndexed { index, group ->
            if (group.id == selectedGroupId) selectedGroupIndex = index
            if (group.id == focusedGroupId) focused = index
        }
        if (selectedGroupId != null && selectedGroupIndex < 0) {
            selectedGroupId = null
            selectionEpoch++
            showingGroups = true
        }
        focusedGroupIndex = if (focused >= 0) focused else 0
        focusedGroupId = available.getOrNull(focusedGroupIndex)?.id
    }

    fun openGroups() { active = true; showingGroups = true }
    fun leaveGroups() { active = false }
    fun focusGroup(index: Int) {
        val group = groups?.getOrNull(index) ?: return
        focusedGroupIndex = index
        focusedGroupId = group.id
    }

    /** Returns only a filter ID. The caller uses the existing ViewModel.selectGroup. */
    fun chooseGroup(index: Int): String? {
        val group = groups?.getOrNull(index) ?: return null
        if (selectedGroupId != group.id) selectionEpoch++
        selectedGroupId = group.id
        selectedGroupIndex = index
        focusGroup(index)
        active = true
        showingGroups = false
        return group.id
    }

    fun back(): Boolean {
        if (!active || showingGroups) return false
        showingGroups = true
        focusGroup(selectedGroupIndex)
        return true
    }
}
