# Integrate DeepSeek Provider with PoW WASM

## Goal

将 DeepSeek Web Chat 功能集成到 RikkaHub 中。DeepSeek 需要通过 WASM 执行 PoW (Proof of Work) 验证才能正常使用 API。

## What I already know

### 源项目分析 (DS2API)

**项目性质**: DS2API 是一个 DeepSeek Web API 代理服务，将 DeepSeek Web Chat 转换为 OpenAI/Claude/Gemini 兼容格式。

**PoW 流程**:
1. 调用 `https://chat.deepseek.com/api/v0/chat/create_pow_challenge` 获取挑战
2. 挑战格式: `{algorithm, challenge, salt, signature, target_path, difficulty, expire_at}`
3. 使用 WASM 的 `wasm_solve` 函数计算答案
4. 将挑战+答案编码为 Base64 放入 `x-ds-pow-response` header

**WASM 文件**:
- 文件: `sha3_wasm_bg.7b9ca65ddd.wasm`
- 来源: Rust + wasm-bindgen 编译（非 Go）
- 导出函数:
  - `wasm_solve(retptr, ch_ptr, ch_len, prefix_ptr, prefix_len, difficulty)` - PoW 求解
  - `__wbindgen_add_to_stack_pointer` - 栈指针操作
  - `__wbindgen_export_0` - 内存分配
  - `__wbindgen_export_2` - 内存释放

**DeepSeek API**:
- Endpoint: `https://chat.deepseek.com/api/v0/chat/completion`
- 认证: `Authorization: Bearer <token>` + `x-ds-pow-response` header
- 模拟客户端: `DeepSeek/1.6.11 Android/35`

### RikkaHub 架构
- **ai 模块**: 提供商抽象层，包含 `Provider<T>` 接口和 `ProviderSetting` 密封类
- **现有提供商**: OpenAI、Google、Claude 三种类型
- **注册机制**: `ProviderManager` 负责注册和获取 Provider 实例
- **设置 UI**: `ProviderConfigure.kt` 处理提供商配置界面

### WASM 运行时研究
- **Chasm**: Kotlin Multiplatform WASM 运行时，支持 Android
- **版本**: `io.github.charlietap.chasm:chasm:1.2.0`
- **特性**: 支持 Wasm 3.0 规范，可调用 wasm-bindgen 生成的模块

## Assumptions (已验证)

1. ~~用户有一个现成的 Go 语言 PoW 实现~~ → 实际是 Rust WASM
2. 该 PoW 算法可以通过 WASM 导出函数接口调用 ✅
3. 该提供商的 API 格式与 OpenAI 兼容 → DS2API 已做适配，需转换格式
4. 不需要 WASI 系统调用（纯计算型 WASM）✅

## Open Questions

~~1. Go 项目详情~~ → 已解决：Rust WASM，函数接口已明确

~~2. API 格式~~ → 已解决：DeepSeek 有自己的响应格式，需转换

**待确认**:

1. **Token 获取方式**:
   - 方案 A: 用户手动提供 DeepSeek Web token（简单）← **Phase 1 采用**
   - 方案 B: 在 App 内登录获取 token（需要实现登录流程）← **Phase 2 再做**

2. ~~账号管理~~ → Phase 2 范畴

## Phased Delivery

> **原则**: 先把 PoW Provider 核心链路跑通，再扩展登录和账号管理。

| Phase | 范围 | 状态 |
|-------|------|------|
| **Phase 1** | 手动 Token + PoW Provider 核心（本 PRD） | Active |
| **Phase 2** | App 内登录流程、Token 刷新、凭证安全存储 | Planned |
| **Phase 3** | 多账号管理、Token 轮换 | Planned |

---

## Requirements

### 功能需求

**后端 (ai 模块)**:
- [ ] 新增 `DeepSeekProviderSetting` 持久配置（仅 token + models）
- [ ] 新增 `DeepSeekProvider` 作为编排层
- [ ] 集成 WASM 运行时 (Chasm)，懒加载 + 单例缓存
- [ ] 内置 PoW WASM 模块 (复用 ds2api 的 WASM)
- [ ] 在 API 请求前执行 PoW 计算
- [ ] 定义协议 DTO（challenge、completion 请求/响应、流式 chunk）
- [ ] 统一流式/非流式响应转换层 (DeepSeek → UIMessage)
- [ ] PoW 失败单次自动重试策略

