# Logging Guidelines

RikkaHub primarily logs through Android `Log`, plus request/generation logging utilities in shared/app code. Logs are useful for provider debugging but can include sensitive data, so keep logging deliberate and scoped.

## Android Log Usage

Use `android.util.Log` with a file-level `private const val TAG = "ClassName"`:

- `GenerationHandler.kt` uses `TAG = "GenerationHandler"` and logs generation steps, tool construction, tool execution, and approval waits.
- `ClaudeProvider.kt` uses `TAG = "ClaudeProvider"` and logs request bodies/SSE events while debugging provider behavior.
- `WebServerManager.kt` uses `TAG = "WebServerManager"` and logs server lifecycle, port conflicts, and NSD registration failures.
- `PreferencesStore.kt` logs a warning when code attempts to update dummy settings.

Use levels consistently:

- `Log.d`: high-volume diagnostics such as SSE event callbacks.
- `Log.i`: lifecycle milestones and generation steps.
- `Log.w`: recoverable issues such as failed NSD registration, failed image encoding, or ignored invalid updates.
- `Log.e`: operation failure that prevents the requested action, such as web server start/stop failure.

## Request and Generation Logging

`app/src/main/java/me/rerere/rikkahub/data/ai/RequestLoggingInterceptor.kt` records OkHttp request metadata through `me.rerere.common.android.Logging.logRequest` and `LogEntry.RequestLog`.

`GenerationHandler.kt` records AI generation inputs through `AILoggingManager.addLog(AILogging.Generation(...))` before streaming or non-streaming provider calls.

Guidance:

- Use existing logging infrastructure for AI/provider diagnostics instead of adding one-off file writers.
- Keep request logging at HTTP/provider boundaries, not scattered across UI code.
- If logging response bodies, do it only where needed to diagnose provider errors and avoid duplicating large stream output.

## Sensitive Data Boundaries

Provider requests can contain API keys, custom headers, message content, attached file paths, tool outputs, and personal settings. Treat these as sensitive.

Current code has some verbose diagnostics, for example `ClaudeProvider.kt` logs request JSON and individual messages, and `RequestLoggingInterceptor.kt` records headers/body. When adding or modifying logs:

- Never log raw API keys, access passwords, JWTs, Authorization headers, or cookies.
- Redact `Authorization`, `x-api-key`, custom secret headers, and web auth query tokens before adding new request logs.
- Avoid logging full prompts/messages in production paths unless the existing AI logging UI intentionally stores them for developer diagnostics.
- Do not log base64 image/audio/document content; log count, MIME type, or local URI shape instead.
- Do not log web server access passwords. `WebApiModule.kt` uses secure comparison and dynamic JWT verifier secrets; keep password handling opaque.

## Web Server Logging

Use lifecycle-oriented logs in `WebServerManager.kt`:

- Starting/stopping server with host/port is appropriate.
- Port conflicts should be warnings and also reflected in `WebServerState.error`.
- NSD registration/unregistration failures are recoverable warnings.
- Startup/shutdown exceptions should be errors and update `WebServerState.error`.

Do not add per-request logs in Ktor routes unless debugging a route-specific issue. The route layer should return DTOs/errors; HTTP-level request diagnostics belong in request logging infrastructure.

## Provider Streaming Logs

Streaming providers can emit many events. Keep logs bounded:

- Use `Log.d` for per-event details, as `ClaudeProvider.onEvent` does.
- Use `Log.i` for the beginning of a request or parsed high-level response.
- On stream failure, log the throwable class/message and parsed provider error if available, then close the flow with the exception.

Avoid logging every generated token at info or warning level. This degrades performance and can leak conversation content.

## Error Logging

- Log recoverable background failures with enough context to act (`Log.w(TAG, "NSD register failed", it)`).
- Log action-blocking failures at error level (`Log.e(TAG, "Failed to start web server", e)`).
- In repository recovery paths, prefer proper structured logging where possible. `ConversationRepository.loadMessageNodes` currently uses `e.printStackTrace()` for page-level recovery; do not expand this pattern to new code when `Log.w` with a tag is available.

## Anti-Patterns

- Adding `println` for Android diagnostics. Use `Log` or existing logging utilities.
- Logging secrets or full Authorization/custom headers.
- Logging large request/response bodies from tight loops or stream callbacks.
- Swallowing exceptions after logging them when the caller expects failure state.
- Creating a second logging abstraction instead of using `Logging.logRequest`, `AILoggingManager`, or Android `Log`.
