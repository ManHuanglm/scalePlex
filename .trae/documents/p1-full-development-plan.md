# ScalePlex P1 全量开发计划

## Context

对照 `information/` 任务清单，P0/P1 底座已完成（通信层、驱动抽象、DB、服务层、UI 主框架、DIGI 驱动、打包）。本轮目标：

1. **落实"资料只能下发不能回收"约束**：移除 `uploadPlu`（不从秤回收 PLU 与标签格式；交易流水读取保留）
2. **一期驱动闭环**：新增大华（TCP）、辉宏（UDP）、友声（TCP+串口）驱动 + 编解码单元测试
3. **DIGI 标签模板下发**（当前返回 pending 错误）
4. **标签编辑器 UI**（可视化画布，P1 最难点）
5. **分组管理 UI**（分组增删、秤分配、分组一键下发）
6. **流水筛选 + 导出 Excel**
7. **设置页完善**（数据库备份/恢复）

按阶段分批提交 git，每阶段独立可编译。

## 阶段 1：接口契约调整（commit 1）

- [IScaleDriver.kt](f:/AiCode/code/ScalePlex/driver-api/src/main/kotlin/com/scaleplex/driver/api/IScaleDriver.kt) L39 删除 `uploadPlu`（已确认全库无调用点，仅接口/Abstract/DigiSmDriver 三处定义）
- [AbstractScaleDriver.kt](f:/AiCode/code/ScalePlex/driver-api/src/main/kotlin/com/scaleplex/driver/api/AbstractScaleDriver.kt) L42-43、[DigiSmDriver.kt](f:/AiCode/code/ScalePlex/driver-impl/src/main/kotlin/com/scaleplex/driver/impl/digi/DigiSmDriver.kt) L159-160 同步删除
- i18n 清理：删除 `device.readPlu`、`plu.readback`（zh/en 两文件）
- `DigiSmDriver` 构造改用 `TransportFactory.create(device, bus)` 统一入口（为友声双模式铺路）
- **不改** information/ 下的需求文档（资料仅作参考）

## 阶段 2：一期三个驱动（commit 2~4，每品牌独立提交）

统一模式（参照 DIGI）：每品牌独立包 `driver-impl/src/main/kotlin/com/scaleplex/driver/impl/{brand}/`，含三个文件：

| 文件 | 职责 |
|---|---|
| `XxxProtocol.kt` (object) | 命令常量、`frame()` 组帧、`parseAck()` 应答解析（单点集中，待联调校准） |
| `XxxPluEncoder.kt` (object) | `encode(PluItem): ByteArray`（GBK 文本、价格小端，风格同 [DigiPluEncoder.kt](f:/AiCode/code/ScalePlex/driver-impl/src/main/kotlin/com/scaleplex/driver/impl/digi/DigiPluEncoder.kt)） |
| `XxxDriver.kt` | `AbstractScaleDriver` 子类，transport 由 `TransportFactory.create(device, bus)` 注入 |

报文结构设计（基于行业通用知识，标注待实体秤联调）：
- **大华 DAHUA**（TCP 4001，ASCII 命令风格）：帧 `CMD+逗号分隔字段+CRLF`（GBK 编码）；命令常量 `SET_PLU/DEL_ALL/SYNC_TIME/STATUS/LABEL/READ_TRADE`；应答按 OK/ERR 前缀判定
- **辉宏 HIHONG**（UDP，类 DIGI 帧）：复用 `STX+op+len(2LE)+body+二补码校验+ETX` 结构，独立 `HiHongPluEncoder`，OpCode 表待抓包校准
- **友声 YOUSHENG**（TCP+串口双模式）：二进制帧 `STX+CMD+LEN+DATA+BCC(XOR)+ETX`；两通道同帧格式，commType 决定 Transport 分派

[DriverRegistry.kt](f:/AiCode/code/ScalePlex/driver-impl/src/main/kotlin/com/scaleplex/driver/impl/DriverRegistry.kt) 注册三个新品牌。

单元测试（junit4，同 [DigiPluEncoderTest.kt](f:/AiCode/code/ScalePlex/driver-impl/src/test/kotlin/com/scaleplex/driver/impl/digi/DigiPluEncoderTest.kt) 风格）：每品牌 `XxxPluEncoderTest`（编码确定性、价格小端、GBK 截断防半汉字、状态位）+ `XxxProtocolTest`（校验和正确性、ACK/NACK 解析、数据内出现定界符的鲁棒性）。

## 阶段 3：DIGI 标签模板下发（commit 4 合并）

- 新增 `driver-impl/.../digi/DigiLabelEncoder.kt`：`LabelTemplate → DIGI 标签格式记录`，`LabelElementKind` → DIGI 格式码映射（首期覆盖 TEXT/BARCODE/WEIGHT/UNIT_PRICE/TOTAL_PRICE/DATE），mm→点阵换算
- 实现 [DigiSmDriver.kt](f:/AiCode/code/ScalePlex/driver-impl/src/main/kotlin/com/scaleplex/driver/impl/digi/DigiSmDriver.kt) `downloadLabelTemplate`（OpCode.DOWNLOAD_LABEL=0x09 已预留）
- [ScaleService.kt](f:/AiCode/code/ScalePlex/service/src/main/kotlin/com/scaleplex/service/ScaleService.kt) 新增 `downloadLabelToDevices/downloadLabelToGroup`（复用信号量并发模式）