**前端 (app 模块)**:
- [ ] 提供商配置 UI（token 输入，使用 `FormItem`）
- [ ] PoW 计算期间的 UI 状态反馈（区别于普通生成状态）
- [ ] PoW 相关错误的特定错误提示（区别于网络错误）
- [ ] Reasoning 内容展开/折叠交互

### 技术需求

- [ ] 复制 WASM 文件到 `app/src/main/assets/`
- [ ] Chasm 运行时集成（懒加载，首次使用时初始化，复用实例）
- [ ] `wasm_solve` 调用必须在 `Dispatchers.Default` 上执行（CPU 密集型，禁止阻塞主线程）
- [ ] WASM 计算支持协程取消（绑定 `viewModelScope`）
- [ ] 为所有 DeepSeek 协议定义 `@Serializable` DTO，避免手搓 `JsonObject`
- [ ] 异常传播风格与项目现有 Provider 一致（不滥用 `Result.failure`）

---

## Technical Approach

### 架构设计

> **核心原则**: `DeepSeekProvider` 只做编排，实际工作委托给职责单一的组件。

```
┌─────────────────────────────────────────────────────────────┐
│                       RikkaHub App                          │
├─────────────────────────────────────────────────────────────┤
│  DeepSeekProviderSetting (持久配置)                          │
│  - token: String                                            │
│  - models: List<Model>                                      │
├─────────────────────────────────────────────────────────────┤
│  DeepSeekProvider : Provider<DeepSeekProviderSetting>       │
│  (编排层 - 协调以下组件)                                     │
│  ├─ DeepSeekChallengeClient                                 │
│  │   └─ getChallenge(token) → PowChallenge                  │
│  ├─ DeepSeekPowSolver (WASM)                                │
│  │   └─ 懒加载 + 单例缓存 WASM 模块                          │
│  │   └─ solve(challenge, prefix, difficulty) → answer        │
│  │   └─ 必须在 Dispatchers.Default 上执行                    │
│  ├─ DeepSeekPowEncoder                                      │
│  │   └─ encode(challenge, answer) → Base64 header value     │
│  └─ DeepSeekMessageMapper                                   │
│      └─ UIMessage ↔ DeepSeek 请求格式                       │
│      └─ DeepSeek 响应 → MessageChunk (统一流式/非流式)       │
└─────────────────────────────────────────────────────────────┘
```

### ProviderSetting 设计

> **原则**: `ProviderSetting` 只放持久配置。Challenge、answer、expire_at 等单次请求状态禁止持久化。

```kotlin
@Serializable
@SerialName("deepseek")
data class DeepSeek(
    override var id: Uuid = Uuid.random(),
    override var enabled: Boolean = true,
    override var name: String = "DeepSeek",
    override var models: List<Model> = emptyList(),
    var token: String = "",           // 用户手动提供的 token (Phase 1)
) : ProviderSetting()
```

### 协议 DTO 定义

> **原则**: 为每个协议定义明确的数据类，使用 `kotlinx.serialization`，不在多处手写 JSON。

```kotlin
// PoW Challenge
@Serializable
data class PowChallenge(
    val algorithm: String,
    val challenge: String,
    val salt: String,
    val signature: String,
    @SerialName("target_path") val targetPath: String,
    val difficulty: Double,
    @SerialName("expire_at") val expireAt: Long,
)

// Completion 请求体
@Serializable
data class DeepSeekCompletionRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val stream: Boolean = true,
    // ... 其他参数
)

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String,
)

// 流式 Chunk 响应
@Serializable
data class DeepSeekStreamChunk(
    val p: String,             // JSON path
    val o: String? = null,     // operation (APPEND, etc.)
    val v: JsonElement,        // value (多态)
)

// 片段类型
@Serializable
data class DeepSeekFragment(
    val type: String,          // THINK / THINKING / RESPONSE
    val content: String,
)
```

### WASM 运行时设计

> **原则**: 懒加载 + 单例缓存。不缓存 challenge 结果，只缓存模块/运行时实例。

