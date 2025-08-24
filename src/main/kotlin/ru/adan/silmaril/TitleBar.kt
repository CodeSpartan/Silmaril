package ru.adan.silmaril


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.standalone.styling.Default
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabContentScope
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.PainterHint
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
    mainWindow: ComposeWindow,
    showTitleMenu: MutableState<Boolean>,
    showProfileDialog: MutableState<Boolean>,
    selectedTabIndex: MutableState<Int>,
) {
    val profileManager: ProfileManager = koinInject()
    val windows by profileManager.gameWindows.collectAsState()

    TitleBar(Modifier.newFullscreenControls(), gradientStartColor = Color(0xff9619b3)) {

        // 71 is magic number. Normally, the TitleBar wouldn't give us more space than the title bar, but
        // due to some JBR bug, it does. Upon update, this value may change.
        Box(Modifier.fillMaxWidth().padding(start = 71.dp, end = 71.dp)) {
            Row(Modifier.align(Alignment.CenterStart)) {
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

            Row(
                Modifier
                    .align(Alignment.Center)
                    //.background(Color.Black)
                    .padding(start = 103.dp)
                    // When CustomTabContent width is 80, the tab's actual size is 120. The Plus button is additional 40.
                    .width((windows.size * 120 + 40).dp),
            ) {
                Row(
                    // Tab size is 120 when CustomTabContent is 80
                    // 103 is menu button, 40 is "add window" button, 171 is magic number for win11
                    Modifier.width((windows.size * 120).coerceAtMost(mainWindow.width - 103 - 40 - 171).dp)
                ) {
                    TabsPanel(selectedTabIndex)
                }

                Tooltip({ Text("Добавить окно") }) {
                    IconButton(
                        onClick = { showProfileDialog.value = true },
                        modifier = Modifier.size(JewelTheme.defaultTabStyle.metrics.tabHeight)
                    ) {
                        Icon(key = AllIconsKeys.General.Add, contentDescription = "Добавить окно")
                    }
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
                    CustomTabContent(
                        icon = icon,
                        label = {
                            Text(
                                text = profile.profileName.capitalized(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        },
                        state = tabState,
                        modifier = Modifier.width(80.dp)
                    )
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

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        TabStrip(tabs = tabs, style = purpleTabStyle)
    }
}

@Composable
fun TabContentScope.CustomTabContent(
    label: @Composable () -> Unit,
    state: TabState,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    vararg painterHints: PainterHint,
) {
    CustomTabContentCentered(
        state = state,
        modifier = modifier,
        icon = icon?.let { { Icon(painter = icon, contentDescription = null) } },
        label = label,
    )
}

@Composable
fun TabContentScope.CustomTabContentCentered(
    state: TabState,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    Row(
        modifier.tabContentAlpha(state),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = JewelTheme.defaultTabStyle.metrics.tabContentSpacing),
    ) {
        if (icon != null) {
            icon()
        }
        label()
    }
}
