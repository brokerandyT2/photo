// shared/core-ui/src/commonMain/kotlin/com/x3squaredcircles/pixmap/core/ui/components/CoreTabRow.kt
package com.x3squaredcircles.pixmap.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Simple tab row component for Core UI screens
 * Currently supports text-only tabs with future icon support
 */
@Composable
fun CoreTabRow(
    tabs: List<CoreTab>,
    pagerState: PagerState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        modifier = modifier.fillMaxWidth()
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                text = { Text(tab.title) },
                icon = tab.icon?.let { icon ->
                    { Icon(icon, contentDescription = tab.title) }
                }
            )
        }
    }
}

/**
 * Simple tab definition for Core UI
 * Text-only for now, with optional icon support for future
 */
data class CoreTab(
    val title: String,
    val icon: ImageVector? = null
)

/**
 * Builder for creating Core tabs
 */
object CoreTabs {
    /**
     * Creates text-only tab
     */
    fun text(title: String) = CoreTab(title = title)

    /**
     * Creates tab with icon (for future use)
     */
    fun withIcon(title: String, icon: ImageVector) = CoreTab(title = title, icon = icon)

    /**
     * Pre-defined tabs for Edit Location screen
     */
    fun editLocationTabs() = listOf(
        text("Edit"),
        text("Weather")
    )
}