[根目录](../CLAUDE.md) > **ai**

# ai 模块

## 模块职责

AI SDK 抽象层，为 RikkaHub 提供统一的多 AI 提供商接口。封装了与 OpenAI、Google (Gemini/Vertex AI)、Anthropic (Claude) 等 AI 服务的通信逻辑，包括文本生成、流式响应、图片生成、向量嵌入等能力。本模块采用无状态设计，所有 Provider 方法均需传入对应的 `ProviderSetting` 参数。

## 入口与启动

本模块为 Android Library 模块（`me.rerere.ai`），不包含独立入口。通过 `app` 模块的 Koin DI 注入 `ProviderManager` 实例供全局使用。

## 架构说明

### 核心层次

```
ai/
├── core/           # 核心数据模型（MessageRole, Tool, Usage, Reasoning）
├── provider/       # Provider 抽象与实现
│   ├── Provider.kt           # Provider 接口定义
│   ├── ProviderManager.kt    # Provider 注册与管理器
│   ├── ProviderSetting.kt    # Provider 配置密封类（OpenAI/Google/Claude）
│   ├── Model.kt              # 模型数据类
│   └── providers/            # 具体 Provider 实现
│       ├── OpenAIProvider.kt
│       ├── GoogleProvider.kt
│       ├── ClaudeProvider.kt
│       ├── ProviderMessageUtils.kt  # 消息转换工具
│       ├── openai/           # OpenAI 具体实现
│       │   ├── OpenAIImpl.kt
│       │   ├── ChatCompletionsAPI.kt
│       │   └── ResponseAPI.kt
│       └── vertex/           # Vertex AI 支持
│           └── ServiceAccountTokenProvider.kt
├── registry/       # 模型注册表（自动识别模型能力）
│   ├── ModelRegistry.kt
│   └── ModelDsl.kt
├── ui/             # UI 层消息抽象
│   ├── Message.kt            # UIMessage / UIMessagePart / MessageChunk
│   └── Image.kt              # 图片生成结果
└── util/           # 工具类
    ├── SSE.kt                # SSE 事件源实现
    ├── Json.kt               # JSON 序列化配置
    ├── KeyRoulette.kt        # API Key 轮换
    ├── ErrorParser.kt        # 错误解析
    ├── FileEncoder.kt        # 文件编码
    ├── Request.kt            # 请求工具
    └── Serializer.kt         # 自定义序列化器
```

### Provider 接口

`Provider<T : ProviderSetting>` 定义了以下核心方法：

| 方法 | 说明 |
|------|------|
| `listModels(providerSetting)` | 列出可用模型 |
| `getBalance(providerSetting)` | 查询余额 |
| `generateText(providerSetting, messages, params)` | 非流式文本生成 |
| `streamText(providerSetting, messages, params)` | 流式文本生成（返回 `Flow<MessageChunk>`） |
| `generateEmbedding(providerSetting, params)` | 向量嵌入生成 |
| `generateImage(providerSetting, params)` | 图片生成 |

### ProviderSetting 类型

密封类，支持三种 Provider 配置：

- `ProviderSetting.OpenAI` -- 支持自定义 baseUrl、chatCompletionsPath、Response API 模式
- `ProviderSetting.Google` -- 支持 Google AI 和 Vertex AI 两种模式
- `ProviderSetting.Claude` -- Anthropic Claude API

### UIMessage 系统

`UIMessage` 是平台无关的消息抽象，包含：

- `role`: SYSTEM / USER / ASSISTANT / TOOL
- `parts`: 消息内容列表，支持多种类型：
  - `Text` -- 文本内容
  - `Image` -- 图片（URL 或 base64）
  - `Video` / `Audio` -- 多媒体
  - `Document` -- 文档附件
  - `Reasoning` -- 推理过程
  - `Tool` -- 工具调用与结果（合并了旧版 ToolCall/ToolResult）
- `annotations`: 消息注解（如 URL 引用）
- `usage`: Token 使用统计

`MessageChunk` 用于流式响应的增量合并，通过 `UIMessage + MessageChunk` 操作符实现流式拼接。

### ModelRegistry

基于 DSL 的模型能力注册表，通过 token 匹配自动识别模型的：
- 输入/输出模态（文本、图片）
- 能力（工具调用、推理）
- 覆盖模型系列：GPT-4o/5、Gemini 2.x/3.x、Claude 3.5/4.x、DeepSeek、Qwen、通义等

## 对外接口

- `Provider` 接口 -- 所有 AI 提供商的统一抽象
- `ProviderManager` -- 提供商注册与获取
- `UIMessage` / `UIMessagePart` / `MessageChunk` -- 消息模型
- `ModelRegistry` -- 模型能力查询
- `Tool` / `InputSchema` -- 工具定义

## 关键依赖

| 依赖 | 用途 |
|------|------|
| `:common` | 通用工具库 |
| `okhttp` + `okhttp-sse` | HTTP 客户端与 SSE 支持 |
| `kotlinx-serialization-json` | JSON 序列化 |
| `kotlinx-coroutines-core` | 协程支持 |
| `kotlinx-datetime` | 日期时间处理 |
| Compose BOM + Material3 | Compose UI 支持（用于 `@Composable` 注解） |

## 数据模型

核心数据类：
- `Model` -- 模型定义（modelId, displayName, type, modalities, abilities, builtInTools）
- `TextGenerationParams` -- 文本生成参数（model, temperature, topP, maxTokens, tools, thinkingBudget）
- `TokenUsage` -- Token 使用统计（promptTokens, completionTokens, cachedTokens）
- `ReasoningLevel` -- 推理等级（OFF, AUTO, LOW, MEDIUM, HIGH）

## 测试与质量

测试目录：`src/test/java/me/rerere/ai/`

| 测试文件 | 覆盖范围 |
|---------|---------|
| `ModelRegistryTest.kt` | 模型注册表匹配逻辑 |
| `provider/providers/ClaudeProviderMessageTest.kt` | Claude 消息格式转换 |
| `provider/providers/GoogleProviderMessageTest.kt` | Google 消息格式转换 |
| `provider/providers/openai/ChatCompletionsAPIMessageTest.kt` | OpenAI Chat Completions 消息转换 |
| `provider/providers/openai/ResponseAPIMessageTest.kt` | OpenAI Response API 消息转换 |
| `provider/providers/ProviderMessageUtilsTest.kt` | 消息工具函数 |
| `ui/MessageTest.kt` | UIMessage 合并/截断逻辑 |
| `util/JsonTest.kt` | JSON 序列化 |

另有 `src/androidTest/` 下的仪器测试。

## 相关文件清单

- `ai/build.gradle.kts` -- 构建配置
- `ai/src/main/java/me/rerere/ai/` -- 主要源码（21 个文件）
- `ai/src/test/java/me/rerere/ai/` -- 单元测试（9 个文件）
- `ai/src/androidTest/` -- 仪器测试
- `ai/proguard-rules.pro` -- ProGuard 规则
- `ai/consumer-rules.pro` -- 消费者 ProGuard 规则

## 变更记录 (Changelog)

- **2026-02-17** -- 初始文档生成
