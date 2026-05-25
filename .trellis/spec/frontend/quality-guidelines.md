# Quality Guidelines

Frontend quality means preserving current UI patterns, avoiding duplicated contracts, and keeping Android Compose and `web-ui` responsive during streaming chat workloads.

## Compose Quality Rules

- Collect flows with `collectAsStateWithLifecycle`, as `SettingProviderPage.kt` and `rememberUserSettingsState()` do.
- Keep business actions in ViewModels/services. Composables should call methods such as `vm.updateSettings(...)`, `chatService`-backed ViewModel actions, or callback props.
- Use existing shared components before creating new UI primitives. Settings-like rows should use `FormItem`; chat errors should use `ErrorCardsDisplay`; page navigation should use `BackButton`.
- Use stable item keys in lazy lists/grids. `SettingProviderPage.kt` keys providers by `provider.id` in `LazyVerticalStaggeredGrid`.
- Avoid storing large input/attachment state in saved instance state. `ChatVM` owns `ChatInputState` specifically to avoid `TransactionTooLargeException`.
- Use `remember` for derived UI state that depends on stable inputs, such as filtered provider lists.

## Compose Localization

Android user-facing strings in localized screens should use `stringResource(R.string...)`. Existing settings/chat components use string resources such as `R.string.setting_provider_page_title`, `R.string.setting_provider_page_search_providers`, and chat error strings.

If a task explicitly requests localization, update all supported Android resource languages (`values`, `values-zh`, `values-ja`, `values-zh-rTW`, `values-ko-rKR`, `values-ru`) using the project localization workflow. If localization is not requested, follow nearby file style and avoid broad translation churn.

## Compose Accessibility and UX

- Provide `contentDescription` for interactive icon-only buttons. Decorative icons may use `null`.
- Keep touch targets and padding consistent with Material 3 components.
- Use `imePadding` for input-heavy scroll areas.
- Keep haptics scoped to interactions that already use them, such as reorder drag handles in `SettingProviderPage.kt`.
- Show recoverable errors through toasts or error cards rather than crashing composables.

## Web UI Quality Rules

- Run API calls through `web-ui/app/services/api.ts`; do not use raw `fetch` for normal REST/SSE app APIs.
- Type API responses/events with `web-ui/app/types` contracts. `sse<T>` should be called with the exact event DTO type.
- Use abortable effects for SSE. `use-settings-subscription.ts` and `use-conversation-list.ts` are the reference patterns.
- Use Zustand selectors to avoid broad re-renders during streaming updates.
- Keep message rendering memoized and type-dispatched. `MessageParts` is memoized and groups reasoning/tool steps before rendering content parts.
- Use `resolveFileUrl` from `web-ui/app/lib/files.ts` when rendering backend/local file URLs.

## Web Localization

`web-ui` uses i18next with namespaces under `web-ui/app/locales`:

- `common`
- `input`
- `markdown`
- `message`

Use `useTranslation()` or `useTranslation("namespace")`, and prefer namespace-qualified keys (`t("input:model_list.title")`) when reading outside the default namespace. Add keys to both `zh-CN` and `en-US` when adding localized web UI text.

## Performance Guidelines

Streaming chat can update UI frequently. Keep hot paths lean:

- Avoid recomputing full conversation/message structures in every render; derive with `remember`/`useMemo` when needed.
- Do not load full conversation message nodes for sidebar/list UI. Backend list DTOs intentionally return summaries only.
- Batch invalidations where appropriate. `useConversationList` debounces list refreshes by 250 ms after SSE invalidation events.
- Avoid logging every token or rendering excessively large hidden DOM trees for message parts.
- Keep image/video/audio/document rendering behind part-specific components so URL/file handling is centralized.

## Verification Commands

Use checks that match the surface touched:

- Android Compose/app changes: `./gradlew :app:compileDebugKotlin`
- Shared AI message/type changes: `./gradlew :ai:compileDebugKotlin` plus app compile if app UI consumes it.
- Web UI changes from `web-ui`: `pnpm run typecheck`, `pnpm run fmt:check`, and optionally `pnpm run build` when static integration matters.
- Documentation-only spec changes: template-term search and index-link validation are sufficient.

## Review Checklist

- UI state owner is appropriate: local state, ViewModel, service/DataStore, or Zustand slice.
- Shared contracts are imported from central types/models, not redefined locally.
- Compose flows are lifecycle-aware.
- React effects clean up timers, subscriptions, and abort controllers.
- User-facing strings follow the localization pattern of the surrounding files.
- API errors use existing `ApiError`/Ktor `ErrorResponse` handling.
- Message part changes update Android, web, provider conversion, and serialization together.

## Forbidden Patterns

- Blocking network/file/database work in Compose or React render paths.
- Raw `fetch` calls to `/api` when `services/api.ts` can handle the request.
- Duplicated DTO/message/settings interfaces inside components.
- SSE subscriptions without cleanup.
- Adding a second global store for data already owned by `SettingsStore`, `ChatService`, or existing Zustand slices.
- Editing generated/built web static files instead of `web-ui/app` source.
