package com.scaleplex.app

import java.io.File

/**
 * 内置官方工具链部署器：各品牌官方 DLL/EXE 以资源形式打包进应用
 * （`resources/tools/<brand>/`），启动时释放到数据目录 `tools/<brand>/`（缺失才释放）。
 *
 * 释放后驱动桥接即可直接使用，无需用户手动配置各品牌工具目录；
 * 用户仍可在设置页覆盖为自己的路径（系统属性优先级最高）。
 */
object ToolsDeployer {

    /** 各品牌工具文件清单（与 resources/tools/<brand>/ 一一对应）。 */
    private val TOOL_FILES: Map<String, List<String>> = mapOf(
        // 寺冈 8442 族（TWS.exe + SMScale.dll + SM.ini/Scale.ini）与 b 族（SMScaleAPI.dll）
        "digi" to listOf("TWS.exe", "SMScale.dll", "SM.ini", "Scale.ini", "SMScaleAPI.dll"),
        // 大华 DhScalePluNet.dll（依赖 dhplunet.dll）
        "dahua" to listOf("DhScalePluNet.dll", "dhplunet.dll"),
        // 顶尖 AclasSDK.dll + AclasSDK.ini
        "aclas" to listOf("AclasSDK.dll", "AclasSDK.ini"),
        // 托利多 MTScaleAPI.dll（MTScaleKey 接口程序）
        "mettler" to listOf("MTScaleAPI.dll"),
    )

    /** 释放全部品牌工具，返回工具根目录（`<dataDir>/tools`）。 */
    fun deploy(dataDir: File): File {
        val toolsDir = File(dataDir, "tools")
        val loader = ToolsDeployer::class.java.classLoader
        TOOL_FILES.forEach { (brand, files) ->
            val dir = File(toolsDir, brand).apply { mkdirs() }
            files.forEach { name ->
                val target = File(dir, name)
                val res = loader.getResourceAsStream("tools/$brand/$name") ?: return@forEach
                // 缺失或大小不一致时释放（覆盖旧版本）
                if (!target.exists() || target.length() != res.available().toLong()) {
                    res.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                } else {
                    res.close()
                }
            }
        }
        return toolsDir
    }
}
