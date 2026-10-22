# djifly

Android 飞手端（DJI Mobile SDK v5）。DJI 依赖优先走本仓 **`local-maven/`**（已入库，大 AAR 用 Git LFS；升级 SDK 时在本机跑 `tools/populate-local-maven-from-gradle.sh` 后提交）。`-PuseUxsdkSource=true` 时从 `Mobile-SDK-Android-V5/` 源码构建 UXSDK。

## 构建与测试

| 目的 | 命令 |
|---|---|
| 编译（类型门禁） | `./gradlew :app:compileDebugKotlin` |
| 单元测试 | `./gradlew :app:testDebugUnitTest` |

### CI（GitHub Actions）

`push` → `master` 与所有 `pull_request` 会跑 [`.github/workflows/ci.yml`](.github/workflows/ci.yml) 作业 **`djifly-tests`**：

1. `sha256sum -c openapi/drone-backend.openapi.sha256`
2. `node tools/openapi-gen.mjs --check`
3. `./gradlew :app:testDebugUnitTest`

建议在仓库 **Branch protection** 里将 `djifly-tests` 设为必需状态检查。

当前 **约 19 个 JVM 单测类**（门禁、WS 协议、送达规则、契约等）；`androidTest` 仅示例 instrumented 测试，**未纳入 CI**（需模拟器/真机）。DJI/TRTC 全链路仍须人工真机验收。

## OpenAPI 契约（消费侧）

后端契约由 backend 仓导出（`backend/spec/openapi/drone-backend.openapi.json`），本仓 **vendor + sha256 对账 + 生成物入库**。

### 自动同步机制

**策略变更**：已从「手动 vendor + 对账」改为 **后端发布 + 机器人自动开 PR**（详见 UavPlatform/docs 仓库 ADR）。

- 后端契约变更时发布 Release `contract-<shortsha>`（附件 `drone-backend.openapi.json` + `.sha256`），并触发本仓 `repository_dispatch` 事件。
- [`.github/workflows/contract-sync.yml`](.github/workflows/contract-sync.yml) 自动下载契约、校验 sha256、生成代码，在固定分支 `bot/openapi-sync` 开或更新 PR（目标 `master`）。
- 触发方式：`repository_dispatch`（后端发布）、每日定时巡检、`workflow_dispatch`（手动触发）。
- PR 会提示契约变更的影响范围；若调用清单（`tools/openapi-gen.mjs` 的 `calledEndpoints`）因契约变化而失效，需人工更新。

**配置要求**（可选）：

- 若要让机器人 PR 自动触发 CI，需配置 GitHub App token：
  - `secrets.CONTRACT_BOT_APP_ID` — GitHub App ID
  - `secrets.CONTRACT_BOT_PRIVATE_KEY` — GitHub App 私钥
- 未配置时回退到 `GITHUB_TOKEN`（PR 不会自动触发 CI，需手动重跑）。

### 契约文件结构

```
openapi/drone-backend.openapi.json    # vendor 进来的规格（本仓唯一事实源副本）
openapi/drone-backend.openapi.sha256  # 对账文件
tools/openapi-sync.mjs                # vendor + 写 sha256（支持 OPENAPI_SOURCE 环境变量）
tools/openapi-gen.mjs                 # 生成（--check 只校验漂移）
app/src/main/java/com/fuwaki/djifly/platform/network/generated/ApiPaths.kt   # 端点常量（Retrofit 注解直接用）
app/src/main/java/com/fuwaki/djifly/platform/network/generated/ApiModels.kt  # 契约模型
.github/workflows/contract-sync.yml   # 自动同步 workflow
```

### 本地操作（手动同步已废弃，仅用于调试）

| 目的 | 命令 |
|---|---|
| vendor 规格 | `OPENAPI_SOURCE=/path/to/spec.json node tools/openapi-sync.mjs` |
| 生成契约代码 | `node tools/openapi-gen.mjs` |
| 校验生成物未过期 | `node tools/openapi-gen.mjs --check` |
| 契约门禁 | `./gradlew :app:testDebugUnitTest`（含 `OpenApiContractTest`） |
| 类型门禁 | `./gradlew :app:compileDebugKotlin` |

### 约定

1. `PlatformApiService` 的 `@GET/@POST/@PUT/@DELETE` 路径一律用 `ApiPaths` 常量，**字面量必须与 Retrofit 的 baseUrl 解析方式一致**（本仓历史上不带前导 `/`，不要擅自改动）；
2. 请求/响应体优先使用生成的契约模型；UI 视图模型可以保留，但取值要来自契约模型，后端改字段名即编译失败；
3. 契约变更由机器人自动同步并开 PR，人工评审 diff 后合并；**不要手动修改生成物**。
