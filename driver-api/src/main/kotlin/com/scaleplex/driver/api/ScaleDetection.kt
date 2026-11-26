package com.scaleplex.driver.api

import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.ScaleBrand

/**
 * IP 自动识别结果（添加秤对话框）。
 *
 * [model] 为空串表示协议未提供型号（如友声/大华状态帧不含机型），用户可手动补充；
 * [port] 为 null 表示该品牌协议不使用"端口"字段（如 DIGI 各族的端口实为秤号或协议内定），
 * UI 保持用户当前输入不动。
 */
data class ScaleDetection(
    val brand: ScaleBrand,
    val model: String,
    val port: Int?,
    val commType: CommType,
    val matchedBy: String,
)
