package com.scaleplex.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.scaleplex.app.ui.ScalePlexApp
import com.scaleplex.app.ui.Theme
import com.scaleplex.core.i18n.I18n
import com.scaleplex.db.Database
import com.scaleplex.driver.impl.DriverRegistry
import com.scaleplex.service.ConfigService
import java.io.File

/**
 * Application entry point. Boots the database, services and driver registry, then
 * opens the Compose Desktop main window.
 */
fun main() {
    DriverRegistry.install()

    val dataDir = File(System.getProperty("user.home"), ".scaleplex").apply { mkdirs() }
    val db = Database(File(dataDir, "scaleplex.db"))

    val configService = ConfigService(db)
    val config = configService.load()
    configService.applyLocale(config.language)

    application {
        val state = remember { AppState(db, configService, config).apply { startHeartbeatPolling() } }
        Window(
            onCloseRequest = {
                state.shutdown()
                db.close()
                exitApplication()
            },
            title = I18n.t("app.title"),
            state = WindowState(width = 1280.dp, height = 800.dp),
        ) {
            Theme(dark = state.darkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = state.bgColor) {
                    ScalePlexApp(state)
                }
            }
        }
    }
}
