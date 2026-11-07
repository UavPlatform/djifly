# Stage1 codec / 原字节 HTTP 适配：第一可审段

## 基线与唯一权威来源

- 客户端基于真实 `master` **8702b4324c65f7d1b5ab2c23ffc1478379fb74af**（自动同步 PR18 已合）；不依赖、不合并、不改写 PR20。
- 远端读取并固定 backend/main **d10fc1bc116a6b952312beced2fd4fd8aa5a1d1b**。
  - [实际草案全文](https://github.com/UavPlatform/backend/blob/d10fc1bc116a6b952312beced2fd4fd8aa5a1d1b/spec/event-contract/DRAFT.md)
  - [严格包 schema](https://github.com/UavPlatform/backend/blob/d10fc1bc116a6b952312beced2fd4fd8aa5a1d1b/drone-backend/src/main/java/com/uav/flight/event/EventPacketSchema.java)
  - [真实服务](https://github.com/UavPlatform/backend/blob/d10fc1bc116a6b952312beced2fd4fd8aa5a1d1b/drone-backend/src/main/java/com/uav/flight/event/EventPacketService.java)
  - [真实存储](https://github.com/UavPlatform/backend/blob/d10fc1bc116a6b952312beced2fd4fd8aa5a1d1b/drone-backend/src/main/java/com/uav/flight/event/EventPacketStore.java)
- 比较完整 Git tree：`spec/event-contract/*` 和 Stage1 schema/service/controller 在 d924e345 → d10fc1b 没有变化；新提交仅修 `deploy/Caddyfile`。不是拿旧 hash 声称同步。
- 自动发布的 OpenAPI 仍为 `contract-2ba6bb1a`，source d924e345，SHA256 `2ba6bb1a876ac4d131c77031aa5e286f1e4585bc0a47004ad2dfe866f7f9193e`。本段**不改** vendored OpenAPI、同步工作流或其来源记录；只按现有 `tools/openapi-gen.mjs` 的标准机制登记新的 POST 消费点并生成新增路径常量/引用到的模型。不是另建同步轨道。
- 后端文档仍明确写“未冻结”。本地只保存固定 SHA 的共享向量作 **test fixture**，不复制一套 `spec/event-contract` 权威合同，不宣布合同或 DJI 单位已冻结。

## 实现边界

新增 `platform/eventstage1`，全部经 `ExperimentalStage1Api` 显式 opt-in；**没有**默认真实地址、Hilt/DI、SDK 回调、生产采集入口、自动上传 worker 或旧路径切换。

- `Stage1Codec`：严格 UTF-8、无 BOM、Unicode 代理项与 ASCII `\\u` hex 检查，解码后重复键（含 null）、整数 token/安全整数、技术上限、根 15 字段与事件 4 字段检查；再使用 JCS 1.1 输出合法对象/数组。JCS 库不是入口校验器。
- identity 精确九字段；要求实际正数 `assignmentVersion`，不默认 0；调用者必须提供已持久化、稳定的 chain/source/order/机身身份。续包 builder 不允许改变 identity。首包显式保留 `prevPacketId:null`。不自动分配链、不从展示名推断身份，不造 SDK 量纲或未知值。
- 自哈希、H0、完整包 contentDigest 依当前服务公式。解码保留原始 bytes 的不可变副本，后续不重新序列化请求。
- `Stage1Uploader`：真正的 OkHttp POST，地址引用现有生成路径常量；只允许 HTTPS（loopback HTTP 测试除外），禁止重定向、cookie 和隐式认证/正文 interceptor；每次调用仅一次 HTTP 尝试，call timeout 最多 30 秒。
- **锁边界**：内部状态锁只覆盖本地簿记与 SQLite 提交，HTTP 请求、响应正文读取、认证回调都在锁外；同一 packet 的重复 `attempt` 由 per-packet gate 串行化，不同 packet/链互不阻塞。回归 `inFlightRequestDoesNotBlockProgressReadsOrOtherPackets` 在旧实现上超时失败（`latestProgress` 与另一 packet 的 `attempt` 被在途响应阻塞），修复后通过。
- 采集并发：`enqueue` 不受上传锁影响，慢 HTTP 不阻塞本地追加。
- 发现并修复真实栈行为：仅设置 `retryOnConnectionFailure(false)` **仍会**在 503 + `Retry-After:0` 隐式发第二次请求。回归先失败 `expected 1 / actual 2`，改为每次 HTTP call 的 one-shot RequestBody 后通过；明确重试会从同一持久化 bytes 新建一次 body。
- 验证 `Result` 成功信封、固定 receipt 版本/包 ID/完整 contentDigest/chainHash/完整持久化声明及每个事件的有序、无遗漏、无重复固定结果。包含 `REJECTED` 或全拒绝不等于未持久化；合法 `DUPLICATE` 保留原 sourcePacketId。
- 先事务提交固定 receipt，再事务释放正文；SQL 失败向调用者抛出，不误报为远端格式错误。提交失败会回滚；提交后释放失败/进程重启从原 receipt 恢复释放而不再 POST。摘要/逐事件结果/本地原文指纹留下。
- **本地** `local_body_sha256` 是原 bytes 的不可变性指纹，绝不是协议 `contentDigest`（后者为完整规范包 SHA256）。两种原文空白不同、规范相同的共享向量专门验证此区别。
- 一个本地 operation 绑定实际 pilot/order/assignment/source；首摘要写入的 CAS 在事务内避免并发首写改归属。仅用有界 operation/packet/首摘要读取，不读整单历史正文。新账户不得继续旧账户作业；来源/记录丢失仍按基础存储 fail closed，绝不由空列表推导完整同步。
- 401 一次恢复回调后等待；恢复失败等显式账户/token 事件。408/429/5xx/网络失败保留原文，非零指数退避；Retry-After 秒（服务当前行为）以及 HTTP date 已测。
- **Retry-After 封顶 1 小时**：超过 `3_600_000` ms 的值被夹到该上限，不再用 `Long.MAX_VALUE` 当哨兵——那会让 endpoint 实际永不到期。回归 `retryAfterAppliesToEndpointNotOnlyTheOldPacketAndHugeValuesDoNotOverflow` 断言 `3_600_000`（旧实现返回 `Long.MAX_VALUE`）。
- **暂停范围**：`serverDue` 是 uploader 级（所有链），因为后端限流是 endpoint 级；本进程内属于该 endpoint 的请求在到期前都返回 `NotDue`。这比「只挡被拒的旧包」更强，是刻意选择：绕过限流会持续撞门。个别 packet 的退避另存于 per-packet 表，不阻塞其他 packet。
- fixed receipt 与动态 progress 分离；只接收更新 validationVersion，不把 highestReceived 当 contiguous。缺前包时后包确认可先释放，旧洞仍留原文待补。
- 动态 progress 缺失或不可解析时：固定 receipt 照常提交并释放正文（`Durable`），同时 `progressInvalid=true`、`latestProgress` 不更新。**不再静默**吞掉该情况——调用者可据此发现服务端回归，而不会被当成正常无进度。
- 相同高度/锚点 hash 矛盾或 FORKED 暂停该链上传但允许继续本地追加及其他换机链。
- **终态与复位**：HTTP 拒绝、不可用的 2xx 确认都会把该 packet 置为 parked，后续 `attempt` 返回 `InterventionRequired(status, errorCode, reason)`，不再伪装成 `InvalidResponse`。只有 `clearIntervention(packetId)` 解除；链级 fork 只有 `clearChainFork(chainId)` 解除。重启进程**不是**复位手段（状态仅进程内，重启后为未暂停）。
- **409 区分**：只有 `errorCode == EVENT_PACKET_CONFLICT` 才冻结整条链；例如 `DISPATCH_ASSIGNMENT_CHANGED` 只 park 该 packet，同 operation 其他链不受影响。回归 `changeAssignmentConflictParksOnlyThatPacketAndCanBeClearedExplicitly` 在旧实现上失败。
- 同一 operation 允许多条机身链（不同 `chainId`/`serialNumber`/`serialSource`）；客户端只钉住 order/assignment/recordingSource，逐链身份由包内 anchor/chainHash 与服务端 `CHAIN_IDENTITY` 保证。回归 `multipleAirframeChainsShareOneOperationWithoutRebinding`。

## 本轮未完成 / 启用生产前置条件

1. **无真实后端 HTTP 联调、Android 运行时/R8 或 DJI 实机验证**。MockWebServer 是真实 loopback TCP + OkHttp，不是真实业务服务；响应为根据已读 VO/服务构造的本地模拟，不能验证数据库、授权窗口或服务端原子性。
2. 当前是阻塞、显式单次调用组件，须在非主线程使用。没有调度器；退避时点、认证暂停、分叉暂停、动态 progress 目前**仅进程内**，不是持久恢复的全量后台工作流。生产启用前需有界/持久化调度状态、重启后对账/分叉 gate、真实 auth provider 和生命周期接入；不可重建组件来当自动解除暂停。
3. 不实现 `event-reconciliation` 请求/固定版本分页/common-prefix，也不声明全量同步、订单截止完成、跨终端完整性、投影或结单。收到固定完整包确认仅证明这一包可释放；不能据空 pending 判定作业完成。
4. 链分配、持久化链尖端/换机编排、可飞单+实际身份采集资格、同类传感器 2 秒与实际状态/操作事件不限频属于下一段 SDK/采集接入。身份来源变化不能自动别名，也不能新造 assignmentVersion。
5. 后端 payload 单位仍是候选，客户端保留原事件、交由服务固定逐条结果；不在这里接 SDK 或发生产事件。旧 `connect/close/activeSerial`、WS、直播与计费不动。
6. OpenAPI 当前未充分表达必填/nullable；自动生成的 nullable DTO/Gson 默认省略 null **不是**规范包编码器。本段显式 tree codec 保留必填 null；需要后端日后改进 OpenAPI，不能在客户端手改权威 schema。

Stage1 实际服务已经取消过去 7/30 天上传窗口、累计 2000 包限制和按到达顺序的 2 秒拒绝：当前约束是采集时间历史 assignment 区间、未来时钟 10 分钟和未验证乱序跨度 2000；已收到最高高度内的洞仍可补。本段不以客户端预过滤替代服务授权，也没有改旧 telemetry 路径的规则。

## 测试证据

固定 fixture：`tools/stage1-client-tests/src/test/resources/backend-d10fc1b/`；SOURCE.json 记录 backend/client SHA、Git blob 与 SHA256；只有测试数据，不是规范副本。

```bash
mvn -B -f tools/stage1-client-tests/pom.xml clean test
mvn -B -f tools/event-store-tests/pom.xml clean test
mvn -B -f tools/jcs-probe/pom.xml clean test
node tools/jcs-probe/fixture-vectors.cjs
node tools/openapi-gen.mjs --check
(cd openapi && sha256sum -c drone-backend.openapi.sha256)
```

本地 JDK21、Kotlin2.0.21、JVM target11；新增 CI 独立 JDK17 gate。Stage1 harness 当前 **72** 测试：codec 10（四条真实共享黄金向量 + canonicalFixtures + 严格反例）、真实 HTTP/SQLite 41、既有完整存储 17、现有 OpenAPI gate 4。另独立存储 17、JCS 17，不重复计算成额外独立案例。Mock HTTP 覆盖 raw bytes、路径/token、部分/全部拒绝、回执篡改/缺失/重复/排序、401、限流/退避/断网、重定向、后包先确认、分叉、归属、事务故障、重启恢复，以及本轮新增的锁边界（在途请求不阻塞 progress/其他 packet）、409 分类型处置、终态显式复位、多机身链共存与 progress 不可用标记。

依赖：JCS1.1 新增 app implementation；Gson 现有依赖、OkHttp4.12.0 现有依赖；MockWebServer4.12.0 与 sqlite-jdbc3.46.1.0 仅测试。Android minSdk24 / Java11 不变。Maven harness 不含 Android SQLite adapter 运行时，不能用 JDBC 通过声称 Android/真机通过。本机执行 `./gradlew :app:testDebugUnitTest --offline --no-daemon --stacktrace`（占位配置）在配置阶段失败：离线 Gradle 缓存没有 `com.android.application:8.8.2`；尚未进入 Android 编译/测试，不是 app 测试通过。没有改仓库以绕过此限制。
