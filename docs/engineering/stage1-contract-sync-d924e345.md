# Stage1 契约同步：backend main@d924e345（不冻结、不接网络）

2026-11-07，北京时间。本 PR 是纯契约/生成工具/测试同步，不是上传功能 PR，未自动合并任何 PR。

## 独立核实与保留工作

- GitHub 连接器读取 backend/main：**d924e34522119ae220b48dee03669752f3d8e331**；PR57 `merged=true`，合并于 **2026-11-07 18:21:34 北京时间**，merge SHA同上。
- djifly/master：**8d3c10ad3393306506ec57254306dc2c6ff8b156**；PR19已于 **2026-11-07 17:34:54 北京时间**合并。
- 本同步分支从上述 djifly master 创建；原 `feat/event-journal-foundation` 保留未推 **50490f8e38840704a374e70c85ed5fbc5846676f**，将其中测试/反馈 cherry-pick 为 **6e8c7f0** 后继续。没有 reset、force push、改写原提交或更动原 checkout 的 ` D Mobile-SDK-Android-V5`。
- 后端没有克隆。全部远端读取固定实际 main SHA，而非旧 a11d931。`spec/event-contract/{DRAFT.md,golden-vectors.json,verify-golden.mjs}` 是完整原样快照。`SOURCE.json` 保存来源 commit、客户端 base、4文件 Git blob SHA/SHA256，以及逐份全文读取核验的11份后端实现证据；导入时还重算 Git blob SHA 对照远端 tree，避免工具围栏/换行混入。
- OpenAPI 按仓库现有同步机制的脚本步骤执行：固定来源 `OPENAPI_SOURCE=... node tools/openapi-sync.mjs`，随后 `node tools/openapi-gen.mjs`。没有运行可能拾取旧 Release 的浮动下载 workflow，也没有覆盖机器人共享分支。

当前 OpenAPI SHA256：`2ba6bb1a876ac4d131c77031aa5e286f1e4585bc0a47004ad2dfe866f7f9193e`。

## 对旧 a11d931 / 50490f8 的真实差异

| 项目 | 旧稿 | 当前 main 文档 + 实现核实 |
| --- | --- | --- |
| 累计容量 | 每链2000包 | **已删除累计包数限制**；`EventLimits.admitsUnverified` 仅限新高度领先 contiguous 的未验证跨度2000；不超过 highest 的旧洞仍可补。前缀追上可继续超过2000包。height仍最大1000000，不是无限高度。 |
| 迟到事件 | 过去7天至未来10分钟 | **已删除过去7天/30天首次上传限制**；`EventDecider.decide` 只检查未来10分钟及历史作业区间。并不保证错误轮次、时间窗外事件也能接受。 |
| 2秒策略 | 服务端按先入库者固定拒绝 | **已删除服务端到达顺序2秒拒绝**；decider不读取上次传感器时间。客户端同类型最低2秒策略仍是后续采集约束；状态跳变/操作不受限。 |
| 授权区间 | accepted/ended两侧各加10分钟 | `AssignmentCaptureWindow` 是绝对epoch **[accepted_at, ended_at)**，不加钟差；开放轮次无结束界，未来时钟检查独立。`EventPacketStore.assignmentEpoch` 用 JDBC Timestamp.getTime，不再由这里把LocalDateTime按JVM默认时区重新转换。数据库/JDBC时区配置仍需部署测试。 |
| payload未知字段 | 旧反馈待明确 | 根/事件封套未知字段整包 `SCHEMA_FIELDS`；已知类型payload多/少字段由 `EventPayloads` 固定该事件 `PAYLOAD_INVALID`，仍持久化包与receipt。 |
| 本地测试剖面 | 负capturedAt曾只做安全整数检查；path错配用本地BINDING标签 | 据实际 `EventPacketSchema` 修正为capturedAt≥0、path orderId错配先SCHEMA；补上order UUID、serial/model/source格式。通用JSON负数仍合法，纬经度/高度的负数不能一律拒绝。 |

