# Error Handling

RikkaHub uses a mix of Kotlin exceptions, coroutine `runCatching`, UI-facing chat errors, and Ktor `StatusPages`. Handle errors at the boundary that can provide useful context; do not swallow failures silently unless the caller can safely continue.

## Ktor API Errors

The embedded web API uses typed exceptions from `app/src/main/java/me/rerere/rikkahub/web/Exceptions.kt`:

- `BadRequestException` -> 400
- `NotFoundException` -> 404
- `UnauthorizedException` -> 401
- `ForbiddenException` -> 403

`WebApiModule.kt` installs `StatusPages` and converts these to `ErrorResponse(error, code)` from `web/dto/WebDto.kt`. Unexpected `Throwable` values become HTTP 500 with the exception message.

Route guidance:

- Validate path parameters and request bodies before mutating state. `ConversationRoutes.kt` checks pagination (`offset >= 0`, `limit in 1..100`) and blank titles before saving.
- Use `toUuid("field name")` route helpers for UUID parsing so errors are consistent.
- Use `NotFoundException` for missing conversations, assistants, models, or managed files.
- Use `BadRequestException` for invalid request shapes, out-of-range indexes, unsupported built-in tool names, or disabled features.
- Return appropriate status codes for accepted/no-content operations. `ConversationRoutes.kt` returns `HttpStatusCode.Accepted` for title regeneration and `HttpStatusCode.NoContent` for delete.

Do not return ad hoc maps for errors from routes. Throw an `ApiException` subclass and let `StatusPages` serialize it.

## Provider and Network Errors

Provider implementations under `ai/src/main/java/me/rerere/ai/provider/providers` generally fail fast with clear messages:

- `OpenAIProvider.kt` uses `require(...)` for invalid caller params such as empty embedding input or missing image files, and `error("Failed to ...: ${response.code} ${response.body?.string()}")` for unsuccessful HTTP responses.
- `ClaudeProvider.kt` parses SSE `error` events and response bodies through `parseErrorDetail()` when possible, logs failures, and closes the `callbackFlow` with the parsed exception.
- Provider work that performs network or file I/O should run on `Dispatchers.IO` (`withContext(Dispatchers.IO)` or `flowOn(Dispatchers.IO)`).

When adding provider code:

- Include HTTP status and response body in thrown errors when a remote API rejects a request.
- Validate local preconditions with `require` before sending network requests.
- Preserve cancellation. Do not catch `Throwable` broadly around streaming flows unless you rethrow or close with the cause.
- For SSE/callback APIs, close the flow on terminal error and cancel the source in `awaitClose`, as `ClaudeProvider.streamText` does.

## Chat Generation Errors

`app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` has two different error paths:

- Generation/provider failures propagate out of the generation flow for `ChatService`/UI handling.
- Tool execution failures are caught per tool. The handler writes a JSON error object into the tool output so the model can continue with the failure context.

Do not convert all generation errors into text messages. Preserve the difference between a failed generation and an executed tool that returned an error.

UI-facing chat errors are represented by `ChatError` and displayed in `ui/components/ui/ErrorCard.kt`. ViewModels should pass meaningful titles for user actions, as `ChatVM.handleCompressContext` does with `R.string.error_title_compress_conversation`.

## Repository and Persistence Errors

Repository code should guard known persistence edge cases close to the read/write logic:

- `ConversationRepository.conversationToConversationEntity` uses `require` to prevent persisting base64 message parts.
- `ConversationRepository.loadMessageNodes` catches `SQLiteBlobTooBigException` and `IllegalStateException` per page, prints the stack trace, advances the offset, and continues loading the rest of a conversation.
- DataStore read errors only suppress `IOException` by emitting `emptyPreferences()`. Other exceptions are rethrown in `SettingsStore.settingsFlowRaw`.

When handling persistence errors:

- Use transactions for multi-table writes so partial state is not persisted.
- Catch only known recoverable exceptions. Unknown mapper/serialization errors should fail visibly.
- If continuing after a bad record, keep the scope narrow and avoid hiding all records.

## Android UI and ViewModel Errors

ViewModels should launch suspend work in `viewModelScope` and route user-visible failures to services or UI state rather than throwing from composables.

Patterns:

- `ChatVM` delegates generation operations to `ChatService` and exposes `errors: StateFlow<List<ChatError>>` plus `dismissError`/`clearAllErrors`.
- UI components show transient error cards through `ErrorCardsDisplay` and allow copying the message.
- Use `LocalToaster.current` for short-lived success/failure feedback in Compose pages when the error does not need persistent chat context.

Composable functions should not run blocking work or throw for expected validation errors. Validate inputs before calling ViewModel/service methods, or let the ViewModel add a `ChatError`/toast.

## Web UI Error Contract

The React client in `web-ui/app/services/api.ts` expects `ErrorResponse { error, code }`. It converts HTTP failures into `ApiError` and triggers web-auth-required events on 401 outside `/api/auth/token`.

If you change backend error responses, update:

- `app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt`
- `web-ui/app/services/api.ts`
- relevant UI error handling components/routes

## Anti-Patterns

- Do not catch and ignore provider/network errors without surfacing them to the user or caller.
- Do not throw raw `IllegalArgumentException` from Ktor routes for client mistakes; use `BadRequestException` or a route helper that does.
- Do not wrap all exceptions in generic `Exception("failed")`; preserve the original message/cause when possible.
- Do not show stack traces in normal UI text. Stack traces are acceptable in tool-output JSON because they are explicit tool execution diagnostics for the model/user context.
- Do not use `SettingsStore.update` with dummy settings; the method intentionally refuses to persist them.