## 阶段 4：标签编辑器 UI（commit 5）

新包 `app/src/main/kotlin/com/scaleplex/app/ui/label/`：
- `LabelEditorDialog.kt`（Dialog 900×640）：三栏布局 —— 左元素面板（ElementPalette：类型列表+添加）、中间 `LabelCanvas.kt` 画布、右属性面板（kind/text/fontSizePt/align/rotation/x/y/w/h 双向绑定）
- `LabelCanvas.kt`：`scale = min(canvasW/widthMm, canvasH/heightMm)` fit 缩放；Canvas 绘纸张边框+元素；`pointerInput(detectDragGestures)` 拖拽换算 mm 坐标、`detectTapGestures` 选中/取消，选中高亮描边
- 工具栏：保存/另存（[LabelService](f:/AiCode/code/ScalePlex/service/src/main/kotlin/com/scaleplex/service/LabelService.kt).save）、JSON 导入导出、1:1 预览、下发到秤（选分组 → 阶段 3 接口 → 进度弹窗）
- [LabelPanel.kt](f:/AiCode/code/ScalePlex/app/src/main/kotlin/com/scaleplex/app/ui/LabelPanel.kt) 列表行增加"编辑"入口

## 阶段 5：分组管理 UI（commit 6）

- [DevicesPanel.kt](f:/AiCode/code/ScalePlex/app/src/main/kotlin/com/scaleplex/app/ui/DevicesPanel.kt)：顶部分组筛选下拉、设备行显示组名；`DeviceEditorDialog` 增加 commType 下拉、串口参数区（SERIAL 时显示 portName/baud/parity/stopBits）、分组下拉
- 新增 `GroupDialog`：分组增删改（走既有 `ScaleService.addGroup/updateGroup/deleteGroup`），删除确认提示"设备将转为未分组"
- 分组"一键下发"按钮：`downloadToGroup(id, pluService.all())` + 通用进度弹窗（展示各秤 BatchResult 明细）

## 阶段 6：流水筛选与导出（commit 7）

- [TradePanel.kt](f:/AiCode/code/ScalePlex/app/src/main/kotlin/com/scaleplex/app/ui/TradePanel.kt)：筛选行（设备下拉、起止日期、PLU 号）→ 调既有 `tradeLogService.query(deviceId,from,to,pluNo,page,pageSize)`
- [ImportExportService.kt](f:/AiCode/code/ScalePlex/service/src/main/kotlin/com/scaleplex/service/io/ImportExportService.kt) 新增 `exportTradeLogXlsx(file, records, decimals)`（POI 模式同 exportPluXlsx）；导出按钮经 JFileChooser

## 阶段 7：设置页完善（commit 8）

- [SettingsPanel.kt](f:/AiCode/code/ScalePlex/app/src/main/kotlin/com/scaleplex/app/ui/SettingsPanel.kt)：备份按钮 → FileChooser + `Database.backupTo(file)`（[Database.kt](f:/AiCode/code/ScalePlex/db/src/main/kotlin/com/scaleplex/db/Database.kt) L54 已实现含 WAL checkpoint）；恢复按钮 → Database 新增 `restoreFrom(file)`（关连接→覆盖→重连+migrate），UI 确认框提示重启生效
- i18n 补充新增文案（见下方 key 清单）

## i18n 新增 key（core/src/main/resources/i18n/messages_zh_CN.properties + messages_en_US.properties）

- 分组：`group.add/edit/name/note/download/deleteConfirm`
- 串口：`serial.parity/stopBits`
- 标签编辑器：`label.edit/addText/addBarcode/addQrcode/exportJson/importJson/font/fontSize/align/rotation/pos/sizeWh/selectHint/downloadOk`
- 流水：`trade.filter.device/from/to/exportDone`
- 设置：`settings.restore/backupDone/restoreConfirm`

已有可复用：`device.field.comm/group`、`trade.export`、`label.preview/download`、`settings.backup`

## 复用清单（不重复造轮子）

- `TransportFactory.create(device, bus)` —— 所有新驱动 Transport 创建入口
- `BaseTransport.withRetry` —— 重试/超时
- `DigiSmDriver.frame()` 校验模式、`DigiPluEncoder` 写字节辅助 —— 新编码器参考实现
- `ScaleService.downloadToDevices` 信号量并发 + `BatchResult` 汇总
- `ImportExportService` POI XSSF 模式
- `AppState`/`tr()`/AlertDialog 表单模式

## 验证方案

1. 每阶段：`.\gradlew build`（含测试）通过后 `git commit`
2. 驱动编解码：`.\gradlew :driver-impl:test` 全绿
3. 标签编辑器/分组/流水/设置：`.\gradlew :app:run` 手动验证 UI 操作路径（建模板→拖元素→保存→JSON 导出再导入一致；建分组→分配秤→一键下发弹进度；流水筛选→导出 xlsx 打开正常；备份→改数据→恢复→重启一致）
4. 打包验证：`.\gradlew packageMsi createDistributable` 成功后便携版冒烟启动

## 风险与待联调清单

1. 三家协议命令字/字段顺序/校验算法基于行业通用知识，**待实体秤抓包校准** —— 协议常量已单点集中在 XxxProtocol.kt，校准只动一处
2. 标签元素 → DIGI 格式码映射首期覆盖有限，复杂版式下发时降级提示
3. SQLite 恢复需重启生效；备份已处理 WAL
4. POI 流水导出超 5 万条建议后续分页写出
