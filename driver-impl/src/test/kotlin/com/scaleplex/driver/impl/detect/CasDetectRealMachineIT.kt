package com.scaleplex.driver.impl.detect

import com.scaleplex.core.model.ScaleBrand
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 凯士 CAS 真机识别测试 @ 192.168.0.111:20304（CL 族条码秤）。
 * 秤不可达时自动跳过；不进入常规测试集。
 *
 * 已知限制：CL 族 TCP 协议为凯士私有帧格式（公开渠道无规范文档），
 * 当前仅以端口指纹识别品牌；PLU 下发/回收驱动待协议规范后接入。
 */
class CasDetectRealMachineIT {

    private val ip = "192.168.0.111"

    @Test
    fun `cas scale detected by port fingerprint`() = runBlocking {
        // 前置：20304 可连通，否则跳过（真机不在线时不误报失败）
        val portOpen = runCatching {
            Socket().use { s -> s.connect(InetSocketAddress(ip, 20304), 2000); true }
        }.getOrDefault(false)
        org.junit.Assume.assumeTrue("凯士秤 20304 不可达，跳过: $ip", portOpen)

        val detected = ScaleAutoDetector.detect(ip)
        check(detected != null) { "识别结果为空（端口已开放但未命中）" }
        check(detected.brand == ScaleBrand.CAS) { "品牌识别错误: ${detected.brand}" }
        check(detected.port == 20304) { "端口回填错误: ${detected.port}" }
        println("detected: $detected")
    }
}
