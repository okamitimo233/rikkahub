# Frontend Development Guidelines

RikkaHub has two frontend surfaces:

- Native Android UI in `app/src/main/java/me/rerere/rikkahub/ui`, built with Jetpack Compose, Material 3, Navigation 3, Koin ViewModels, and lifecycle-aware Flow collection.
- Embedded web UI in `web-ui/app`, built with React Router 7, React 19, TypeScript, Tailwind CSS v4, shadcn/Radix components, Zustand, ky, SSE, and i18next.

## Pre-Development Checklist

- Identify whether the change belongs to Compose UI, `web-ui`, or both.
- For Compose pages, inspect nearby pages under `ui/pages`, shared components under `ui/components`, local hooks under `ui/hooks`, and CompositionLocals under `ui/context`.
- For web UI, inspect `web-ui/app/routes`, `components`, `hooks`, `stores`, `services/api.ts`, and `types`.
- For shared message/settings/conversation changes, inspect Kotlin contracts in `ai/src/main/java/me/rerere/ai/ui/Message.kt`, `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`, and `app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt` before editing frontend types.
- Prefer existing components: Compose `FormItem`, `BackButton`, `ErrorCard`, message/input components; web `~/components/ui/*`, message part components, and existing hooks/stores.

## Guidelines Index

- [Directory Structure](./directory-structure.md) - Compose and `web-ui` folder ownership and placement rules.
- [Component Guidelines](./component-guidelines.md) - Compose page/component patterns and React component patterns.
- [Hook Guidelines](./hook-guidelines.md) - Compose hooks/classes and React custom hook conventions.
- [State Management](./state-management.md) - ViewModels, DataStore flows, ChatService state, Zustand slices, and SSE subscriptions.
- [Type Safety](./type-safety.md) - Kotlin serialization/domain types and TypeScript DTO/type mirroring.
- [Quality Guidelines](./quality-guidelines.md) - UI validation, accessibility, performance, localization, and verification.

## Quality Check

- Compose code collects flows with `collectAsStateWithLifecycle` or ViewModel-owned `StateFlow`, not raw collection in composables.
- Compose pages use Koin ViewModels (`koinViewModel`) and existing CompositionLocals (`LocalNavController`, `LocalToaster`, `LocalSettings`) instead of manual service construction.
- `web-ui` API calls go through `web-ui/app/services/api.ts`, and long-lived subscriptions use abortable SSE.
- `web-ui` types remain aligned with Kotlin DTOs and serial names.
- UI strings follow the repository's localization expectations: Android uses `stringResource` for localized pages; `web-ui` uses i18next namespaces when adding user-facing translated web strings.
- Run relevant checks: Android compile/lint for Compose changes, `pnpm run typecheck`/`pnpm run fmt:check` under `web-ui` for TypeScript changes.
