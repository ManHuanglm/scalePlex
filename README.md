# ScalePlex 条码秤管理系统

ScalePlex 是一款基于 Kotlin / Compose Desktop 的跨品牌条码秤管理软件，提供商品（PLU）资料编辑、
多秤批量下发、流水回收、时间同步、标签模板设计与协议级在线检测等能力。驱动层按品牌插件化，
新增品牌只需实现 `IScaleDriver` 并注册到 `ScaleDriverFactory`，UI 与业务层零改动。

## 核心功能

- **设备管理**：添加秤时输入 IP 自动识别品牌与型号（端口指纹 + 协议握手探测）；按品牌自动分组；
  协议级在线检测（间隔可配，5s~600s）。
- **PLU 管理**：商品编辑（名称/条码/价格/部门/保质期/热键等 60+ 字段）、分类管理、Excel/文本导入导出。
- **批量下发**：勾选多台秤并发下发（信号量限流 4 并发），支持按分组一键下发；可选自动下发。
- **下发/回收交互**：PLU 下发与流水/PLU 回收统一弹窗选择目标秤（全部启用秤/单台，默认第一台在线秤）；
  大华回收结果先表格多选，再按「覆盖导入 / 增量导入 / 重试 / 取消」决定入库方式。
- **流水回收**：读取秤端销售明细并入库（DIGI 8442 族、大华已接入，其余品牌待校准）。
- **时间同步**：单台/批量对秤校时（支持的品牌见下表）。
- **标签模板**：本地标签设计器 + 托利多原厂编辑器（VisEdit/bLabel/SmartLabel）联动。
- **日志调试**：原始报文抓取、日志窗口（文件位于数据目录 `logs/scaleplex.log`）。

## 支持品牌与真机校准状态

| 品牌 | 传输 | 默认端口 | 在线检测 | PLU 下发 | 回收 | 时间同步 | 备注 |
|---|---|---|---|---|---|---|---|
| DIGI 寺冈 | UDP/TCP/FTP | 秤号(1) | ✅ | ✅（8442 族 / b 族） | ✅（8442/b 族；ETP 族仅回收） | ✅（b 族） | 8442 族走 TWS.exe 工具链；b 族走 SMScaleAPI.dll；ETP 族（SM-120）仅 FTP 回收 |
| 梅特勒-托利多 | TCP | 3001 | ✅ | ✅（Item Update） | ✅（ReadAll） | ✅ | 经 MTScaleKey 接口 XML + 32 位 PowerShell 桥接 MTScaleAPI.dll |
| 顶尖 Aclas | TCP | 5002 | ✅ | ✅ | ✅ | ❌ | 经 AclasSDK.dll（需配置工具目录） |
| 容大 Rongta | UDP | 5001 | ✅ | ✅（真机通过） | ✅（真机通过） | ✅（真机通过） | RLS1000 私有二进制协议（详见下文"协议速览"）；回收走官方 rtslabelscale.dll 桥接 |
| CAS 凯士 | TCP | 20304 | ✅ | ✅（真机通过） | ✅（真机通过） | ✅（读时间✓） | 原厂 Cl5k .NET 库生成协议帧 + 裸 TCP 发送（详见下文） |
| 大华 DAHUA | TCP | 4001 | ✅ | ✅（真机通过） | ✅（真机通过） | ✅（真机通过） | TMA_07 ASCII 协议，裸 Socket 免 DLL；流水回收✓、标签格式表✓；**无远程删除/清空命令**、时钟仅可写 |
| 友声 YouSheng | TCP | 33571 | ✅ | ✅（真机通过） | 秤端固件未开放 | ✅（真机通过） | 官方 JHScale.dll 32 位桥接（需配置工具目录）；读时间✓ |
| 辉宏 HuiHong | UDP | 1111 | 基础 | 待校准 | ❌ | ❌ | 协议未真机校准 |

> ✅ = 已真机验证；"基础"= 通道可用但协议帧未与实体秤比对；工具链类驱动需在系统设置中配置
> 原厂 DLL/工具目录（`scaleplex.digi.tools`、`scaleplex.mt.dll`、`scaleplex.aclas.tools`、
> `scaleplex.cas.tools`、`scaleplex.yousheng.tools`、`scaleplex.rongta.tools`）。

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
3. **下发**：PLU 页勾选商品 → 「下发选中」→ 弹窗选择目标秤（全部/单台，默认第一台在线秤）→ 一次性下发；
   或设备管理勾选目标秤 → 「下发选中设备」，按分组一键下发。
4. **回收**：PLU 页「回收」→ 弹窗选秤；大华秤回收结果表格多选后选择
   「覆盖导入（清空本地商品库）/ 增量导入（合并）/ 重试 / 取消」，其他品牌自动入库。
5. **在线检测**：心跳轮询自动运行，也可侧边栏手动刷新。

## 协议速览（真机校准结论）

### 容大 RLS1000（UDP 5001，已真机验证）

- 会话帧 `0E 01 00 34 12 00` + BCD 时间戳 → 配置帧 `0E 04 00 04 04 04 04` →
  0B 数据帧（每帧 4 条 PLU 子记录）+ 01 名称帧（每 PLU 一帧）→ 结束帧 `0E 02 00 34 12 00`。
