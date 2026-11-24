package com.scaleplex.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.scaleplex.app.ui.ScalePlexApp
import com.scaleplex.app.ui.Theme
import com.scaleplex.core.i18n.I18n
import com.scaleplex.db.Database
import com.scaleplex.driver.impl.DriverRegistry
import com.scaleplex.service.ConfigService
import java.awt.Dimension
import java.io.File

/**
 * Application entry point. Boots the database, services and driver registry, then
 * opens the Compose Desktop main window.
 *
 * 便携化数据目录：优先使用工作目录下 `data/`（便携版 exe 旁，随目录携带），
 * 不可写时回退 `~/.scaleplex`。内置官方工具链（各品牌 DLL/EXE 资源）释放到
 * `data/tools/<brand>/`，驱动桥接默认即用，无需手动配置工具目录。
 */
fun main() {
    DriverRegistry.install()

    val portable = File(System.getProperty("user.dir"), "data")
    val dataDir = if (usable(portable)) portable
    else File(System.getProperty("user.home"), ".scaleplex").apply { mkdirs() }
    System.setProperty("scaleplex.data.dir", dataDir.absolutePath)
    val toolsDir = ToolsDeployer.deploy(dataDir)
    System.setProperty("scaleplex.tools.dir", toolsDir.absolutePath)

    val db = Database(File(dataDir, "scaleplex.db"))

    val configService = ConfigService(db)
    val config = configService.load()
    configService.applyLocale(config.language)

    application {
        val state = remember {
            AppState(db, configService, config).apply {
                startHeartbeatPolling()
                startAutoDownloadIfNeeded()
            }
        }
        Window(
            onCloseRequest = {
                state.shutdown()
                db.close()
                exitApplication()
            },
            title = I18n.t("app.title"),
            state = rememberWindowState(
                width = 1280.dp,
                height = 820.dp,
                position = WindowPosition(Alignment.Center),
            ),
        ) {
            // 最小窗口尺寸：低于该尺寸时各面板内部滚动条保证内容仍可达
            LaunchedEffect(Unit) { window.minimumSize = Dimension(1024, 680) }
            Theme(dark = state.darkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = state.bgColor) {
                    ScalePlexApp(state)
                }
            }
        }
    }
}

/** 判断目录可用于便携数据（可创建且可写）。 */
private fun usable(dir: File): Boolean = try {
    dir.mkdirs() && dir.isDirectory && dir.canWrite()
} catch (_: Exception) {
    false
}
