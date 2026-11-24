package com.scaleplex.app

import com.scaleplex.core.model.MtLabelEditor
import java.io.File

/**
 * 托利多标签编辑器启动器：按设备型号选择 VisEdit / bLabel / SmartLabel，
 * 并从 Scale Manager 安装目录启动对应程序。
 *
 * 安装目录解析顺序：系统设置中的配置值 → Program Files 环境变量 →
 * 各磁盘根目录下的 `Program Files (x86)\Scale Manager` / `Program Files\Scale Manager`。
 */
object MtLabelEditorLauncher {

    /** 按型号启动编辑器；成功返回所用编辑器，失败返回可展示的错误说明。 */
    fun launch(model: String, configuredRoot: String): Result<MtLabelEditor> = runCatching {
        require(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "托利多标签编辑器仅支持 Windows"
        }
        val editor = MtLabelEditor.fromModel(model)
        val root = resolveRoot(configuredRoot)
            ?: error("未找到 Scale Manager 安装目录")
        val dir = File(root, editor.folder)
        val exe = File(dir, editor.executable)
        check(exe.isFile) { "未找到 ${editor.displayName}：${exe.absolutePath}" }

        if (exe.extension.equals("bat", ignoreCase = true)) {
            // SmartLabel 为 Java 程序，由官方 start.bat 设置 jre6 环境后启动
            ProcessBuilder("cmd.exe", "/c", exe.name).directory(dir).start()
        } else {
            ProcessBuilder(exe.absolutePath).directory(dir).start()
        }
        editor
    }

    /** 解析 Scale Manager 根目录；配置为空时自动探测常见安装位置。 */
    fun resolveRoot(configuredRoot: String): File? {
        if (configuredRoot.isNotBlank()) {
            return File(configuredRoot).takeIf { it.isDirectory }
        }
        val envRoots = listOfNotNull(
            System.getenv("ProgramFiles(x86)"),
            System.getenv("ProgramFiles"),
        ).map { File(it, "Scale Manager") }
        if (envRoots.any { it.isDirectory }) {
            return envRoots.first { it.isDirectory }
        }
        // 官方工具可能装在非系统盘（如 E:\Program Files (x86)\Scale Manager）
        return File.listRoots()
            .flatMap { root ->
                listOf(
                    File(root, "Program Files (x86)\\Scale Manager"),
                    File(root, "Program Files\\Scale Manager"),
                )
            }
            .firstOrNull { it.isDirectory }
    }
}
