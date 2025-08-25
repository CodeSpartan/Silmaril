package ru.adan.silmaril


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.misc.capitalized
import ru.adan.silmaril.model.ConnectionState
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.visual_styles.StyleManager
import kotlin.math.max
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.adan.silmaril.model.RoomDataManager
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.Window
import java.awt.event.KeyEvent

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalLayoutApi
@Composable
internal fun DecoratedWindowScope.TitleBarView(
    showMapWindow: MutableState<Boolean>,
    showAdditionalOutputWindow: MutableState<Boolean>,
    showGroupWindow: MutableState<Boolean>,
    showMobsWindow: MutableState<Boolean>,
    showProfileDialog: MutableState<Boolean>,
    showTitleMenu: MutableState<Boolean>,
    selectedTabIndex: MutableState<Int>,
    onExit: () -> Unit
) {
    val density = LocalDensity.current
    val settingsManager: SettingsManager = koinInject()
    val roomDataManager : RoomDataManager = koinInject()
    val profileManager: ProfileManager = koinInject()
    val settings by settingsManager.settings.collectAsState()
    val currentWindowConnectionState by profileManager.currentClient.value.connectionState.collectAsState()

    val gameWindows by profileManager.gameWindows.collectAsState()
    val titleBarWidth = remember { mutableStateOf(500)}

    TitleBar(Modifier.newFullscreenControls(), gradientStartColor = Color(0xff9619b3)) {

        // 71 is magic number. Normally, the TitleBar wouldn't give us more space than the title bar, but
        // due to some JBR bug, it does. Upon update, this value may change.
        Box (Modifier.fillMaxWidth().padding(start = 71.dp, end = 71.dp).onGloballyPositioned { coords ->
            titleBarWidth.value = (coords.size.width / density.density).toInt()
            // also gives position, window bounds, etc.
        }) {
            Row(Modifier.align(Alignment.CenterStart)) {
                Dropdown(
                    Modifier.height(30.dp),
                    menuContent = {

                        passiveItem {
                            // @TODO remove it from here later
                            /** A bit ugly to hide this in the first composable, but we need to hide the dialogs (map, monsters, etc)
                             * because they're AWT heavyweights and their Z-index is higher than the menu, so the menu needs to hide them
                             */
                            showTitleMenu.value = true
                            DisposableEffect(Unit) {
                                onDispose {
                                    showTitleMenu.value = false
                                }
                            }
                            Row (Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text(
                                    text = profileManager.currentProfileName.value.capitalized(),
                                    color = Color(0xff6f737a),
                                )
                            }
                        }
                        selectableItem(
                            selected = false,
                            iconKey = if (settings.autoReconnect) AllIconsKeys.Actions.Checked else null,
                            onClick = { settingsManager.toggleAutoReconnect() },
                        ) {
                            Text("Переподключение")
                        }

                        separator()

                        passiveItem {
                            Row (Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text(
                                    text = "Окна",
                                    color = Color(0xff6f737a),
                                )
                            }
                        }

                        selectableItem(
                            selected = false,
                            iconKey = if (showMapWindow.value) AllIconsKeys.Actions.Checked else null,
                            onClick = {
                                showMapWindow.value = !showMapWindow.value
                                settingsManager.updateFloatingWindowState("MapWindow", showMapWindow.value)
                            },
                        ) {
                            Text("Карта")
                        }

                        selectableItem(
                            selected = false,
                            iconKey = if (showGroupWindow.value) AllIconsKeys.Actions.Checked else null,
                            onClick = {
                                showGroupWindow.value = !showGroupWindow.value
                                settingsManager.updateFloatingWindowState("GroupWindow", showGroupWindow.value)
                            },
                        ) {
                            Text("Окно группы")
                        }

                        selectableItem(
                            selected = false,
                            iconKey = if (showMobsWindow.value) AllIconsKeys.Actions.Checked else null,
                            onClick = {
                                showMobsWindow.value = !showMobsWindow.value
                                settingsManager.updateFloatingWindowState("MobsWindow", showMobsWindow.value)
                            },
                        ) {
                            Text("Окно монстров")
                        }

                        selectableItem(
                            selected = false,
                            iconKey = if (showAdditionalOutputWindow.value) AllIconsKeys.Actions.Checked else null,
                            onClick = {
                                showAdditionalOutputWindow.value = !showAdditionalOutputWindow.value
                                settingsManager.updateFloatingWindowState("AdditionalOutput", showAdditionalOutputWindow.value)
                            },
                        ) {
                            Text("Окно вывода")
                        }

                        separator()

                        passiveItem {
                            Row (Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text(
                                    text = "Вид",
                                    color = Color(0xff6f737a),
                                )
                            }
                        }

                        submenu(
                            true,
                            AllIconsKeys.FileTypes.Font,
                            submenu = {
                                passiveItem {
                                    // Tweak margins after a bit of testing
                                    SubmenuGuard(safeTopMarginPx = 228, safeBottomMarginPx = 252, menuWidth = 200)
                                }
                                FontManager.fontFamilies.keys.filter { it != "RobotoClassic" && it != "SourceCodePro" }
                                    .forEach { fontName ->
                                        selectableItem(
                                            selected = false,
                                            iconKey = if (settings.font == fontName) AllIconsKeys.Actions.Checked else null,
                                            onClick = { settingsManager.updateFont(fontName) },
                                        ) {
                                            Text(fontName)
                                        }
                                    }
                            },
                            content = {
                                Text("Шрифт")
                            }
                        )

                        submenu(
                            true,
                            AllIconsKeys.MeetNewUi.LightTheme,
                            submenu = {
                                passiveItem {
                                    // Tweak margins after a bit of testing
                                    SubmenuGuard(safeTopMarginPx = 253, safeBottomMarginPx = 279, menuWidth = 200)
                                }
                                StyleManager.styles.keys.forEach { styleName ->
                                        selectableItem(
                                            selected = false,
                                            iconKey = if (settings.colorStyle == styleName) AllIconsKeys.Actions.Checked else null,
                                            onClick = { settingsManager.updateColorStyle(styleName) },
                                        ) {
                                            Text(styleName)
                                        }
                                }
                            },
                            content = {
                                Text("Цветовая тема")
                            }
                        )

                        separator()

                        selectableItem(
                            selected = false,
                            iconKey = AllIconsKeys.CodeWithMe.CwmAccess,
                            onClick = { showProfileDialog.value = true },
                        ) {
                            Text("Добавить игровое окно")
                        }

                        selectableItem(
                            selected = false,
                            iconKey = AllIconsKeys.Gutter.ReadAccess,
                            onClick = {
                                roomDataManager.loadAdanRoomData()
                            },
                        ) {
                            Text("Импортировать карты из AMC")
                        }

                        selectableItem(
                            selected = false,
                            enabled = currentWindowConnectionState != ConnectionState.CONNECTED && currentWindowConnectionState != ConnectionState.CONNECTING,
                            iconKey = AllIconsKeys.CodeWithMe.CwmEnableCall,
                            onClick = { profileManager.currentClient.value.connect() },
                        ) {
                            Text("Подключиться")
                        }
                        selectableItem(
                            selected = false,
                            iconKey = AllIconsKeys.Vcs.Abort,
                            onClick = { onExit() },
                        ) {
                            Text("Выход")
                        }
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
                            Image(painterResource(Res.drawable.icon), null)
                            Text("Silmaril")
                        }
                    }
                }
            }

            Row(
                Modifier
                    .align(Alignment.Center)
                    .padding(start = 103.dp)
                    // When CustomTabContent width is 80, the tab's actual size is 120. The Plus button is additional 40.
                    .width((gameWindows.size * 120 + 40).dp),
            ) {
                Row(
                    // Tab size is 120 when CustomTabContent is 80
                    // 103 is menu button, 40 is "add window" button
                    Modifier.width((gameWindows.size * 120).coerceAtMost(titleBarWidth.value - 103 - 40).dp)
                ) {
                    TitleBarTabsPanel(selectedTabIndex)
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
private fun TitleBarTabsPanel(selectedTabIndex: MutableState<Int>) {
    val profileManager: ProfileManager = koinInject()
    val gameWindows by profileManager.gameWindows.collectAsState()
    val robotoFont = remember { FontManager.getFont("RobotoClassic") }

    val purpleTabStyle = remember {
        TabStyle.Default.dark(
            colors = TabColors.Default.dark(underlineSelected = IntUiDarkTheme.colors.purple(6))
        )
    }

    val tabs = remember(gameWindows.size, selectedTabIndex.value) {
        gameWindows.values.mapIndexed { index, profile ->
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
                                fontFamily = robotoFont, // default font (jetbrains?) displays some russian glyphs with incorrect height
                                fontSize = 14.sp,
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
                        val maxPossibleIndex = max(0, gameWindows.size - 1)
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
        TabStrip(tabs = tabs, style = purpleTabStyle, modifier = Modifier.verticalScrollToHorizontal())
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

// hacky function to scroll toolbars horizontally with vertical scrolls of the mouse wheel
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.verticalScrollToHorizontal(): Modifier = this.onPointerEvent(PointerEventType.Scroll) { event ->
    val change = event.changes.firstOrNull()
    if (change != null) {
        val scrollDelta = change.scrollDelta

        // If we detect vertical scroll (y != 0) and no horizontal scroll (x == 0)
        if (scrollDelta.y != 0f && scrollDelta.x == 0f) {
            try {
                // Use Robot to simulate Shift + Wheel
                //val robot = Robot()

                // Press Shift
                RobotHolder.robot.keyPress(KeyEvent.VK_SHIFT)

                // Wait a bit for the key to be properly registered
                Thread.sleep(10)

                // Simulate mouse scroll
                // Note: scrollDelta.y is negative for scroll up, positive for scroll down
                // MouseWheelEvent uses the inverse convention
                val scrollAmount = if (scrollDelta.y > 0) 1 else -1
                RobotHolder.robot.mouseWheel(scrollAmount)

                // Release Shift
                Thread.sleep(10)
                RobotHolder.robot.keyRelease(KeyEvent.VK_SHIFT)

                // Consume the original event to avoid double scroll
                change.consume()
            } catch (e: Exception) {
                // In case of error (for example on certain platforms where Robot is not available)
                e.printStackTrace()
            }
        }
    }
}

// hack to fix jewel's bug with sticky submenu refusing to lose focus when we hover back to the original dropdown menu
// it looks for cursor and if it's to the left of the submenu, and not inside a safezone (the menu element calling the submenu),
// then we simulate a left arrow key
@Composable
fun SubmenuGuard(
    safeTopMarginPx: Int = 30,
    safeBottomMarginPx: Int = 30,
    menuWidth: Int = 200,
    pollIntervalMs: Long = 24L
) {
    // One Robot per composition
    //val robot = remember { Robot() }

    LaunchedEffect(Unit) {
        var lastWindow: Window? = null
        var leftSentForWindow: Window? = null

        while (isActive) {
            val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Window
            if (active != null && active.isShowing) {
                if (active !== lastWindow) {
                    // New popup window (likely a new submenu) -> allow one Left send for it
                    lastWindow = active
                    leftSentForWindow = null
                }

                val mouse = MouseInfo.getPointerInfo()?.location
                if (mouse != null) {
                    val loc = active.locationOnScreen
                    val w = active.width
                    val h = active.height

                    val leftEdge = loc.x + menuWidth
                    val top = loc.y
                    val bottom = loc.y + h

                    val safeTop = top + safeTopMarginPx
                    val safeBottom = top + safeBottomMarginPx

                    val isLeftOfSubmenu = mouse.x < leftEdge
                    val inVerticalSafeBand = mouse.y in safeTop..safeBottom

                    // If pointer is to the left AND outside the vertical safe band,
                    // we consider this an intentional move toward a different parent item.
                    if (isLeftOfSubmenu && !inVerticalSafeBand && leftSentForWindow !== active) {
                        try {
                            RobotHolder.robot.keyPress(KeyEvent.VK_LEFT)
                            RobotHolder.robot.keyRelease(KeyEvent.VK_LEFT)
                            leftSentForWindow = active
                        } catch (_: Throwable) {
                            // ignore (permissions, Wayland, etc.)
                        }
                    }
//                    else {
//                        if (!isLeftOfSubmenu) {
//                            println("mouse.x: ${mouse.x} >= loc.x: ${loc.x}")
//                        }
//                        if (inVerticalSafeBand) {
//                            println("mouse.y: ${mouse.y} not outside top: ${safeTop}, bottom: ${safeBottom}. Window top: ${top}")
//                        }
//                        if (!(leftSentForWindow !== active))
//                        println("leftSentForWindow isn't different from active")
//                    }

                    // If pointer comes back to the right or into safe band, allow another send later.
                    if (!isLeftOfSubmenu || inVerticalSafeBand) {
                        leftSentForWindow = null
                    }
                }
            } else {
                // No active window (or not showing) -> reset
                lastWindow = null
                leftSentForWindow = null
            }

            delay(pollIntervalMs)
        }
    }
}

// App-wide singleton (one Robot for the whole process)
object RobotHolder { val robot: Robot by lazy { Robot() } }