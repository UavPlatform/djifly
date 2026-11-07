# Backend a11d931：Kotlin 独立合同验证（未冻结）

2026-11-07，北京时间。仅测试/反馈；无生产请求编码器、API、SDK、持久化实现或 app 依赖改动。后端仓库未克隆。

## 固定来源与复现

- [backend PR57](https://github.com/UavPlatform/backend/pull/57)，固定 **a11d9316e97d582e7460a13000314c873f065bb5**；通过 GitHub 连接器按该 ref 读取，不以浮动 PR head 为输入。
- 原文件：[DRAFT.md](https://github.com/UavPlatform/backend/blob/a11d9316e97d582e7460a13000314c873f065bb5/spec/event-contract/DRAFT.md)、[golden-vectors.json](https://github.com/UavPlatform/backend/blob/a11d9316e97d582e7460a13000314c873f065bb5/spec/event-contract/golden-vectors.json)、[verify-golden.mjs](https://github.com/UavPlatform/backend/blob/a11d9316e97d582e7460a13000314c873f065bb5/spec/event-contract/verify-golden.mjs)。
- 本地只读快照在 `tools/jcs-probe/src/test/resources/backend-a11d931/`。测试固定快照 SHA256（不是协议摘要，也不是 Git blob SHA）：

| 文件 | 本地快照 SHA256 |
| --- | --- |
| DRAFT.md | `999020874207ead51692e29133c2d9fa6d24b75abdd53e2e8ce6aa8a82ff35cc` |
| golden-vectors.json | `08228ecea348d292db2cdf688112b0d2aae8ade3f1ac6daaaccf4e29e1b2a9b4` |
| verify-golden.mjs | `e499d44c91567600ac1b844532abbd01443b180a06aedf4372e96548b663d8fa` |

```sh
JAVA_HOME=/home/box/jdk21 /home/box/bin/mvn -B -f tools/jcs-probe/pom.xml test
JAVA_HOME=/home/box/jdk21 /home/box/bin/mvn -B -f tools/event-store-tests/pom.xml test
```

JCS **33 tests**（既有17 + 本轮16），存储 **17 tests**；均 0 failure / 0 error / 0 skip。日志分别是 box 上 `/workspace/djifly-backend-a11d931-validation-final.log`、`/workspace/djifly-backend-a11d931-storage-regression.log`。最初导入时本地误保留工具输出尾围栏造成 JSON 解析错误，已修正快照导入；不是后端向量差异，不拿这次工具包装错误作为合同反例。

## 真正独立核验了什么

`BackendA11d931GoldenTest` 从输入 hex 解码原 bytes，再以 Kotlin test-only 严格入口 + erdtman JCS1.1 serializer + JDK MessageDigest 重新计算，逐字节对照固定期望。没有复制/运行后端 Node 或 Java 实现来产生期望值。检查过 Node 脚本：其四包循环主要校验提供的 canonical/without bytes 与摘要，只有 canonicalFixtures 明确重新生成规范字节；本轮 Kotlin 同时重算四包的规范全文及去 chainHash 正文。

四条包全部一致：规范全文、只去 `chainHash` 的 C、提取九字段 identity 的规范值、H0、chainHash、完整规范包 contentDigest，以及原 bytes SHA256。首包/续包前驱、换机新锚点也吻合。**未发现实际字节或摘要差异**。

| 向量 | H0 | chainHash | contentDigest |
| --- | --- | --- | --- |
| height1-position | `55589764b485229a8d0a93f6ea7b1abac5bc56417ab58cbf9c816172e5ff2213` | `711841518845f5a7527c5b16ded73d5016d8509a7ca0d7752ba21ced47323a26` | `b09ec0b701acb44fb080fefb02ca36bdb2de01d0912846594785436a13d4e17c` |
| height1-same-canonical-different-bytes | 同首包 | 同首包 | 同首包 |
| height2-altitude | 同首包 | `d46050c19f4c283884810e4e64f685bb5c3001dc25dd5959f7f3df17b04d6747` | `e754657c3b9a3f8c299d261d0db92ce0ed00cb0072a86a8b0c43bb19cf0d38ca` |
| other-airframe-product-serial | `0669b2f631886d598f43b0646f346b68f483650c45e96d74ca84fae758feba0b` | `ea56e62864856a2a2bff188022a45de3d093e5fbdc2f630d756651521be045e5` | `1f3127085e028334c074874aa4b7ecb65433928a6559eae4211432feec46778e` |

共享非 ASCII fixture 也逐字节一致：

```
input:     7b227a223a312c2273223a22c3a92fe280a8e280a9227d
canonical: 7b2273223a22c3a92fe280a8e280a9222c227a223a317d
```

即 `é`、`/`、U+2028、U+2029 保留原 UTF8，不改成行分隔符转义；不作 NFC。包的对象字段顺序/空白差异不改 contentDigest；数组顺序变化会改变 C。第二个向量原 bytes SHA256 是 `ea522e2863194253610c2b9fd815fced7879ba808d1be85a7e879bd0fe71cdd5`，明显不等于 contentDigest。本地持久层 `local_body_sha256` 仍只校验原 bytes，绝不改名或拿它充当 contentDigest。客户端重试仍使用最初固定 bytes，而非规范重组包。

## 严格解析反例与拒绝层（本地测试标签，不是线上错误码）

`PinnedDraftProbe` 是新的 test-only 草案剖面，**不是完整生产 schema 验证器**；不取代既有合成 feasibility probe，不决定 payload 单位/业务结果。

| 层 | 本轮覆盖 |
| --- | --- |
| STRICT_JSON：规范化前 | 非法 UTF8 `c3 28`、UTF8 编码代理项 `ed a0 80`、BOM、键/值孤立 surrogate；合法 surrogate pair 可过。重复 null key、解码后 `a` / `\u0061` 重复、嵌套重复均拒绝。`01`、`-0`、小数、指数、NaN/Infinity、越安全整数界 token 均拒绝。`\uＦＦＦＦ`、`\u０000`、`\u066٠` 在解码前拒绝，合法 `\u00E9` 可过。 |
| SCHEMA：根/事件 envelope | 未知根字段、缺失必填、字段类型错误、首包缺 prevPacketId 与续包 prevPacketId:null、assignmentVersion:null/0、不支持的 schema/hashVersion、非 ASCII/大写哈希或 UUID、空 events、同包重复 eventId。显式 null 不被当缺失或 0。 |
| BINDING：已知身份模拟 | 在结构检查通过后逐项比对现有链九字段及 path orderId；schema/hashVersion 不支持先是 schema 错，而不伪称绑定错。未测试服务器现有链锁/DB/鉴权。 |
| SELF_HASH：原包自洽 | 由本包九字段重算首包 H0、由 raw32(prevChainHash)+C 重算 Hn；改变事件顺序/正文不能复用原 hash。缺前包本身不在此永久拒绝，高度2的自哈希可以独立验证。 |
| 逐事件语义/动态进度：留给服务器 | 未知事件类型或位置越范围不被伪装成 lexical 拒绝；本探针仅放过 envelope，不生成 receipt、不声称 ACCEPTED。实际时间窗、授权、节流、跨包 eventId、投影/补洞必须服务器测试。 |

安全整数 ±9007199254740991 可精确规范化；±9007199254740992 不可。capturedAt 的整数 transport 检查与有效采集窗口分开：即使 epoch 值在安全整数范围内，也不代表授权/当前时间语义合法。黄金向量时间是固定历史值，**不能拿到当前接口上传后期待 ACCEPTED**。

JCS 标准允许将负零规范成0，但**本草案明确拒绝 -0 token**；两层不冲突。候选 JCS 库直接入口仍有既有 duplicate-null/01/Unicode/UTF8/舍入缺陷；必须严格解析后才用于合法整数序列化，绿色测试不批准其生产入口。

## 给后端的精炼待确认点

1. **身份/轮次**：九字段和首包/续包公式本轮已验证；assignmentVersion 使用已有接单历史，不造新编号。无有效轮次不采集、不补0。同作业同来源/型号/SN重连恢复原链；来源变化不能把 FC SN 与 Product SN 当别名，重复SN也不证明物理同机。SDK 对 FC30/serialSource/型号稳定映射仍需独立核实，向量 `FC30` 不是兼容支持证据。
2. **语义冻结边界**：草案技术“过去7天”会使长离线首次上传的历史事件固定 REJECTED，不能因为后来补洞改 receipt；请确认与长期离线保全事实的目标是否相容。先入库者节流也使乱序补洞的固定事件结果与上传顺序有关；明确这是接受的语义，而非把投影延迟误当永久拒绝。本轮不擅自修改这些规则。
3. **仍缺跨端反例黄金向量**：四包+一规范 fixture 已过，但重复key、非ASCII hex、极限整数、归属错配等只有本地 test-only 策略断言，没有对后端严格 parser/错误层的独立执行证明。建议共享带预期拒绝层/原因的固定输入；另补固定 receipt（跨包同eventId同/异内容与归属）、乱序前后 receipt不变、分叉后进度/投影资格样例。不能把本轮说成 API 联通或后端已验收。
4. **授权/时间**：历史飞手可补旧轮次、recordingSourceId非凭证的方向明确；服务器 LocalDateTime 用 JVM 默认时区转 epoch，需固定部署/迁移时区解释，免得作业窗口随环境改变。客户端源ID丢失/换登录不允许悄悄续旧作业身份。
5. **截止未实现**：草案明确不做截止结单，故全换机 cutoff 集合、空作业/空链、跨终端/本地记录丢失的完整同步判定仍未验收；不能凭有 receipt 或 highestReceivedHeight 声称全量同步。记录源不排他不等于允许遗漏另一源历史；后续 cutoff 必须封定全部已知链且不追尖端。
6. **payload 与耐久保留**：当前单位/高度基准/速度轴向是提案，未 SDK/真机冻结；未知字段在 type-specific payload 内究竟整包 schema 错还是逐事件语义错，请给显式例子。服务器删除策略未定，客户端已释放的后包正文依赖服务器保留用于补洞续链，不得仅靠动态 progress 等同于完整耐久承诺。

## Android/依赖与交付限制

候选依赖不变：Kotlin2.0.21、JCS1.1、Gson2.10.1、JUnit4.13.2 均只在 JVM test module；存储回归使用 sqlite-jdbc3.46.1.0。App minSdk24 / target JVM11。沿用上轮 JCS Java8 bytecode + Gson D8 min-api24 转换通过的有限证据；**本轮没有 Android24运行、R8、全App构建或真机测试**。本轮 probe 使用 JVM 测试辅助 API，不宣称可直接移植到 Android。

开工远端读取发现 [djifly PR19](https://github.com/UavPlatform/djifly/pull/19) 已由他方于 **2026-11-07 17:34:54 北京时间**合并，head仍为 c025112，merge commit `8d3c10ad3393306506ec57254306dc2c6ff8b156`，不是开放 Draft。本轮未合并任何PR，也未重开/新建PR或发评论。测试/反馈保存在原工作区分支的本地提交；未把新提交推入已合并分支冒称“同Draft更新”，由父代理决定后续PR承载方式。
