package com.scaleplex.driver.impl.detect

import com.scaleplex.core.model.CommConfig
import com.scaleplex.core.model.CommType
import com.scaleplex.core.model.ScaleBrand
import com.scaleplex.core.model.ScaleDevice
import com.scaleplex.driver.api.ScaleDetection
import com.scaleplex.driver.impl.aclas.AclasBridge
import com.scaleplex.driver.impl.aclas.AclasDriver
import com.scaleplex.driver.impl.dahua.DahuaTcpDriver
import com.scaleplex.driver.impl.digi.DigiEtpFtpClient
import com.scaleplex.driver.impl.digi.DigiSm5100Bridge
import com.scaleplex.driver.impl.digi.DigiToolsBridge
import com.scaleplex.driver.impl.hihong.HiHongUdpDriver
import com.scaleplex.driver.impl.mettler.MettlerToledoDriver
import com.scaleplex.driver.impl.mettler.MtScaleKeyBridge
import com.scaleplex.driver.impl.yousheng.YouShengDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 添加秤时的 IP 自动识别引擎：先并发探测候选 TCP 端口形成指纹，再按品牌协议握手 /
 * 工具链探测逐个确认，首个命中的品牌即识别结果。
 *
 * 探测顺序（从轻到重，外部工具未配置的自动跳过）：
 * 1. 寺冈 ETP 族（SM-120）：FTP 21 可 admin 登录（无需外部工具）；
 * 2. 寺冈 b 族（SM-110N/SM-5100）：SMScaleAPI ConnectScale（端口 = 2000 + IP 尾段）；
 * 3. 友声：JHScale 桥接连接 TCP 33571（2026-11-27 标定端口）；
 * 4. 大华：TCP 4001 任意应答（友声未命中时兜底）；
 * 5. 托利多：TCP 3001 预检 + ScaleKey CheckOnline（按 ScaleType 候选逐个尝试）；
 * 6. 顶尖：AclasSDK GetDevicesInfo（设备名即型号）；
 * 7. 凯士：TCP 20304 端口指纹（CAS 默认端口，CL 族私有协议无公开规范）；
 * 8. 容大：TCP 9100/25/110/143 端口组指纹（RLS1000 传秤走 UDP 5001 无应答，TCP 指纹替代）；
 * 9. 辉宏：UDP 1111 状态帧 ACK（协议待真机校准，尽力探测）；
 * 10. 寺冈 8442 族：TWS.exe RDR（秤号候选 = IP 尾段 / 1，最后手段）。
 *
 * 全部未命中返回 null（设备离线、端口未开放或外部工具未配置）。
 */
object ScaleAutoDetector {

    private val log = LoggerFactory.getLogger(ScaleAutoDetector::class.java)

    /** 探测用快速通信参数：短超时 + 单次尝试，避免整个识别流程拖沓。 */
    private val FAST_COMM = CommConfig(connectTimeoutMs = 1500, readTimeoutMs = 1200, retries = 1)

    /** 单端口 TCP 连通性探测超时。 */
    private const val PORT_PROBE_TIMEOUT_MS = 1500

    /** 大华默认端口（4001；友声已标定独立端口 33571，此口仅作大华指纹）。 */
    private const val DAHUA_YOUSHENG_PORT = 4001

    /** 友声 JH 族 TCP 通讯口（真机校准 2026-11-27；DLL 内置默认 33581，实秤为 33571）。 */
    private const val YOUSHENG_DEFAULT_PORT = 33571

    /** 托利多 ScaleKey 设备端口。 */
    private const val MT_DEFAULT_PORT = 3001

    /** 顶尖 Aclas 默认端口。 */
    private const val ACLAS_DEFAULT_PORT = 5002

    /** 辉宏 UDP 默认端口（ScaleBrand 占位值）。 */
    private const val HIHONG_DEFAULT_PORT = 1111

    /** 寺冈 ETP 族 FTP 端口。 */
    private const val DIGI_ETP_FTP_PORT = 21

    /** 凯士 CAS 默认端口（CL 族条码秤 TCP 监听端口，真机校准 2026-11-26）。 */
    private const val CAS_DEFAULT_PORT = 20304

    /** 容大 RLS1000 UDP 传秤端口（TCP 同号不开放，仅用于回填）。 */
    private const val RONGTA_DEFAULT_PORT = 5001

    /** 容大 RLS1000 TCP 端口组指纹：原始打印 + 邮件三件套（真机 2026-11-26）。 */
    private val RONGTA_FINGERPRINT_PORTS = listOf(9100, 25, 110, 143)

    /**
     * 托利多 ScaleType 候选（真机校准：b 族 bCom/bComS/bPlus 三值 CheckOnline 互通，
     * RL00 终端必须 RL00，8442 族必须 8442；b 族具体型号无法从探测区分）。
     */
    private val MT_SCALE_TYPE_CANDIDATES = listOf("bCom", "RL00", "8442")

