# Cross-Layer Thinking Guide

Use this guide for RikkaHub changes that move data or behavior across modules, storage, services, APIs, Android Compose UI, and the embedded web UI.

## Map the Data Flow

Before implementing, write down the actual path. Common RikkaHub flows include:

```text
User input -> Compose/web input state -> ChatService -> GenerationHandler -> transformers -> Provider -> UIMessage chunks -> ChatService -> Room/message nodes -> Compose/web rendering
```

```text
DataStore Settings -> SettingsStore normalization -> Compose ViewModel/CompositionLocal -> Ktor settings SSE -> web-ui Zustand settings slice -> React components
```

```text
Room ConversationEntity + MessageNodeEntity -> ConversationRepository -> WebDto.kt -> /api/conversations -> web-ui/app/types -> React route/components
```

For each boundary, identify:

- The owning file/type.
- The serialized field names and defaults.
- Which side validates input.
- Whether data is a snapshot, stream event, or durable persisted state.

## Important RikkaHub Boundaries

### Message Boundary

Source types:

- Kotlin: `ai/src/main/java/me/rerere/ai/ui/Message.kt`
- Android rendering: `app/src/main/java/me/rerere/rikkahub/ui/components/message`
- Web types/rendering: `web-ui/app/types/parts.ts`, `web-ui/app/components/message/message-part.tsx`
- Provider conversion: `ai/src/main/java/me/rerere/ai/provider/providers`
- Persistence: `app/src/main/java/me/rerere/rikkahub/data/db/entity/MessageNodeEntity.kt` and `ConversationRepository.kt`

Checklist for message changes:

- Update Kotlin sealed class and serial names.
- Update provider conversions for upload/download/streaming.
- Update Android and web renderers.
- Update input/attachment handling if users can create the part.
- Update migration code if old persisted messages need conversion.
- Verify base64/local-file persistence rules still hold.

### Settings Boundary

Source type:

- `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`

Consumers:

- Compose hooks/ViewModels (`rememberUserSettingsState`, `SettingVM`, `ChatVM`).
- Ktor settings routes and `/api/settings/stream`.
- `web-ui/app/types/settings.ts` and Zustand settings slice.

Checklist for settings changes:

- Add key/default/decode/write path in `SettingsStore`.
- Add cleanup/normalization if the field references providers, assistants, models, MCP servers, injections, files, or tools.
- Add DataStore migration when old serialized data needs transformation.
- Update web DTO/types and UI if exposed to the web UI.
- Update Compose UI and Android string resources if user-facing.

### Conversation Persistence Boundary

Source files:

- Domain: `app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt`
- Room: `data/db/entity/ConversationEntity.kt`, `MessageNodeEntity.kt`, DAOs, migrations
- Repository: `data/repository/ConversationRepository.kt`
- Web DTOs: `web/dto/WebDto.kt`
- Web types: `web-ui/app/types/conversation.ts`, `dto.ts`

Checklist:

- Keep list queries lightweight; do not load message-node JSON for sidebars.
- Use repository mappers for entity/domain conversion.
- Keep multi-table writes transactional.
- Update FTS indexing when message content changes.
- Keep web list DTOs and full conversation DTOs distinct.

### Embedded Web API Boundary

Source files:

- Generic server/static host: `web/src/main/java/me/rerere/rikkahub/web/Entry.kt`
- App API: `app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt`
- Routes: `app/src/main/java/me/rerere/rikkahub/web/routes`
- Web client: `web-ui/app/services/api.ts`

Checklist:

- Validate route input server-side and throw `ApiException` subclasses.
- Keep errors in `ErrorResponse(error, code)` shape unless updating `api.ts` too.
- Use SSE event DTOs for streams and TypeScript mirrors for `sse<T>` calls.
- Keep auth behavior consistent with JWT settings and web auth storage.

## Define the Contract Before Coding

For a cross-layer feature, answer:

- What is the Kotlin source type?
- Is it persisted in Room, DataStore, files, or only live service state?
- Is it exposed through Ktor REST or SSE?
- What TypeScript type mirrors it?
- Which UI surfaces render or edit it?
- What happens for old persisted data and missing fields?
- What validation belongs on Android UI, web UI, route, repository, or provider boundary?

## Common Mistakes

- Updating Kotlin `UIMessagePart` but not web `parts.ts` or `message-part.tsx`.
- Adding a web route response field but leaving TypeScript with an optional workaround instead of updating the DTO mirror.
- Treating live generation state as persisted conversation data. Generation jobs belong to `ChatService` and are combined into DTOs by routes.
- Adding a DataStore field without updating decode, write, defaults, and cleanup in `PreferencesStore.kt`.
- Changing provider request behavior in `GenerationHandler` instead of the provider implementation.
- Editing built web static files instead of `web-ui/app` source.

## Verification Checklist

Before finishing cross-layer work:

- Kotlin compiles for affected modules.
- `web-ui` typecheck passes when web types/components changed.
- REST/SSE DTOs match between `WebDto.kt` and `web-ui/app/types`.
- Room/DataStore migrations/defaults cover existing users.
- Both Android and web UIs handle loading, empty, invalid, and streaming states.
- Error handling is consistent at every boundary.
