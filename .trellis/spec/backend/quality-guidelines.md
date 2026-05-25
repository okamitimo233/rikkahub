# Quality Guidelines

Backend code quality in RikkaHub means preserving existing module boundaries, coroutine behavior, serialization contracts, and persistent data integrity. Prefer small changes that follow current patterns over new abstractions.

## Kotlin and Coroutine Rules

- Keep blocking I/O off the main thread. Provider implementations use `withContext(Dispatchers.IO)` and `GenerationHandler.generateText` finishes with `flowOn(Dispatchers.IO)`.
- Preserve structured cancellation. Streaming provider methods such as `ClaudeProvider.streamText` use `callbackFlow` plus `awaitClose { eventSource.cancel() }`; new stream bridges should do the same.
- Expose long-lived observed state as `StateFlow` or `Flow`, not callbacks. Examples: `SettingsStore.settingsFlow`, `WebServerManager.state`, `ChatService` conversation/job flows, and repository list flows.
- Use `viewModelScope` in ViewModels and app-level `AppScope` in managers/services that outlive a screen. `WebServerManager.start` launches in `AppScope`; `ChatVM` launches UI actions in `viewModelScope`.

## Serialization and Domain Contracts

- Use `kotlinx.serialization` for persisted/web contracts. `Settings`, `UIMessage`, `UIMessagePart`, web DTOs, provider settings, and tool states are serializable.
- Preserve `@SerialName` discriminators. `UIMessagePart` uses values such as `text`, `image`, `document`, `reasoning`, and `tool`; TypeScript mirrors in `web-ui/app/types/parts.ts` depend on these strings.
- Use `JsonInstant` when serializing app/domain data that includes time or Kotlin UUID-backed models. `PreferencesStore.kt`, `ConversationRepository.kt`, and `WebApiModule.kt` use it.
- Keep DTO conversion centralized. `web/dto/WebDto.kt` owns `Conversation.toDto`, `MessageNode.toDto`, and `UIMessage.toDto`.

When adding a cross-layer field, update the Kotlin model, Room/DataStore persistence if any, web DTO, TypeScript type, and UI render/update code in the same task.

## Provider Quality Rules

Provider code must remain provider-boundary code:

- Accept provider settings and params from method arguments; do not store per-request mutable state on provider instances.
- Convert `UIMessage`/`UIMessagePart` into the remote API shape inside the provider implementation. `ClaudeProvider.buildMessageRequest` handles system messages, prompt caching, reasoning, tool definitions, images, and tool results at that boundary.
- Merge custom headers/body through existing helpers (`toHeaders`, `mergeCustomBody`) so assistant/model overrides keep working.
- Validate local inputs before request construction (`require(params.input.isNotEmpty())`, image file existence/type checks, expected provider setting type checks).
- Include provider HTTP status/body in errors so users can diagnose API configuration issues.

Do not put OpenAI/Anthropic/Google API-specific payload logic in `GenerationHandler` or UI code.

## Message Transformation Rules

`data/ai/transformers/Transformer.kt` defines the transformer lifecycle:

- `InputMessageTransformer.transform` changes messages before provider upload.
- `OutputMessageTransformer.transform` may process output chunks during generation.
- `OutputMessageTransformer.visualTransform` exists for streaming-only UI display transformations that should not mutate persisted semantics yet.
- `OutputMessageTransformer.onGenerationFinish` applies final processing after generation completes.

Follow these boundaries. For example, document-to-prompt conversion belongs in input transformers, while `<think>`/reasoning output handling belongs in output transformers. Do not mutate global settings or repositories inside transformers unless the transformer is explicitly designed as a side-effect boundary.

## Service and Repository Rules

- Keep persistence in repositories. `ConversationRepository` owns Room mappers, message-node persistence, FTS indexing, and file cleanup integration.
- Keep conversation lifecycle/generation orchestration in `ChatService`/`ConversationSession`, not in Compose pages or Ktor routes.
- Keep Ktor routes thin: parse/validate request, call service/repository/store, respond with DTO.
- Register singleton repositories/managers through Koin modules such as `RepositoryModule.kt`.
- Avoid retaining Android `Context` outside classes that already need it (`SettingsStore`, managers, providers with key rotation, file managers).

## Validation Commands

Use the narrowest checks that cover changed modules:

- App backend/data/service changes: `./gradlew :app:compileDebugKotlin`
- AI provider/message changes: `./gradlew :ai:compileDebugKotlin`
- Generic web server module changes: `./gradlew :web:compileKotlin`
- Search/speech/document module changes: run that module's Kotlin compile task.
- Room schema/migration changes: run an app compile task that triggers KSP and inspect generated schema changes under `app/schemas`.

For documentation-only `.trellis/spec` changes, run template-term and link checks rather than Gradle builds.

## Review Checklist

Before reporting backend work complete:

- The change lives in the current owner package/module.
- All affected serializers and DTO mirrors are updated.
- Database changes include entity, DAO, repository mapper, migration/version, and schema considerations.
- Settings changes include key, default decode, write path, cleanup/normalization, and migrations if needed.
- Provider changes support streaming/non-streaming behavior as appropriate and do not block the main thread.
- Ktor routes validate input and return existing `ErrorResponse` shape on errors.
- Logs do not expose API keys, JWTs, access passwords, or large base64 payloads.

## Forbidden Patterns

- Direct Room access from Compose UI or web routes when a repository exists.
- Long-running or blocking network/file operations on the main thread.
- Adding provider-specific assumptions to `UIMessage` consumers outside provider conversion code.
- Ad hoc JSON parsing/casting at multiple boundaries instead of shared serializable DTOs/types.
- Silent catch-all exception handling that hides user-visible failures.
- New documentation or conventions that contradict current source patterns.
