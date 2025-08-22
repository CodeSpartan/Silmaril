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
    onTabSelected: (String) -> Unit,
    onTabClose: (Int) -> Unit
) {
    val profileManager: ProfileManager = koinInject()
    val safeSelectedTabIndex = selectedTabIndex.coerceIn(tabs.indices.takeIf { !it.isEmpty() } ?: 0..0)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = safeSelectedTabIndex == index,
                    onClick = { onTabSelected(tab.title) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(tab.title)
                            // don't display the close button if there's only one tab
                            if (tabs.size < 2)
                                return@Row
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    var newSelectIndex = selectedTabIndex
                                    profileManager.gameWindows.value[tab.title]?.onCloseWindow()
                                    profileManager.assignNewWindowsTemp(profileManager.gameWindows.value.filterKeys { it != tab.title }
                                        .toMap())
                                    // if we're closing the currently opened tab, switch to the first available one
                                    if (index == selectedTabIndex) {
                                        val firstValidProfile = profileManager.gameWindows.value.values.first()
                                        profileManager.currentClient.value = firstValidProfile.client
                                        profileManager.currentMainViewModel.value = firstValidProfile.mainViewModel
                                        profileManager.currentGroupModel.value = firstValidProfile.groupModel
                                        profileManager.currentMobsModel.value = firstValidProfile.mobsModel

                                        val firstAvailableTabIndex =
                                            tabs.indexOfFirst { it.title == firstValidProfile.profileName }
                                        newSelectIndex =
                                            if (firstAvailableTabIndex > index) firstAvailableTabIndex - 1 else firstAvailableTabIndex
                                        profileManager.currentProfileName.value =
                                            firstValidProfile.profileName.capitalized()
                                    }
                                    // if we're closing a tab to the left of current, the current id will need to be adjusted to the left
                                    else {
                                        if (selectedTabIndex > index) {
                                            newSelectIndex--
                                        }
                                    }
                                    onTabClose(newSelectIndex)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tab"
                                )
                            }
                        }
                    }
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