package ru.adan.silmaril


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.standalone.styling.Default
import java.awt.Desktop
import java.net.URI
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.koin.compose.koinInject
import ru.adan.silmaril.generated.resources.Res
import ru.adan.silmaril.generated.resources.icon
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.model.ProfileManager
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalLayoutApi
@Composable
internal fun DecoratedWindowScope.TitleBarView(
    showTitleMenu: MutableState<Boolean>,
    showProfileDialog: MutableState<Boolean>,
    selectedTabIndex: MutableState<Int>,
    ) {

    TitleBar(Modifier.newFullscreenControls(), gradientStartColor = Color(0xff9619b3)) {
        Row(Modifier.align(Alignment.Start)) {
            Dropdown(
                Modifier.height(30.dp),
                menuContent = {
                    selectableItem(
                        selected = true,
                        onClick = { println("selectable 1") },
                    ) {
                        showTitleMenu.value = true
                        DisposableEffect(Unit) {
                            onDispose {
                                showTitleMenu.value = false
                            }
                        }

                        Row(
                            modifier = Modifier.height(30.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(painterResource(Res.drawable.icon), null)
                            Text("Title selectable 1")
                        }
                    }

                    selectableItem(
                        selected = true,
                        onClick = { println("selectable 2") },
                    ) {
                        Row(
                            modifier = Modifier.height(30.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(painterResource(Res.drawable.icon), null)
                            Text("Title selectable 2")
                        }
                    }

                    submenu(
                        true,
                        null,
                        submenu = {
                            selectableItem(
                                selected = true,
                                onClick = { println("Submenu selectable 1") },
                            ) {
                                Row(
                                    modifier = Modifier.height(30.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(painterResource(Res.drawable.icon), null)
                                    Text("Submenu Title selectable 1")
                                }
                            }

                            separator()

                            selectableItem(
                                selected = true,
                                onClick = { println("Submenu selectable 2") },
                            ) {
                                Row(
                                    modifier = Modifier.height(30.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Image(painterResource(Res.drawable.icon), null)
                                    Text("Submenu Title selectable 2")
                                }
                            }
                        },
                        content = {
                            Text("Submenu items")
                        }
                    )

                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        //Icon(MainViewModel.currentView.iconKey, null, hint = Size(20))
                        Image(painterResource(Res.drawable.icon), null)
                        Text("Silmaril")
                    }
                }
            }
        }

        // Silmaril menubar imitation
//        Dropdown(
//            Modifier.height(30.dp),
//            menuContent = {
//                selectableItem(
//                    selected = true,
//                    iconKey = AllIconsKeys.Actions.Checked,
//                    onClick = { println("selectable 1") },
//                ) {
//                    Row(
//                        //modifier = Modifier.height(30.dp),
//                        horizontalArrangement = Arrangement.spacedBy(4.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                    ) {
//                        //Icon(AllIconsKeys.Actions.Checked, contentDescription = null)
//                        Text("Title selectable 1")
//                    }
//                }
//
//                selectableItem(
//                    selected = true,
//                    iconKey = AllIconsKeys.General.GreenCheckmark,
//                    onClick = { println("Selectable 2") },
//                ) {
//                    Row(
//                        //modifier = Modifier.height(30.dp),
//                        horizontalArrangement = Arrangement.spacedBy(4.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                    ) {
//                        Text(text = "Open...", /* fontSize = 13.sp */)
//                    }
//                }
//                selectableItem(
//                    selected = true,
//                    iconKey = AllIconsKeys.Actions.Checked_selected,
//                    onClick = { println("Selectable 2") },
//                ) {
//                    Row(
//                        //modifier = Modifier.height(30.dp),
//                        horizontalArrangement = Arrangement.spacedBy(4.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                    ) {
//                        //Image(painterResource(Res.drawable.compose_multiplatform), null)
//                        Text("Open...")
//                    }
//                }
//            }
//        ) {
//            Row(
//                horizontalArrangement = Arrangement.spacedBy(3.dp),
//                verticalAlignment = Alignment.CenterVertically,
//            ) {
//                Row(
//                    horizontalArrangement = Arrangement.spacedBy(4.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                ) {
//                    Text("Файл")
//                }
//            }
//        }

        Row(Modifier.align(Alignment.CenterHorizontally).width(500.dp)) {
            TabsPanel(selectedTabIndex)
        }

        Row(Modifier.align(Alignment.End)) {
            Tooltip({ Text("Добавить окно") }) {
                IconButton(
                    onClick = { showProfileDialog.value = true },
                    modifier = Modifier.size(JewelTheme.defaultTabStyle.metrics.tabHeight)
                ) {
                    Icon(key = AllIconsKeys.General.Add, contentDescription = "Добавить окно")
                }
            }

            Tooltip({ Text("Open Jewel Github repository") }) {
                IconButton(
                    { Desktop.getDesktop().browse(URI.create("https://github.com/JetBrains/jewel")) },
                    Modifier.size(40.dp).padding(5.dp),
                ) {
                    //Icon(ShowcaseIcons.gitHub, "Github")
                    Image(painterResource(Res.drawable.icon), null)
                }
            }

            Tooltip({
                Text("Switch to light theme with light header")
            }) {
                IconButton(
                    {
                        println("Button click 1")
                    },
                    Modifier.size(40.dp).padding(5.dp),
                ) {
                    //Icon(key = ShowcaseIcons.themeDark, contentDescription = "Dark", hints = arrayOf(Size(20)))
                    Image(painterResource(Res.drawable.icon), null)
                }
            }
        }
    }
}

@Composable
private fun TabsPanel(selectedTabIndex: MutableState<Int>) {
    val profileManager: ProfileManager = koinInject()
    val windows by profileManager.gameWindows.collectAsState()

    val purpleTabStyle = remember {
        TabStyle.Default.dark(
            colors = TabColors.Default.dark(underlineSelected = IntUiDarkTheme.colors.purple(6))
        )
    }

    val tabs = remember(windows.size, selectedTabIndex.value) {
        windows.values.mapIndexed { index, profile ->
            TabData.Default(
                selected = index == selectedTabIndex.value,
                content = { tabState ->
                    val iconProvider = rememberResourcePainterProvider(AllIconsKeys.Actions.ChangeView)
                    val icon by iconProvider.getPainter(Stateful(tabState))
                    SimpleTabContent(label = profile.profileName.capitalized(), state = tabState, icon = icon)
                },
                onClose = {
                    profileManager.removeWindow(profile.profileName)
                    if (selectedTabIndex.value >= index) {
                        val maxPossibleIndex = max(0, windows.size - 1)
                        val idToSelect = (selectedTabIndex.value - 1).coerceIn(0..maxPossibleIndex)
                        profileManager.switchWindow(idToSelect)
                    }
                },
                onClick = {
                    profileManager.switchWindow(profile.profileName)
                },
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TabStrip(tabs = tabs, style = purpleTabStyle)
    }
}
