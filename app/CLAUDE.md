[根目录](../CLAUDE.md) > **app**

# app 模块

## 模块职责

主应用模块，包含 RikkaHub 的所有 UI 界面、ViewModel、数据层（Repository / Database / DataStore）、AI 聊天核心逻辑（消息生成、工具调用、消息变换）、MCP 协议支持、数据同步、Web 服务器集成等。这是整个项目的核心模块，依赖所有其他子模块。

## 入口与启动

- `RikkaHubApp.kt` -- Application 类，初始化 Koin DI
- `RouteActivity.kt` -- 主 Activity，配置 Navigation Compose 路由

应用启动流程：
```
RikkaHubApp (Application)
  -> Koin 初始化 (appModule + dataSourceModule + repositoryModule + viewModelModule)
  -> RouteActivity (MainActivity)
    -> Navigation Compose 路由系统
    -> ChatPage (默认首页)
```

## 架构说明

### 目录结构

```
app/src/main/java/me/rerere/rikkahub/
├── RikkaHubApp.kt              # Application 类
├── RouteActivity.kt            # 主 Activity + Navigation
│
├── data/                       # 数据层
│   ├── ai/                     # AI 相关逻辑
│   │   ├── GenerationHandler.kt       # 消息生成核心处理器
│   │   ├── GenerationPrompts.kt       # 生成提示词
│   │   ├── AILogging.kt              # AI 请求日志
│   │   ├── AIRequestInterceptor.kt    # OkHttp 请求拦截器
│   │   ├── RequestLoggingInterceptor.kt # 请求日志拦截器
│   │   ├── mcp/                       # MCP (Model Context Protocol) 支持
│   │   │   ├── McpManager.kt         # MCP 连接管理
│   │   │   ├── McpConfig.kt          # MCP 配置
│   │   │   ├── McpStatus.kt          # MCP 状态
│   │   │   └── transport/            # MCP 传输层
│   │   │       ├── SseClientTransport.kt
│   │   │       └── StreamableHttpClientTransport.kt
│   │   ├── prompts/                   # 内置提示词模板
│   │   │   ├── CompressPrompt.kt     # 上下文压缩
│   │   │   ├── LearningMode.kt       # 学习模式
│   │   │   ├── OcrPrompt.kt          # OCR 提示
│   │   │   ├── Suggestion.kt         # 对话建议
│   │   │   ├── TitleSummary.kt       # 标题摘要
│   │   │   └── Translation.kt        # 翻译
│   │   ├── tools/                     # AI 工具
│   │   │   ├── LocalTools.kt         # 本地工具（日期、计算等）
│   │   │   ├── MemoryTools.kt        # 记忆工具
│   │   │   └── SearchTools.kt        # 搜索工具
│   │   └── transformers/              # 消息变换器
│   │       ├── Transformer.kt                # 变换器接口
│   │       ├── TemplateTransformer.kt        # Pebble 模板
│   │       ├── ThinkTagTransformer.kt        # <think> 标签提取
│   │       ├── RegexOutputTransformer.kt     # 正则替换
│   │       ├── DocumentAsPromptTransformer.kt # 文档转文本
│   │       ├── Base64ImageToLocalFileTransformer.kt # Base64 图片本地化
│   │       ├── OcrTransformer.kt             # OCR
│   │       ├── PlaceholderTransformer.kt     # 占位符替换
│   │       └── PromptInjectionTransformer.kt # 提示注入（模式/知识库）
│   │
│   ├── api/                    # 外部 API 客户端
│   │   ├── RikkaHubAPI.kt     # RikkaHub 官方 API
│   │   └── SponsorAPI.kt      # 赞助者 API
│   │
│   ├── datastore/              # 偏好存储
│   │   ├── PreferencesStore.kt        # DataStore 设置管理
│   │   ├── DefaultProviders.kt        # 默认提供商配置
│   │   └── migration/                 # 设置迁移
│   │
│   ├── db/                     # Room 数据库
│   │   ├── AppDatabase.kt     # 数据库定义（版本 14）
│   │   ├── dao/               # DAO 接口
│   │   │   ├── ConversationDAO.kt
│   │   │   ├── MessageNodeDAO.kt
│   │   │   ├── MemoryDAO.kt
│   │   │   ├── GenMediaDAO.kt
│   │   │   └── ManagedFileDAO.kt
│   │   ├── entity/            # 数据库实体
│   │   │   ├── ConversationEntity.kt
│   │   │   ├── MessageNodeEntity.kt
│   │   │   ├── MemoryEntity.kt
│   │   │   ├── GenMediaEntity.kt
│   │   │   └── ManagedFileEntity.kt
│   │   └── migrations/        # 数据库迁移
│   │       ├── Migration_6_7.kt
│   │       ├── Migration_8_9.kt
│   │       ├── Migration_11_12.kt
│   │       └── Migration_13_14.kt
│   │
│   ├── export/                 # 数据导出
│   │   ├── ExportHooks.kt
│   │   └── ExportSerializer.kt
│   │
│   ├── files/                  # 文件管理
│   │   └── FilesManager.kt
│   │
│   ├── model/                  # 领域模型
│   │   ├── Assistant.kt       # 助手配置
│   │   ├── Conversation.kt    # 对话 + MessageNode
│   │   ├── Avatar.kt          # 头像
│   │   ├── Tag.kt             # 标签
│   │   ├── Leaderboard.kt     # 排行榜
│   │   └── Sponsor.kt         # 赞助者
│   │
│   ├── repository/             # 仓库层
│   │   ├── ConversationRepository.kt
│   │   ├── MemoryRepository.kt
│   │   ├── GenMediaRepository.kt
│   │   └── FilesRepository.kt
│   │
│   └── sync/                   # 数据同步
│       ├── S3Sync.kt          # S3 同步
│       ├── s3/                # S3 客户端
│       └── webdav/            # WebDAV 同步
│
├── di/                         # Koin 依赖注入
│   ├── AppModule.kt           # 应用级单例
│   ├── DataSourceModule.kt    # 数据源（DB、OkHttp、Retrofit 等）
│   ├── RepositoryModule.kt    # 仓库
│   └── ViewModelModule.kt    # ViewModel
│
├── service/                    # Android 服务
│   ├── ChatService.kt        # 聊天服务（消息发送、生成管理）
│   └── ConversationSession.kt # 对话会话
│
└── ui/                         # UI 层
    ├── activity/
    │   └── ShortcutHandlerActivity.kt
    ├── components/             # 通用 UI 组件
    │   ├── ai/               # AI 相关组件（ChatInput, ModelList, Pickers）
    │   ├── message/          # 消息渲染组件（ChatMessage, Actions, Branch 等）
    │   ├── richtext/         # 富文本（Markdown, LaTeX, CodeBlock）
    │   ├── nav/              # 导航组件
    │   └── easteregg/        # 彩蛋
    └── pages/                 # 页面
        ├── chat/             # 聊天页面（ChatPage, ChatVM, ChatList）
        ├── setting/          # 设置页面（Provider, Model, Display, TTS, Search, MCP, Web 等）
        ├── assistant/        # 助手管理（列表 + 详情配置）
        ├── history/          # 历史对话
        ├── backup/           # 备份（导入导出、S3、WebDAV）
        ├── imggen/           # 图片生成
        ├── translator/       # 翻译器
        ├── prompts/          # 提示词市场
        ├── debug/            # 调试页面
        ├── developer/        # 开发者页面
        ├── log/              # 日志查看
        ├── menu/             # 主菜单
        ├── share/            # 分享处理
        └── webview/          # WebView 页面
```

