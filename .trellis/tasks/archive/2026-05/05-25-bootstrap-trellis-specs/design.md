# Design

## Scope and Boundaries

Refresh only `.trellis/spec/` documentation. The specs should describe how to work in the existing repository; they must not prescribe source changes or create new architecture.

The final spec set will keep the current top-level split:

- `backend/`: Kotlin modules, data layer, Room/DataStore, AI providers, message transformation, embedded Ktor server, logging/errors, and validation guidance for non-UI logic.
- `frontend/`: Jetpack Compose app UI plus `web-ui` React/TypeScript UI, state, components, hooks, and type contracts.
- `guides/`: thinking guides that supplement project-specific specs.

## Evidence Sources

Use the current codebase as authority:

- Gradle module declarations in `settings.gradle.kts` and app dependencies in `app/build.gradle.kts`.
- Android data and service patterns in `app/src/main/java/me/rerere/rikkahub/data`, `service`, `web`, and `di`.
- Provider abstractions in `ai/src/main/java/me/rerere/ai/provider` and concrete providers such as `OpenAIProvider` and `ClaudeProvider`.
- Compose UI patterns in `app/src/main/java/me/rerere/rikkahub/ui/pages`, `ui/components`, and `ui/hooks`.
- Web UI patterns in `web-ui/app/components`, `hooks`, `services`, `stores`, `types`, and routes.

## Spec Shape

Keep the existing file names where they can be made meaningful:

- Backend:
  - `directory-structure.md`: module/package ownership and where new code belongs.
  - `database-guidelines.md`: Room entities/DAOs/repositories/migrations and FTS/message-node persistence.
  - `error-handling.md`: coroutine, route, provider, and UI-facing error patterns.
  - `logging-guidelines.md`: Android Log usage, request logging boundaries, and sensitive-data cautions.
  - `quality-guidelines.md`: Kotlin/service/provider/serialization quality rules.
- Frontend:
  - `directory-structure.md`: Compose UI and `web-ui` folder ownership.
  - `component-guidelines.md`: Compose page/component patterns and React component patterns.
  - `hook-guidelines.md`: Compose state hooks/classes and React hooks.
  - `state-management.md`: ViewModel/service flows, DataStore settings, Zustand-style web stores, SSE subscriptions.
  - `type-safety.md`: Kotlin serialization/domain types and TypeScript DTO mirroring.
  - `quality-guidelines.md`: UI validation, accessibility, performance, localization boundaries.

## Compatibility and Rollback

Documentation-only changes are reversible by git diff. No production code, dependencies, generated files, schemas, or build configuration should be modified.

## Trade-offs

- Keep fewer, denser files instead of adding many narrow specs, because Trellis indexes are easier to scan when each existing category has high-signal guidance.
- Include representative examples rather than exhaustive class listings, because code remains the source of truth for details.
