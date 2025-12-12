package ru.adan.silmaril.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.model.ProfileManager

data class Tab(
    val title: String,
    val content: @Composable (isFocused: Boolean, thisTabId: Int) -> Unit
)

@Composable
fun TabbedView(
    tabs: List<Tab>,
    selectedTabIndex: Int,
) {
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