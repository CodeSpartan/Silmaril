package view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import misc.StyleManager
import misc.UiColor
import viewmodel.MainViewModel
import viewmodel.SettingsViewModel

@Composable
@Preview
fun AdditionalOutputWindow(mainViewModel: MainViewModel, settingsViewModel: SettingsViewModel) {

    val currentColorStyle by settingsViewModel.currentColorStyle.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StyleManager.getStyle(currentColorStyle).getUiColor(UiColor.AdditionalWindowBackground))
    ) {
    }
}