package ru.adan.silmaril.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import org.koin.compose.koinInject
import ru.adan.silmaril.misc.FontManager
import ru.adan.silmaril.model.ProfileManager
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.visual_styles.StyleManager

@Composable
fun FrameWindowScope.AppMenuBar(
    showMapWindow: MutableState<Boolean>,
    showAdditionalOutputWindow: MutableState<Boolean>,
    showProfileDialog: MutableState<Boolean>,
    onExit: () -> Unit
) {
    val settingsManager: SettingsManager = koinInject()
    val profileManager: ProfileManager = koinInject()
    val settings by settingsManager.settings.collectAsState()

    MenuBar {
        Menu("Файл", mnemonic = 'Ф') {
            Item("Выход", mnemonic = 'В') {
                onExit()
            }
        }
        Menu("Вид", mnemonic = 'В') {
            Item("Добавить игровое окно", mnemonic = 'Д', onClick = { showProfileDialog.value = true })
            Separator()
            Menu("Шрифт", mnemonic = 'Ш') {
                FontManager.fontFamilies.keys.filter { it != "RobotoClassic" && it != "SourceCodePro" }
                    .forEach { key ->
                        CheckboxItem(
                            text = key,
                            checked = settings.font == key,
                            onCheckedChange = {
                                settingsManager.updateFont(key)
                            }
                        )
                    }
            }
            Menu("Цветовая тема", mnemonic = 'Ц') {
                StyleManager.styles.keys.forEach { key ->
                    CheckboxItem(
                        text = key,
                        checked = settings.colorStyle == key,
                        onCheckedChange = {
                            settingsManager.updateColorStyle(key)
                        }
                    )
                }
            }
            CheckboxItem(
                text = "Карта",
                mnemonic = 'К',
                checked = showMapWindow.value,
                onCheckedChange = {
                    showMapWindow.value = it
                    settingsManager.updateFloatingWindowState("MapWindow", it)
                }
            )
            CheckboxItem(
                text = "Окно вывода",
                mnemonic = 'О',
                checked = showAdditionalOutputWindow.value,
                onCheckedChange = {
                    showAdditionalOutputWindow.value = it
                    settingsManager.updateFloatingWindowState("AdditionalOutput", it)
                }
            )
        }
        Menu(profileManager.currentProfileName.value, mnemonic = profileManager.currentProfileName.value.first()) {
            CheckboxItem(
                text = "Авто-переподкл.",
                mnemonic = 'А',
                checked = settings.autoReconnect,
                onCheckedChange = { settingsManager.toggleAutoReconnect(it) }
            )
            Item("Группы", mnemonic = 'Г', onClick = { /*showGroupsDialog.value = true*/ })
        }
    }
}