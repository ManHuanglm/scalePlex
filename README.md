# ScalePlex 条码秤管理系统

ScalePlex 是一款基于 Kotlin / Compose Desktop 的跨品牌条码秤管理软件，提供商品（PLU）资料编辑、
多秤批量下发、流水回收、时间同步、标签模板设计与协议级在线检测等能力。驱动层按品牌插件化，
新增品牌只需实现 `IScaleDriver` 并注册到 `ScaleDriverFactory`，UI 与业务层零改动。

## 核心功能

- **设备管理**：添加秤时输入 IP 自动识别品牌与型号（端口指纹 + 协议握手探测）；按品牌自动分组；
  协议级在线检测（间隔可配，5s~600s）。
- **PLU 管理**：商品编辑（名称/条码/价格/部门/保质期/热键等 60+ 字段）、分类管理、Excel/文本导入导出。
- **批量下发**：勾选多台秤并发下发（信号量限流 4 并发），支持按分组一键下发；可选自动下发。
- **流水回收**：读取秤端销售明细并入库（DIGI 8442 族已接入，其余品牌待校准）。
- **时间同步**：单台/批量对秤校时（支持的品牌见下表）。
- **标签模板**：本地标签设计器 + 托利多原厂编辑器（VisEdit/bLabel/SmartLabel）联动。
- **日志调试**：原始报文抓取、日志窗口（文件位于数据目录 `logs/scaleplex.log`）。

## 支持品牌与真机校准状态

| 品牌 | 传输 | 默认端口 | 在线检测 | PLU 下发 | 回收 | 时间同步 | 备注 |
|---|---|---|---|---|---|---|---|
| DIGI 寺冈 | UDP/TCP/FTP | 秤号(1) | ✅ | ✅（8442 族 / b 族） | ✅（8442/b 族） | ✅（b 族） | 8442 族走 TWS.exe 工具链；b 族走 SMScaleAPI.dll；ETP 族（SM-120）仅 FTP 回收 |
| 梅特勒-托利多 | TCP | 3001 | ✅ | ✅（Item Update） | ✅（ReadAll） | ✅ | 经 MTScaleKey 接口 XML + 32 位 PowerShell 桥接 MTScaleAPI.dll |
| 顶尖 Aclas | TCP | 5002 | ✅ | ✅ | ✅ | ❌ | 经 AclasSDK.dll（需配置工具目录） |
| 容大 Rongta | UDP | 5001 | ✅ | ✅（真机通过） | 待校准 | 待校准 | RLS1000 私有二进制协议（详见下文"协议速览"） |
| CAS 凯士 | TCP | 20304 | ✅ | ✅（真机联调中） | 待接入 | 待接入 | ASCII 键值协议（详见下文）；在线探测可走原厂 CL-Works Pro .NET 库桥接 |
| 大华 DAHUA | TCP | 4001 | 基础 | 待校准 | ❌ | ❌ | 协议未真机校准 |
| 友声 YouSheng | TCP | 4001 | 基础 | 待校准 | ❌ | ❌ | 协议未真机校准 |
| 辉宏 HuiHong | UDP | 1111 | 基础 | 待校准 | ❌ | ❌ | 协议未真机校准 |

> ✅ = 已真机验证；"基础"= 通道可用但协议帧未与实体秤比对；工具链类驱动需在系统设置中配置
> 原厂 DLL/工具目录（`scaleplex.digi.tools`、`scaleplex.mt.dll`、`scaleplex.aclas.tools`、`scaleplex.cas.tools`）。

## 模块结构

```
app/            Compose Desktop UI（设备/PLU/流水/标签/设置面板）
core/           领域模型（ScaleDevice/PluItem/LabelTemplate...）+ i18n（中/英）
communication/  TCP/UDP/串口传输抽象（超时重试、报文事件总线）
db/             SQLite（schema 迁移 V1~V5），数据目录 ${scaleplex.data.dir:-~/.scaleplex}
service/        业务服务（ScaleService 并发下发、PluService、TradeLogService...）
driver-api/     IScaleDriver 驱动契约 + ScaleDriverFactory + ScaleDetection
driver-impl/    各品牌驱动实现 + 自动识别引擎（ScaleAutoDetector）
```

