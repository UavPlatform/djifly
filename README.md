# djifly

Android 飞手端（DJI Mobile SDK v5）。DJI SDK 依赖来自本仓 `local-maven/` 与 `Mobile-SDK-Android-V5/`（默认使用缓存 AAR，`-PuseUxsdkSource=true` 时才从源码构建 UXSDK）。

## 构建与测试

| 目的 | 命令 |
|---|---|
| 编译（类型门禁） | `./gradlew :app:compileDebugKotlin` |
| 单元测试 | `./gradlew :app:testDebugUnitTest` |

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