    /** 输入 IP 识别品牌与型号；全部探测未命中返回 null。 */
    suspend fun detect(ip: String): ScaleDetection? = withContext(Dispatchers.IO) {
        val trimmed = ip.trim()
        val lastSegment = trimmed.substringAfterLast('.').toIntOrNull()
        if (lastSegment == null || lastSegment !in 0..255) return@withContext null

        val ports = scanTcpPorts(
            trimmed,
            listOf(
                DIGI_ETP_FTP_PORT,
                MT_DEFAULT_PORT,
                ACLAS_DEFAULT_PORT,
                DAHUA_YOUSHENG_PORT,
                YOUSHENG_DEFAULT_PORT,
                CAS_DEFAULT_PORT,
                9100, 25, 110, 143,
                2000 + lastSegment,
            ),
        )
        log.info("detect {}: openPorts={}", trimmed, ports)

        detectDigiEtp(trimmed, ports)
            ?: detectDigiB(trimmed, lastSegment, ports)
            ?: detectYouShengOrDahua(trimmed, ports)
            ?: detectMettler(trimmed, ports)
            ?: detectAclas(trimmed, ports)
            ?: detectCas(trimmed, ports)
            ?: detectRongta(trimmed, ports)
            ?: detectHiHong(trimmed)
            ?: detectDigi8442(trimmed)
    }

    // ---- 各品牌探测 ----

    /** 寺冈 ETP 族（SM-120）：FTP 21 可 admin 登录即命中。 */
    private suspend fun detectDigiEtp(ip: String, openPorts: Set<Int>): ScaleDetection? {
        if (DIGI_ETP_FTP_PORT !in openPorts) return null
        val online = runCatching { DigiEtpFtpClient.checkOnline(ip) }.getOrDefault(false)
        if (!online) return null
        return ScaleDetection(ScaleBrand.DIGI, "SM-120", null, CommType.TCP, "FTP 21 可登录（ETP 族）")
    }

    /** 寺冈 b 族：SMScaleAPI ConnectScale 探测（端口 = 2000 + IP 尾段），需寺冈工具目录。 */
    private suspend fun detectDigiB(ip: String, lastSegment: Int, openPorts: Set<Int>): ScaleDetection? {
        val port = 2000 + lastSegment
        if (port !in openPorts || !DigiSm5100Bridge.isConfigured()) return null
        val connected = runCatching {
            DigiSm5100Bridge.execute(ip, DigiSm5100Bridge.Mode.PROBE, timeoutMs = 20_000).connected
        }.getOrDefault(false)
        if (!connected) return null
        return ScaleDetection(
            ScaleBrand.DIGI, "SM-110N", null, CommType.TCP,
            "SMScaleAPI 连接成功（b 族，端口 $port）",
        )
    }

    /**
     * 友声 / 大华：友声先经 JHScale 桥接探测 33571（2026-11-27 标定端口），再大华 4001 任意应答兜底。
     */
    private suspend fun detectYouShengOrDahua(ip: String, openPorts: Set<Int>): ScaleDetection? {
        if (YOUSHENG_DEFAULT_PORT in openPorts) {
            val youShengDev = tempDevice(ip, YOUSHENG_DEFAULT_PORT, ScaleBrand.YOUSHENG, CommType.TCP)
            val youShengOnline = runCatching {
                YouShengDriver(youShengDev).getStatus().getOrNull()?.online == true
            }.getOrDefault(false)
            if (youShengOnline) {
                return ScaleDetection(
                    ScaleBrand.YOUSHENG, "", YOUSHENG_DEFAULT_PORT, CommType.TCP,
                    "JHScale 桥接连接成功（33571）",
                )
            }
        }
        if (DAHUA_YOUSHENG_PORT !in openPorts) return null
        val dahuaDev = tempDevice(ip, DAHUA_YOUSHENG_PORT, ScaleBrand.DAHUA, CommType.TCP)
        val dahuaOnline = runCatching {
            DahuaTcpDriver(dahuaDev).getStatus().getOrNull()?.online == true
        }.getOrDefault(false)
        if (dahuaOnline) {
            return ScaleDetection(
                ScaleBrand.DAHUA, "", DAHUA_YOUSHENG_PORT, CommType.TCP,
                "TCP 4001 有应答（大华）",
            )
        }
        return null
    }

    /** 托利多：TCP 3001 预检后按 ScaleType 候选逐个 CheckOnline，命中即品牌 + 机型族。 */
    private suspend fun detectMettler(ip: String, openPorts: Set<Int>): ScaleDetection? {
        if (MT_DEFAULT_PORT !in openPorts) return null
        if (!File(MtScaleKeyBridge.dllPath()).exists()) return null
        for (scaleType in MT_SCALE_TYPE_CANDIDATES) {
            val dev = tempDevice(ip, MT_DEFAULT_PORT, ScaleBrand.METTLER_TOLEDO, CommType.TCP, model = scaleType)
            val online = runCatching {
                MettlerToledoDriver(dev).getStatus().getOrNull()?.online == true
            }.getOrDefault(false)
            if (online) {
                val hint = if (scaleType == "bCom") "（b 族具体型号请按实际机型核对）" else ""
                return ScaleDetection(
                    ScaleBrand.METTLER_TOLEDO, scaleType, MT_DEFAULT_PORT, CommType.TCP,
                    "ScaleKey CheckOnline 通过（ScaleType=$scaleType）$hint",
                )
            }
        }
        return null
    }

