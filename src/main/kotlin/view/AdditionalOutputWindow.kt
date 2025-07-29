package view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import visual_styles.StyleManager
import misc.UiColor
import model.SettingsManager
import viewmodel.MainViewModel

@Composable
@Preview
fun AdditionalOutputWindow(mainViewModel: MainViewModel, settingsManager: SettingsManager) {

    val settings = settingsManager.settings.collectAsState()
    val currentColorStyle = StyleManager.getStyle(settings.value.colorStyle)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentColorStyle.getUiColor(UiColor.AdditionalWindowBackground))
    ) {
    }
}