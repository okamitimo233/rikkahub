# Code Reuse Thinking Guide

Use this guide before adding new RikkaHub code. The project already has established patterns for repositories, DataStore settings, provider conversions, message rendering, Compose forms, web API calls, hooks, and Zustand slices.

## Search Before You Create

Before writing a new helper/component/route/type:

1. Search for an existing symbol or similar file name with CodeGraph when the question is structural.
2. Search literal route paths, string keys, serial names, or column names with repository search.
3. Read the nearest existing implementation in the same package.
4. Extend or follow that pattern unless there is a clear reason not to.

Examples of existing owners:

- Settings persistence and helpers: `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`.
- Conversation persistence: `app/src/main/java/me/rerere/rikkahub/data/repository/ConversationRepository.kt`.
- Web API DTOs: `app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt`.
- Provider implementations: `ai/src/main/java/me/rerere/ai/provider/providers`.
- Compose settings rows: `app/src/main/java/me/rerere/rikkahub/ui/components/ui/Form.kt`.
- Web REST/SSE client: `web-ui/app/services/api.ts`.
- Web global state: `web-ui/app/stores/app-store.ts` and `stores/slices`.
- Web message part rendering: `web-ui/app/components/message/message-part.tsx` and `components/message/parts`.

## Common Duplication Patterns

### Duplicate DTO or Payload Shapes

Bad: a React component declares a local interface for a `/api/conversations` response.

Good: import `ConversationDto`, `ConversationListDto`, or event DTOs from `web-ui/app/types`, which mirror `WebDto.kt`.

Rule: if a payload crosses Kotlin/Web boundaries, the contract belongs in Kotlin DTOs and `web-ui/app/types`, not in a component or route callback.

### Duplicate Message Part Logic

Bad: adding `if (part.type === "tool")` rendering logic directly in several message components.

Good: update the central dispatcher in `web-ui/app/components/message/message-part.tsx` and the corresponding Android message component under `app/.../ui/components/message`.

Rule: `UIMessagePart` behavior should have one rendering dispatch point per UI surface and provider conversion at provider boundaries.

### Duplicate Settings Updates

Bad: editing DataStore directly from a screen or route.

Good: add/update helper methods in `SettingsStore` or call `settingsStore.update { it.copy(...) }`, as `SettingsRoutes.kt` and ViewModels do.

Rule: `PreferencesStore.kt` owns settings defaults, cleanup, normalization, and writes.

### Duplicate API/SSE Handling

Bad: using raw `fetch` and a custom `EventSource` parser in a web component.

Good: use `api.get/post/...` and `sse<T>` from `web-ui/app/services/api.ts`, which handle auth, errors, and SSE parsing.

Rule: extend `services/api.ts` if the client boundary needs a new capability.

### Duplicate Compose UI Rows

Bad: building a new label/description/switch row for every settings page.

Good: use `FormItem` from `ui/components/ui/Form.kt` and nearby settings-page patterns.

Rule: only create a new UI primitive when existing components cannot express the layout.

## When to Abstract

Abstract when:

- The same logic appears in three or more places.
- The logic is a contract boundary, such as a DTO decoder, route validation, message part dispatch, file URL conversion, or settings cleanup.
- A bug fix must apply everywhere the behavior appears.

Do not abstract when:

- The code is used once and is clearer inline.
- The only commonality is visual resemblance but behavior differs.
- The abstraction would obscure a provider-specific or platform-specific boundary.

## Checklist Before Editing

- Identify the current owner module/package for the behavior.
- Search for an existing implementation in that owner.
- Reuse existing helpers/components/hooks/services where possible.
- If adding a new shared type/helper, import it everywhere instead of duplicating private shapes.
- If changing a serial name, route, DataStore key, Room column, or message part, search all references and update mirrors.

## RikkaHub Gotchas

- `web/src/main/resources/static` is copied build output from `web-ui`; do not patch it directly.
- Conversation list DTOs are summaries. Do not duplicate full-conversation loading for sidebar UI.
- Provider request JSON belongs in provider implementations, not in `GenerationHandler` or UI code.
- `ChatInputState` exists to avoid large saved-instance-state payloads; do not replace it with many `rememberSaveable` attachment fields.
- `Settings.dummy()` is an initial sentinel value and must not be persisted.
