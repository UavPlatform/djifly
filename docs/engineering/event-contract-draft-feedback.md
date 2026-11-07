# 后端事件草案：客户端可行性反馈（待冻结）

2026-11-07（北京时间）。只评估草案和独立测试；未生成生产包、未新增生产 DTO/API、未固定传感器单位。沿用 Draft PR #19。产品规则不在此扩展。

## 实测结果与 Android 边界

- 项目 `app/build.gradle.kts`：minSdk **24**，Java/Kotlin target **11**。
- 测试候选 `io.github.erdtman:java-json-canonicalization:1.1`：JAR major **52 (Java8)**；POM 无运行时传递依赖，只有 test JUnit；jdeps 仅 java.base（io/lang/math/util/regex）。
- Gson streaming **2.10.1** 作为测试严格扫描器；与 [Retrofit converter-gson 2.11.0 POM](https://repo.maven.apache.org/maven2/com/squareup/retrofit2/converter-gson/2.11.0/converter-gson-2.11.0.pom) 声明版本一致，不能据此声称实际 Gradle 解析结果已经核定。[Gson 官方](https://google.github.io/gson/) 列 2.10.1 支持 API19；这里只测试 streaming，不靠 Gson Kotlin DTO 反射约束。
- 本地 D8（build-tools35）以 `--min-api 24 --lib android-35/android.jar` 转换上述两个候选 JAR **成功、无告警**。这不是 Android24 运行时、R8 release 或整 App 构建验证。无 Android24 平台/设备测试结果；库尚未接入 app。
- SHA256 使用 `MessageDigest`，UTF8 使用明确 charset；可行实现不需要 Java17 `HexFormat`、API26 `java.util.Base64`、Java11新增 HTTP/文件便捷 API。Java target11 不等于 Android24 拥有全部 Java11 类。

### 严格入口不能只调用 JCS 库

`tools/jcs-probe` 的 Kotlin/JUnit **17 tests 通过**（12条解析/规范化 + 5条草案公式）；全部 test-only，app 无新增依赖。

**真实复现候选库 1.1 的入口缺陷**：

| 输入 | 库直接行为 | test-only 严格扫描器 |
| --- | --- | --- |
| `{"a":null,"a":1}` | 静默成为 `{"a":1}`；重复 key 检查有 null 绕过 | 拒绝，含 `a`/`\u0061` 等价 key 与嵌套重复 |
| `{"a":01}` | 接受并规范成 `{"a":1}`，但不是合法 JSON 数字 | 拒绝 |
| `{"s":"\ud800"}` | 返回含单独代理字符的 String，UTF8 编码会替换字符 | 拒绝键和值中的非法代理；合法 surrogate pair 保留 |
| 非法UTF8字节 `c3 28` 出现在字符串 | byte[] 构造器静默解码成 U+FFFD | `CharsetDecoder REPORT` 拒绝；BOM也拒绝 |
| 未知字段 | 正常排序并保留，不懂 schema | 测试 toy root allowlist 拒绝，真实每层/每事件 payload schema 尚未实现 |
| `9007199254740993` | binary64 舍入，和 `9007199254740992` 产生相同规范数值 | 原始 numeric token 用 BigDecimal/BigInteger 精确验安全整数后才交 JCS |

结论：该坐标**不能直接充当生产严格解析器**。候选 RFC8785 serializer 也必须在整套冻结黄金向量通过后才选用，不因名字/引用是“参考实现”就放行。不引入自制生产 JCS 替代实现。

RFC8785 与本轮实测一致：

- `-0`/`-0.0` 规范化为 `0`；若业务必须保留负零则不是 JCS 数字的能力，不可私改算法。
- 不作 Unicode NFC/NFD；`é` 与 `e+组合重音` **不同内容**，但 `é` 与 `\u00e9` 是相同字符串。
- 对象名按 **UTF16 code unit** 排序（不是 locale/UTF8/codepoint），数组顺序保持；缺失与显式 null 不同。
- 非有限数、非法JSON、重复解码后 key、非法UTF8/代理字符、未知字段分别严格检查；不能先反序列化成 Map/DTO 丢掉信息再检查。
- test-only integer profile 拒绝分数/超范围/溢出指数/极小数，在库处理前拒绝。任何字段若日后允许一般 Double，必须另跑数值全向量；当前测试不证明候选库完整一般浮点合规。测试内64KB/深度32/token长度64只是安全试验上限，不是生产合同。

参考：[RFC8785 §3.1、3.2.2、3.2.3、Appendix B](https://www.rfc-editor.org/rfc/rfc8785)，[候选库源码](https://github.com/erdtman/java-json-canonicalization/blob/master/src/main/java/org/erdtman/jcs/JsonCanonicalizer.java)。源码 parseObject 用 put返回null检查重复、byte[] 构造器默认UTF8替换非法输入，与实测对应。

## 新 contentDigest 定义与独立公式向量

按最新草案测试：

```
C = JCS(完整包去掉且仅去掉 chainHash)
H0 = SHA256(UTF8("UAV-EVENT-ANCHOR-v1\n") || JCS(chainIdentity))
Hn = SHA256(UTF8("UAV-EVENT-PACKET-v1\n") || raw32(prevChainHash) || C)
contentDigest = SHA256(JCS(完整包，含 chainHash))
```

本地 Node/V8+crypto 先生成固定 synthetic fixture，Kotlin/Java候选库+MessageDigest核对：

- fake identity=`{"fixtureIdentity":"not-a-production-chain"}`，**明确不是待定 chainIdentity 字段方案**。
- H0=`d5d33098d023a8b12089c8ed477fa81970d2f166f60a6abca8bba7abe5ccf4c0`
- H1=`afa5af9e67c80379c07f9e0a9efb5e9e1d1fd43dd2504b798ffcafa20541ae4b`
- contentDigest=`e558a5d9f246ccad66bb2e0bd207e84b167192b385ec8c0be2b5e7dbbae570ee`

完整输入与 oracle 见 `tools/jcs-probe/fixture-vectors.cjs`，Kotlin 常量见 `JcsHashDraftTest.kt`。fixture payload只有 `testValue`，没有物理单位。验证对象键序变化 digest 相同，原bytes摘要不同；去chainHash的C摘要不等于contentDigest；修改chainHash/prevHash、用hex字符串替代raw32、把chainHash放入C、遗漏首包prevPacketId:null均不能误通过原向量。

服务器保存最初原bytes与固定结果，按完整规范包判同内容；客户端仍发送持久化原bytes，不重组。原bytes的本地文件校验值可另存，但不叫 contentDigest、不替代协议比较。服务端须分别重算本包Hn与contentDigest（互不替代），且校验 body内 prevChainHash 对应 raw32输入；链前包存在后再检查真实连续性，不只是回显。

**待后端共同黄金向量**：冻结identity全部字段/模型身份字段名、assignmentVersion、payload schema与缩放后，给实际合法首包/第二包/换机链、规范全文UTF8 hex/C/H0/Hn/contentDigest，另给字段错配、Unicode、整数、重复key和固定receipt样例。当前synthetic向量只证明公式理解和候选路径一致，不是线上合同冻结。

## 给后端的最小补齐清单

1. **chainIdentity最小边界**：建议绑定现有 `orderId + assignmentVersion + recordingSourceId + chainId + serialNumber + serialSource + 稳定modelCode`，明确schema/hash版本是否固定入identity。所有重复包字段与identity逐项相等，path orderId也须相等；显示名不入稳定键。chainId作为UUID不应允许同一作业同一身份任意开出平行链来绕过连续性；明确服务端如何由包确定identity并约束一对一，不新增预登记编号。
2. **assignmentVersion确实已有**：当前 OpenAPI `AdminDispatchOrderAssignment` 明确重新派单递增；`DispatchOrderVO` 字段int64、generated Kotlin为 nullable Long。`FlightConnectDTO` 字段有 `exclusiveMinimum:0`，却不在required；旧 `FlightRecorder:79` 和 `TaskViewModel:81` 用 `?:0L` 兜底。新合同不能照搬0代表未知：需把有效现有轮次作为必要作业身份，缺失先刷新订单/阻止新采集，不造新编号。按采集时已持久化轮次鉴权历史补传，不能拿重试时新轮次覆盖旧包。
3. **断连/重连/重复SN**：同作业+来源+机型身份的同一SN恢复原链与持久化尖端，断连不是新链理由；换机另链。同机回来应定位旧链（明确同SN不能随意新chainId）。serialSource在链内固定，FC/Product来源切换或型号矛盾不能静默改旧identity或把两种SN混同；先标识冲突/重新核实，不假装是正常连续。规则不做机身排他锁，也不承诺仅凭重复SN能识别两台物理飞机。
4. **安全整数/时间**：schema/hashVersion、height、assignmentVersion、epoch-ms和所有缩放传感器数值均应明确取值范围，JSON数字在±(2^53−1)内，并在binary64舍入前从token精确校验。height/轮次应正数，capturedAt须整毫秒及合理时窗；极大标识用string。整数是否允许 `1.0/1e3/-0` 的输入写法需定（JCS本身会规范化），不能据 `Long` 类型或OpenAPI int64放任精度丢失；节流用单调时钟，capturedAt不能代替授权证明。
5. **记录源与登录归属**：recordingSourceId只是持久化来源ID，不是授权凭证。服务器由现有接单轮次绑定的飞手/作业核验；改token/退出登录不能迁移作业所有权。客户端已存在作业不能被新登录者续写；丢本地记录/新终端不得新建空记录冒称旧历史完整。noBackupFilesDir避免自动迁移冒充，服务器仍需核验来源与已知链集合。
6. **幂等与固定event结果**：同packetId+同完整规范包必须返回最初固定receipt，不能重放时把最初ACCEPTED改成DUPLICATE；DUPLICATE可用于首次处理时eventId已在其他包存在且内容一致。eventId同内容的比较范围/作用域、同eventId改内容、同包重复eventId、部分拒绝原因码需写清。事件语义拒绝与整包结构/未知字段/坏hash错误应区分，坏结构不能借partial acceptance掩盖。
7. **缺前包不永久拒绝**：本包内自洽hash与可独立鉴权/校验事件可先固定结果并发完整durable receipt；不要把MISSING_PREV写成REJECTED。序列相关资格/投影延迟，contiguous不得越洞；前包到达后重算投影/推进或标分叉，但不改固定receipt。明确后来链冲突对既有可视遥测、计费投影的资格边界，不能因ACCEPTED就算“连续/可结单”。
8. **receipt/progress/cutoff**：固定receipt应含packetId+contentDigest+chainHash+明确RAW_AND_RESULTS_COMMITTED，且全部事件都有固定结果；动态progress/validationVersion不混进receipt幂等比较。cutoff清单无重复chainId，核验当前作业来源下所有本地/云端换机链；重试沿用固定清单。末包只有cutoffId避免hash循环。明确从未采集的空作业 `chains:[]`、创建却无事件的空链是否允许（建议没事件不建链，height0不伪装首包）；任何已知链或记录丢失不能被空清单盖掉。完整同步与有效计费证据独立，本文不定义无飞行/永久失败的费用规则。
9. **输入约束**：按schemaVersion列出每层字段/type-specific payload允许集、必填/nullable、整数上下界、包字节/事件数/深度限制、错误码；UUID/64hex先按小写格式拒绝不偷偷改写，serial是否trim/case-fold必须在进入不可变身份前确定，不用显示名做规范化。

## 复现与本轮边界

```sh
JAVA_HOME=/path/to/jdk21 mvn -B -f tools/jcs-probe/pom.xml test
node tools/jcs-probe/fixture-vectors.cjs
JAVA_HOME=/path/to/jdk21 mvn -B -f tools/event-store-tests/pom.xml test
# 可选候选库打包探针；把PATH/JAVA_HOME指向JDK，路径按本机实际安装：
d8 --min-api 24 --lib /path/to/android-35/android.jar --output /existing/output-dir \
  /path/to/java-json-canonicalization-1.1.jar /path/to/gson-2.10.1.jar
```

本轮仅 tools/jcs-probe 和工程反馈文档；无生产编码/网络/SDK逻辑改动，没有替后端冻结identity或单位，没有实机或真实接口通过声明。