黄金向量文件与 a11d931 **字节完全相同**，但文档和验证脚本不同；不能因此说“合同没有变化”。当前九字段身份、JCS整数剖面、域分隔、C/H0/Hn/contentDigest保持一致，本轮重新针对当前快照独立运行。4包及非ASCII fixture均逐字节/逐摘要一致，未发现哈希差异。仍明确 `local_body_sha256` 是原bytes本地指纹，不是规范内容 contentDigest。

## 实际 Stage1 已有接口（仅源码核实，未服务联调）

以下都在 [固定 backend commit](https://github.com/UavPlatform/backend/tree/d924e34522119ae220b48dee03669752f3d8e331) 的 `drone-backend/src/main/java/com/uav/flight/` 下，证据文件hash见SOURCE。

### 上传 / 存储

- `EventPacketController.upload`：`POST /dispatch/pilot/orders/{orderId}/event-packets`，角色1；application/json，charset若指定必须UTF8；直接读取原bytes，上限262144。不由Jackson DTO重组，不要求connect/activeSerial/开播。
- `EventPacketSchema.parse`：15个根字段、4个事件字段、九字段身份，严格解析后重算H0/Hn/contentDigest；path与body orderId须一致。捕获时间非负安全整数。schema/hash错误无durable receipt。
- `EventPacketService.upload` + `EventPacketStore`：先锁订单，再锁链；使用现有dispatch_assignment历史飞手和轮次授权。原包、canonical、原bytes摘要、chainHash/contentDigest、固定逐事件结果、接受事实、receipt在同一事务中提交。固定receipt持久化与动态progress分开。
- 成功是现有 `Result` 信封 `data={schemaVersion,receipt,progress}`。receipt含receiptVersion/packetId/contentDigest/chainHash/durability/decidedAt/results；结果按事件序，ACCEPTED/DUPLICATE/REJECTED，含reason/sourcePacketId。
- 同packetId同规范内容返回最初receipt，不覆盖最初raw bytes；不同内容409并保留冲突证据。同eventId同归属内容可DUPLICATE，异归属/内容逐事件拒绝；缺前包本身不会永久拒绝事件。
- `repair` 经 `linksAfter` 以64条**元数据**页续链，不把后继正文整批读入内存。contiguous不越洞，分叉进度与receipt分离；projectionRevision仍0。
- 限流600/60秒；文档规定401认证恢复后原包重试、408/429/5xx保留原包，429 Retry-After秒。**客户端调度、回执严格校验、释放正文、认证恢复和退避本 PR 没有接入。**

### 对账

- `EventPacketController.reconcile`：`POST /dispatch/pilot/orders/{orderId}/event-reconciliation`，也是原JSON bytes与角色1。
- `EventReconciliationSchema`：9键全部出现，validationVersion/cursor/missingCursor/prefix允许null；from/to正整数且跨度≤500；limit先按long检1..200后转Int；prefix最多64项。cursor与missingCursor是独立正整数高度，不是自造opaque字符串。
- `EventReconciliationService`：链须属于path订单与历史链飞手；validationVersion:null取当前版本，指定版本变化409 / `EVENT_RECONCILIATION_VERSION_CHANGED`，message含current，不给混合版本残页。entries按高度返回，nextCursor是首个未返回高度，下一页含该高度；缺洞另用missingCursor分页。
- 响应仍 `Result` 信封，data含schemaVersion/validationVersion/chainId/请求区间/entries/nextCursor/missingRanges/missingCursor/contiguousHeight+Hash/highestReceivedHeight/integrity/projectionRevision/commonPrefixHeight。共同前缀只沿已验证连续历史，高洞/分叉后的相同hash不算。

### 明确没有实现

没有客户端POST、HTTP MockWebServer或真实服务联调；没有链投影、全换机截止结单、丢记录恢复声明、计费扩展、图传取票接口或通知。RECORDING_CUTOFF作为事件type存在，**不等于截止结单API已实现**。未删除任何旧业务路径或切换双轨；未来各功能独立PR。

## 实质契约缺口：只同步，不假冻结

1. **后端main仍明写未冻结。** OpenAPI只有结构快照，Event DTO/VO没有required列表、整数边界/模式/枚举也不完整，payload/prefix是 `{}`。prevPacketId只写type:string、未声明nullable，但真实首包必须显式null；对账四个nullable必填键也没机器表达。不能据生成模型的nullable默认值放宽实际合同。
2. **已实测最小编码反例**：用默认Gson将当前首包解析成生成DTO后再序列化，必填 `prevPacketId:null` 消失；对账DTO的validationVersion/cursor/missingCursor/prefix也被省略。测试明确记录这是不合实际严格合同的codec，不是成功往返。生产应独立严格编码/校验与持久化固定bytes；不拿Gson DTO往返充当原包重试/JCS。
3. 生成器新增单独 `modelOnlyEndpoints`：生成12个Stage1 schema闭包，但**不注册为已经调用**；现有calledEndpoints/ApiPaths调用表仍14条，Retrofit/DI/业务代码均未改。全部DTO只作未冻结结构模型，未使用其构造生产请求。OpenAPI缺失不在本地伪补成“已冻结线上schema”。
4. 当前全文快照也带来上游已有结构变化：新增2条Stage1路径、移除3条旧 `/rider/drone/{bind,list,unbind}` 文档路径、另有既有schema/描述变化；可达DispatchOrderVO新增deliveryNote。保持SSOT原样，不删客户端旧实现，也不假称这些删除是本PR业务迁移。
5. SDK型号/serialSource稳定映射、高度基准/速度轴向仍未真机验证；`FC30`只是向量值。payload拒绝层已由源码明确，但严格拒绝反例/固定receipt/乱序分叉的共享跨端响应向量还需补齐；当前只有离线数学与结构核验。
6. 截止结单未实现，空作业/空链、全换机链集合、跨终端和本地丢记录的完整性判断继续待后续合同；receipt或最高收到高度不等于完整同步。服务端原文删除策略未定，不能用现有180天旧sample清理推断新原包保留期限。
7. 快照内有历史说明“仍未Kotlin验证”与首段更新说明不一致；fixture status/note也保留旧稿。为保真未改上游快照，实际本轮验证证据以测试与来源SHA为准，不把该陈旧文字当API状态。

本轮因此优先交纯同步Draft PR；真实POST适配作为下一独立功能PR，不在契约同步中夹带生产实现。可针对已实现Stage1先做隔离HTTP适配与MockWebServer测试，但必须明确上述严格规则、未冻结状态与非服务联调，不能宣称投影/结单/推流全流程。

## 测试与环境

```sh
OPENAPI_SOURCE=/workspace/backend-d924e345.openapi.json node tools/openapi-sync.mjs
node tools/openapi-gen.mjs
node tools/openapi-gen.mjs --check
(cd openapi && sha256sum -c drone-backend.openapi.sha256)
JAVA_HOME=/home/box/jdk21 /home/box/bin/mvn -B -f tools/jcs-probe/pom.xml clean test
JAVA_HOME=/home/box/jdk21 /home/box/bin/mvn -B -f tools/event-store-tests/pom.xml test
```

- 契约/JCS JVM **59 tests**：旧基础17、旧a11d931 corpus16、当前main corpus19（共享16+3来源/结构差异）、实际app OpenApiContractTest7；全部0失败/错误/跳过。当前生成的ApiModels/ApiPaths实际作为JVM test source编译，app原门禁测试按原文件复制进target运行，未维护第二份源码副本。
- 存储 **17 tests**：恢复、封存/释放正文后幂等、receipt、SQL限量keyset、混单/分页等回归全过；生产存储代码未改。合计 **76 tests**。
- 首次重跑暴露旧探针对path错配仍期待BINDING，本轮改为实际SCHEMA_ORDER层；没有改黄金字节/哈希去迎合测试。先失败日志 `/workspace/djifly-stage1-contract-tests.log`，修后 `/workspace/djifly-stage1-contract-tests-final.log`；存储 `/workspace/djifly-stage1-storage-regression.log`。
- CI加入同一独立JVM共享向量门禁，不依赖DJI或真机；原有Gradle门禁不移除。远端CI状态另看PR，不据本地通过冒称远端绿。
- 测试依赖版本不变：Kotlin2.0.21/JCS1.1/Gson2.10.1/JUnit4.13.2；存储sqlite-jdbc3.46.1.0。App minSdk24 / JVM target11未变；本轮未跑Android24、R8、整App Gradle或真机，前次D8候选JAR转换证据不能扩大为这些验证。
