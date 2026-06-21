# FlightScreen `LayoutNode should be attached to an owner` 排查总结

日期：2026-04-03

## 结论

已经足以在应用代码层面定位问题。

这次崩溃的直接触发点不在：

- `FlightScreen` 的沉浸式布局本身
- `FpvWidget`
- 顶部状态条这种 UI 结构本身
- `ServerConnectionIndicatorDot` 这个“圆点视觉元素”本身

问题集中在旧版 [app/src/main/java/com/fuwaki/djifly/ui/widget/compose/ServerConnectionStatus.kt](/run/media/fuwaki/Workspace/djifly/app/src/main/java/com/fuwaki/djifly/ui/widget/compose/ServerConnectionStatus.kt) 的实现方式。

更准确地说，旧实现把下面几类职责耦合进了同一个很小的状态组件里：

- 状态展示
- 点击交互
- `remember` 本地状态
- 条件式 `collectAsState()`
- `Dialog` 弹层创建/销毁

这个组合会在 `FlightScreen` 进入和布局更新过程中触发 Compose 的异常测量路径，最终出现：

```text
java.lang.IllegalStateException: LayoutNode should be attached to an owner
```

## 已验证的排查结果

### 1. 稳定基线

把 `FlightScreen` 简化为：

- 全屏 `FpvWidget`
- 纯 Compose overlay
- 无 `ServerConnectionStatus` 组件
- 无额外 DJI 控件恢复

用户确认：稳定。

### 2. 恢复旧版 `ServerConnectionIndicatorDot`

只恢复顶部服务器状态圆点，其他都不动。

用户确认：崩溃复现。

这说明问题已经不需要依赖那批 DJI 控件才会出现。

### 3. 用本地内联 `Box` 替代圆点组件

在 `FlightScreen` 顶部保留同样的圆点视觉效果，但改成最简单的：

- `Surface`
- `Box`
- `background`
- 无点击
- 无 `remember`
- 无 `Dialog`
- 无消息流收集

用户确认：稳定。

这说明“顶部多一个状态圆点”不是问题，问题在旧组件内部逻辑。

### 4. 把 `ServerConnectionIndicatorDot` 改为纯展示组件再接回

将 `ServerConnectionStatus.kt` 中的：

- `ServerConnectionIndicatorDot`
- `ServerConnectionChip`

都改成纯展示组件，只保留颜色和文本展示，不再内部持有：

- `showHistory`
- `clickable`
- `collectAsState()`
- `Dialog`

然后再把安全版 `ServerConnectionIndicatorDot` 接回 `FlightScreen`。

用户确认：仍然稳定。

## 根因判断

### 已经可以确认的应用层根因

旧版 `ServerConnectionStatus` 的问题模式是：

1. 小组件内部自己持有弹层开关状态 `showHistory`
2. 点击后才在 `if (showHistory)` 分支中开始 `collectAsState()` 订阅 `wsCommunicationState.messages`
3. 同时在这个分支内创建 `Dialog`
4. 该组件又被放在 `FlightScreen` 顶部 overlay 这种进入时会快速参与重组和测量的位置

这会把“状态展示组件”和“独立弹层宿主”耦合在一起，导致 Compose 在某些时机对已经脱离 owner 的布局节点继续测量，最终抛出异常。

### 还不能 100% 证明的部分

目前还不能严格证明是 Compose 框架内部哪一条具体实现路径有 bug。

但这已经不影响修复，因为从应用代码角度看，问题组件已经被稳定地隔离出来了：

- 旧实现会复现
- 纯展示实现不会复现

这已经足够作为工程结论。

## 旧实现中的高风险点

旧版 `ServerConnectionStatus.kt` 中，下面这类模式是高风险的：

```kotlin
var showHistory by remember { mutableStateOf(false) }

Box(
    modifier = modifier.clickable { showHistory = true }
)

if (showHistory) {
    val messages by wsCommunicationState.messages.collectAsState()
    WebSocketMessageHistoryDialog(
        messages = messages,
        onDismiss = { showHistory = false }
    )
}
```

风险不在某一行单独存在，而在于这些行为被塞进同一个微型组件中。

## 当前修复策略

当前采用的安全策略是：

1. `ServerConnectionIndicatorDot` 和 `ServerConnectionChip` 仅负责展示状态
2. 不在组件内部创建 `Dialog`
3. 不在组件内部条件式收集消息流
4. 需要弹层时，由更稳定的父级 screen 管理弹层状态和数据收集

## 设计原则

后续类似组件应遵守：

- 展示组件尽量无状态或仅持有非常轻的视觉状态
- 弹层由 screen 或稳定容器统一托管
- Flow 收集不要藏在临时出现/消失的小组件分支里
- 小状态点、小 chip 不应该自己兼任“消息历史弹层宿主”

## 后续建议

如果后面还要恢复“点击服务器状态查看消息历史”的功能，建议改成下面的结构：

1. `FlightScreen` 持有 `showWsHistory`
2. `FlightScreen` 顶层稳定地收集 `wsCommunicationState.messages`
3. `ServerConnectionIndicatorDot` 只暴露 `onClick`
4. `FlightScreen` 顶层决定是否显示 `Dialog`

也就是把“展示”和“弹层/数据源”分层。

## 本次排查的工程结论

这次问题已经定位到可操作层面：

- 问题文件：`ServerConnectionStatus.kt`
- 问题类型：状态展示组件内部耦合了弹层和条件式消息流收集
- 当前修复：已改为纯展示组件
- 当前状态：用户已验证稳定

这可以视为本次 `FlightScreen` 崩溃问题的有效定位结果。
