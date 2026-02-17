[根目录](../CLAUDE.md) > **web**

# web 模块

## 模块职责

嵌入式 Web 服务器模块，基于 Ktor CIO 引擎提供 HTTP 服务器功能。负责：
1. 启动嵌入式 Web 服务器
2. 托管 `web-ui` 项目构建的静态前端文件（SPA）
3. 提供 CORS、压缩、SSE 等中间件支持
4. 通过 `module` 回调允许 `app` 模块注册自定义路由（API 端点）

## 入口与启动

本模块为 Android Library 模块（`me.rerere.rikkahub.web`），不包含独立入口。由 `app` 模块调用 `startWebServer()` 函数启动。

### startWebServer 函数

```kotlin
fun startWebServer(
    port: Int = 8080,
    module: suspend Application.() -> Unit
): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
```

- 默认监听 `0.0.0.0:8080`
- 自动安装以下 Ktor 插件：
  - `Compression` -- HTTP 压缩
  - `CORS` -- 跨域支持（anyHost, anyMethod）
  - `SSE` -- Server-Sent Events
  - `DefaultHeaders` -- 默认响应头
- 自动配置静态资源路由（`/` -> `static/index.html`，SPA 模式）
- 通过 `module` 参数接收外部路由注册

## 架构说明

### 目录结构

```
web/
├── src/main/java/me/rerere/rikkahub/web/
│   └── Entry.kt                # 唯一源文件，包含 startWebServer()
└── src/main/resources/
    └── static/                 # web-ui 构建产物托管目录
        ├── index.html          # SPA 入口
        └── assets/             # JS/CSS 等静态资源
```

### 静态文件来源

```
web-ui/ (React 项目)
  ↓ bun run build
web-ui/build/client/
  ↓ copy.ts 脚本
web/src/main/resources/static/
  ↓ Ktor staticResources
用户浏览器访问
```

### 与 app 模块的集成

`app` 模块通过 `module` 回调注册 API 路由：
- `GET /api/settings/stream` -- 设置 SSE 流
- `GET /api/conversations` -- 对话列表
- `GET /api/conversations/:id` -- 获取对话
- `GET /api/conversations/:id/stream` -- 对话 SSE 流
- `POST /api/conversations/:id/send` -- 发送消息
- `POST /api/files/upload` -- 文件上传
- `GET /api/files/path/*` -- 文件访问
- 其他 API 端点

## 对外接口

| 接口 | 说明 |
|------|------|
| `startWebServer(port, module)` | 启动嵌入式 Web 服务器 |

返回 `EmbeddedServer` 实例，调用方可通过 `.start()` / `.stop()` 控制生命周期。

## 关键依赖

| 依赖 | 用途 |
|------|------|
| `ktor-server-cio` | CIO 引擎（异步非阻塞） |
| `ktor-server-core` | Ktor 核心框架 |
| `ktor-server-cors` | CORS 支持 |
| `ktor-server-compression` | HTTP 压缩 |
| `ktor-server-sse` | Server-Sent Events |
| `ktor-server-default-headers` | 默认响应头 |
| `ktor-server-conditional-headers` | 条件请求头 |
| `ktor-server-auth` + `auth-jwt` | JWT 认证（`api` 暴露给 app 使用） |
| `ktor-server-content-negotiation` | 内容协商（`api` 暴露） |
| `ktor-server-status-pages` | 状态页（`api` 暴露） |
| `ktor-server-host-common` | 通用主机功能 |

## 测试与质量

- `src/test/java/me/rerere/rikkahub/web/ExampleUnitTest.kt` -- 示例单元测试
- `src/androidTest/` -- 仪器测试

缺口：缺少 Web 服务器启动/路由的集成测试。

## 常见问题 (FAQ)

**Q: 静态文件如何更新？**
A: 在 `web-ui/` 目录执行 `bun run build`，构建脚本会自动将产物复制到 `web/src/main/resources/static/`。

**Q: 为什么使用 Ktor CIO 而不是 Netty？**
A: CIO 是纯 Kotlin 实现的轻量级引擎，APK 体积更小，适合 Android 嵌入式场景。

**Q: CORS 为什么配置为 anyHost？**
A: Web UI 可能从不同设备访问（局域网内），需要允许跨域请求。

## 相关文件清单

- `web/build.gradle.kts` -- 构建配置
- `web/src/main/java/me/rerere/rikkahub/web/Entry.kt` -- 主要源码（1 个文件）
- `web/src/main/resources/static/` -- 静态前端资源
- `web/src/test/` -- 单元测试
- `web/src/androidTest/` -- 仪器测试

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成
