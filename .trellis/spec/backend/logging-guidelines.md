# Logging Guidelines

> How logging is done in this project.

---

## Overview

Three logging systems are used in parallel:

| System | Scope | Storage | Purpose |
|--------|-------|---------|---------|
| `android.util.Log` | All modules | Logcat | Standard Android debug/info/warning/error logs |
| `Logging` singleton | `common` module | In-memory ring buffer (100 entries) | Structured HTTP request/response logging, viewable in-app |
| `AILoggingManager` | `app` module | In-memory (32 entries) | AI generation request tracking with params and messages |

---

## android.util.Log

### TAG Convention

Define a private `TAG` constant at file level:

```kotlin
private const val TAG = "ClassName"

// Usage
Log.i(TAG, "Migration started")
Log.d(TAG, "SSE event received: $event")
Log.w(TAG, "Skipping oversized blob for conversation $id")
Log.e(TAG, "WebDAV request failed: ${response.code}")
```

### Log Level Usage

| Level | When to Use | Example |
|-------|------------|---------|
| `Log.d()` | Detailed debug info (SSE events, HTTP details, internal state) | `Log.d(TAG, "SSE event: $data")` |
| `Log.i()` | Significant operations (migration start/success, generation steps, search ops) | `Log.i(TAG, "Migration 11→12 completed")` |
| `Log.w()` | Non-critical issues (skipped items, parse failures, degraded behavior) | `Log.w(TAG, "Failed to parse response, using fallback")` |
| `Log.e()` | Errors that affect functionality (HTTP failures, initialization errors) | `Log.e(TAG, "Failed to init jieba tokenizer", e)` |

### What to Log at Each Level

**`Log.i()`** — Operations a developer would want to see in normal operation:
- Database migration start/end
- AI generation pipeline steps
- Search query execution
- Sync operations (WebDAV, S3)

**`Log.d()`** — Verbose details useful only during debugging:
- SSE stream events and data
- HTTP request/response details
- JSON parsing intermediate steps

**`Log.w()`** — Recoverable issues:
- Skipped rows due to `SQLiteBlobTooBigException`
- Failed JSON parsing with fallback
- Missing optional data

**`Log.e()`** — Failures requiring attention:
- HTTP errors (4xx, 5xx responses)
- Failed native library initialization
- Data restore failures

---

## Logging Singleton (Structured Logging)

**File**: `common/src/main/java/me/rerere/common/android/Logging.kt`

In-memory ring buffer (max 100 entries) for structured logging viewable in the app's debug/log page.

### Entry Types

```kotlin
sealed class LogEntry {
    data class TextLog(val tag: String, val message: String) : LogEntry()
    data class RequestLog(
        val url: String, val method: String,
        val requestHeaders: Map<String, String>,
        val requestBody: String?,
        val responseCode: Int?,
        val responseBody: String?,
        val duration: Long?,
        val error: String?,
    ) : LogEntry()
}
```

### Usage

```kotlin
// Text log
Logging.log("MyTag", "Something happened")

// Request logs are captured automatically by RequestLoggingInterceptor
// (see below)
```

### Access

```kotlin
Logging.getRecentLogs()     // All entries
Logging.getTextLogs()       // TextLog entries only
Logging.getRequestLogs()    // RequestLog entries only
```

---

## HTTP Request Logging

**File**: `app/.../data/ai/RequestLoggingInterceptor.kt`

An OkHttp `Interceptor` that captures every HTTP request/response into the `Logging` singleton:
- URL, method, headers
- Request/response body
- Response code, duration
- Errors

Additionally, OkHttp's built-in `HttpLoggingInterceptor` is configured at `HEADERS` level in `DataSourceModule.kt`.

---

## AI Request Logging

**File**: `app/.../data/ai/AILogging.kt`

`AILoggingManager` tracks AI generation requests with:
- Generation parameters (model, temperature, etc.)
- Input messages
- Provider settings
- Whether streaming was used

Exposed as `StateFlow<List<AILogging>>` for the debug UI.

```kotlin
sealed class AILogging {
    data class Generation(
        val params: TextGenerationParams,
        val messages: List<UIMessage>,
        val providerSetting: ProviderSetting,
        val stream: Boolean
    ) : AILogging()
}
```

---

## What NOT to Log

| Data | Why |
|------|-----|
| API keys / tokens | Security risk |
| User message content at `Log.i()` level | Privacy — use `Log.d()` only |
| Full response bodies at `Log.i()` level | Too verbose — use `Log.d()` or `Logging` singleton |
| Passwords or credentials | Never log auth credentials |

---

## Common Mistakes

| Mistake | Correct Approach |
|---------|-----------------|
| Using `println()` for debugging | Use `Log.d(TAG, ...)` |
| Logging at wrong level (e.g., `Log.e()` for non-errors) | Follow log level guide above |
| Missing TAG constant | Always define `private const val TAG = "ClassName"` |
| Logging sensitive data at info level | Use `Log.d()` for detailed/sensitive data |
| Not logging migration progress | Always log migration start/end with `Log.i()` |