- 固定 257 字节帧，**每帧重复发送 8 次**（无应用层 ACK 重传机制），秤端每帧回 1 字节 `0x02`。
- 子记录（64B）字段：品名 GBK 36B（+0）、货号 int24 大端（+36）、部门 BCD（+41）、条码 int8（+42）、
  **价格 4B BCD ×100 大端数字序（+43，如 20.00 → `00 00 20 00`）**、保质期 int8 天（+50）。
- **时间同步** = 单发会话开场帧（BCD 当前时间）×8；**红线：绝不能补发 `0E 02` 结束帧**——
  空会话提交会清空秤端 PLU 表。
- **PLU 回收** = 官方 `rtslabelscale.dll` 桥接（内置 32 位自研桥接 exe，NDJSON 输出）：
  PC 发 `0E 03 00 {slot LE16}{0x80}` 逐槽请求，秤回 64B 记录或 `FF` 空槽填充。
- 注意：原厂 DLL `rtslabelscale.dll` 的 TCP 5001 通道未被 GUI 使用，实际传输为 UDP。

### CAS 凯士 CL 族（TCP 20304，已真机验证）

- 协议帧由原厂 Cl5k .NET 库生成（`Cl5k.Plu.Make4Download` / `ScaleDate.Make4Download|Make4Upload`），
  Kotlin 裸 TCP 发送（×8 连发）；PLU 结构需手动填充 `PluSchema`（23 个 FieldInfo）。
- PLU 回收无需桥接：直接拼请求帧 `R02F{dept:X2}{plu:X6},00\n` 逐号查询，
  按回帧 `F={code:X2}.{type},{len:X}` 解析（**len 为十六进制**）。
- 时间同步/读时间：`W45F01` 帧（DateInfo，arrSystemDate 原始字节非 BCD）。
- 注意：手工逆向帧一律被秤端回 `W02:EFE` 拒绝，必须走原厂库生成。

### 大华 DAHUA TMA_07（TCP 4001，已真机验证）

- 全 ASCII 文本帧（PC 端 CRLF 结尾，秤端应答以 ETX(0x03) 结尾），裸 Socket 免 DLL。
- PLU 下发 `!0V{号}A{73位数字段}{条码段}`（ACK 不区分成败，无效记录前置拦截）；
  回收 `!0U{号}A`（空槽 = `noplu`）；流水 `!0J{记录}A`（结束 = `0j{no}ac`，`!0HA` 清空）；
  校时 `!0P{yyMMddHHmm}`；标签格式表 `!0L{页}A{23×4位}`（4 页 92 格）。
- 73 位数字段：商品代码7 + 单价6(分/kg) + 称重方式1 + 特殊6 + 有效期3 + 店号2 + 部门2 +
  代号13 + 皮重5 + 标签2 + 打折2 + 时段24；条码段 `B{条码}C..D..E`。
- 流水数据 56-58 位：时间10（**月份≥10 编码为 0x10+m 十进制**，27=11月）+ 店号4 + PLU4 +
  重量6(0.1g) + 单价6(0.1分) + 总价6(分) + 折前6(角，不映射) + …
- **秤端行为**：无远程删除/清空命令（官方软件亦无）；时钟仅可写不可读；会话活跃期间
  拒绝新 TCP 连接（驱动已做操作互斥，批量下发每条独立连接）。

### 友声 JH 系列（TCP 33571，已真机验证）

- 官方 `JHScale.dll`（32 位 stdcall）桥接：连接/探测/读时间/写时间/PLU 下发；
  PLU 结构体 Pack=4（Text[8][256] GBK、DA[4] 嵌套），NDJSON + 退出码。
- 上传类命令（JH_GetPLU/JH_GetInfo）该固件未开放（恒空/失败），回收需秤端确认
  "上传商品资料"模式后重测。

### DIGI 寺冈 / 托利多 / 顶尖

- DIGI：8442 族 `TWS.exe RDR 25` 在线探测、`SMScale.dll` CSV 下发；b 族 `SMScaleAPI.dll`
  （端口 = 2000 + IP 尾段，写成功码 0x06）；ETP 族仅 FTP（admin/admin）回收 `plu0uall.csv`。
- 托利多：Task.xml（TaskType 4=查在线）→ `MTScaleAPI.dll ExecuteTaskInFileW`，
  在线以 `CheckOnline/Status ≥ 0` 为准（TaskResult 状态恒 OK 不可信）。
- 顶尖：`AclasSDK GetDevicesInfo` 设备探测（设备名即型号）+ `Sync_ExecTaskA` 上下行。

## 已知限制与待办

- 容大：PLU 删除/清空、标签格式下发/回收的帧类型未标定；时间同步/下发/回收已实现。
- 凯士：删除 PLU（`Make4DeleteOne`）未接入；流水回收未接入。
- DIGI：b 族流水（Receipt_D）、标签格式下发待接入；ETP 族下发需 `plu0uall.csv` 写入规范。
- 大华：型号口径待确认（TMA_07 版协议）；流水折前金额（角粒度）与未确认字段未映射。
- 友声：PLU 上传待秤端确认"上传商品资料"模式；流水/删除功能未真机验证。
- 辉宏：协议帧基于行业通用设计，待实体秤抓包校准。
- 打包任务依赖 GitHub 下载 Wix，网络受限环境建议以 jar 替换方式部署
  （替换安装目录同名 jar 即可，cfg classpath 不变）。

## 许可证

见 [LICENSE](LICENSE)。
