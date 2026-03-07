# Type Safety

> Kotlin type safety patterns in this project.

---

## Overview

The project uses Kotlin with strict null safety and `kotlinx.serialization` for all JSON handling. Types are organized by module boundary: the `ai` module defines platform-agnostic types, the `app` module defines app-specific domain types.

---

## Type Organization

| Module | Type Location | Examples |
|--------|--------------|---------|
| `ai` | `ai/src/main/java/me/rerere/ai/` | `UIMessage`, `UIMessagePart`, `Provider`, `ProviderSetting`, `Model`, `Tool` |
| `app` | `app/.../data/model/` | `Assistant`, `Conversation`, `MessageNode`, `Avatar`, `Favorite` |
| `app` | `app/.../data/db/entity/` | Room entities (`ConversationEntity`, `MessageNodeEntity`, etc.) |
| `common` | `common/.../` | Utility types (`Logging`, `CacheStore`) |
| `search` | `search/.../` | `SearchServiceOptions` sealed hierarchy (16 search provider variants) |
| `tts` | `tts/.../` | `TTSProviderSetting` sealed hierarchy (6 TTS provider variants) |

### Rule: Cross-Module Type Boundaries

- `ai` module types are referenced by `app` module, never the reverse
- Each feature module (`search`, `tts`) is self-contained with its own type hierarchies
- All sealed class hierarchies that cross serialization boundaries use `@SerialName` discriminators

---

## Serialization

### Json Configuration

**AI module** (`ai/src/main/java/me/rerere/ai/util/Json.kt`):
```kotlin
internal val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
```

**App module** (`app/.../utils/Json.kt`):
```kotlin
val JsonInstant by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
```

Both share `ignoreUnknownKeys = true` and `encodeDefaults = true`. The AI module adds `explicitNulls = false`.

### Custom Serializers

- `InstantSerializer` (`ai/.../util/Serializer.kt`): Serializes `java.time.Instant` to ISO string. Used with `@Serializable(with = InstantSerializer::class)`.

---

## Default Value Conventions

All data class fields must have default values:

| Field Type | Default Convention | Example |
|-----------|-------------------|---------|
| `Uuid` | `Uuid.random()` | `val id: Uuid = Uuid.random()` |
| `String` (required) | `""` | `val name: String = ""` |
| `String` (optional) | `null` | `val translation: String? = null` |
| `List<T>` / `Set<T>` | `emptyList()` / `emptySet()` | `val tags: List<Uuid> = emptyList()` |
| `Boolean` | Explicit `true` or `false` | `val streamOutput: Boolean = true` |
| `Nullable optional` | `null` | `val chatModelId: Uuid? = null` |
| `Numeric` | `0` or meaningful default | `val contextMessageSize: Int = 0` |
| `Enum/Sealed` | Specific default variant | `val avatar: Avatar = Avatar.Dummy` |

**Reference**: `Assistant.kt` — 30+ fields, every one with a default value.

---

## Sealed Class Patterns

Sealed types are the primary tool for discriminated unions:

### App Module

| Sealed Type | Variants | Serialization |
|------------|---------|---------------|
| `Avatar` | `Dummy`, `Emoji(content)`, `Image(url)` | `@SerialName` |
| `PromptInjection` | `ModeInjection`, `RegexInjection` | `@SerialName("mode")`, `@SerialName("regex")` |
| `UiState<T>` | `Idle`, `Loading`, `Success(data)`, `Error(error)` | Not serialized |
| `McpServerConfig` | `SseTransportServer`, `StreamableHTTPServer` | `@SerialName` |
| `McpStatus` | `Idle`, `Connecting`, `Connected`, `Reconnecting`, `Error` | Not serialized |

### AI Module

| Sealed Type | Variants | Serialization |
|------------|---------|---------------|
| `UIMessagePart` | `Text`, `Image`, `Video`, `Audio`, `Document`, `Reasoning`, `Tool` | `@SerialName("text")`, etc. |
| `ProviderSetting` | `OpenAI`, `Google`, `Claude` | `@SerialName("openai")`, etc. |
| `ToolApprovalState` | `Auto`, `Pending`, `Approved`, `Denied(reason)`, `Answered(answer)` | `@SerialName` |
| `BuiltInTools` | `Search`, `UrlContext` | `@SerialName` |

### Pattern: Generic Provider Interface

```kotlin
interface Provider<T : ProviderSetting> {
    suspend fun chatCompletions(params: TextGenerationParams, setting: T): Flow<MessageChunk>
}

// Concrete implementations
class OpenAIProvider : Provider<ProviderSetting.OpenAI>
class GoogleProvider : Provider<ProviderSetting.Google>
class ClaudeProvider : Provider<ProviderSetting.Claude>
```

---

## Nullable vs Non-Nullable Rules

| Scenario | Convention |
|----------|-----------|
| Required identifiers | Non-nullable: `val id: Uuid` |
| Optional references | Nullable with null default: `val chatModelId: Uuid? = null` |
| Optional parameters | Nullable: `val temperature: Float? = null` |
| Collections | **Never nullable** — use `emptyList()` / `emptySet()` |
| Required strings | Non-nullable with `""` default |
| Optional strings | Nullable only when absence is semantically meaningful |

---

## Extension Function Patterns

Extension functions are used for type-safe operations on domain types:

```kotlin
// Conversion
fun UIMessage.toMessageNode(): MessageNode

// Immutable update
fun UIMessage.finishReasoning(): UIMessage

// List operations
fun List<UIMessage>.handleMessageChunk(chunk: MessageChunk): List<UIMessage>
fun List<UIMessage>.limitContext(size: Int): List<UIMessage>

// Nullable receiver
fun TokenUsage?.merge(other: TokenUsage?): TokenUsage?

// Callback chaining
fun UiState<T>.onSuccess { ... }.onError { ... }.onLoading { ... }
```

---

## `@Transient` Usage

Fields marked `@Transient` (from kotlinx.serialization) for non-serializable runtime-only data:

```kotlin
@Serializable
data class Conversation(
    // ... serialized fields ...
    @Transient val newConversation: Boolean = false,  // Runtime flag
)
```

---

## Enum Patterns

Enums use `@Serializable` and `@SerialName` for JSON values:

```kotlin
@Serializable
enum class MessageRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL,
}
```

Enums with constructor parameters:
```kotlin
enum class ReasoningLevel(val budgetTokens: Int, val effort: String) {
    LOW(1024, "low"),
    MEDIUM(4096, "medium"),
    HIGH(32768, "high");

    val isEnabled get() = true
    companion object {
        fun fromBudgetTokens(tokens: Int): ReasoningLevel = ...
    }
}
```

---

## Forbidden Patterns

| Pattern | Why | Alternative |
|---------|-----|------------|
| Nullable collections (`List<T>?`) | Ambiguous null vs empty | Use `emptyList()` default |
| `typealias` for domain types | Not used in this codebase | Use data classes or sealed classes |
| `Any` or unchecked casts | Type unsafety | Use sealed classes or generics |
| Inline JSON construction without `@Serializable` | Error-prone | Use `@Serializable` data classes for domain types |
| Missing `@SerialName` on sealed class variants | Breaks polymorphic serialization | Always add `@SerialName` discriminator |
