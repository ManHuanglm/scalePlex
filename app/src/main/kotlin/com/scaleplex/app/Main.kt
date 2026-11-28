package com.scaleplex.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import com.scaleplex.core.model.ScaleConfig
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
    // 数据目录属性须最先设置：logback 在首次取 logger 时初始化，日志文件路径依赖该属性
    val portable = File(System.getProperty("user.dir"), "data")
    val dataDir = if (usable(portable)) portable
    else File(System.getProperty("user.home"), ".scaleplex").apply { mkdirs() }
    System.setProperty("scaleplex.data.dir", dataDir.absolutePath)

    DriverRegistry.install()

    val toolsDir = ToolsDeployer.deploy(dataDir)
    System.setProperty("scaleplex.tools.dir", toolsDir.absolutePath)

    val db = Database(File(dataDir, "scaleplex.db"))

    val configService = ConfigService(db)
    val config = applyToolDefaults(configService.load(), toolsDir)
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

/** 判断目录可用于便携数据（存在或可创建，且可写）。 */
private fun usable(dir: File): Boolean = try {
    // 注意：mkdirs() 对已存在目录返回 false，不能直接作为判据（否则 data/ 一旦存在
    // 便永久回退 ~/.scaleplex，便携数据库失效）
    (dir.isDirectory || dir.mkdirs()) && dir.canWrite()
} catch (_: Exception) {
    false
}

/**
 * 官方工具默认路径：配置为空时填入随程序释放的内置工具目录
 * （data/tools/<brand>）或自动探测到的 Scale Manager 安装目录，
 * 系统设置页即可直接看到实际路径。
 */
private fun applyToolDefaults(config: ScaleConfig, toolsDir: File): ScaleConfig {
    val defaults = config.copy(
        digiToolsPath = config.digiToolsPath.ifBlank { existingDir(File(toolsDir, "digi")) },
        aclasToolsPath = config.aclasToolsPath.ifBlank { existingDir(File(toolsDir, "aclas")) },
        mtApiDllPath = config.mtApiDllPath.ifBlank {
            File(toolsDir, "mettler/MTScaleAPI.dll").takeIf { it.isFile }?.absolutePath ?: ""
        },
        mtScaleManagerPath = config.mtScaleManagerPath.ifBlank {
            MtLabelEditorLauncher.resolveRoot("")?.absolutePath ?: ""
        },
    )
    // 同步给驱动桥接（与系统设置保存后的行为一致）
    if (defaults.digiToolsPath.isNotBlank()) {
        System.setProperty("scaleplex.digi.tools", defaults.digiToolsPath)
    }
    if (defaults.aclasToolsPath.isNotBlank()) {
        System.setProperty("scaleplex.aclas.tools", defaults.aclasToolsPath)
    }
    if (defaults.mtApiDllPath.isNotBlank()) {
        System.setProperty("scaleplex.mt.dll", defaults.mtApiDllPath)
    }
    return defaults
}

private fun existingDir(dir: File): String = dir.takeIf { it.isDirectory }?.absolutePath ?: ""