### 核心流程

#### 消息生成流程

```
用户发送消息 (ChatVM / ChatService)
  -> InputMessageTransformer 管道（模板、OCR、文档转文本、占位符、提示注入）
  -> Provider.streamText() 调用 AI API
  -> OutputMessageTransformer 管道（ThinkTag、Regex、Base64图片本地化）
  -> 工具调用处理（Tool approval -> 执行 -> 递归生成）
  -> 保存到数据库
```

#### 消息变换器 (Transformer) 系统

**输入变换器** (`InputMessageTransformer`)：
- `TemplateTransformer` -- Pebble 模板引擎，注入时间、日期等变量
- `DocumentAsPromptTransformer` -- 将文档附件解析为文本
- `OcrTransformer` -- 图片 OCR 文字识别
- `PlaceholderTransformer` -- 占位符替换
- `PromptInjectionTransformer` -- 提示注入（模式 prompt / lorebook 知识库）

**输出变换器** (`OutputMessageTransformer`)：
- `ThinkTagTransformer` -- 提取 `<think>` 标签转为 Reasoning part
- `RegexOutputTransformer` -- 正则替换助手回复
- `Base64ImageToLocalFileTransformer` -- Base64 图片保存为本地文件

### 依赖注入 (Koin)

4 个 DI 模块：

| 模块 | 职责 | 关键注入 |
|------|------|---------|
| `appModule` | 应用级单例 | Highlighter, TTSManager, ChatService, WebServerManager, Firebase |
| `dataSourceModule` | 数据源 | SettingsStore, AppDatabase, OkHttpClient, ProviderManager, McpManager |
| `repositoryModule` | 仓库层 | ConversationRepository, MemoryRepository, FilesManager |
| `viewModelModule` | ViewModel | ChatVM, SettingVM, HistoryVM, AssistantVM 等 12 个 VM |

### 页面清单

