# Error Handling

> How errors are handled in this project.

---

## Overview

Error handling follows a "let it propagate" philosophy. Repositories and data layer code generally do **not** catch exceptions internally — they let exceptions propagate to ViewModels, which handle them in coroutine scopes. The main exceptions are network clients and tool execution, which have explicit error handling.

---

## Error Types

### Custom Exception Classes

| Class | File | Fields | Purpose |
|-------|------|--------|---------|
| `HttpException` | `ai/.../util/ErrorParser.kt` | `code: Int`, `body: String` | HTTP errors from AI providers |
| `WebDavException` | `app/.../sync/webdav/WebDavClient.kt` | `statusCode: Int`, `responseBody: String` | WebDAV operation failures |

### Standard Exceptions Used

| Exception | Where | Example |
|-----------|-------|---------|
| `Exception(message)` | AI providers | `"Failed to get response: ${response.code} ${response.body}"` |
| `IllegalStateException` via `error()` | Repositories | `"Memory record #$id not found"` |
| `SQLiteBlobTooBigException` | Database migrations | Gracefully skipped, not re-thrown |

---

## Error Handling Patterns

### Pattern 1: Let It Propagate (Default)

Repositories and DAOs do not catch exceptions. Errors propagate to ViewModels.

```kotlin
// Repository — no try/catch
suspend fun getConversation(id: Uuid): Conversation {
    val entity = conversationDao.getConversationById(id.toString())
    return conversationEntityToConversation(entity)
}

// ViewModel — handles error in coroutine scope
fun loadData() {
    viewModelScope.launch {
        try {
            val data = repository.getData()
            _state.value = UiState.Success(data)
        } catch (e: Exception) {
            _state.value = UiState.Error(e)
        }
    }
}
```

### Pattern 2: Result<T> (Network Clients)

WebDAV client and export serialization use Kotlin's `Result<T>`:

```kotlin
// WebDavClient.kt — every operation returns Result<T>
suspend fun put(path: String, data: ByteArray): Result<Unit> = runCatching {
    val response = client.newCall(request).await()
    if (!response.isSuccessful) {
        throw WebDavException(response.code, response.body?.string() ?: "")
    }
}

// Caller
val result = webDavClient.put(path, data)
result.onSuccess { /* handle success */ }
result.onFailure { e -> /* handle error */ }
```

### Pattern 3: runCatching for Tool Execution

Individual tool executions are wrapped in `runCatching` so one tool failure doesn't halt the entire generation:

```kotlin
// GenerationHandler.kt
val toolResult = runCatching {
    executeTool(toolCall)
}.getOrElse { error ->
    // Return error as JSON output, don't crash
    buildJsonObject { put("error", error.message) }
}
```

### Pattern 4: HTTP Response Checking

Every AI provider checks `response.isSuccessful` and throws with code + body:

```kotlin
// ChatCompletionsAPI.kt
if (!response.isSuccessful) {
    throw Exception("Failed to get response: ${response.code} ${response.body?.string()}")
}
```

### Pattern 5: JSON Error Parsing

`ErrorParser.kt` provides `JsonElement.parseErrorDetail()` which recursively searches API error responses for common error fields (`error`, `detail`, `message`, `description`):

```kotlin
// In SSE stream onFailure callback
val errorMessage = try {
    val json = Json.parseToJsonElement(responseBody)
    json.parseErrorDetail() ?: responseBody
} catch (e: Exception) {
    responseBody
}
```

### Pattern 6: Graceful Degradation in Migrations

Database migrations handle `SQLiteBlobTooBigException` by skipping oversized rows:

```kotlin
try {
    val nodes = cursor.getString(nodesIndex)
    // Process nodes
} catch (e: SQLiteBlobTooBigException) {
    Log.w(TAG, "Skipping conversation $id: blob too big")
    // Continue migration without this row
}
```

---

## UiState Pattern

UI error state is represented using a sealed class:

```kotlin
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: Throwable) : UiState<Nothing>()
}

// Usage in ViewModel
_state.value = UiState.Loading
try {
    _state.value = UiState.Success(result)
} catch (e: Exception) {
    _state.value = UiState.Error(e)
}

// Usage in Composable
state.onSuccess { data -> /* render */ }
     .onError { error -> /* show error */ }
     .onLoading { /* show spinner */ }
```

---

## Precondition Assertions

Use `require()` and `error()` for programming errors (not user errors):

```kotlin
// ConversationRepository.kt — assert no base64 images leaked
require(conversation.messageNodes.none { node ->
    node.messages.any { msg -> msg.parts.any { it is UIMessagePart.Image && it.url.startsWith("data:") } }
}) { "Base64 images must be saved to local files before saving conversation" }

// MemoryRepository.kt — assert record exists
val entity = memoryDao.getMemoryById(id) ?: error("Memory record #$id not found")
```

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Catching all exceptions in repositories | Let exceptions propagate to ViewModel |
| Using `Result<T>` everywhere | Only use for network clients and fallible I/O; repositories propagate directly |
| Swallowing exceptions silently | Always log or propagate; never catch-and-ignore |
| Crashing on migration data issues | Handle `SQLiteBlobTooBigException` and skip gracefully |
| Throwing generic `Exception` without context | Include status code, response body, or error details |
