[根目录](../CLAUDE.md) > **common**

# common 模块

## 模块职责

通用工具库模块，提供 HTTP 通信、缓存系统、Android 上下文工具、日志记录等基础设施。作为所有其他模块共享的底层依赖，不包含业务逻辑。

## 入口与启动

本模块为 Android Library 模块（`me.rerere.common`），不包含独立入口。通过 Gradle 依赖以 `api` 方式暴露给上层模块使用。

## 架构说明

### 目录结构

```
common/src/main/java/me/rerere/common/
├── android/              # Android 平台工具
│   ├── ContextUtil.kt    # Context 扩展（临时目录、缓存目录）
│   └── Logging.kt        # 日志系统（文本日志 + 请求日志）
├── cache/                # 缓存系统
│   ├── CacheEntry.kt     # 缓存条目（含过期时间）
│   ├── CacheStore.kt     # 缓存存储接口
│   ├── FileIO.kt         # 文件 I/O 工具
│   ├── KeyCodec.kt       # 缓存键编解码
│   ├── LruCache.kt       # LRU 缓存实现
│   ├── PerKeyFileCacheStore.kt  # 按键文件缓存
│   └── SingleFileCacheStore.kt  # 单文件缓存
└── http/                 # HTTP 通信工具
    ├── AcceptLang.kt     # Accept-Language 头构建器
    ├── Json.kt           # JsonElement 扩展函数
    ├── JsonExpression.kt # JSON 表达式求值引擎
    ├── Request.kt        # OkHttp Call 协程扩展
    └── SSE.kt            # SSE 事件流封装
```

## 对外接口

### 缓存系统

- `CacheStore<K, V>` -- 缓存存储接口（loadEntry, saveEntry, remove, clear, keys）
- `LruCache<K, V>` -- 线程安全的 LRU 缓存，支持：
  - 可配置容量与 TTL 过期策略
  - 可选驱逐时删除持久化条目
  - 可选从 Store 预加载
- `PerKeyFileCacheStore` -- 每个缓存键一个文件的持久化实现
- `SingleFileCacheStore` -- 所有缓存条目写入单文件的持久化实现

### HTTP 工具

- `Call.await()` -- 将 OkHttp `Call` 转为挂起函数（`suspendCancellableCoroutine`）
- `OkHttpClient.sseFlow(request)` -- 将 OkHttp SSE 封装为 `Flow<SseEvent>`
  - `SseEvent.Open` / `SseEvent.Event` / `SseEvent.Closed` / `SseEvent.Failure`
- `evaluateJsonExpr(input, root)` -- JSON 表达式求值引擎，支持：
  - 路径导航：`field.sub`, `array[0]`
  - 字符串字面量、数字字面量
  - 算术运算：`+`, `-`, `*`, `/`
  - 字符串连接：`++`
- `JsonObject.getByKey(key)` -- 便捷的 JSON 路径取值

### Android 工具

- `Context.appTempFolder` -- 应用临时目录
- `Context.getCacheDirectory(namespace)` -- 命名空间缓存目录

### 日志系统

- `Logging` 单例 -- 应用级日志管理器
  - `log(tag, message)` -- 文本日志
  - `logRequest(entry)` -- HTTP 请求日志
  - `getRecentLogs()` / `getTextLogs()` / `getRequestLogs()` -- 日志查询
  - 保留最近 100 条日志记录

### Accept-Language 构建器

- `AcceptLanguageBuilder` -- 构建 HTTP Accept-Language 头
  - `fromAndroid(context)` -- 从 Android 系统语言创建
  - `fromJvmSystem()` -- 从 JVM 系统语言创建
  - `withLocales(locales)` -- 自定义语言列表
  - 支持 RFC 7231 q 值递减、通用语言码展开、去重

## 关键依赖

| 依赖 | 用途 |
|------|------|
| `okhttp` + `okhttp-sse` | HTTP 客户端与 SSE |
| `kotlinx-serialization-json` | JSON 序列化 |
| `kotlinx-coroutines-core` | 协程支持 |
| `kotlinx-datetime` | 日期时间 |
| `commons-text` | Apache Commons 文本工具 |
| `floatingx` / `floatingx-compose` | 悬浮窗支持 |

## 测试与质量

- `src/test/java/me/rerere/common/ExampleUnitTest.kt` -- 示例单元测试
- `src/androidTest/` -- 仪器测试

缺口：缓存系统和 JSON 表达式求值引擎缺少专项单元测试。

## 相关文件清单

- `common/build.gradle.kts` -- 构建配置
- `common/src/main/java/me/rerere/common/` -- 主要源码（14 个文件）
- `common/src/test/` -- 单元测试
- `common/src/androidTest/` -- 仪器测试

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成