```kotlin
class DeepSeekPowSolver(private val context: Context) {
    // 懒加载: 首次使用时初始化, 之后复用
    private val wasmInstance by lazy {
        val bytes = context.assets.open("sha3_wasm_bg.wasm").readBytes()
        val store = store()
        val module = module(bytes)
        instance(store, module)
    }

    // 必须在 Dispatchers.Default 上调用
    suspend fun solve(
        challenge: String,
        prefix: String,
        difficulty: Double
    ): Long = withContext(Dispatchers.Default) {
        // 支持协程取消检查
        ensureActive()
        val result = invoke(wasmInstance, "wasm_solve", listOf(/* params */))
        // 解析结果
        resultValue
    }
}
```

**加载失败处理**: 模块加载失败时抛出 `DeepSeekPowException`，与网络错误区分。

### 响应转换层

> **原则**: 统一流式/非流式转换，不要各写一份解析逻辑。

```kotlin
/**
 * 统一的 DeepSeek 响应 → MessageChunk 转换器
 * 流式和非流式共用同一套映射规则
 */
class DeepSeekMessageMapper {
    // 映射规则 (共用)
    fun mapFragment(fragment: DeepSeekFragment): UIMessagePart = when (fragment.type) {
        "THINK", "THINKING" -> ReasoningPart(text = fragment.content)
        "RESPONSE" -> TextPart(text = fragment.content)
        else -> TextPart(text = fragment.content)
    }

    // 流式: DeepSeekStreamChunk → MessageChunk
    fun mapStreamChunk(chunk: DeepSeekStreamChunk): MessageChunk { ... }

    // 非流式: 完整响应 → UIMessage
    fun mapFullResponse(response: DeepSeekCompletionResponse): UIMessage { ... }
}
```

### DeepSeek API 调用流程

```kotlin
suspend fun streamText(
    setting: DeepSeekProviderSetting,
    messages: List<UIMessage>,
    params: TextGenerationParams
): Flow<MessageChunk> {
    // 1. 获取 PoW 挑战
    val challenge = challengeClient.getChallenge(setting.token)

    // 2. 计算 PoW 答案 (Dispatchers.Default, 支持取消)
    val answer = powSolver.solve(challenge)
    val powHeader = powEncoder.encode(challenge, answer)

    // 3. 发送请求
    val response = client.completion(
        token = setting.token,
        powResponse = powHeader,
        messages = messageMapper.toDeepSeekMessages(messages),
        model = params.model.modelId
    )

    // 4. 统一解析响应流
    return messageMapper.parseStreamResponse(response)
}
```

### 关键文件修改

| 文件 | 操作 | 内容 |
|------|------|------|
| `ai/.../ProviderSetting.kt` | 修改 | 添加 `DeepSeek` 子类（仅 token + models） |
| `ai/.../ProviderManager.kt` | 修改 | 注册 DeepSeekProvider |
| `ai/.../providers/DeepSeekProvider.kt` | 新建 | Provider 编排层 |
| `ai/.../providers/deepseek/DeepSeekChallengeClient.kt` | 新建 | Challenge 请求 |
| `ai/.../providers/deepseek/DeepSeekPowSolver.kt` | 新建 | WASM 调用封装（懒加载 + 单例） |
| `ai/.../providers/deepseek/DeepSeekPowEncoder.kt` | 新建 | Challenge + Answer → Base64 header |
| `ai/.../providers/deepseek/DeepSeekMessageMapper.kt` | 新建 | 统一响应转换（流式/非流式共用） |
| `ai/.../providers/deepseek/DeepSeekDto.kt` | 新建 | 协议 DTO 定义 |
| `app/.../ProviderConfigure.kt` | 修改 | 添加 DeepSeek 配置 UI（FormItem） |
| `app/build.gradle.kts` | 修改 | 添加 Chasm 依赖 |
| `app/src/main/assets/sha3_wasm_bg.wasm` | 新建 | WASM 模块 |

### 依赖添加

```kotlin
// app/build.gradle.kts (或 ai/build.gradle.kts, 取决于模块划分)
dependencies {
    implementation("io.github.charlietap.chasm:chasm:1.2.0")
}
```

---

## Error Handling

### 错误矩阵

