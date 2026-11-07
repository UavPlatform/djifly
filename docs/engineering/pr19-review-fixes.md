# PR19 两条持久化审查修复

2026-11-07（北京时间）。基线 96ea023；已读取 yx9926 的 [真实审查评论](https://github.com/UavPlatform/djifly/pull/19#issuecomment-6704619944)。只改隔离存储、回归测试和工程说明；不接网络、JCS生产编码或SDK采集，不改变线上合同。

## 先失败的独立回归

在**尚未修改生产存储代码**时，先加两个测试并执行：

```sh
JAVA_HOME=/home/box/jdk21 /home/box/bin/mvn -B -f tools/event-store-tests/pom.xml \
  '-Dtest=EventPacketStoreTest#identicalSealRetryAfterReleaseAndRestartStillSucceeds+pendingReadsOnlySqlFilteredBoundedRowsNotConfirmedHistory' test
```

结果：2 tests，1 failure + 1 error。原样 `seal → acknowledgeDurable → releaseBody → 关闭/重开SQLite → seal` 抛 `Immutable packet conflict or body already released`；SQL spy 证明原 pending 未使用 `receipt IS NULL + LIMIT`。不是仅靠阅读推断，也不通过制造真实OOM来测。

box证据：`/workspace/djifly-pr19-review-before.log`。

## 修复1：已释放末包的 seal 幂等

- 每包入库时同时保存 `local_body_sha256 = SHA256(原始body bytes)`。仅作本地不可变性校验，**不是**合同 `contentDigest`（后者是完整包JCS摘要），不发送、不解析生产包。
- 正文释放后仍比较 operation、packetId、serialNumber、raw-body fingerprint、原摘要。相同请求允许重复验证，但不恢复正文、不覆写摘要或receipt；改body/摘要/机身号或跨作业/订单则拒绝。
- 同事务保存**有序末包ID清单**与截止值；同截止值不能掩盖末包缺项、调序、替换为旧合法包或重复ID。重试必须同清单/内容，不因cutoff相等直接成功。
- 仅要沿用既定截止时，读取 `sealedCutoff(operationId)`，不通过提交空末包清单假装重试原请求。
- resume只撤销待结单截止/当前seal请求清单，保留所有历史包、指纹、摘要与回执；重新封存采用新清单。
- 此隔离实验库采用本地layout2（SQLite user_version）。旧实验库没有已释放正文的指纹，不能安全反推；版本不符明确受阻，**不静默删库/重建/伪造兼容摘要**。目前仍未接生产，无历史用户迁移承诺。

## 修复2：SQL限量未确认包查询

```
pending(operationId, afterPosition = 0, limit = 64)
WHERE operation_id=? AND receipt IS NULL AND local_position>?
ORDER BY local_position ASC LIMIT ?
```

- 使用持久化 `INTEGER PRIMARY KEY AUTOINCREMENT local_position`，不把可变化的隐式rowid、offset或云端高度当游标。
- 默认64、最多256；0/负数/超上限limit及负游标直接拒绝，没有 `LIMIT -1` 或默认无界读。SQL层过滤已确认历史，按operation+位置的partial index索引未确认包。
- 每页按返回最后位置前进，即使旧包本轮上传失败也继续扫后页；旧洞仍保存，下一轮从0重扫。不能只反复取默认首页并将失败当停止条件。此库仍不包含网络调度器。
- ack/release导致集合缩小也不用offset，防跳包；新增包自然排到后面。localPosition随重启保留，查询隔离作业，不混订单。
- Android adapter原本用 `rawQuery`将参数转String，JDBC用typed bind；测试完整分页分别跑typed与string参数，证实LIMIT/整数比较在SQLite中一致。返回本地位置统一按Number.toLong处理。额外将全部storage core + Android adapter对API35独立编译通过。

## 修后验证

```sh
JAVA_HOME=/home/box/jdk21 /home/box/bin/mvn -B -f tools/event-store-tests/pom.xml test
JAVA_HOME=/home/box/jdk21 /home/box/bin/mvn -B -f tools/jcs-probe/pom.xml test
```

- 存储 **17 tests passed，0 failures/errors**（原10条+新增7条）。覆盖释放/重启同seal、篡改/跨作业/跨订单/末包清单、SQL筛选限量、301包+交错他单的多页不漏不重、分页期间ack/新增、旧坏包不堵后页、默认64/最大256及非法值、重启游标/乱序确认、旧layout受阻保留数据。
- JCS独立探针 **17 tests passed，0 failures/errors**；未改JCS/网络合同。
- Android API35 core+adapter Kotlin独立编译：PASS；不是Android运行时或完整Gradle构建。
- box证据：`/workspace/djifly-pr19-review-after-final.log`、`/workspace/djifly-pr19-jcs-regression.log`、`/workspace/djifly-pr19-review-android-compile.log`。

## 保留边界

未做Android instrumented/真机、磁盘满/断电或真实上传联调；页大小限制按包数，单包字节上限仍需合同确定。`recoverOperation`是显式全历史诊断快照，仍不能用于无界生产日志/上传循环；本次pending已不调用它。未发任何GitHub评论或消息，未自动合并。