    /** 顶尖：AclasSDK GetDevicesInfo 设备探测，设备名即型号。 */
    private suspend fun detectAclas(ip: String, openPorts: Set<Int>): ScaleDetection? {
        if (ACLAS_DEFAULT_PORT !in openPorts || !AclasBridge.isConfigured()) return null
        val dev = tempDevice(ip, ACLAS_DEFAULT_PORT, ScaleBrand.DINGTOP, CommType.TCP)
        val status = runCatching { AclasDriver(dev).getStatus().getOrNull() }.getOrNull()
        if (status?.online != true) return null
        val model = status.model.takeIf { it.isNotBlank() && it != "ACLAS" } ?: ""
        return ScaleDetection(ScaleBrand.DINGTOP, model, ACLAS_DEFAULT_PORT, CommType.TCP, "AclasSDK 设备探测")
    }

    /** 凯士：TCP 20304 端口指纹（CL 族私有协议无公开规范，暂以端口识别品牌）。 */
    private suspend fun detectCas(ip: String, openPorts: Set<Int>): ScaleDetection? {
        if (CAS_DEFAULT_PORT !in openPorts) return null
        return ScaleDetection(
            ScaleBrand.CAS, "CL5200", CAS_DEFAULT_PORT, CommType.TCP,
            "TCP 20304 端口开放（CAS 默认端口）",
        )
    }

    /** 容大：TCP 端口组指纹（9100+25+110+143 全开即判容大；传秤 UDP 5001 无应用层应答）。 */
    private suspend fun detectRongta(ip: String, openPorts: Set<Int>): ScaleDetection? {
        if (RONGTA_FINGERPRINT_PORTS.any { it !in openPorts }) return null
        return ScaleDetection(
            ScaleBrand.RONGTA, "RLS1000", RONGTA_DEFAULT_PORT, CommType.UDP,
            "端口组指纹 9100/25/110/143 全开（Rongta RLS1000）",
        )
    }

    /** 辉宏：UDP 状态帧 ACK（协议待真机校准，尽力探测）。 */
    private suspend fun detectHiHong(ip: String): ScaleDetection? {
        val dev = tempDevice(ip, HIHONG_DEFAULT_PORT, ScaleBrand.HIHONG, CommType.UDP)
        val online = runCatching {
            HiHongUdpDriver(dev).getStatus().getOrNull()?.online == true
        }.getOrDefault(false)
        if (!online) return null
        return ScaleDetection(ScaleBrand.HIHONG, "", HIHONG_DEFAULT_PORT, CommType.UDP, "UDP 状态帧 ACK（1111）")
    }

    /** 寺冈 8442 族：TWS.exe RDR 读首条记录（秤号候选 = IP 尾段 / 默认 1），需寺冈工具目录。 */
    private suspend fun detectDigi8442(ip: String): ScaleDetection? {
        if (!DigiToolsBridge.isConfigured()) return null
        val scaleNoCandidates = listOfNotNull(
            ip.trim().substringAfterLast('.').toIntOrNull(),
            1,
        ).distinct()
        for (scaleNo in scaleNoCandidates) {
            val online = runCatching { DigiToolsBridge.checkOnline(ip, scaleNo) }.getOrDefault(false)
            if (online) {
                return ScaleDetection(
                    ScaleBrand.DIGI, "SM-110", scaleNo, CommType.TCP,
                    "TWS 在线探测通过（秤号=$scaleNo）",
                )
            }
        }
        return null
    }

    // ---- 基础设施 ----

    /** 构造探测用临时设备（不入库；id=1 供 MT DeviceList.xml 的 DeviceID/ScaleNo 使用）。 */
    private fun tempDevice(
        ip: String,
        port: Int,
        brand: ScaleBrand,
        commType: CommType,
        model: String = "",
    ): ScaleDevice = ScaleDevice(
        id = 1,
        name = "detect",
        brand = brand,
        model = model,
        commType = commType,
        ip = ip,
        port = port,
        comm = FAST_COMM,
    )

    /** 并发探测候选 TCP 端口，返回可连通集合。 */
    private suspend fun scanTcpPorts(ip: String, ports: List<Int>): Set<Int> = coroutineScope {
        ports.distinct().map { port -> async { if (tcpOpen(ip, port)) port else null } }
            .awaitAll().filterNotNull().toSet()
    }

    /** 单端口 TCP 连通性探测（连接成功即视为端口开放）。 */
    private fun tcpOpen(ip: String, port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), PORT_PROBE_TIMEOUT_MS)
            true
        }
    } catch (_: Exception) {
        false
    }
}