| 错误场景 | 异常类型 | 处理策略 | 用户感知 |
|----------|----------|----------|----------|
| Challenge 接口网络失败 | IOException | 不重试，直接抛出 | "网络错误" |
| Challenge 接口返回异常 | `DeepSeekProtocolException` | 不重试，直接抛出 | "服务异常" |
| WASM 模块加载失败 | `DeepSeekPowException` | 不重试，直接抛出 | "PoW 初始化失败" |
| `wasm_solve` 计算失败 | `DeepSeekPowException` | 不重试，直接抛出 | "验证计算失败" |
| Token 无效 / 过期 | `DeepSeekAuthException` | 不重试，提示用户更新 | "Token 已失效，请重新配置" |
| Completion 返回 PoW 无效/过期 | `DeepSeekPowException` | **单次自动重试** | 用户无感知（重试成功）或 "验证失败" |

### PoW 失败重试策略

> **原则**: 只做一次自动恢复，不无限重试。

```
completion 请求失败 (PoW 无效/过期)
  → 重新获取 challenge
  → 重新计算 PoW
  → 重新发起 completion
  → 若仍失败 → 抛出异常，终止
```

### 异常类型定义

```kotlin
// 语义明确的异常，便于 UI 层区分展示
class DeepSeekAuthException(message: String) : Exception(message)
class DeepSeekPowException(message: String, cause: Throwable? = null) : Exception(message, cause)
class DeepSeekProtocolException(message: String) : Exception(message)
```

### 结构化日志

按项目 backend 规范，使用 `android.util.Log` + TAG：

| 日志项 | 级别 | 注意事项 |
|--------|------|----------|
| Challenge 获取成功/失败 | DEBUG / ERROR | 不打印 token 原文 |
| PoW 计算耗时 | DEBUG | 记录毫秒数 |
| Completion 首次请求是否命中 PoW 错误 | WARN | 不打印完整 header |
| 是否触发自动重试 | WARN | 记录重试原因 |
| WASM 模块加载耗时 | INFO | 仅首次加载时记录 |

---

## Frontend UI Design (Phase 1)

### 配置 UI

使用 `FormItem` 搭建 DeepSeek 配置界面（Phase 1 仅 token 输入）：

```
┌─────────────────────────────────────────┐
│ DeepSeek 配置                           │
├─────────────────────────────────────────┤
│ Token: [________________]              │
│                                         │
│ [测试连接]                              │
│                                         │
│ 状态: ✓ Token 有效                      │
└─────────────────────────────────────────┘
```

### PoW 状态反馈

PoW 计算属于 CPU 密集型任务，UI 需要区分这段延迟：

- 用户发送消息后，在进入"生成中"状态前，显示"验证中"状态提示
- 考虑在 UI 层对 PoW 阶段做可视化区分（如不同的加载动画/文案）
- PoW 特有的错误提示应区别于普通网络错误（如 "验证失败，请重试" vs "网络连接失败"）

### Reasoning 展示

- DeepSeek `THINK`/`THINKING` 内容转换为 `ReasoningPart`
- 流式传输期间默认展开显示思考过程
- 生成完成后可折叠，仅在用户需要时展开查看
- 复用项目现有的 Reasoning 展示组件（如有）

---

## DeepSeek 响应格式

### SSE 流式响应

每行格式: `data: {JSON}`

**关键路径**:

| 路径 | 类型 | 说明 |
|------|------|------|
| `response/thinking_content` | thinking | 思考内容 |
| `response/content` | text | 文本内容 |
| `response/fragments` | 数组 | 片段数组 |
| `response/fragments/-1/content` | text/thinking | 追加内容 |
| `response/status` | - | FINISHED 表示结束 |

**片段类型**:
- `THINK` / `THINKING` → 思考内容
- `RESPONSE` → 正常回复

### 示例响应流

```
data: {"p":"response/thinking_content","v":"让我思考一下..."}
data: {"p":"response/fragments","o":"APPEND","v":[{"type":"THINK","content":"分析问题"}]}
data: {"p":"response/fragments","o":"APPEND","v":[{"type":"RESPONSE","content":"答案是"}]}
data: {"p":"response/fragments/-1/content","v":"42"}
data: {"p":"response/status","v":"FINISHED"}
data: [DONE]
```

### 模型能力

| 模型 | thinking | search |
|------|----------|--------|
| `deepseek-chat` | - | - |
| `deepseek-reasoner` | Yes | - |
| `deepseek-chat-search` | - | Yes |
| `deepseek-reasoner-search` | Yes | Yes |

---

## Acceptance Criteria

