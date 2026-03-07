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
   - 方案 A: 用户手动提供 DeepSeek Web token（简单）
   - 方案 B: 在 App 内登录获取 token（需要实现登录流程）

2. **账号管理**: 
   - 是否需要多账号轮换？
   - 是否需要 token 刷新机制？

## Requirements (evolving)

### 功能需求
- [ ] 支持新的 DeepSeek Provider 设置
- [ ] 集成 WASM 运行时 (Chasm)
- [ ] 内置 PoW WASM 模块 (复用 ds2api 的 WASM)
- [ ] 在 API 请求前执行 PoW 计算
- [ ] DeepSeek 响应格式转换为 UIMessage
- [ ] 提供商配置 UI (token 输入)

### 技术需求
- [ ] 复制 WASM 文件到 app/src/main/assets/
- [ ] Chasm 运行时集成
- [ ] Kotlin 调用 wasm_solve 函数
- [ ] DeepSeekProvider 实现
- [ ] DeepSeekProviderSetting 定义
- [ ] 响应格式转换 (DeepSeek → UIMessage)

## Acceptance Criteria (evolving)

- [ ] 可以添加和配置 DeepSeek 提供商
- [ ] PoW 计算正确执行并返回结果
- [ ] API 请求携带正确的 `x-ds-pow-response` header
- [ ] 流式和非流式文本生成都能正常工作
- [ ] reasoning 内容正确显示

## Definition of Done

- 代码通过 Lint 检查
- 单元测试覆盖 PoW 计算逻辑
- 手动测试验证功能
- 更新相关文档

## Out of Scope (explicit)

- DeepSeek 登录功能（用户手动提供 token）
- 多账号轮换
- 其他提供商的修改
- WASM 模块的开发（复用现有）

## Technical Approach

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                       RikkaHub App                          │
├─────────────────────────────────────────────────────────────┤
│  DeepSeekProviderSetting                                    │
│  - token: String                                            │
│  - models: List<Model>                                      │
├─────────────────────────────────────────────────────────────┤
│  DeepSeekProvider : Provider<DeepSeekProviderSetting>       │
│  ├─ PoWSolver (WASM)                                        │
│  │   └─ load sha3_wasm_bg.wasm via Chasm                    │
│  │   └─ call wasm_solve()                                   │
│  ├─ DeepSeekClient                                          │
│  │   ├─ getChallenge() → call create_pow_challenge          │
│  │   ├─ solvePow() → call PoWSolver                         │
│  │   └─ completion() → call /api/v0/chat/completion         │
│  └─ ResponseParser                                          │
│      └─ DeepSeek format → UIMessage                         │
└─────────────────────────────────────────────────────────────┘
```

### 关键文件修改

| 文件 | 操作 | 内容 |
|------|------|------|
| `ai/.../ProviderSetting.kt` | 修改 | 添加 `DeepSeek` 子类 |
| `ai/.../ProviderManager.kt` | 修改 | 注册 DeepSeekProvider |
| `ai/.../providers/DeepSeekProvider.kt` | 新建 | Provider 实现 |
| `ai/.../providers/deepseek/PoWSolver.kt` | 新建 | WASM 调用封装 |
| `ai/.../providers/deepseek/DeepSeekClient.kt` | 新建 | HTTP 客户端 |
| `ai/.../providers/deepseek/ResponseParser.kt` | 新建 | 响应解析 |
| `app/.../ProviderConfigure.kt` | 修改 | 添加 DeepSeek 配置 UI |
| `app/build.gradle.kts` | 修改 | 添加 Chasm 依赖 |
| `app/src/main/assets/sha3_wasm_bg.wasm` | 新建 | WASM 模块 |

### 依赖添加

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.charlietap.chasm:chasm:1.2.0")
}
```

### WASM 调用示例

```kotlin
class PoWSolver(context: Context) {
    private val wasmBytes: ByteArray = context.assets
        .open("sha3_wasm_bg.wasm").readBytes()
    
    private val store = store()
    private val module = module(wasmBytes)
    private val instance = instance(store, module)
    
    fun solve(challenge: String, prefix: String, difficulty: Double): Long {
        // 调用 wasm_solve 函数
        val result = invoke(store, instance, "wasm_solve", 
            listOf(/* params */))
        // 解析结果
        return resultValue
    }
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
    val challenge = client.getChallenge(setting.token)
    
    // 2. 计算 PoW 答案
    val answer = powSolver.solve(challenge)
    val powHeader = buildPowHeader(challenge, answer)
    
    // 3. 发送请求
    val response = client.completion(
        token = setting.token,
        powResponse = powHeader,
        messages = convertMessages(messages),
        model = params.model.modelId
    )
    
    // 4. 解析响应流
    return parseResponse(response)
}
```

## Decision (ADR-lite)

**Context**: 需要集成 DeepSeek Web API 到 RikkaHub，DeepSeek 要求 PoW 验证

**Decision**: 
1. 复用 ds2api 的现成 WASM 文件（Rust 编译，已验证可用）
2. 使用 Chasm 作为 WASM 运行时（Kotlin Multiplatform，支持 Android）
3. **App 内实现登录流程**（支持邮箱/手机号登录）
4. 新建独立 Provider（不完全复用 OpenAIProvider，因 API 格式差异较大）

**Consequences**:
- 需要 WASM 文件复制和运行时集成
- 需要实现 DeepSeek 响应格式解析
- 需要实现登录 UI 和凭证存储
- 需要处理 token 刷新逻辑

---

## 登录流程设计

### DeepSeek 登录 API

**登录接口**: `POST https://chat.deepseek.com/api/v0/users/login`

**请求体**:
```json
{
  "email": "user@example.com",  // 或 "mobile": "12345678901"
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

### 凭证存储设计

```kotlin
// DeepSeekProviderSetting 扩展
@Serializable
@SerialName("deepseek")
data class DeepSeek(
    override var id: Uuid = Uuid.random(),
    override var enabled: Boolean = true,
    override var name: String = "DeepSeek",
    override var models: List<Model> = emptyList(),
    // 登录凭证
    var email: String = "",           // 邮箱登录
    var mobile: String = "",          // 手机号登录
    var password: String = "",        // 密码（加密存储）
    var token: String = "",           // 当前 token
    var tokenExpireAt: Long = 0,      // token 过期时间
) : ProviderSetting()
```

### 登录 UI 设计

在 `ProviderConfigure.kt` 中添加 DeepSeek 配置：

```
┌─────────────────────────────────────────┐
│ DeepSeek 配置                           │
├─────────────────────────────────────────┤
│ 登录方式: [邮箱 ▼]                      │
│                                         │
│ 邮箱: [________________]                │
│ 密码: [________________]                │
│                                         │
│ [登录]  [测试连接]                       │
│                                         │
│ 状态: ✓ 已登录 (token 有效至 xx:xx)     │
└─────────────────────────────────────────┘
```

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

### 转换为 UIMessage

```kotlin
data class ContentPart(
    val text: String,
    val type: String  // "thinking" or "text"
)

// 解析后映射到 UIMessage
when (part.type) {
    "thinking" -> ReasoningPart(text = part.text)
    "text" -> TextPart(text = part.text)
}
```

### 模型能力

| 模型 | thinking | search |
|------|----------|--------|
| `deepseek-chat` | ❌ | ❌ |
| `deepseek-reasoner` | ✅ | ❌ |
| `deepseek-chat-search` | ❌ | ✅ |
| `deepseek-reasoner-search` | ✅ | ✅ |
