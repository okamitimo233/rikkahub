[根目录](../CLAUDE.md) > **highlight**

# highlight 模块

## 模块职责

代码语法高亮模块，基于 PrismJS 引擎（通过 QuickJS 在 Android 端运行 JavaScript）实现多语言代码的语法高亮。提供 Compose `@Composable` 组件 `HighlightText` 供 UI 层直接使用。

## 入口与启动

本模块为 Android Library 模块（`me.rerere.highlight`），不包含独立入口。通过 Compose 的 `CompositionLocal` 机制（`LocalHighlighter`）向 UI 层提供 `Highlighter` 实例。

## 架构说明

### 目录结构

```
highlight/src/main/java/me/rerere/highlight/
├── Highlighter.kt       # 高亮引擎核心（QuickJS + PrismJS）
└── HighlightText.kt     # Compose 高亮文本组件
```

### 高亮引擎 (Highlighter)

- 使用 QuickJS Android 引擎在单独的线程池中执行 PrismJS
- PrismJS 脚本从 `res/raw/prism` 资源加载
- 高亮结果解析为 `HighlightToken` 树结构
- `highlight(code, language)` 是挂起函数，通过 `suspendCancellableCoroutine` 桥接线程池

### Token 模型

```
HighlightToken (密封类)
├── Plain(content: String)           # 普通文本
└── Token (密封类)
    ├── StringContent(content, type, length)      # 单字符串 token
    ├── StringListContent(content, type, length)  # 字符串列表 token
    └── Nested(content: List<Token>, type, length) # 嵌套 token
```

### HighlightText 组件

- Compose `@Composable` 函数，自动处理代码高亮并渲染彩色 `AnnotatedString`
- 代码长度超过 4096 字符时跳过高亮，直接显示纯文本
- 支持自定义颜色主题 (`HighlightTextColorPalette`)
- 使用 `snapshotFlow` 监听代码/语言变化，自动重新高亮

### 支持的 Token 类型与默认颜色

| Token 类型 | 默认颜色 |
|-----------|---------|
| `keyword` | `#CC7832` (橙色) |
| `string` | `#6A8759` (绿色) |
| `number` | `#6897BB` (蓝色) |
| `comment` | `#808080` (灰色斜体) |
| `function` / `method` | `#FFC66D` (黄色) |
| `operator` / `punctuation` | `#CC7832` (橙色) |
| `class-name` / `property` | `#CB772F` (棕色) |
| `boolean` / `constant` | `#6897BB` (蓝色) |
| `tag` | `#E8BF6A` (黄色) |

## 对外接口

| 接口 | 说明 |
|------|------|
| `Highlighter(ctx: Context)` | 高亮引擎构造（初始化 QuickJS） |
| `Highlighter.highlight(code, language): List<HighlightToken>` | 挂起函数，执行高亮 |
| `Highlighter.destroy()` | 释放 QuickJS 上下文 |
| `LocalHighlighter` | Compose CompositionLocal，提供 Highlighter 实例 |
| `HighlightText(...)` | Compose 组件，渲染高亮代码文本 |
| `HighlightTextColorPalette` | 颜色主题配置 |

## 关键依赖

| 依赖 | 用途 |
|------|------|
| `quickjs` (via `libs.quickjs`) | JavaScript 运行时 |
| Compose BOM + Material3 | Compose UI |
| `kotlinx-serialization-json` | Token JSON 反序列化 |
| `kotlinx-coroutines-core` | 协程支持 |

## 测试与质量

- `src/test/java/me/rerere/highlight/ExampleUnitTest.kt` -- 示例单元测试
- `src/androidTest/` -- 仪器测试

缺口：缺少针对不同语言高亮结果的自动化测试。

## 常见问题 (FAQ)

**Q: 为什么使用 QuickJS 而不是原生实现？**
A: PrismJS 已有成熟的多语言语法定义，通过 QuickJS 运行可以直接复用，避免重复实现大量语言的语法规则。

**Q: 性能如何？**
A: 高亮在单独的线程池执行，不阻塞 UI 线程。超过 4096 字符的代码会跳过高亮以避免性能问题。

## 相关文件清单

- `highlight/build.gradle.kts` -- 构建配置
- `highlight/src/main/java/me/rerere/highlight/` -- 主要源码（2 个文件）
- `highlight/src/main/res/raw/prism` -- PrismJS 脚本资源
- `highlight/src/test/` -- 单元测试
- `highlight/src/androidTest/` -- 仪器测试

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成
