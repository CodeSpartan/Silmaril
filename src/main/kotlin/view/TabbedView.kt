package view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex

data class Tab(
    val title: String,
    val content: @Composable (isFocused : Boolean, thisTabId: Int) -> Unit
)

@Composable
fun TabbedView(
    tabs: List<Tab>,
    selectedTabIndex: Int,
    onTabSelected: (Int, String) -> Unit
) {
    val safeSelectedTabIndex = selectedTabIndex.coerceIn(tabs.indices.takeIf { !it.isEmpty() } ?: 0..0)

    // @TODO: profile tabs need an RMB -> Close, which should call cleanup and make a note in settings
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = safeSelectedTabIndex == index,
                    onClick = { onTabSelected(index, tab.title) },
                    text = { Text(tab.title) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                key(tab.title) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // @TODO is this easier for the gpu or harder? transparency, I mean
                            .graphicsLayer(alpha = if (selectedTabIndex == index) 1f else 0f)
                            .zIndex(if (selectedTabIndex == index) 1f else 0f)
                    ) {
                        tab.content(selectedTabIndex == index, index)
                    }
                }
            }
        }
    }
}