### 核心功能
- [ ] 可以添加和配置 DeepSeek 提供商（token 输入使用 FormItem）
- [ ] PoW 计算正确执行并返回结果
- [ ] API 请求携带正确的 `x-ds-pow-response` header
- [ ] 流式和非流式文本生成都能正常工作
- [ ] Reasoning 内容正确显示，支持展开/折叠

### 架构质量
- [ ] Provider 职责清晰拆分（编排层 + 组件）
- [ ] ProviderSetting 不包含运行时状态（无 challenge/answer 字段）
- [ ] 流式/非流式共用同一套响应转换逻辑
- [ ] 所有协议使用 DTO，无手搓 JsonObject

### 性能与稳定性
- [ ] WASM 模块懒加载 + 单例缓存，不重复初始化
- [ ] `wasm_solve` 在 `Dispatchers.Default` 上执行，不阻塞主线程
- [ ] WASM 计算支持协程取消
- [ ] PoW 过期/无效时只重试一次，不无限循环

### 错误处理
- [ ] 定义语义明确的异常类型（Auth / Pow / Protocol）
- [ ] PoW 相关错误在 UI 有特定提示，区别于网络错误
- [ ] Token 无效/过期有明确提示
- [ ] 结构化日志不泄露 token 和完整 header

---

## Definition of Done

### 代码质量
- 代码通过 Lint 检查
- 异常传播风格与现有 Provider 一致
- 注释语言与代码库一致

### 测试覆盖

| 测试类别 | 内容 | 优先级 |
|----------|------|--------|
| `DeepSeekPowEncoder` | 给定 challenge + answer，生成正确 Base64 header | P0 |
| `DeepSeekMessageMapper` | UIMessage → DeepSeek payload 映射正确性 | P0 |
| 流式 chunk 合并 | reasoning 和 text 顺序、收尾是否正确 | P0 |
| 失败恢复 | 收到 PoW 过期错误后只重试一次 | P1 |
| DTO 序列化 | 样例 JSON 反序列化回归 | P1 |

> **WASM 测试策略**: 如果 WASM 运行时在 JVM 单测中不好直接跑，将 `solve()` 抽象为接口，单测使用 fake solver 替身验证流程。

### 手动验证
- 手动测试验证功能
- 更新相关文档

---

## Out of Scope (Phase 1)

- App 内登录流程（Phase 2）
- Token 自动刷新（Phase 2）
- 多账号轮换（Phase 3）
- 其他提供商的修改
- WASM 模块的开发（复用现有）

---

## Decision (ADR-lite)

**Context**: 需要集成 DeepSeek Web API 到 RikkaHub，DeepSeek 要求 PoW 验证

**Decision**:
1. 复用 ds2api 的现成 WASM 文件（Rust 编译，已验证可用）
2. 使用 Chasm 作为 WASM 运行时（Kotlin Multiplatform，支持 Android）
3. Phase 1 采用用户手动提供 Token，Phase 2 再实现 App 内登录
4. 新建独立 Provider（不完全复用 OpenAIProvider，因 API 格式差异较大）
5. Provider 拆分为编排层 + 组件（ChallengeClient / PowSolver / PowEncoder / MessageMapper）
6. WASM 懒加载 + 单例缓存，`wasm_solve` 在 `Dispatchers.Default` 执行
7. 为所有协议定义 DTO，统一流式/非流式响应转换层

**Consequences**:
- 需要 WASM 文件复制和运行时集成
- 需要实现 DeepSeek 响应格式解析
- 组件拆分增加文件数量，但提升可测试性和可维护性
- 首版范围收敛，登录等扩展功能独立迭代

---

## Phase 2 参考：登录流程设计（暂不实施）

> 以下内容保留为 Phase 2 参考资料，Phase 1 不实施。

### DeepSeek 登录 API

**登录接口**: `POST https://chat.deepseek.com/api/v0/users/login`

**请求体**:
```json
{
  "email": "user@example.com",
  "password": "your-password",
  "device_id": "deepseek_to_api",
  "os": "android"
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "biz_code": 0,
    "biz_data": {
      "user": {
        "token": "xxx"
      }
    }
  }
}
```

### Phase 2 注意事项

- 密码不能明文存储，获取 Token 后应立即清除密码
- 登录按钮需有加载动画和防重复点击
- Token 过期前应主动预警，而非等请求失败后才提示
- `ProviderSetting` 中新增 `email`/`mobile`/`tokenExpireAt` 等字段
