# Directory Structure

Frontend code is split between the native Android Compose app and the embedded React web UI. Keep new code in the surface that owns the user interaction, and update both surfaces only when a shared contract changes.

## Native Android Compose UI

Compose UI lives under `app/src/main/java/me/rerere/rikkahub/ui`:

- `ui/pages`: screen-level implementations grouped by feature. Most feature directories contain a page composable and a ViewModel, for example `pages/chat/ChatPage.kt` + `ChatVM.kt`, `pages/setting/SettingPage.kt` + `SettingVM.kt`, and `pages/assistant/AssistantPage.kt` + `AssistantVM.kt`.
- `ui/components`: reusable components grouped by domain:
  - `components/ai`: chat-input/provider/model/search/MCP pickers.
  - `components/message`: message rendering, actions, branches, reasoning, tools, translation.
  - `components/richtext`: Markdown, code highlighting, LaTeX, image zoom.
  - `components/ui`: common UI primitives such as `FormItem`, `ErrorCard`, `ConfirmDialog`, `Select`, `Tag`, `TTSController`.
  - `components/nav`: navigation helpers such as `BackButton`.
  - `components/webview`: embedded WebView UI.
- `ui/hooks`: Compose state helpers and non-composable state holders, for example `Settings.kt`, `ChatInputState.kt`, `Debounce.kt`, `Lifecycle.kt`, `UseAssistant.kt`.
- `ui/context`: CompositionLocals such as `LocalNavController`, `LocalToaster`, `LocalSettings`, `LocalASRState`, `LocalTTSState`, and shared-element context.
- `ui/theme`: app themes, color helpers, and custom theme types.
- `ui/activity`: Android activities outside normal Compose navigation.

Add a new screen under `ui/pages/<feature>`. Promote UI pieces to `ui/components` only when reused or clearly cross-screen. Keep screen-specific helper composables private in the page file or feature directory.

## Compose Page Pattern

Use existing pages as references:

- `ui/pages/setting/SettingProviderPage.kt` shows common page scaffolding: `Scaffold`, `LargeFlexibleTopAppBar`, `BackButton`, Material 3 colors, `collectAsStateWithLifecycle`, `remember` state, `rememberCoroutineScope`, and Koin ViewModel injection.
- `ui/pages/chat/ChatVM.kt` shows ViewModel state ownership for conversations, generation jobs, settings, chat input state, and actions.
- `ui/components/ui/Form.kt` provides `FormItem` for settings forms.
- `ui/components/ui/ErrorCard.kt` provides a reusable error-card display pattern.

## Embedded `web-ui` Structure

`web-ui/app` is a React Router 7 SPA embedded behind the Android/Ktor web server:

- `routes`: type-safe route modules. `routes.ts` maps `/` to `routes/home.tsx` and `/c/:id` to `routes/c.$id.tsx`; both re-export the conversation UI from `routes/conversations.tsx`.
- `components/ui`: shadcn/Radix/Tailwind base components.
- `components/message`: message container, dispatch, and part components under `components/message/parts`.
- `components/input`: chat input, model list, and picker components.
- `components/markdown`: Markdown rendering and code block handling.
- `components/workbench`: workbench host/context/code preview helpers.
- `hooks`: reusable React hooks such as `use-conversation-list.ts`, `use-current-assistant.ts`, `use-current-model.ts`, `use-picker-popover.ts`, and `use-mobile.ts`.
- `services/api.ts`: single ky REST client and SSE parser.
- `stores`: Zustand store composition and slices. `app-store.ts` combines settings, chat input, and clock slices.
- `types`: TypeScript contracts mirroring Kotlin message/settings/conversation/web DTOs.
- `lib`: frontend utilities such as file URL resolution, display helpers, error helpers, clipboard, and Tailwind class merging.
- `locales`: i18next JSON resources by namespace/language.

Use the `~` path alias for imports from `web-ui/app`, as existing files do (`~/services/api`, `~/types`, `~/components/ui/button`).

## Web Build Boundary

The web UI build output is copied into the Kotlin `web` module:

- `web-ui/package.json` defines `build` as `react-router build && tsx copy.ts`.
- `web/src/main/java/me/rerere/rikkahub/web/Entry.kt` serves static resources from `web/src/main/resources/static` and falls back to the SPA index.
- Ktor API endpoints consumed by web UI live in `app/src/main/java/me/rerere/rikkahub/web/routes`.

Do not edit built static resources directly. Change `web-ui/app`, run the web build when needed, and let `copy.ts` update `web/src/main/resources/static`.

## Placement Rules

- New Android setting UI: page or section under `ui/pages/setting`, shared rows with `FormItem` in `ui/components/ui` if reused.
- New Android chat component: `ui/components/ai` for input/pickers or `ui/components/message` for rendered message content.
- New Android state helper: `ui/hooks` if UI-only; ViewModel if screen/service state must survive recomposition.
- New web API call: add method/use site through `web-ui/app/services/api.ts` rather than raw `fetch`.
- New web global state: add a Zustand slice under `web-ui/app/stores/slices` and compose it in `app-store.ts` only if it is shared across routes/components.
- New web local UI behavior: keep it in component `useState`/`useMemo` or a hook under `web-ui/app/hooks`.
- New message part: update Kotlin `UIMessagePart`, web `types/parts.ts`, web `components/message/parts`, web `message-part.tsx`, and Android message rendering.

## Naming Conventions

- Compose pages and ViewModels use PascalCase with `Page`/`VM` suffixes (`SettingWebPage.kt`, `StatsVM.kt`).
- Compose reusable state helpers use descriptive names (`ChatInputState`, `rememberUserSettingsState`).
- Web component files use kebab-case (`conversation-sidebar.tsx`, `chat-message.tsx`, `reasoning-step-part.tsx`) and export PascalCase React components.
- Web hook files use `use-*` names and export `useSomething` functions.
- Web type files group contracts by domain (`parts.ts`, `message.ts`, `conversation.ts`, `dto.ts`, `settings.ts`).

## Anti-Patterns

- Do not construct repositories/services directly in composables; use Koin ViewModels or CompositionLocals.
- Do not put screen-only Compose state into global app settings.
- Do not duplicate API DTO definitions in components; import from `web-ui/app/types`.
- Do not add route-specific web API fetch code outside `services/api.ts` helpers and hooks.
- Do not edit `web/src/main/resources/static` as source; it is build output from `web-ui`.
