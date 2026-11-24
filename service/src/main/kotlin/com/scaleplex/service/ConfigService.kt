package com.scaleplex.service

import com.scaleplex.core.i18n.I18n
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleConfig
import com.scaleplex.db.Database
import com.scaleplex.db.dao.ConfigDao
import java.util.Locale

/**
 * Global configuration service backed by `app_config`. Changes propagate to
 * [ScaleConfig] and apply language switching live via [I18n].
 */
class ConfigService(db: Database) {

    private val dao = ConfigDao(db)

    fun load(): ScaleConfig {
        val g = { k: String, d: String -> dao.get(k) ?: d }
        val config = ScaleConfig(
            language = g("language", "zh-CN"),
            priceDecimals = (g("priceDecimals", "2")).toIntOrNull() ?: 2,
            weightDecimals = (g("weightDecimals", "3")).toIntOrNull() ?: 3,
            connectTimeoutMs = (g("connectTimeoutMs", "3000")).toIntOrNull() ?: 3000,
            readTimeoutMs = (g("readTimeoutMs", "2000")).toIntOrNull() ?: 2000,
            retries = (g("retries", "3")).toIntOrNull() ?: 3,
            heartbeatIntervalMs = (g("heartbeatIntervalMs", "5000")).toIntOrNull() ?: 5000,
            defaultBrand = ScaleBrand.fromId(g("defaultBrand", "digi")),
            rawPacketLog = g("rawPacketLog", "false").toBoolean(),
            logLevel = g("logLevel", "INFO"),
            autoBackup = g("autoBackup", "true").toBoolean(),
            backupPath = g("backupPath", ""),
            autoDownload = g("autoDownload", "false").toBoolean(),
            barcodeFormat = (g("barcodeFormat", "0")).toIntOrNull() ?: 0,
            mtApiDllPath = g("mtApiDllPath", ""),
            digiToolsPath = g("digiToolsPath", ""),
            dahuaToolsPath = g("dahuaToolsPath", ""),
            aclasToolsPath = g("aclasToolsPath", ""),
            mtScaleManagerPath = g("mtScaleManagerPath", ""),
        )
        // 启动时同步官方工具链路径（托利多 MTScaleKey / 寺冈 TWS / 大华 DhScalePluNet / 顶尖 AclasSDK）
        if (config.mtApiDllPath.isNotBlank()) {
            System.setProperty("scaleplex.mt.dll", config.mtApiDllPath)
        }
        if (config.digiToolsPath.isNotBlank()) {
            System.setProperty("scaleplex.digi.tools", config.digiToolsPath)
        }
        if (config.dahuaToolsPath.isNotBlank()) {
            System.setProperty("scaleplex.dahua.tools", config.dahuaToolsPath)
        }
        if (config.aclasToolsPath.isNotBlank()) {
            System.setProperty("scaleplex.aclas.tools", config.aclasToolsPath)
        }
        return config
    }

    fun save(cfg: ScaleConfig) {
        dao.put("language", cfg.language)
        dao.put("priceDecimals", cfg.priceDecimals.toString())
        dao.put("weightDecimals", cfg.weightDecimals.toString())
        dao.put("connectTimeoutMs", cfg.connectTimeoutMs.toString())
        dao.put("readTimeoutMs", cfg.readTimeoutMs.toString())
        dao.put("retries", cfg.retries.toString())
        dao.put("heartbeatIntervalMs", cfg.heartbeatIntervalMs.toString())
        dao.put("defaultBrand", cfg.defaultBrand.id)
        dao.put("rawPacketLog", cfg.rawPacketLog.toString())
        dao.put("logLevel", cfg.logLevel)
        dao.put("autoBackup", cfg.autoBackup.toString())
        dao.put("backupPath", cfg.backupPath)
        dao.put("autoDownload", cfg.autoDownload.toString())
        dao.put("barcodeFormat", cfg.barcodeFormat.toString())
        dao.put("mtApiDllPath", cfg.mtApiDllPath)
        dao.put("digiToolsPath", cfg.digiToolsPath)
        dao.put("dahuaToolsPath", cfg.dahuaToolsPath)
        dao.put("aclasToolsPath", cfg.aclasToolsPath)
        dao.put("mtScaleManagerPath", cfg.mtScaleManagerPath)
        // 同步官方工具链路径（托利多 MTScaleKey / 寺冈 TWS / 大华 DhScalePluNet / 顶尖 AclasSDK）
        if (cfg.mtApiDllPath.isNotBlank()) {
            System.setProperty("scaleplex.mt.dll", cfg.mtApiDllPath)
        }
        if (cfg.digiToolsPath.isNotBlank()) {
            System.setProperty("scaleplex.digi.tools", cfg.digiToolsPath)
        }
        if (cfg.dahuaToolsPath.isNotBlank()) {
            System.setProperty("scaleplex.dahua.tools", cfg.dahuaToolsPath)
        }
        if (cfg.aclasToolsPath.isNotBlank()) {
            System.setProperty("scaleplex.aclas.tools", cfg.aclasToolsPath)
        }
        applyLocale(cfg.language)
    }

    fun applyLocale(language: String) {
        val locale = when (language) {
            "en", "en-US", "en_US" -> Locale.US
            else -> Locale.SIMPLIFIED_CHINESE
        }
        I18n.setLocale(locale)
    }
}
