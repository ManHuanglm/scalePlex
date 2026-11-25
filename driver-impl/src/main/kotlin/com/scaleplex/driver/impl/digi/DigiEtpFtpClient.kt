package com.scaleplex.driver.impl.digi

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.nio.charset.Charset

/**
 * 寺冈 ETP 族（SM-120）FTP 客户端 —— 最小实现（真机校准 2026-11-25）：
 *
 * - 端口 21，用户 admin / 密码 admin（秤端设置页可改，系统属性
 *   `scaleplex.digi.ftp.user` / `scaleplex.digi.ftp.password` 可覆盖）；
 * - 控制连接 ISO-8859-1，数据连接 PASV + 二进制流；
 * - PLU 全量文件为根目录 `plu0uall.csv`（GBK CSV，[DigiEtpCsvCodec] 解析）。
 */
object DigiEtpFtpClient {

    private val log = LoggerFactory.getLogger(DigiEtpFtpClient::class.java)

    private const val DEFAULT_USER = "admin"
    private const val DEFAULT_PASSWORD = "admin"
    private const val DEFAULT_FTP_PORT = 21

    /** 秤端 PLU 全量导出文件名。 */
    const val PLU_ALL_FILE = "plu0uall.csv"

    fun user(): String = System.getProperty("scaleplex.digi.ftp.user", DEFAULT_USER)
    fun password(): String = System.getProperty("scaleplex.digi.ftp.password", DEFAULT_PASSWORD)

    /** 一次 FTP 会话（AutoCloseable，退出时发送 QUIT）。 */
    class Session(private val ip: String, ftpPort: Int = DEFAULT_FTP_PORT) : AutoCloseable {

        private val ctrl: Socket = Socket(ip, ftpPort).apply { soTimeout = 10_000 }
        private val reader = BufferedReader(InputStreamReader(ctrl.getInputStream(), Charsets.ISO_8859_1))
        private val GBK: Charset = Charset.forName("GBK")

        init {
            val banner = reader.readLine()
                ?: throw IllegalStateException("ETP FTP 无响应（$ip）")
            if (!banner.startsWith("220")) throw IllegalStateException("ETP FTP 欢迎语异常: $banner")
            val r1 = cmd("USER ${user()}")
            if (!r1.startsWith('2') && !r1.startsWith('3')) throw IllegalStateException("FTP 用户被拒: $r1")
            val r2 = cmd("PASS ${password()}")
            if (!r2.startsWith("230")) throw IllegalStateException("FTP 密码错误: $r2")
        }

        /** 发送命令并读取一行响应（单行响应；多行响应取末行）。 */
        fun cmd(line: String): String {
            ctrl.getOutputStream().write((line + "\r\n").toByteArray(Charsets.ISO_8859_1))
            ctrl.getOutputStream().flush()
            var resp = reader.readLine() ?: throw IllegalStateException("FTP 连接已断开")
            while (resp.length >= 4 && resp[3] == '-') {
                resp = reader.readLine() ?: break
            }
            return resp
        }

        /** 进入被动模式，返回数据连接。 */
        private fun openData(): Socket {
            val resp = cmd("PASV")
            val m = Regex("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)").find(resp)
                ?: throw IllegalStateException("PASV 响应异常: $resp")
            // groupValues[0] 为整个匹配串，须从 1 开始取捕获组
            val n = m.groupValues.drop(1).map { it.toInt() }
            return Socket("${n[0]}.${n[1]}.${n[2]}.${n[3]}", n[4] * 256 + n[5]).apply { soTimeout = 15_000 }
        }

        /** 下载文件（二进制），失败（非 1xx 前导）抛错。 */
        fun retr(name: String): ByteArray {
            val data = openData()
            try {
                val resp = cmd("RETR $name")
                if (!resp.startsWith("1")) throw IllegalStateException("RETR $name 失败: $resp")
                val out = java.io.ByteArrayOutputStream()
                data.getInputStream().use { it.copyTo(out) }
                val final = reader.readLine() ?: ""
                if (!final.startsWith("226")) throw IllegalStateException("RETR $name 未完成: $final")
                return out.toByteArray()
            } finally {
                data.close()
            }
        }

        /** 探测在线：可登录即在线。 */
        fun checkOnline(): Boolean = true

        override fun close() {
            runCatching { cmd("QUIT") }
            runCatching { ctrl.close() }
        }
    }

    /** 在线探测：FTP 可登录即在线（超时/拒绝即离线）。 */
    fun checkOnline(ip: String, ftpPort: Int = DEFAULT_FTP_PORT): Boolean =
        try {
            Session(ip, ftpPort).use { true }.also { log.info("ETP {}:{} online={}", ip, ftpPort, it) }
        } catch (e: Exception) {
            log.info("ETP {}:{} online=false err={}", ip, ftpPort, e.message)
            false
        }

    /** 下载 PLU 全量 CSV 文本（GBK），失败返回 null。 */
    fun downloadPluCsv(ip: String, ftpPort: Int = DEFAULT_FTP_PORT): String? =
        try {
            Session(ip, ftpPort).use { s ->
                String(s.retr(PLU_ALL_FILE), Charset.forName("GBK"))
            }.also { log.info("ETP {}:{} retr {} bytes={}", ip, ftpPort, PLU_ALL_FILE, it.length) }
        } catch (e: Exception) {
            log.warn("ETP {}:{} retr {} failed: {}", ip, ftpPort, PLU_ALL_FILE, e.message)
            null
        }
}
