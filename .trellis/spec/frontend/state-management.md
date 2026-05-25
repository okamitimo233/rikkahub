# State Management

RikkaHub state is split by lifetime and owner: ViewModels/services/DataStore on Android, Ktor/SSE for web API state, and Zustand/local React state in `web-ui`. Keep state close to its source of truth.

## Android State Categories

- Durable settings: `SettingsStore` in `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`, backed by DataStore Preferences.
- Durable relational data: repositories backed by Room, for example `ConversationRepository` and `MemoryRepository`.
- Long-lived chat/session state: `ChatService` and `ConversationSession` under `app/src/main/java/me/rerere/rikkahub/service`.
- Screen state: ViewModels under `ui/pages/**`, such as `ChatVM`, `SettingVM`, `BackupVM`, and `StatsVM`.
- Local UI state: `remember`, `mutableStateOf`, lazy-list state, dialog open flags, and small UI-only classes under `ui/hooks`.

Do not promote state to DataStore or a singleton service unless it must survive screens/app restarts or be shared across unrelated features.

## ViewModel Patterns

`ChatVM.kt` is the main example for complex screen state:

- It exposes `StateFlow` values derived from services/settings (`conversation`, `conversationJob`, `processingStatus`, `conversationJobs`, `settings`, `currentChatModel`, `errors`).
- It uses `stateIn(viewModelScope, SharingStarted.Eagerly/Lazily, initialValue)` for derived flows.
- It owns `ChatInputState` to keep input drafts out of saved instance state and avoid `TransactionTooLargeException`.
- It delegates durable actions to `ChatService`, `ConversationRepository`, `SettingsStore`, and `FilesManager`.
- It releases session references in `onCleared()`.

Guidelines:

- ViewModels should expose immutable `StateFlow`/`SharedFlow`/state values and functions for actions.
- Launch suspend work in `viewModelScope`.
- Keep direct Android `Application`/`Context` usage narrow and justified, as in `ChatVM` for resources and simple preference writes.
- Do not run business logic directly in composables if it can live in the ViewModel/service.

## Settings State

`SettingsStore.settingsFlow` is the single source of truth for app settings. It is decoded from DataStore, normalized, de-duplicated, and converted to a `MutableStateFlow` via `toMutableStateFlow(scope, Settings.dummy())`.

Use patterns:

- Compose: `rememberUserSettingsState()` or ViewModel `settingsStore.settingsFlow.stateIn(...)`.
- Mutations: `settingsStore.update { settings -> settings.copy(...) }` or helper methods such as `updateAssistantModel`, `updateAssistantReasoningLevel`, and `updateAssistantMcpServers`.
- Web API: `SettingsRoutes.kt` validates requests, then calls `SettingsStore` helpers or `update`.
- Web UI: `useSettingsSubscription` listens to `/api/settings/stream` and writes the current `Settings` into Zustand.

Do not maintain a second settings source in UI code.

## Chat and Conversation State

Conversation persistence lives in Room via `ConversationRepository`, while live generation/session state lives in `ChatService`.

Important boundaries:

- `ConversationRepository` stores/retrieves conversations and message nodes, maintains FTS, and cleans files on delete.
- `ChatService` handles active conversations, generation jobs, errors, tool approvals, title generation, edits, forks, and saves.
- `ConversationRoutes.kt` exposes list/detail/mutation/SSE APIs by combining repository state with `ChatService` generation jobs.
- Compose `ChatVM` observes `ChatService` flows and delegates actions.
- Web `useConversationList` observes list invalidation SSE and refreshes paged REST data.

Avoid writing UI code that tries to reconstruct generation state from persisted conversation rows alone; generation state is service-owned.

## Web UI Zustand State

`web-ui/app/stores/app-store.ts` combines slices:

- `settings-slice.ts`: current backend settings from SSE.
- `chat-input-slice.ts`: per-conversation chat drafts and attachments.
- `clock-slice.ts`: time-driven state.

Use slice exports (`useSettingsStore`, `useChatInputStore`, `useClockStore`) with selectors.

When adding global web state:

- Add types to `stores/slices/types.ts`.
- Add a slice file under `stores/slices` if it is a new domain.
- Compose the slice in `app-store.ts`.
- Export a focused hook alias only if components should treat it as a separate store domain.

Do not add global state for component-only UI such as a single dialog open flag.

## Web Server State and SSE

The embedded web API uses REST for snapshots/mutations and SSE for live updates:

- `/api/settings/stream` emits full `Settings` updates (`SettingsRoutes.kt`).
- `/api/conversations/stream` emits conversation-list invalidation events when assistant conversations or generation jobs change (`ConversationRoutes.kt`).
- Conversation detail streams emit snapshot/node-update/error events defined in `WebDto.kt`.

Web UI subscriptions should:

- Use `sse<T>` from `web-ui/app/services/api.ts`.
- Pass an `AbortController.signal` and abort on cleanup.
- Filter events by active assistant/conversation.
- Use typed event DTOs from `web-ui/app/types/dto.ts`.

## Derived State

- Keep sorting/merging functions close to the hook/component that owns the list. `useConversationList` owns pinned-first/update-time sorting and page merging.
- Use `remember`/`useMemo` for derived lists that are expensive or passed to memoized children.
- For current assistant/model on web, use `use-current-assistant.ts` and `use-current-model.ts` rather than repeating lookup logic.
- For current model on Android, use settings extension helpers such as `Settings.getCurrentChatModel()` from `PreferencesStore.kt`.

## Common Mistakes

- Duplicating settings state outside `SettingsStore`/Zustand subscription.
- Updating conversation persistence directly from UI without `ChatService` when generation/session state is involved.
- Subscribing to SSE without cleanup, causing duplicate streams after route changes.
- Treating conversation list DTOs as full conversations; list DTOs intentionally omit message nodes.
- Promoting transient UI state to global Zustand or DataStore.
