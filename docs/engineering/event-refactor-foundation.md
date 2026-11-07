# 事件重构第一段：SDK 证据与本地持久化边界

核实日期：2026-11-07（北京时间）。依据统一计划 v1.2；用户本轮已明确授权本地开发及提 PR，不自动合并。本文件是工程调查，不替代产品规范或后端合同。

## 1. 实际依赖与证据级别

- `app/build.gradle.kts:154-158`：aircraft / networkImp / aircraft-provided **5.17.0**。
- `gradle.properties:30`：UXSDK `SDK_VERSION=5.17.0`；`dependencies.gradle:4-6` 同版本。
- 本地公开 provided JAR SHA-256：`a2c8e5e42871e84418a2335abb0a947977173c8e8c60a333fc63c53489182552`。
- `tools/verify-dji-event-keys.sh` 用 `javac` 对该 JAR 编译字段和泛型类型；仅验证公开 API 存在，不执行类初始化、不证明某台飞机会回调。
- 官方网页是滚动文档，不是不可变 5.17 快照。因此把“JAR 符号存在”、“文档语义”、“实机通过”三层分开。本阶段**没有真机、没有飞行、没有 SDK 模拟器验证**。

官方来源：

1. [V5 发布记录](https://developer.dji.com/doc/mobile-sdk-tutorial/cn/index.html) —— 本文只取 **Android MSDK 5.17.0** 段，不套用后续 5.18/5.20 的机型。
2. [FlightControllerKey](https://developer.dji.com/api-reference-v5/android-api/Components/IKeyManager/Key_FlightController_FlightControllerKey.html)
3. [继承的 DJIFlightControllerKey](https://developer.dji.com/api-reference-v5/android-api/Components/IKeyManager/Key_FlightController_DJIFlightControllerKey.html)
4. [BatteryKey](https://developer.dji.com/api-reference-v5/android-api/Components/IKeyManager/Key_Battery_BatteryKey.html)
5. [4→5 Key 映射](https://developer.dji.com/doc/mobile-sdk-tutorial/en/quick-start/version-differences.html)
6. [Velocity3D](https://developer.dji.com/api-reference-v5/android-api/Components/IKeyManager/Value_Common_Struct_Velocity3D.html)
7. [ProductKey](https://developer.dji.com/api-reference-v5/android-api/Components/IKeyManager/Key_Product_ProductKey.html)

| 优先能力 | 5.17 JAR 公开字段与类型 | 文档与当前实现 | 边界／下一步 |
| --- | --- | --- | --- |
| 实际在飞 | `FlightControllerKey.KeyIsFlying: DJIKeyInfo<Boolean>` | 文档 true=aircraft in flight，since 5.0；`sdk/DjiSdkManager.kt:135` 已监听 | 真机核实地面开电机、实际起飞、降落仍开电机、断连、重连初值；null 不是 false。计费投影依赖状态事实，不依赖操作回调成功 |
| 电机 | `KeyAreMotorsOn: DJIKeyInfo<Boolean>` | 文档 true=motor starts，get/listen since 5.0；当前还未监听 | 必须与 flying 分开；本 PR 不接采集，不把开电机算飞行 |
| 位置 | `KeyAircraftLocation: LocationCoordinate2D`、`KeyAircraftLocation3D: LocationCoordinate3D` | 2D 现监听于 117 行；V5 映射确认 2D；文档列出 3D | 不以 0,0 表示未知；缺值/无效值不生成传感器事件，按具体机型验证坐标和高度参考 |
| 高度 | `KeyAltitude: DJIKeyInfo<Double>` | 现监听于 123 行，现有代码称相对起飞点米；映射文档列出此 Key，滚动 V5 API 详情没有完整解释此继承字段 | 不冒充海拔/离地高度；发布前核实相对起飞点及 barometer 语义，必要时用 3D 并明确基准，不混用超声高度 |
| 速度 | `KeyAircraftVelocity: DJIKeyInfo<Velocity3D>`；`getX/Y/Z(): Double` | 文档明确 NED；现代码 129 行仅合成水平速度，丢 z | 合同建议保留实际分量，派生水平速度不顶替原事件。Velocity3D 英文页写 “Unit: second” 有疑似文档错误，单位需进一步与机型/示例核对，不凭页面错误定协议 |
| 电池 | `BatteryKey.KeyChargeRemainingInPercent: DJIKeyInfo<Integer>` | 现 90 行仅 LEFT_OR_MAIN；文档 M30 index0=左、1=右 | 不能把左电池值叫整机总电量；双电池按 index 单独事件或按容量加权，先定合同；无数据不补 0 |
| 累计时间 | `KeyFlightTimeInSeconds: DJIKeyInfo<Integer>` | 文档明确从电机启动累计，单位 **0.1s**，重新上电清零 | **不能直接作空中飞行时长**，不能被字段名 Seconds 误导。当前 remainingFlightTime 是低电返航估计，也不是已飞时长 |
| 机型/机身号 | `ProductKey.KeyProductType`；FC / Product 的 `KeySerialNumber` 均在 JAR | `fetchProductDetails:189-240` 当前优先 FC serial，失败才 Product serial；官方 FC Key 描述是**飞控序列号**，Product 滚动文档未列 serial 详情 | 这两种 SN 不可未经核实视为相同机身号；需确定链身份用哪个，并在机型上比对机身铭牌/Pilot。源混用会断链，不在本基础 PR 自行切换 |
| 操作意图 | `KeyStartTakeoff`/`KeyStartGoHome` action | `performTakeOff/RTH:173-187` 目前只记 log | 后续独立记录按钮意图/结果，不能用 action success 合成 isFlying=true |

实际状态/操作逐次记录不限频；传感器同类型至少间隔 2 秒。未来必须直接消费带身份与连接代次的 SDK 回调，不能从已合并的 StateFlow 快照反推稀疏事实，避免合流丢状态跳变及缓存跨机污染。当前断连未重置 `currentFlightData`、异步身份 fetch 也需连接代次防串；本 PR 没改这些旧路径。

### 5.17.0 官方机型表（不是枚举值表）

| 飞机 | 5.17 表内飞机固件 | 对应遥控器（表内） |
| --- | --- | --- |
| Matrice 350 RTK | 14.01.00.06 | RC Plus 05.02.11.11 |
| Matrice 300 RTK | 64.01.00.04（RC Plus）/10.01.08.01（行业带屏） | RC Plus 05.02.11.11 / 行业带屏 09.00.05.01 |
| Mini 4 Pro | 01.00.0800 | RC N2 / N3 01.01.0300 |
| Mini 3 | 01.00.05.00 | RC N1 04.16.05.00 |
| Mini 3 Pro | 01.00.09.00 | RC N1 04.14.07.00 / RC Pro 03.02.09.00 |
| Mavic 3 行业系列 / 多光谱 / 3TA | 14.02.00.22 | RC Pro 行业版 02.01.07.10 |
| Matrice 30 系列 | 10.01.05.04 | RC Plus 05.02.11.11 |
| Matrice 4E/4T | 14.03.00.09 | RC Plus 2 01.64.07.02 |
| Matrice 4D/4TD | 14.03.00.03 | RC Plus 2 01.64.07.02 |
| Matrice 400 | 16.00.00.05 | RC Plus 2 01.64.07.02 |

同段还列妙算3与禅思负载；不据此推断载重传感器。固件列表是官方该版本测试组合，不宣称所有其他组合一定不工作。

**FlyCart 30 风险**：当前 `DroneModelMapper.kt:9-10` 有 FC30 字符串映射，但本地 5.17 `ProductType` 无 FLYCART_30/DJI_FLYCART_30，5.17 官方支持列表也未列 FlyCart。枚举中的其他旧产品同样不等于支持。不能以平台模型目录或字符串映射宣称 FlyCart 可用；若实际验收机是 FlyCart，需单独确认 DJI 支持路径，不能虚构载荷/飞行能力。

## 2. 本 PR 的持久化基础

隔离包 `platform/eventstore` 未注入 DI、未触达 Retrofit、不连接任何新 endpoint、不删除 connect/close/旧遥测/设备 WS。先落存储能力，待合同确认再接唯一业务路径，不建设长期双轨。

- `RecordingSourceFiles` 显式首次创建随机 UUID；工厂将 identity + SQLite 全部置于 `Context.noBackupFilesDir/event-packets`。Android [Auto Backup 说明](https://developer.android.com/identity/data/autobackup) 指这些目录始终排除，不能让系统云备份/D2D 把 ID 移到新终端冒充原来源；不使用可备份 SharedPreferences、不用账号/机身 SN 当终端 ID。root 手工克隆/恶意复制不在此机制承诺内。
- 恢复要求 identity、数据库都存在，且 source 表匹配；缺任何一边抛出受阻异常，不静默重建。显式新安装创建≠续旧作业。整目录丢失无法单凭本地发现：旧作业恢复缺记录必失败；后续服务端必须按订单对比已知记录源/全链清单。**此库没有 isSynced=true 或可结单 API**，空队列绝不是同步证明。
- 每个作业显式 begin，不 upsert；订单号、换机机身号用于本地归属；包 ID 全库唯一。包原文、摘要、固定确认结果、截止点均以不透明 BLOB 保存，不定义线上 DTO/规范化/哈希。
- 原文写入为 SQLite transaction + `synchronous=FULL`；写入失败回滚。重复 ID 仅接受完全相同原文与摘要，不允许覆盖、换作业、换机；正文已释放后拒绝凭新输入“恢复原文”。
- 完整 durable confirmation 和 event 部分结果不是同一事。`acknowledgeDurable` 只给未来合同适配层调用，调用前必须核实服务器已持久化不可变原包与固定结果，不能因为 2xx 就调用。固定 receipt 不可变，包含拒绝原因；确认与释放正文可分步，崩溃多保正文不丢数据。未确认不允许释放；已确认可独立释放，无需等缺洞补齐。此阶段未实现网络响应判断。
- `seal` 一次事务封末包+固定截止 BLOB；重复结单仅沿用原截止。截止后 append 拒绝，即使落地 SDK 持续回调也不追尾。resume 显式清待结单截止但保留全部历史，再追加新包并新截止。**截止里全链内容及末包状态事件的正确性仍需未来合同适配层校验**。
- pending 不按最旧包阻塞后包；摘要/receipt 持续保存可供未来差异查找。摘要现在不解释高度/哈希，更不自动生成假云端确认；不承诺已实现缺洞分页或二分对账。
- 工厂尚未成运行时单例；集成时须一个受控写入协调器、在后台 IO 调用。正文 BLOB 释放不是 SQLite 文件立即缩容，也不是安全擦除保证。空间上限、增量/分页读取与归档策略待包大小合同后加，不能把内存读全表扩展到飞行无限日志。

## 3. 与后端必须先固定的契约（当前 OpenAPI 没有事件包合同）

检查现 checkout `openapi/drone-backend.openapi.json`：有旧 telemetry/connect 等及 dispatch SSE，未见 packet/hash 链 POST。以下是建议/疑点，**不是已批准的线上协议**：

1. 链 ID 是否精确为 order+aircraft body SN；operation/assignment 与 recordingSourceId 的授权绑定、同作业换机和同 SN 再连规则；FC SN 与 Product SN 必须先定。
2. packetId / prevPacketId / eventId 类型；高度从 0 还是 1；初始锚点；hash 算法/版本与 domain separation；prevHash 的编码（原始 bytes 还是 hex）。包归属、机型、终端 ID、事件顺序、capturedAt、截止事件及 previous 标识哪些入 hash。
3. Kotlin/Java 不能直接分别 Gson/Jackson 普通序列化后 hash。建议统一 RFC 8785 JCS（需实际支持的实现与跨语言 golden vectors），或明确固定字段顺序/数值规则的规范编码；**不要临时自制“排序 JSON”**。两边同 UTF-8、无 BOM、无空白；null vs 缺失、Unicode转义/规范化、事件数组顺序、未知字段、重复 key、负零、指数格式、NaN/Infinity 禁止、Long 时间精度必须写明。若 JCS 使用 IEEE754，时间/ID 超 2^53 需字符串或限制范围，不能无声丢精度。Double 传感器应明确精度/缩放，不随客户端本地 locale 漂移。建议 SHA-256，但以合同决定为准。
4. 最安全的重试：合同编码一次，存固定请求 bytes；以后 Retrofit 用原 RequestBody，不重新经 DTO 序列化。服务端必须重算规范内容和前链而非回显 hash；HTTP envelope/认证 token/固定处理结果/最新链进度不应被混成同一 hash 输入。
5. 完整原包持久化 receipt 的明确信号、固定 event 终态、部分拒绝代码；固定结果与移动链进度分离；同 packetId 冲突；正文释放后仍可用服务器原包补洞重算的集成证据。
6. 401 恢复触发、408/429/5xx 重试/Retry-After、永久包级拒绝、最大包字节/事件数/时钟容差；历史订单结束后允许合法采集历史补传的授权依据。
7. 连续高度及对应 hash、最高已收高度、有界缺洞区间/分页、固定验证版本与共同前缀查询、一致快照防移动视图。真实分叉停该链上传但采集不中断。
8. 结单提交事务核验记录源+客户端与云端全部换机链最终位置、固定截止 ID/版本、空链、丢记录/新终端行为；关键飞行状态拒绝与网络待传要分开，不扩展待定费用规则。
9. SDK 事件类型/单位：FLYING/MOTORS 与 SENSOR 和 OPERATION_INTENT 分开；电池 index、速度 NED、nullable、capturedAt 源、连接代次去串；2秒只限同类传感器，用单调时钟节流，wall clock 只作事件时间。

## 4. 建议 PR 分段与验收

1. 本 PR：SDK 调查、opaque-byte SQLite 持久化/记录源/截止及恢复测试；Draft，合同未定不接生产。
2. 后端合同生效后：跨 Kotlin/Java golden vectors、SDK 原始回调稀疏采集+事务封包、身份/作业守卫；重启、换机、未知值、实际起降/意图分开。
3. 原包 Retrofit POST、认证恢复与退避、部分结果、正文释放、持续对账/缺洞/冲突和共同前缀；模拟及真实接口联调通过后删除旧连机/编号/序号业务。
4. 固定截止+所有换机链结单核验、丢记录/跨终端受阻；本地主动 POST 取票据开播，切换验收后删除设备长连接和远控/旧契约。异常终止费用由用户决定，不擅自扩展。

本地最小复现（不需 Android/DJI native runtime）：

```sh
JAVA_HOME=/path/to/jdk21 mvn -B -f tools/event-store-tests/pom.xml test
JAVA_HOME=/path/to/jdk21 DJI_PROVIDED_JAR=/path/to/5.17-provided.jar tools/verify-dji-event-keys.sh
```

SQLite JVM 测试使用 app 的同一 core 源码与 sqlite-jdbc；Android adapter 另用 API35 android.jar 编译。两者都**不是**Android SQLite 实机、闪存断电或 SDK 真机验证。发布门槛仍含 instrumented 重启/kill、磁盘满、设备备份迁移、地面开电机/起降/重连/换机/丢关键回调、落地持续回调可截止、真实后端乱序/部分拒绝/正文释放补洞等。

### 本阶段实际测试结果（2026-11-07，北京时间）

- Kotlin 2.0.21 + JDK21、SQLite JDBC：**10 tests，0 failures，0 errors**。覆盖原 bytes/来源标识重启恢复、调用方数组修改隔离、整批回滚、重复/冲突、未确认保正文、固定部分拒绝结果与释放正文恢复、后包确认不掩盖旧洞、新包继续、换机全链末包+固定截止事务、截止后回调不可追加、显式续作、丢记录/错误来源/第二终端受阻、未提交 SQLite 事务重开回滚。
- SDK 5.17 public Key 类型编译：PASS；Android adapter 对 API35 android.jar 的 Kotlin 独立编译：PASS。
- 未运行完整 Gradle Android build、Android instrumented tests、真机/模拟器采集、断电/磁盘满或真实后端联调；JVM 模拟未确认场景并非 Retrofit 状态码/退避测试。
