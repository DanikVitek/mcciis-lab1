package me.danikvitek.lab1

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import lab1.shared.generated.resources.Res
import lab1.shared.generated.resources.window_title
import me.danikvitek.lab1.util.AppLocaleManager
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    val appLanguage by AppLocaleManager.currentLanguage.collectAsState()
    val windowState = rememberWindowState(width = 1600.dp, height = 960.dp)
    val title = key(appLanguage) { stringResource(Res.string.window_title) }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = title,
    ) {
        App()
    }
}
