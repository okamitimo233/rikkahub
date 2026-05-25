# Type Safety

RikkaHub type safety depends on keeping Kotlin serialization models, Room/DataStore persistence, Ktor DTOs, and `web-ui` TypeScript mirrors aligned. Treat cross-layer types as contracts, not local implementation details.

## Kotlin Contract Types

Important Kotlin sources:

- `ai/src/main/java/me/rerere/ai/ui/Message.kt`: `UIMessage`, `UIMessagePart`, `ToolApprovalState`, `UIMessageAnnotation`, `MessageChunk`, streaming merge helpers, and legacy tool migration helpers.
- `ai/src/main/java/me/rerere/ai/core`: roles, token usage, reasoning, and tool contracts.
- `ai/src/main/java/me/rerere/ai/provider/Provider.kt`: provider interface and generation params (`TextGenerationParams`, `ImageGenerationParams`, `CustomHeader`, `CustomBody`).
- `ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt`: serializable provider settings.
- `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`: conversation and message-node domain model.
- `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`: assistant settings and feature flags.
- `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`: `Settings`, `DisplaySetting`, backup configs, and settings extension helpers.
- `app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt`: embedded web API request/response/SSE DTOs.

Use `@Serializable` and stable `@SerialName` values for contracts that cross process/module/language boundaries.

## TypeScript Mirror Types

Web UI types live in `web-ui/app/types`:

- `parts.ts` mirrors Kotlin `UIMessagePart` and `ToolApprovalState` serial names.
- `message.ts`, `conversation.ts`, `dto.ts`, `settings.ts`, `core.ts`, and `annotations.ts` mirror Kotlin message, conversation, settings, usage, and web DTO contracts.
- `helpers.ts` and `lib/type-guards.ts` own type guards and safe derived helpers.
- `index.ts` centralizes exports.

When a Kotlin serialized field changes, update the matching TypeScript type in the same task. Do not let components define private versions of DTO shapes.

## Message Part Contract

`UIMessagePart` uses a sealed Kotlin hierarchy with serial names:

- `text` -> text content
- `image` -> image URL/base64/local file reference
- `video` -> video URL
- `audio` -> audio URL
- `document` -> URL, file name, MIME
- `reasoning` -> reasoning text and timing metadata
- `tool` -> tool call ID/name/input/output/approval state

The web mirror is `web-ui/app/types/parts.ts`, and the web renderer dispatches by `part.type` in `web-ui/app/components/message/message-part.tsx`.

Adding or changing a part requires updating all affected surfaces:

1. Kotlin `UIMessagePart` in `Message.kt`.
2. Provider conversion logic in `ai/provider/providers`.
3. Android message rendering under `app/.../ui/components/message`.
4. Web type union in `web-ui/app/types/parts.ts`.
5. Web renderer in `web-ui/app/components/message/message-part.tsx` and any part component under `parts/`.
6. Web/Android input attachment handling if users can create the part.
7. Persistence/migration code if legacy messages need conversion.

## DTO Contracts

Ktor DTOs in `WebDto.kt` are the source for web API response/request shapes. Examples:

- `ConversationListDto` mirrors `web-ui/app/types/dto.ts` `ConversationListDto`.
- `PagedResult<T>` mirrors web pagination handling in `use-conversation-list.ts`.
- `ConversationSnapshotEvent`, `ConversationNodeUpdateEvent`, and `ConversationListInvalidateEvent` mirror web SSE event DTOs.
- `ErrorResponse` mirrors `web-ui/app/services/api.ts` error parsing.

Rules:

- Use numbers for epoch millis fields exposed to web (`createAt`, `updateAt`, `serverTime`, `expiresAt`).
- Use strings for UUIDs in DTOs; parse/validate UUIDs in routes with route helpers before entering domain logic.
- Keep request DTOs minimal and validate references server-side (`SettingsRoutes.kt` validates assistant/model/MCP/injection IDs).
- Avoid adding fields only on one side. TypeScript optional fields should reflect real Kotlin optional/default behavior, not hide missing backend work.
- Treat stale mirror fields as cleanup signals, not conventions. For example, `web-ui/app/types/dto.ts` and `conversation.ts` still include `truncateIndex`, while the current Kotlin `ConversationDto`/`Conversation` sources no longer expose that field; do not copy this mismatch into new contracts.

## Runtime Validation

The project uses compile-time types plus targeted runtime validation, not a universal schema layer:

- Kotlin routes validate IDs, ranges, blank strings, model types, and known built-in tool names before mutation.
- Provider methods validate local preconditions with `require`.
- Web API client converts HTTP errors to `ApiError` and trusts typed JSON after server validation.
- Type guards belong in shared web helpers when data is not guaranteed by the backend or comes from browser/file/user sources.

`web-ui` has `zod` available in dependencies, but current API DTO handling is TypeScript-interface based. Do not introduce Zod for one endpoint unless the task requires runtime validation for untrusted external payloads and the pattern is applied consistently at that boundary.

## Serialization Details

- Kotlin uses kotlinx.serialization sealed classes. Preserve serial names and default values.
- `JsonInstant` is used by app persistence and Ktor JSON installation to handle project-specific time serialization.
- `kotlin.uuid.Uuid` is used throughout Kotlin models; web DTOs should expose UUIDs as strings.
- `kotlinx.datetime.LocalDateTime` is used in `UIMessage`; web `MessageDto.createdAt`/`finishedAt` are strings.

## Anti-Patterns

- Adding `any` or broad `Record<string, unknown>` in web components for known API payloads. Define/import the DTO type.
- Casting raw SSE events in components instead of typing the `sse<T>` call.
- Changing Kotlin `@SerialName` values without a migration and TypeScript update.
- Adding nullable/optional fields in TypeScript to silence errors when the backend does not actually send them.
- Parsing UUID/date strings in many components; parse/validate at route/service boundaries or keep display formatting in shared helpers.

## Verification

- For Kotlin contract changes, run affected module compile tasks such as `./gradlew :ai:compileDebugKotlin` and `./gradlew :app:compileDebugKotlin`.
- For web type changes, run `pnpm run typecheck` from `web-ui`.
- For DTO/SSE changes, test or inspect both backend route/event emission and web subscription parsing.
