# Hook Guidelines

RikkaHub uses two hook styles: Compose helpers/state holders in Android and React hooks in `web-ui`. Hooks should encapsulate UI state or subscriptions, not duplicate service/repository business logic.

## Compose Hooks and State Holders

Android UI helpers live in `app/src/main/java/me/rerere/rikkahub/ui/hooks`.

Representative patterns:

- `Settings.kt`: `rememberUserSettingsState()` injects `SettingsStore` with Koin and collects `settingsFlow` through `collectAsStateWithLifecycle(initialValue = Settings.dummy())`.
- `ChatInputState.kt`: a plain state-holder class stores `TextFieldState`, attachments, edit state, and file-removal decisions. `ChatVM` owns an instance so input state survives recomposition and avoids `TransactionTooLargeException`.
- `Debounce.kt`, `Lifecycle.kt`, `ImeAutoScroller.kt`: UI behavior helpers that belong near Compose code, not in data/services.
- `ASR.kt`, `TTS.kt`, `UseAssistant.kt`: feature-specific UI hooks that bridge settings/service state into composables.

Guidelines:

- Prefix composable state helpers with `remember...` when they allocate or collect state in composition.
- Use lifecycle-aware collection (`collectAsStateWithLifecycle`) for flows observed by composables.
- Keep plain classes like `ChatInputState` free of Android lifecycle references unless needed; let ViewModels own them when state must outlive recomposition.
- Do not perform repository writes inside a hook unless the hook is explicitly a UI adapter around an existing ViewModel/service action.

## Compose Side Effects

- Use `LaunchedEffect(key)` for lifecycle-bound effects such as auto-dismiss timers. `ErrorCard.kt` uses `LaunchedEffect(error.id)` plus `delay(5000)`.
- Use `rememberCoroutineScope` for UI-only actions from callbacks, such as scrolling a lazy grid in `SettingProviderPage.kt` or clipboard writes in `ErrorCard.kt`.
- Use ViewModel functions for durable operations (save settings, send messages, delete files, start generation).

Avoid launching unkeyed effects that restart on every recomposition.

## React Custom Hooks

Web hooks live in `web-ui/app/hooks` and `web-ui/app/stores/hooks`.

Representative patterns:

- `hooks/use-conversation-list.ts`: owns paged conversation-list state, debounced SSE invalidation, stale-request protection with epochs, active conversation selection, and `loadMore`.
- `stores/hooks/use-settings-subscription.ts`: subscribes once to `/api/settings/stream`, writes updates into Zustand, and aborts the SSE connection on unmount.
- `hooks/use-current-assistant.ts` and `hooks/use-current-model.ts`: derive current assistant/model/provider from settings store.
- `hooks/use-picker-popover.ts`: encapsulates repeated picker popover state.

Guidelines:

- Name files `use-*.ts` and exported functions `useSomething`.
- Keep hooks focused on one state/subscription concern. If the hook manages REST + SSE + pagination, document the result shape with an interface as `UseConversationListResult` does.
- Use `AbortController` for SSE or long-running async effects and abort in cleanup.
- Use refs for mutable values used by callbacks without re-subscribing. `useConversationList` keeps `currentAssistantIdRef`, `conversationsRef`, and request epochs.
- Use `React.useCallback`/`React.useMemo` for callbacks/derived data passed to memoized children or effects.

## Data Fetching Hooks

All web API access should go through `web-ui/app/services/api.ts`:

- REST: `api.get<T>`, `api.post<T>`, `api.postMultipart<T>`, `api.put<T>`, `api.patch<T>`, `api.delete<T>`.
- SSE: `sse<T>(url, callbacks, { signal })`.

Hook rules:

- Do not call raw `fetch` for app API endpoints unless `api.ts` cannot support the protocol; extend `api.ts` instead.
- Use TypeScript DTOs from `web-ui/app/types` for response/event shapes.
- Guard against stale async results when route IDs or assistants change. `useConversationList` uses `listRequestEpochRef` and an `active` flag.
- Keep retry/debounce behavior local to the hook that owns the subscription. `useConversationList` batches invalidations with a 250 ms timer.

## Store Hooks

Zustand store hooks are exported from `web-ui/app/stores/app-store.ts`:

- `useSettingsStore`
- `useChatInputStore`
- `useClockStore`

Use selector functions to avoid broad subscriptions:

```ts
const settings = useSettingsStore((state) => state.settings);
const setSettings = useSettingsStore((state) => state.setSettings);
```

Do not read the entire store in components that only need one value.

## Common Mistakes

- Collecting a Flow in Compose without lifecycle awareness.
- Creating a new ViewModel/service inside a hook or composable instead of using Koin.
- Starting an SSE connection in React without aborting it in cleanup.
- Letting stale REST responses update state after the selected assistant/conversation changes.
- Duplicating current-assistant/current-model derivation in multiple components instead of using existing hooks.