| 页面 | ViewModel | 说明 |
|------|-----------|------|
| ChatPage | ChatVM | 主聊天页面（消息列表、输入、抽屉） |
| SettingPage | SettingVM | 设置主页 |
| SettingProviderPage | SettingVM | AI 提供商配置 |
| SettingModelPage | SettingVM | 模型管理 |
| SettingDisplayPage | SettingVM | 显示设置 |
| SettingTTSPage | SettingVM | TTS 配置 |
| SettingSearchPage | SettingVM | 搜索服务配置 |
| SettingMcpPage | SettingVM | MCP 配置 |
| SettingWebPage | SettingVM | Web 服务器设置 |
| SettingFilesPage | SettingVM | 文件管理 |
| AssistantPage | AssistantVM | 助手列表 |
| AssistantDetailPage | AssistantDetailVM | 助手详情配置 |
| HistoryPage | HistoryVM | 历史对话搜索 |
| BackupPage | BackupVM | 备份与恢复 |
| ImgGenPage | ImgGenVM | 图片生成 |
| TranslatorPage | TranslatorVM | 翻译器 |
| PromptPage | PromptVM | 提示词市场 |
| DebugPage | DebugVM | 调试工具 |
| DeveloperPage | DeveloperVM | 开发者选项 |
| LogPage | -- | 日志查看 |
| MenuPage | -- | 主菜单 |

## 对外接口

本模块为应用入口模块，不对外暴露 API。通过 `web` 模块提供 HTTP API 端点。

## 关键依赖

### 子模块依赖

| 模块 | 用途 |
|------|------|
| `:ai` | AI Provider 接口与消息模型 |
| `:web` | Web 服务器 |
| `:document` | 文档解析 |
| `:highlight` | 代码高亮 |
| `:search` | 搜索服务 |
| `:tts` | 文本转语音 |
| `:common` | 通用工具 |

### 主要第三方依赖

| 依赖 | 用途 |
|------|------|
| Jetpack Compose + Material3 | UI 框架 |
| Navigation Compose | 页面导航 |
| Koin | 依赖注入 |
| Room + KSP | 数据库 ORM |
| DataStore | 偏好存储 |
| OkHttp + Retrofit | HTTP 客户端 |
| Ktor Client | HTTP 客户端（备选） |
| Coil | 图片加载 |
| Pebble | 模板引擎 |
| Firebase (Analytics/Crashlytics/RemoteConfig) | 分析与崩溃报告 |
| MCP Kotlin SDK | Model Context Protocol |
| jmDNS | mDNS 发现 |
| JLatexMath | LaTeX 渲染 |
| Lucide Icons | 图标库 |

## 数据模型

### Room 数据库（版本 14）

| 实体 | 说明 |
|------|------|
| `ConversationEntity` | 对话记录 |
| `MessageNodeEntity` | 消息节点（支持分支） |
| `MemoryEntity` | AI 记忆条目 |
| `GenMediaEntity` | 生成的媒体（图片等） |
| `ManagedFileEntity` | 托管文件记录 |

Schema 文件位于 `app/schemas/` 目录。

### 领域模型

- `Assistant` -- 助手配置（系统提示、模型参数、工具、记忆选项、正则变换、提示注入）
- `Conversation` -- 对话（MessageNode 列表 + 元数据）
- `MessageNode` -- 消息节点（支持分支，包含多个 UIMessage）

## 测试与质量

- `src/androidTest/java/.../data/db/migrations/Migration_11_12_Test.kt` -- 数据库迁移测试
- `src/androidTest/java/.../ExampleInstrumentedTest.kt` -- 仪器测试

缺口：ViewModel 层、Repository 层、Transformer 管道缺少单元测试；UI 组件缺少 Compose 测试。

## 常见问题 (FAQ)

**Q: 如何新增 AI 提供商？**
A: 在 `ai` 模块实现 `Provider` 接口，然后在 `ProviderManager` 中注册。UI 配置页面在 `SettingProviderPage` 中添加。

**Q: 如何新增消息变换器？**
A: 实现 `InputMessageTransformer` 或 `OutputMessageTransformer` 接口，然后在 `GenerationHandler` 中注册到管道。

**Q: 数据库迁移如何处理？**
A: 在 `data/db/migrations/` 目录新建迁移类，在 `DataSourceModule.kt` 的 Room Builder 中注册。Schema 文件自动生成到 `app/schemas/`。

## 相关文件清单

- `app/build.gradle.kts` -- 构建配置
- `app/src/main/java/me/rerere/rikkahub/` -- 主要源码（~120+ 个文件）
- `app/src/main/res/` -- 资源文件（布局、字符串、图片等）
- `app/schemas/` -- Room 数据库 Schema（版本 1-14）
- `app/src/androidTest/` -- 仪器测试
- `app/baselineprofile/` -- Baseline Profile 配置

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成
