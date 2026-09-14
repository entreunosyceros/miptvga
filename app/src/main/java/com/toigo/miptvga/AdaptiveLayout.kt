package com.toigo.miptvga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive window classes for phones, tablets and Android TV boxes.
 */
enum class AppWindowSizeClass {
    Compact,  // phones / narrow (< 600dp)
    Medium,   // small TV / tablet (600–839dp)
    Expanded  // TV landscape / large (≥ 840dp)
}

@Immutable
data class AppWindowMetrics(
    val sizeClass: AppWindowSizeClass,
    val widthDp: Int,
    val heightDp: Int,
    val isLandscape: Boolean
) {
    val isCompact: Boolean get() = sizeClass == AppWindowSizeClass.Compact
    val isMedium: Boolean get() = sizeClass == AppWindowSizeClass.Medium
    val isExpanded: Boolean get() = sizeClass == AppWindowSizeClass.Expanded

    /** Outer safe inset — larger on TVs (overscan), tighter on phones. */
    val safePadding: PaddingValues
        get() = when (sizeClass) {
            AppWindowSizeClass.Compact -> PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            AppWindowSizeClass.Medium -> PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            AppWindowSizeClass.Expanded -> PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        }

    /** Content padding used by secondary full screens. */
    val screenPadding: Dp
        get() = when (sizeClass) {
            AppWindowSizeClass.Compact -> 12.dp
            AppWindowSizeClass.Medium -> 16.dp
            AppWindowSizeClass.Expanded -> 20.dp
        }

    /**
     * Side panel width for the main TV dashboard.
     * Uses a fraction of the available width, clamped to sensible min/max.
     */
    fun sidePanelWidth(availableWidth: Dp, largePlaylist: Boolean): Dp {
        val fraction = when (sizeClass) {
            AppWindowSizeClass.Compact -> 1f
            AppWindowSizeClass.Medium -> if (largePlaylist) 0.40f else 0.36f
            AppWindowSizeClass.Expanded -> if (largePlaylist) 0.32f else 0.28f
        }
        val minW = when (sizeClass) {
            AppWindowSizeClass.Compact -> availableWidth
            AppWindowSizeClass.Medium -> 280.dp
            AppWindowSizeClass.Expanded -> 300.dp
        }
        val maxW = when (sizeClass) {
            AppWindowSizeClass.Compact -> availableWidth
            AppWindowSizeClass.Medium -> 360.dp
            AppWindowSizeClass.Expanded -> if (largePlaylist) 420.dp else 380.dp
        }
        val target = availableWidth * fraction
        return target.coerceIn(minW, maxW)
    }

    /**
     * Height fraction for the channel panel when stacked above the player (narrow screens).
     */
    fun stackedPanelHeight(availableHeight: Dp): Dp {
        val fraction = when {
            availableHeight < 520.dp -> 0.48f
            availableHeight < 700.dp -> 0.42f
            else -> 0.38f
        }
        return (availableHeight * fraction).coerceIn(220.dp, 420.dp)
    }

    /** Prefer stacked (panel on top, player below) on narrow widths. */
    fun useStackedMainLayout(availableWidth: Dp): Boolean {
        return availableWidth < 720.dp || sizeClass == AppWindowSizeClass.Compact
    }

    /** Prefer side-by-side guide columns on wide screens. */
    fun useWideGuideLayout(availableWidth: Dp): Boolean {
        return availableWidth >= 900.dp && isLandscape
    }

    fun guideGroupsWidth(availableWidth: Dp): Dp {
        val target = availableWidth * 0.28f
        return target.coerceIn(220.dp, 320.dp)
    }

    fun guideStackedGroupsHeight(availableHeight: Dp): Dp {
        return (availableHeight * 0.30f).coerceIn(140.dp, 240.dp)
    }

    /** Max content width for centered panels (About, Landing, dialogs). */
    val maxContentWidth: Dp
        get() = when (sizeClass) {
            AppWindowSizeClass.Compact -> 560.dp
            AppWindowSizeClass.Medium -> 700.dp
            AppWindowSizeClass.Expanded -> 820.dp
        }
}

@Composable
fun rememberAppWindowMetrics(): AppWindowMetrics {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    return remember(widthDp, heightDp) {
        val sizeClass = when {
            widthDp < 600 -> AppWindowSizeClass.Compact
            widthDp < 840 -> AppWindowSizeClass.Medium
            else -> AppWindowSizeClass.Expanded
        }
        AppWindowMetrics(
            sizeClass = sizeClass,
            widthDp = widthDp,
            heightDp = heightDp,
            isLandscape = widthDp >= heightDp
        )
    }
}
