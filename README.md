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

```
openapi/drone-backend.openapi.json    # vendor 进来的规格（本仓唯一事实源副本）
openapi/drone-backend.openapi.sha256  # 对账文件
tools/openapi-sync.mjs                # vendor + 写 sha256
tools/openapi-gen.mjs                 # 生成（--check 只校验漂移）
app/src/main/java/com/fuwaki/djifly/platform/network/generated/ApiPaths.kt   # 端点常量（Retrofit 注解直接用）
app/src/main/java/com/fuwaki/djifly/platform/network/generated/ApiModels.kt  # 契约模型
```

| 目的 | 命令 |
|---|---|
| vendor 规格 | `node tools/openapi-sync.mjs` |
| 生成契约代码 | `node tools/openapi-gen.mjs` |
| 校验生成物未过期 | `node tools/openapi-gen.mjs --check` |
| 契约门禁 | `./gradlew :app:testDebugUnitTest`（含 `OpenApiContractTest`） |
| 类型门禁 | `./gradlew :app:compileDebugKotlin` |

约定：

1. `PlatformApiService` 的 `@GET/@POST/@PUT/@DELETE` 路径一律用 `ApiPaths` 常量，**字面量必须与 Retrofit 的 baseUrl 解析方式一致**（本仓历史上不带前导 `/`，不要擅自改动）；
2. 请求/响应体优先使用生成的契约模型；UI 视图模型可以保留，但取值要来自契约模型，后端改字段名即编译失败；
3. 契约变更后重跑 sync + gen 并评审 diff，生成物必须提交入库。
