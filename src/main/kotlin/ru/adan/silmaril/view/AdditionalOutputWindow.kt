package ru.adan.silmaril.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import ru.adan.silmaril.visual_styles.StyleManager
import ru.adan.silmaril.misc.UiColor
import ru.adan.silmaril.model.SettingsManager
import ru.adan.silmaril.viewmodel.MainViewModel

@Composable
fun AdditionalOutputWindow(mainViewModel: MainViewModel) {

    val settingsManager: SettingsManager = koinInject()
    val settings = settingsManager.settings.collectAsState()
    val currentColorStyle = StyleManager.getStyle(settings.value.colorStyle)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentColorStyle.getUiColor(UiColor.AdditionalWindowBackground))
    ) {

    }
}