## 构建与运行

```bash
# 环境：JDK 21+（本机示例：D:\jdk-21.0.13.1+2）
.\gradlew.bat :app:run                 # 开发运行
.\gradlew.bat :app:createDistributable # 打包（依赖 GitHub 下载 wix，网络受限时不可用）
.\gradlew.bat test                     # 单元测试（*RealMachineIT 为真机测试，秤不可达自动跳过）
```

## 快速上手

1. **添加秤**：设备管理 → 添加秤 → 输入 IP（新增设备输入停顿 1.2 秒自动识别；也可点「识别」手动触发）。
   识别命中后自动回填品牌/型号/通信方式/端口。
2. **录入商品**：PLU 管理页编辑商品资料（或 Excel 导入）。
3. **下发**：设备管理勾选目标秤 → 「下发选中设备」；或按分组一键下发。
4. **在线检测**：心跳轮询自动运行，也可侧边栏手动刷新。

## 协议速览（真机校准结论）

### 容大 RLS1000（UDP 5001，已真机验证）

- 会话帧 `0E 01 00 34 12 00` + BCD 时间戳 → 配置帧 `0E 04 00 04 04 04 04` →
  0B 数据帧（每帧 4 条 PLU 子记录）+ 01 名称帧（每 PLU 一帧）→ 结束帧 `0E 02 00 34 12 00`。
- 固定 257 字节帧，**每帧重复发送 8 次**（无应用层 ACK 重传机制），秤端每帧回 1 字节 `0x02`。
- 子记录（64B）字段：品名 GBK 36B（+0）、货号 int24 大端（+36）、部门 BCD（+41）、条码 int8（+42）、
  **价格 4B BCD ×100 大端数字序（+43，如 20.00 → `00 00 20 00`）**、保质期 int8 天（+50）。
- 注意：原厂 DLL `rtslabelscale.dll` 的 TCP 5001 通道未被 GUI 使用，实际传输为 UDP。

### CAS 凯士 CL 族（TCP 20304，联调中）

- ASCII 键值协议：`W02A<PLU:5>,01L010A:F=<字段ID>.<类型>,<长度>:<数据>...`
  - 字段 `01`=PLU 号(int16LE)、`02`=部门(int32LE)、`06`=单价(int32LE 分)、`0A`=品名(GBK)，
    其余字段按真机模板置零；末字段省略 `,0:` 后缀。
- 原厂模式：每个 PLU 独立 TCP 连接 → 同帧重复发送 8 次（50ms 间隔）→ 秤端去重应答
  `W02:O`（成功）/ `W02:E<code>`（错误）。
- 在线探测可用原厂 CL-Works Pro .NET 库（`CasComm.dll` 的 `Scales.Open/IsOpen`，PS 桥接）。

### DIGI 寺冈 / 托利多 / 顶尖

- DIGI：8442 族 `TWS.exe RDR 25` 在线探测、`SMScale.dll` CSV 下发；b 族 `SMScaleAPI.dll`
  （端口 = 2000 + IP 尾段，写成功码 0x06）；ETP 族仅 FTP（admin/admin）回收 `plu0uall.csv`。
- 托利多：Task.xml（TaskType 4=查在线）→ `MTScaleAPI.dll ExecuteTaskInFileW`，
  在线以 `CheckOnline/Status ≥ 0` 为准（TaskResult 状态恒 OK 不可信）。
- 顶尖：`AclasSDK GetDevicesInfo` 设备探测（设备名即型号）+ `Sync_ExecTaskA` 上下行。

## 已知限制与待办

- 容大：流水回收、删除 PLU、时间同步、标签下发的帧类型未抓包标定。
- 凯士：PLU 通道偶发 `W02:EFE`（疑似会话锁死，断电重启秤后恢复）；回收/删除/校时未接入。
- DIGI：b 族流水（Receipt_D）、标签格式下发待接入；ETP 族下发需 `plu0uall.csv` 写入规范。
- 大华/友声/辉宏：协议帧基于行业通用设计，待实体秤抓包校准。
- 打包任务依赖 GitHub 下载 Wix，网络受限环境建议以 jar 替换方式部署。

## 许可证

见 [LICENSE](LICENSE)。
